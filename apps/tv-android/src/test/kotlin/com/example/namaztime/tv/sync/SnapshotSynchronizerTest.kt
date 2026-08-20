package com.example.namaztime.tv.sync

import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotSynchronizerTest {
    private val temporaryDirectories = mutableListOf<java.io.File>()
    private val credentials = DeviceSyncCredentials(
        deviceId = "device-fixture-0001",
        token = "fixture-device-token-not-production",
        manifestUrl = "https://api.example.invalid/v1/devices/device-fixture-0001/manifest",
        mosqueId = "synthetic-verification-mosque",
        mosqueTimezone = "Europe/Ulyanovsk",
    )

    @After
    fun removeTemporaryDirectories() {
        temporaryDirectories.forEach { directory -> directory.deleteRecursively() }
    }

    @Test
    fun status200StagesValidatesActivatesAndThenUses304WithoutDownload() = runTest {
        val snapshot = "signed-snapshot-fixture".encodeToByteArray()
        val manifest = manifest(snapshot, version = 1, snapshotId = "snapshot-fixture-0001")
        val transport = QueueTransport(
            SyncHttpResponse(200, mapOf("ETag" to "\"manifest-v1\""), manifest),
            SyncHttpResponse(200, emptyMap(), snapshot),
            SyncHttpResponse(304, mapOf("ETag" to "\"manifest-v1\""), byteArrayOf()),
        )
        val activator = RecordingActivator()
        val storage = FileSnapshotSyncStorage(temporaryDirectory())
        val synchronizer = SnapshotSynchronizer(transport, storage, activator)

        val updated = synchronizer.sync(credentials)
        val unchanged = synchronizer.sync(credentials)

        assertEquals(SnapshotSyncResult.Updated("snapshot-fixture-0001", null), updated)
        assertEquals(SnapshotSyncResult.NotModified("snapshot-fixture-0001"), unchanged)
        assertEquals(listOf("snapshot-fixture-0001"), activator.snapshotIds)
        assertEquals(3, transport.requests.size)
        assertEquals("\"manifest-v1\"", transport.requests.last().ifNoneMatch)
        assertEquals(null, storage.load().pendingManifest)
        assertEquals(1, storage.load().acceptedManifestVersion)
    }

    @Test
    fun transportFailuresAreBoundedAndNeverCallActivator() = runTest {
        val cases = listOf(
            SyncHttpResponse(401, emptyMap(), byteArrayOf()) to SnapshotSyncResult.AuthFailure,
            SyncHttpResponse(404, emptyMap(), byteArrayOf()) to SnapshotSyncResult.Rejected("manifest_not_found"),
            SyncHttpResponse(500, emptyMap(), byteArrayOf()) to SnapshotSyncResult.Retry("manifest_server_error"),
        )
        cases.forEach { (response, expected) ->
            val activator = RecordingActivator()
            val result = SnapshotSynchronizer(
                QueueTransport(response),
                FileSnapshotSyncStorage(temporaryDirectory()),
                activator,
            ).sync(credentials)

            assertEquals(expected, result)
            assertTrue(activator.snapshotIds.isEmpty())
        }

        val activator = RecordingActivator()
        val timeout = SnapshotSynchronizer(
            ThrowingTransport(IOException("synthetic timeout")),
            FileSnapshotSyncStorage(temporaryDirectory()),
            activator,
        ).sync(credentials)
        assertEquals(SnapshotSyncResult.Retry("manifest_io"), timeout)
        assertTrue(activator.snapshotIds.isEmpty())
    }

    @Test
    fun tamperedOrTruncatedSnapshotIsRejectedBeforeActivation() = runTest {
        val expected = "signed-snapshot-fixture".encodeToByteArray()
        val manifest = manifest(expected, version = 1, snapshotId = "snapshot-fixture-0001")
        listOf(
            "signed-snapshot-tampered".encodeToByteArray() to "snapshot_byte_length_mismatch",
            expected.copyOf().also { it[0] = 'X'.code.toByte() } to "snapshot_sha256_mismatch",
        ).forEach { (downloaded, code) ->
            val activator = RecordingActivator()
            val result = SnapshotSynchronizer(
                QueueTransport(
                    SyncHttpResponse(200, mapOf("ETag" to "\"manifest-v1\""), manifest),
                    SyncHttpResponse(200, emptyMap(), downloaded),
                ),
                FileSnapshotSyncStorage(temporaryDirectory()),
                activator,
            ).sync(credentials)

            assertEquals(SnapshotSyncResult.Rejected(code), result)
            assertTrue(activator.snapshotIds.isEmpty())
        }
    }

    @Test
    fun processInterruptionAfterDurableStageResumesWithoutNetwork() = runTest {
        val root = temporaryDirectory()
        val snapshot = "signed-snapshot-fixture".encodeToByteArray()
        val manifest = manifest(snapshot, version = 2, snapshotId = "snapshot-fixture-0002")
        val firstStorage = FileSnapshotSyncStorage(root)
        val interrupted = SnapshotSynchronizer(
            QueueTransport(
                SyncHttpResponse(200, mapOf("ETag" to "\"manifest-v2\""), manifest),
                SyncHttpResponse(200, emptyMap(), snapshot),
            ),
            firstStorage,
            RecordingActivator(),
            interruptionHook = SnapshotSyncInterruptionHook {
                throw CancellationException("synthetic process stop after staging")
            },
        )

        try {
            interrupted.sync(credentials)
            throw AssertionError("expected cancellation")
        } catch (_: CancellationException) {
            // The durable stage and journal simulate state visible after a new process starts.
        }

        val activator = RecordingActivator()
        val resumed = SnapshotSynchronizer(
            ThrowingTransport(AssertionError("network must not be called while a stage is pending")),
            FileSnapshotSyncStorage(root),
            activator,
        ).sync(credentials)

        assertEquals(SnapshotSyncResult.Updated("snapshot-fixture-0002", null), resumed)
        assertEquals(listOf("snapshot-fixture-0002"), activator.snapshotIds)
        assertEquals(null, FileSnapshotSyncStorage(root).load().pendingManifest)
    }

    @Test
    fun pendingStageFromPreviousProvisioningCannotActivateAfterRepairing() = runTest {
        val root = temporaryDirectory()
        val oldSnapshot = "old-signed-snapshot".encodeToByteArray()
        val oldManifest = manifest(oldSnapshot, version = 2, snapshotId = "snapshot-fixture-old1")
        val interrupted = SnapshotSynchronizer(
            QueueTransport(
                SyncHttpResponse(200, mapOf("ETag" to "\"manifest-old\""), oldManifest),
                SyncHttpResponse(200, emptyMap(), oldSnapshot),
            ),
            FileSnapshotSyncStorage(root),
            RecordingActivator(),
            interruptionHook = SnapshotSyncInterruptionHook {
                throw CancellationException("synthetic stop before re-pairing")
            },
        )
        try {
            interrupted.sync(credentials)
            throw AssertionError("expected cancellation")
        } catch (_: CancellationException) {
            // Pending state belongs to the original provisioning identity.
        }
        val newCredentials = credentials.copy(
            deviceId = "device-fixture-0002",
            token = "replacement-device-token-not-production",
            mosqueId = "replacement-mosque",
        )
        val transport = QueueTransport(
            SyncHttpResponse(404, emptyMap(), byteArrayOf()),
        )
        val activator = RecordingActivator()

        val result = SnapshotSynchronizer(
            transport,
            FileSnapshotSyncStorage(root),
            activator,
        ).sync(newCredentials)

        assertEquals(SnapshotSyncResult.Rejected("manifest_not_found"), result)
        assertEquals(1, transport.requests.size)
        assertTrue(activator.snapshotIds.isEmpty())
        assertEquals(null, FileSnapshotSyncStorage(root).load().pendingManifest)
    }

    @Test
    fun manifestVersionCannotGoBackwardOrChangeIdentityAtSameVersion() = runTest {
        val root = temporaryDirectory()
        val storage = FileSnapshotSyncStorage(root)
        storage.save(
            SnapshotSyncCheckpoint(
                provisioningFingerprint = credentials.provisioningFingerprint(),
                acceptedManifestEtag = "\"manifest-v5\"",
                acceptedManifestVersion = 5,
                acceptedSnapshotId = "snapshot-fixture-0005",
                acceptedSnapshotSha256 = "a".repeat(64),
            ),
        )
        val bytes = "next".encodeToByteArray()
        listOf(
            manifest(bytes, version = 4, snapshotId = "snapshot-fixture-0004") to "manifest_version_downgrade",
            manifest(bytes, version = 5, snapshotId = "snapshot-fixture-changed") to "manifest_version_conflict",
        ).forEach { (body, code) ->
            val activator = RecordingActivator()
            val result = SnapshotSynchronizer(
                QueueTransport(SyncHttpResponse(200, mapOf("ETag" to "\"new\""), body)),
                storage,
                activator,
            ).sync(credentials)
            assertEquals(SnapshotSyncResult.Rejected(code), result)
            assertTrue(activator.snapshotIds.isEmpty())
        }
    }

    @Test
    fun crossOriginSnapshotUrlIsRejectedBeforeBearerTokenCanLeaveApiOrigin() = runTest {
        val snapshot = "signed-snapshot-fixture".encodeToByteArray()
        val body = manifest(snapshot, version = 1, snapshotId = "snapshot-fixture-0001")
            .decodeToString()
            .replace("https://api.example.invalid/", "https://untrusted.invalid/")
            .encodeToByteArray()
        val transport = QueueTransport(
            SyncHttpResponse(200, mapOf("ETag" to "\"manifest-v1\""), body),
        )
        val activator = RecordingActivator()

        val result = SnapshotSynchronizer(
            transport,
            FileSnapshotSyncStorage(temporaryDirectory()),
            activator,
        ).sync(credentials)

        assertEquals(SnapshotSyncResult.Rejected("snapshot_origin_mismatch"), result)
        assertEquals(1, transport.requests.size)
        assertTrue(activator.snapshotIds.isEmpty())
    }

    @Test
    fun protocolEdgeCasesFailClosedBeforeSnapshotDownload() = runTest {
        val snapshot = "signed-snapshot-fixture".encodeToByteArray()
        val valid = manifest(snapshot, version = 1, snapshotId = "snapshot-fixture-0001")
            .decodeToString()
        val cases = listOf(
            SyncHttpResponse(304, emptyMap(), byteArrayOf()) to "manifest_304_without_cache",
            SyncHttpResponse(
                200,
                mapOf("ETag" to "\"manifest-v1\""),
                valid.replace(
                    "\"signing_key_id\": \"phase1-fixture-key-2026-08\"",
                    "\"signing_key_id\": \"phase1-fixture-key-2026-08\",\n  \"minimum_app_version\": null",
                ).encodeToByteArray(),
            ) to "manifest_invalid",
            SyncHttpResponse(
                200,
                mapOf("ETag" to "\"manifest-v1\""),
                valid.replace(
                    "\"signing_key_id\": \"phase1-fixture-key-2026-08\"",
                    "\"signing_key_id\": \"phase1-fixture-key-2026-08\",\n  \"server_time\": \"not-rfc3339\"",
                ).encodeToByteArray(),
            ) to "manifest_invalid",
            SyncHttpResponse(
                200,
                mapOf("ETag" to "\"manifest-v1\""),
                valid.replace(
                    "\"signing_key_id\": \"phase1-fixture-key-2026-08\"",
                    "\"signing_key_id\": \"phase1-fixture-key-2026-08\",\n  \"minimum_app_version\": \"99.0.0\"",
                ).encodeToByteArray(),
            ) to "minimum_app_version_required",
            SyncHttpResponse(
                200,
                mapOf("ETag" to "\"manifest-v1\""),
                valid.replace(
                    "\"signing_key_id\": \"phase1-fixture-key-2026-08\"",
                    "\"signing_key_id\": \"phase1-fixture-key-2026-08\",\n  \"assets\": [{\"asset_id\":\"asset-0001\",\"url\":\"https://api.example.invalid/assets/asset-0001\",\"sha256\":\"${"a".repeat(64)}\",\"byte_length\":1024,\"media_type\":\"image/png\"}]",
                ).encodeToByteArray(),
            ) to "manifest_assets_unsupported",
        )
        cases.forEach { (response, expectedCode) ->
            val transport = QueueTransport(response)
            val result = SnapshotSynchronizer(
                transport,
                FileSnapshotSyncStorage(temporaryDirectory()),
                RecordingActivator(),
            ).sync(credentials)
            assertEquals(SnapshotSyncResult.Rejected(expectedCode), result)
            assertEquals(1, transport.requests.size)
        }
    }

    private fun manifest(snapshot: ByteArray, version: Long, snapshotId: String): ByteArray =
        """
        {
          "manifest_version": $version,
          "snapshot_id": "$snapshotId",
          "snapshot_url": "https://api.example.invalid/v1/snapshots/$snapshotId",
          "snapshot_sha256": "${snapshot.sha256()}",
          "snapshot_byte_length": ${snapshot.size},
          "signing_key_id": "phase1-fixture-key-2026-08"
        }
        """.trimIndent().encodeToByteArray()

    private fun temporaryDirectory(): java.io.File =
        Files.createTempDirectory("namaz-sync-test-").toFile().also(temporaryDirectories::add)
}

private class RecordingActivator : SnapshotActivator {
    val snapshotIds = mutableListOf<String>()

    override suspend fun activate(bytes: ByteArray, manifest: DeviceSnapshotManifest): String? {
        snapshotIds += manifest.snapshotId
        return null
    }
}

private class QueueTransport(vararg responses: SyncHttpResponse) : DeviceSyncTransport {
    private val responses = ArrayDeque(responses.toList())
    val requests = mutableListOf<SyncHttpRequest>()

    override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse {
        requests += request
        return responses.removeFirstOrNull() ?: error("unexpected request $request")
    }
}

private class ThrowingTransport(private val error: Throwable) : DeviceSyncTransport {
    override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse = throw error
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
