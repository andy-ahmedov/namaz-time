package com.example.namaztime.tv.sync

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import com.example.namaztime.tv.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

private const val MAX_MANIFEST_BYTES = 64 * 1024
private const val MAX_SNAPSHOT_BYTES = 5 * 1024 * 1024

@Serializable
data class DeviceSyncCredentials(
    val deviceId: String,
    val token: String,
    val manifestUrl: String,
    val mosqueId: String,
    val mosqueTimezone: String,
)

data class SyncHttpRequest(
    val url: String,
    val bearerToken: String,
    val ifNoneMatch: String? = null,
    val maximumBodyBytes: Int,
    val method: String = "GET",
    val contentType: String? = null,
    val body: ByteArray? = null,
)

data class SyncHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
        ?.value
}

fun interface DeviceSyncTransport {
    @Throws(IOException::class)
    suspend fun execute(request: SyncHttpRequest): SyncHttpResponse
}

@Serializable
data class DeviceSnapshotManifest(
    @SerialName("manifest_version") val manifestVersion: Long,
    @SerialName("snapshot_id") val snapshotId: String,
    @SerialName("snapshot_url") val snapshotUrl: String,
    @SerialName("snapshot_sha256") val snapshotSha256: String,
    @SerialName("snapshot_byte_length") val snapshotByteLength: Long,
    @SerialName("signing_key_id") val signingKeyId: String,
    @SerialName("minimum_app_version") val minimumAppVersion: String? = null,
    @SerialName("server_time") val serverTime: String? = null,
    val assets: List<DeviceAssetManifestItem> = emptyList(),
)

@Serializable
data class DeviceAssetManifestItem(
    @SerialName("asset_id") val assetId: String,
    val url: String,
    val sha256: String,
    @SerialName("byte_length") val byteLength: Long,
    @SerialName("media_type") val mediaType: String,
)

@Serializable
data class SnapshotSyncCheckpoint(
    val provisioningFingerprint: String? = null,
    val acceptedManifestEtag: String? = null,
    val acceptedManifestVersion: Long = 0,
    val acceptedSnapshotId: String? = null,
    val acceptedSnapshotSha256: String? = null,
    val acceptedSnapshotByteLength: Long? = null,
    val acceptedSigningKeyId: String? = null,
    val pendingManifestEtag: String? = null,
    val pendingManifest: DeviceSnapshotManifest? = null,
    val lastRejectedSnapshotId: String? = null,
    val lastRejectedCode: String? = null,
)

sealed interface SnapshotSyncResult {
    data class Updated(val snapshotId: String, val previousSnapshotId: String?) : SnapshotSyncResult
    data class NotModified(val snapshotId: String?) : SnapshotSyncResult
    data class Rejected(val code: String) : SnapshotSyncResult
    data class Retry(val code: String) : SnapshotSyncResult
    data object AuthFailure : SnapshotSyncResult
}

fun interface SnapshotActivator {
    suspend fun activate(bytes: ByteArray, manifest: DeviceSnapshotManifest): String?
}

fun interface SnapshotSyncInterruptionHook {
    suspend fun afterStage()

    suspend fun afterActivation() = Unit
}

class SnapshotActivationRejectedException(val code: String) : IllegalArgumentException(code)

