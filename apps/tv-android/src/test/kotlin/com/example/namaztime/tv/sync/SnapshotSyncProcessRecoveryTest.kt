package com.example.namaztime.tv.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.namaztime.tv.data.local.BeforeSnapshotActivation
import com.example.namaztime.tv.data.local.NamazDatabase
import com.example.namaztime.tv.data.local.SnapshotImporter
import com.example.namaztime.tv.data.snapshot.AndroidSnapshotAssetSource
import com.example.namaztime.tv.data.snapshot.SnapshotActivationGate
import com.example.namaztime.tv.data.snapshot.SnapshotAuthenticityVerifier
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class SnapshotSyncProcessRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseNames = mutableListOf<String>()
    private val directories = mutableListOf<File>()

    @After
    fun cleanUp() {
        databaseNames.forEach(context::deleteDatabase)
        directories.forEach(File::deleteRecursively)
    }

    @Test
    fun interruptionInsideRoomImportRollsBackThenNewProcessResumesStage() = runTest {
        val databaseName = databaseName()
        var database = openDatabase(databaseName)
        val bundled = SnapshotActivationGate.bundledSynthetic(
            AndroidSnapshotAssetSource(context).read(),
        )
        SnapshotImporter(database).importAndActivate(bundled)
        val fixture = fixture()
        val storageRoot = directory()
        val interruptedActivator = AuthenticatedRoomSnapshotActivator(
            fixture.verifier,
            SnapshotImporter(
                database,
                BeforeSnapshotActivation {
                    throw CancellationException("synthetic process stop inside Room transaction")
                },
            ),
            database.snapshotDao(),
            "synthetic-verification-mosque",
            "Europe/Ulyanovsk",
        )
        try {
            synchronizer(storageRoot, fixture, interruptedActivator).sync(credentials())
            throw AssertionError("expected cancellation")
        } catch (_: CancellationException) {
            // Simulate abrupt process loss by closing and rebuilding every dependency.
        }
        assertEquals(bundled.payload.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
        database.close()

        database = openDatabase(databaseName)
        val resumed = SnapshotSynchronizer(
            ProcessThrowingTransport(),
            FileSnapshotSyncStorage(storageRoot),
            AuthenticatedRoomSnapshotActivator(
                fixture.verifier,
                SnapshotImporter(database),
                database.snapshotDao(),
                "synthetic-verification-mosque",
                "Europe/Ulyanovsk",
            ),
        ).sync(credentials())

        assertEquals(
            SnapshotSyncResult.Updated(fixture.manifest.snapshotId, bundled.payload.snapshotId),
            resumed,
        )
        assertEquals(fixture.manifest.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
        assertEquals(bundled.payload.snapshotId, database.snapshotDao().getSelection()?.previousSnapshotId)
        database.close()
    }

    @Test
    fun interruptionAfterRoomCommitButBeforeCheckpointIsIdempotentAfterReopen() = runTest {
        val databaseName = databaseName()
        var database = openDatabase(databaseName)
        val bundled = SnapshotActivationGate.bundledSynthetic(
            AndroidSnapshotAssetSource(context).read(),
        )
        SnapshotImporter(database).importAndActivate(bundled)
        val fixture = fixture()
        val storageRoot = directory()
        val interrupted = synchronizer(
            storageRoot,
            fixture,
            AuthenticatedRoomSnapshotActivator(
                fixture.verifier,
                SnapshotImporter(database),
                database.snapshotDao(),
                "synthetic-verification-mosque",
                "Europe/Ulyanovsk",
            ),
            hook = object : SnapshotSyncInterruptionHook {
                override suspend fun afterStage() = Unit

                override suspend fun afterActivation() {
                    throw CancellationException("synthetic process stop after Room commit")
                }
            },
        )
        try {
            interrupted.sync(credentials())
            throw AssertionError("expected cancellation")
        } catch (_: CancellationException) {
            // Room commit is durable while the sync checkpoint remains pending.
        }
        assertEquals(fixture.manifest.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
        database.close()

        database = openDatabase(databaseName)
        val resumed = SnapshotSynchronizer(
            ProcessThrowingTransport(),
            FileSnapshotSyncStorage(storageRoot),
            AuthenticatedRoomSnapshotActivator(
                fixture.verifier,
                SnapshotImporter(database),
                database.snapshotDao(),
                "synthetic-verification-mosque",
                "Europe/Ulyanovsk",
            ),
        ).sync(credentials())

        assertEquals(
            SnapshotSyncResult.Updated(fixture.manifest.snapshotId, bundled.payload.snapshotId),
            resumed,
        )
        assertEquals(fixture.manifest.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
        assertEquals(bundled.payload.snapshotId, database.snapshotDao().getSelection()?.previousSnapshotId)
        database.close()
    }

    private fun synchronizer(
        storageRoot: File,
        fixture: ProcessFixture,
        activator: SnapshotActivator,
        hook: SnapshotSyncInterruptionHook = SnapshotSyncInterruptionHook {},
    ) = SnapshotSynchronizer(
        ProcessQueueTransport(
            SyncHttpResponse(200, mapOf("ETag" to "\"manifest-v1\""), fixture.manifestJson),
            SyncHttpResponse(200, emptyMap(), fixture.snapshot),
        ),
        FileSnapshotSyncStorage(storageRoot),
        activator,
        hook,
    )

    private fun fixture(): ProcessFixture {
        val snapshot = File("../../fixtures/verification/synthetic-signed-snapshot.json").readBytes()
        val key = Json.parseToJsonElement(
            File("../../fixtures/verification/phase1-public-key.json").readText(),
        ).jsonObject
        val keyId = key.getValue("signing_key_id").jsonPrimitive.content
        val publicKey = Base64.getDecoder().decode(
            key.getValue("public_key_ed25519_base64").jsonPrimitive.content,
        )
        val hash = snapshot.sha256ForProcessTest()
        val manifest = DeviceSnapshotManifest(
            manifestVersion = 1,
            snapshotId = "synthetic-android-verification-v1",
            snapshotUrl = "https://api.example.invalid/v1/snapshots/synthetic-android-verification-v1",
            snapshotSha256 = hash,
            snapshotByteLength = snapshot.size.toLong(),
            signingKeyId = keyId,
        )
        val manifestJson = """
            {
              "manifest_version": 1,
              "snapshot_id": "${manifest.snapshotId}",
              "snapshot_url": "${manifest.snapshotUrl}",
              "snapshot_sha256": "$hash",
              "snapshot_byte_length": ${snapshot.size},
              "signing_key_id": "$keyId"
            }
        """.trimIndent().encodeToByteArray()
        return ProcessFixture(
            snapshot,
            manifest,
            manifestJson,
            SnapshotAuthenticityVerifier(mapOf(keyId to publicKey)),
        )
    }

    private fun credentials() = DeviceSyncCredentials(
        "device-fixture-0001",
        "fixture-device-token-not-production",
        "https://api.example.invalid/v1/devices/device-fixture-0001/manifest",
        "synthetic-verification-mosque",
        "Europe/Ulyanovsk",
    )

    private fun openDatabase(name: String): NamazDatabase = Room.databaseBuilder(
        context,
        NamazDatabase::class.java,
        name,
    ).allowMainThreadQueries().build()

    private fun databaseName(): String = "sync-process-${System.nanoTime()}.db".also(databaseNames::add)

    private fun directory(): File = Files.createTempDirectory("namaz-sync-process-")
        .toFile().also(directories::add)
}

private data class ProcessFixture(
    val snapshot: ByteArray,
    val manifest: DeviceSnapshotManifest,
    val manifestJson: ByteArray,
    val verifier: SnapshotAuthenticityVerifier,
)

private class ProcessQueueTransport(vararg responses: SyncHttpResponse) : DeviceSyncTransport {
    private val responses = ArrayDeque(responses.toList())
    override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse = responses.removeFirst()
}

private class ProcessThrowingTransport : DeviceSyncTransport {
    override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse =
        throw AssertionError("network must not run while durable stage is pending")
}

private fun ByteArray.sha256ForProcessTest(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