class SnapshotSynchronizer(
    private val transport: DeviceSyncTransport,
    private val storage: FileSnapshotSyncStorage,
    private val activator: SnapshotActivator,
    private val interruptionHook: SnapshotSyncInterruptionHook = SnapshotSyncInterruptionHook {},
    private val currentAppVersion: String = BuildConfig.VERSION_NAME,
) {
    suspend fun sync(credentials: DeviceSyncCredentials): SnapshotSyncResult {
        if (!credentials.isValid()) return SnapshotSyncResult.Rejected("credentials_invalid")
        val provisioningFingerprint = credentials.provisioningFingerprint()
        var checkpoint = try {
            storage.load()
        } catch (_: IOException) {
            return SnapshotSyncResult.Retry("sync_state_io")
        }
        if (checkpoint.provisioningFingerprint != provisioningFingerprint && checkpoint.hasState()) {
            checkpoint = try {
                storage.replaceProvisioning(provisioningFingerprint, checkpoint.pendingManifest?.snapshotId)
            } catch (_: IOException) {
                return SnapshotSyncResult.Retry("sync_state_io")
            }
        } else if (checkpoint.provisioningFingerprint == null) {
            checkpoint = checkpoint.copy(provisioningFingerprint = provisioningFingerprint)
        }
        checkpoint.pendingManifest?.let { pending ->
            return resumePending(checkpoint, pending)
        }

        val response = try {
            transport.execute(
                SyncHttpRequest(
                    url = credentials.manifestUrl,
                    bearerToken = credentials.token,
                    ifNoneMatch = checkpoint.acceptedManifestEtag,
                    maximumBodyBytes = MAX_MANIFEST_BYTES,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return SnapshotSyncResult.Retry("manifest_io")
        }
        when (response.statusCode) {
            304 -> return if (checkpoint.acceptedManifestEtag != null && checkpoint.acceptedSnapshotId != null) {
                SnapshotSyncResult.NotModified(checkpoint.acceptedSnapshotId)
            } else {
                SnapshotSyncResult.Rejected("manifest_304_without_cache")
            }
            401, 403 -> return SnapshotSyncResult.AuthFailure
            404 -> return SnapshotSyncResult.Rejected("manifest_not_found")
            in 500..599 -> return SnapshotSyncResult.Retry("manifest_server_error")
            200 -> Unit
            else -> return SnapshotSyncResult.Rejected("manifest_http_status")
        }
        val manifestEtag = response.header("ETag")
            ?.takeIf(::isStrongEtag)
            ?: return SnapshotSyncResult.Rejected("manifest_etag_invalid")
        if (response.body.size > MAX_MANIFEST_BYTES) {
            return SnapshotSyncResult.Rejected("manifest_too_large")
        }
        val manifest = try {
            val document = response.body.decodeToString(throwOnInvalidSequence = true)
            val element = manifestJson.parseToJsonElement(document)
            require(!element.containsExplicitNull())
            manifestJson.decodeFromJsonElement(DeviceSnapshotManifest.serializer(), element)
                .also(::validateManifest)
        } catch (_: Exception) {
            return SnapshotSyncResult.Rejected("manifest_invalid")
        }
        if (manifest.assets.isNotEmpty()) {
            return SnapshotSyncResult.Rejected("manifest_assets_unsupported")
        }
        manifest.minimumAppVersion?.let { minimum ->
            if (!versionAtLeast(currentAppVersion, minimum)) {
                return SnapshotSyncResult.Rejected("minimum_app_version_required")
            }
        }
        if (!sameHttpsOrigin(credentials.manifestUrl, manifest.snapshotUrl)) {
            return SnapshotSyncResult.Rejected("snapshot_origin_mismatch")
        }
        when {
            manifest.manifestVersion < checkpoint.acceptedManifestVersion ->
                return SnapshotSyncResult.Rejected("manifest_version_downgrade")
            manifest.manifestVersion == checkpoint.acceptedManifestVersion &&
                checkpoint.acceptedManifestVersion > 0L -> {
                if (manifest.snapshotId != checkpoint.acceptedSnapshotId ||
                    manifest.snapshotSha256 != checkpoint.acceptedSnapshotSha256 ||
                    manifest.snapshotByteLength != checkpoint.acceptedSnapshotByteLength ||
                    manifest.signingKeyId != checkpoint.acceptedSigningKeyId
                ) {
                    return SnapshotSyncResult.Rejected("manifest_version_conflict")
                }
                return try {
                    storage.save(checkpoint.copy(acceptedManifestEtag = manifestEtag))
                    SnapshotSyncResult.NotModified(checkpoint.acceptedSnapshotId)
                } catch (_: IOException) {
                    SnapshotSyncResult.Retry("sync_state_io")
                }
            }
        }

        val snapshotResponse = try {
            transport.execute(
                SyncHttpRequest(
                    url = manifest.snapshotUrl,
                    bearerToken = credentials.token,
                    maximumBodyBytes = minOf(MAX_SNAPSHOT_BYTES, manifest.snapshotByteLength.toInt()),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return SnapshotSyncResult.Retry("snapshot_io")
        }
        when (snapshotResponse.statusCode) {
            401, 403 -> return SnapshotSyncResult.AuthFailure
            404 -> return SnapshotSyncResult.Rejected("snapshot_not_found")
            in 500..599 -> return SnapshotSyncResult.Retry("snapshot_server_error")
            200 -> Unit
            else -> return SnapshotSyncResult.Rejected("snapshot_http_status")
        }
        try {
            storage.writeStage(snapshotResponse.body)
        } catch (_: IOException) {
            return SnapshotSyncResult.Retry("snapshot_stage_io")
        }
        validateDownloadedSnapshot(snapshotResponse.body, manifest)?.let { code ->
            return rejectStaged(checkpoint, manifest.snapshotId, code)
        }
        val pending = checkpoint.copy(
            provisioningFingerprint = provisioningFingerprint,
            pendingManifestEtag = manifestEtag,
            pendingManifest = manifest,
            lastRejectedSnapshotId = null,
            lastRejectedCode = null,
        )
        try {
            storage.save(pending)
            interruptionHook.afterStage()
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return SnapshotSyncResult.Retry("sync_state_io")
        }
        return activatePending(pending, manifest, snapshotResponse.body)
    }

    private suspend fun resumePending(
        checkpoint: SnapshotSyncCheckpoint,
        manifest: DeviceSnapshotManifest,
    ): SnapshotSyncResult {
        val bytes = try {
            storage.readStage()
        } catch (_: IOException) {
            return rejectStaged(checkpoint, manifest.snapshotId, "snapshot_stage_missing")
        }
        validateDownloadedSnapshot(bytes, manifest)?.let { code ->
            return rejectStaged(checkpoint, manifest.snapshotId, code)
        }
        return activatePending(checkpoint, manifest, bytes)
    }

    private suspend fun activatePending(
        checkpoint: SnapshotSyncCheckpoint,
        manifest: DeviceSnapshotManifest,
        bytes: ByteArray,
    ): SnapshotSyncResult {
        val previous = try {
            activator.activate(bytes, manifest)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SnapshotActivationRejectedException) {
            return rejectStaged(checkpoint, manifest.snapshotId, error.code)
        } catch (_: IOException) {
            return SnapshotSyncResult.Retry("snapshot_activation_io")
        } catch (_: Exception) {
            return rejectStaged(checkpoint, manifest.snapshotId, "snapshot_activation_failed")
        }
        try {
            interruptionHook.afterActivation()
            storage.save(
                SnapshotSyncCheckpoint(
                    provisioningFingerprint = checkpoint.provisioningFingerprint,
                    acceptedManifestEtag = checkpoint.pendingManifestEtag,
                    acceptedManifestVersion = manifest.manifestVersion,
                    acceptedSnapshotId = manifest.snapshotId,
                    acceptedSnapshotSha256 = manifest.snapshotSha256,
                    acceptedSnapshotByteLength = manifest.snapshotByteLength,
                    acceptedSigningKeyId = manifest.signingKeyId,
                ),
            )
            storage.deleteStage()
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return SnapshotSyncResult.Retry("sync_state_io")
        }
        return SnapshotSyncResult.Updated(manifest.snapshotId, previous)
    }

    private fun rejectStaged(
        checkpoint: SnapshotSyncCheckpoint,
        snapshotId: String,
        code: String,
    ): SnapshotSyncResult {
        return try {
            storage.quarantineStage(snapshotId, code)
            storage.save(
                checkpoint.copy(
                    pendingManifestEtag = null,
                    pendingManifest = null,
                    lastRejectedSnapshotId = snapshotId,
                    lastRejectedCode = code,
                ),
            )
            SnapshotSyncResult.Rejected(code)
        } catch (_: IOException) {
            SnapshotSyncResult.Retry("snapshot_quarantine_io")
        }
    }
}

class FileSnapshotSyncStorage(private val directory: File) {
    private val checkpointFile = File(directory, "checkpoint.json")
    private val stageFile = File(directory, "snapshot-stage.json")
    private val rejectedFile = File(directory, "snapshot-rejected.json")
    private val rejectedMetadataFile = File(directory, "snapshot-rejected-metadata.json")

    @Throws(IOException::class)
    fun load(): SnapshotSyncCheckpoint {
        if (!checkpointFile.exists()) return SnapshotSyncCheckpoint()
        val bytes = checkpointFile.readBytes()
        if (bytes.size > MAX_MANIFEST_BYTES) throw IOException("sync checkpoint is too large")
        return try {
            manifestJson.decodeFromString(
                bytes.decodeToString(throwOnInvalidSequence = true),
            )
        } catch (error: Exception) {
            throw IOException("sync checkpoint is invalid", error)
        }
    }

    @Throws(IOException::class)
    fun save(checkpoint: SnapshotSyncCheckpoint) {
        atomicWrite(checkpointFile, manifestJson.encodeToString(checkpoint).encodeToByteArray())
    }

    @Throws(IOException::class)
    fun replaceProvisioning(
        provisioningFingerprint: String,
        pendingSnapshotId: String?,
    ): SnapshotSyncCheckpoint {
        if (stageFile.exists()) {
            quarantineStage(pendingSnapshotId ?: "unknown-pending-snapshot", "provisioning_changed")
        }
        return SnapshotSyncCheckpoint(provisioningFingerprint = provisioningFingerprint)
            .also(::save)
    }

    @Throws(IOException::class)
    fun writeStage(bytes: ByteArray) {
        if (bytes.size > MAX_SNAPSHOT_BYTES) throw IOException("staged snapshot is too large")
        atomicWrite(stageFile, bytes)
    }

    @Throws(IOException::class)
    fun readStage(): ByteArray {
        if (!stageFile.isFile) throw IOException("staged snapshot is missing")
        val bytes = stageFile.readBytes()
        if (bytes.size > MAX_SNAPSHOT_BYTES) throw IOException("staged snapshot is too large")
        return bytes
    }

    @Throws(IOException::class)
    fun quarantineStage(snapshotId: String, code: String) {
        if (stageFile.exists()) moveReplacing(stageFile, rejectedFile)
        atomicWrite(
            rejectedMetadataFile,
            manifestJson.encodeToString(
                RejectedSnapshotMetadata(snapshotId = snapshotId, code = code),
            ).encodeToByteArray(),
        )
    }

    @Throws(IOException::class)
    fun deleteStage() {
        if (stageFile.exists() && !stageFile.delete()) {
            throw IOException("cannot delete activated stage")
        }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("cannot create sync directory")
        }
        val temporary = File(directory, "${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            moveReplacing(temporary, target)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

@Serializable
private data class RejectedSnapshotMetadata(val snapshotId: String, val code: String)

private val manifestJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
    coerceInputValues = false
    explicitNulls = false
}

private fun DeviceSyncCredentials.isValid(): Boolean =
    deviceId.length in 8..128 && token.length in 16..4096 && isSafeHttpsUrl(manifestUrl) &&
        mosqueId.length in 1..128 && isNamedIanaTimezone(mosqueTimezone)

internal fun DeviceSyncCredentials.provisioningFingerprint(): String {
    val uri = URI(manifestUrl)
    val origin = "https://${uri.host.lowercase()}:${effectiveHttpsPort(uri)}"
    return MessageDigest.getInstance("SHA-256").digest(
        listOf(deviceId, mosqueId, mosqueTimezone, origin).joinToString("\u0000").encodeToByteArray(),
    ).toHex()
}

private fun SnapshotSyncCheckpoint.hasState(): Boolean =
    provisioningFingerprint != null || acceptedManifestVersion != 0L || acceptedManifestEtag != null ||
        acceptedSnapshotId != null || pendingManifest != null || pendingManifestEtag != null ||
        lastRejectedSnapshotId != null || lastRejectedCode != null

private fun isNamedIanaTimezone(value: String): Boolean = try {
    java.time.ZoneId.getAvailableZoneIds().contains(value) && java.time.ZoneId.of(value).id == value
} catch (_: Exception) {
    false
}

private fun validateManifest(manifest: DeviceSnapshotManifest) {
    require(manifest.manifestVersion >= 1)
    require(manifest.snapshotId.length in 8..128)
    require(isSafeHttpsUrl(manifest.snapshotUrl))
    require(manifest.snapshotSha256.matches(Regex("^[a-f0-9]{64}$")))
    require(manifest.snapshotByteLength in 1..MAX_SNAPSHOT_BYTES.toLong())
    require(manifest.signingKeyId.length in 1..128)
    require(manifest.minimumAppVersion == null || manifest.minimumAppVersion.length <= 64)
    manifest.serverTime?.let { serverTime ->
        require(serverTime.length <= 64 && serverTime.endsWith('Z'))
        Instant.parse(serverTime)
    }
    manifest.assets.forEach { asset ->
        require(asset.assetId.length in 1..128)
        require(isSafeHttpsUrl(asset.url))
        require(asset.sha256.matches(Regex("^[a-f0-9]{64}$")))
        require(asset.byteLength in 1..20_971_520)
        require(asset.mediaType in setOf("image/jpeg", "image/png", "image/webp"))
    }
}

private fun validateDownloadedSnapshot(bytes: ByteArray, manifest: DeviceSnapshotManifest): String? {
    if (bytes.size.toLong() != manifest.snapshotByteLength) return "snapshot_byte_length_mismatch"
    val actualHash = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    if (!MessageDigest.isEqual(actualHash.encodeToByteArray(), manifest.snapshotSha256.encodeToByteArray())) {
        return "snapshot_sha256_mismatch"
    }
    return null
}

private fun isSafeHttpsUrl(value: String): Boolean = try {
    val uri = URI(value)
    uri.scheme == "https" && uri.rawAuthority != null && uri.host != null &&
        uri.userInfo == null && uri.fragment == null
} catch (_: Exception) {
    false
}

private fun sameHttpsOrigin(first: String, second: String): Boolean = try {
    val left = URI(first)
    val right = URI(second)
    left.scheme == "https" && right.scheme == "https" &&
        left.host.equals(right.host, ignoreCase = true) &&
        effectiveHttpsPort(left) == effectiveHttpsPort(right)
} catch (_: Exception) {
    false
}

private fun effectiveHttpsPort(uri: URI): Int = if (uri.port == -1) 443 else uri.port

private fun versionAtLeast(current: String, minimum: String): Boolean {
    fun parse(value: String): List<Long>? {
        val core = value.substringBefore('-')
        if (!core.matches(Regex("^[0-9]+(?:\\.[0-9]+){0,3}$"))) return null
        return core.split('.').map { component -> component.toLongOrNull() ?: return null }
    }
    val currentParts = parse(current) ?: return false
    val minimumParts = parse(minimum) ?: return false
    val length = maxOf(currentParts.size, minimumParts.size)
    repeat(length) { index ->
        val currentPart = currentParts.getOrElse(index) { 0L }
        val minimumPart = minimumParts.getOrElse(index) { 0L }
        if (currentPart != minimumPart) return currentPart > minimumPart
    }
    return true
}

private fun JsonElement.containsExplicitNull(): Boolean = when (this) {
    JsonNull -> true
    is JsonObject -> values.any(JsonElement::containsExplicitNull)
    is JsonArray -> any(JsonElement::containsExplicitNull)
    else -> false
}

private fun isStrongEtag(value: String): Boolean =
    value.length in 3..130 && value.startsWith('"') && value.endsWith('"') &&
        !value.startsWith("W/") && value.drop(1).dropLast(1).none { it <= '\u001f' || it == '"' }

private fun ByteArray.toHex(): String = joinToString("") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
