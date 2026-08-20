package com.example.namaztime.tv.sync

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class HeartbeatSyncStatus {
    @SerialName("ok") OK,
    @SerialName("offline") OFFLINE,
    @SerialName("transient_failure") TRANSIENT_FAILURE,
    @SerialName("rejected_snapshot") REJECTED_SNAPSHOT,
    @SerialName("auth_failure") AUTH_FAILURE,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
enum class HeartbeatHealth {
    @SerialName("ok") OK,
    @SerialName("low") LOW,
    @SerialName("critical") CRITICAL,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
enum class HeartbeatBootMode {
    @SerialName("manual") MANUAL,
    @SerialName("best_effort") BEST_EFFORT,
    @SerialName("managed") MANAGED,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
enum class HeartbeatKioskMode {
    @SerialName("none") NONE,
    @SerialName("best_effort") BEST_EFFORT,
    @SerialName("managed") MANAGED,
    @SerialName("unknown") UNKNOWN,
}

data class DeviceHeartbeatState(
    val appVersion: String,
    val osVersion: String,
    val model: String,
    val activeSnapshotId: String,
    val syncStatus: HeartbeatSyncStatus,
    val coverageDaysRemaining: Int,
    val clockMismatch: Boolean,
    val timezoneMismatch: Boolean,
    val storageHealth: HeartbeatHealth,
    val memoryHealth: HeartbeatHealth,
    val bootMode: HeartbeatBootMode,
    val kioskMode: HeartbeatKioskMode,
)

sealed interface DeviceHeartbeatResult {
    data object Accepted : DeviceHeartbeatResult
    data class Rejected(val code: String) : DeviceHeartbeatResult
    data class Retry(val code: String) : DeviceHeartbeatResult
    data object AuthFailure : DeviceHeartbeatResult
}

class DeviceHeartbeatClient(
    private val transport: DeviceSyncTransport,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun report(
        credentials: DeviceSyncCredentials,
        state: DeviceHeartbeatState,
    ): DeviceHeartbeatResult {
        val endpoint = heartbeatEndpoint(credentials)
            ?: return DeviceHeartbeatResult.Rejected("heartbeat_request_invalid")
        if (!state.isValid()) return DeviceHeartbeatResult.Rejected("heartbeat_request_invalid")
        val body = heartbeatJson.encodeToString(
            HeartbeatPayload(
                sentAt = clock.instant().toString(),
                appVersion = state.appVersion,
                osVersion = state.osVersion,
                model = state.model,
                activeSnapshotId = state.activeSnapshotId,
                syncStatus = state.syncStatus,
                coverageDaysRemaining = state.coverageDaysRemaining,
                clockMismatch = state.clockMismatch,
                timezoneMismatch = state.timezoneMismatch,
                storageHealth = state.storageHealth,
                memoryHealth = state.memoryHealth,
                bootMode = state.bootMode,
                kioskMode = state.kioskMode,
            ),
        ).encodeToByteArray()
        val response = try {
            transport.execute(
                SyncHttpRequest(
                    url = endpoint,
                    bearerToken = credentials.token,
                    maximumBodyBytes = 16 * 1024,
                    method = "POST",
                    contentType = "application/json",
                    body = body,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return DeviceHeartbeatResult.Retry("heartbeat_io")
        }
        return when (response.statusCode) {
            204 -> DeviceHeartbeatResult.Accepted
            401, 403 -> DeviceHeartbeatResult.AuthFailure
            in 500..599 -> DeviceHeartbeatResult.Retry("heartbeat_server_error")
            else -> DeviceHeartbeatResult.Rejected("heartbeat_rejected")
        }
    }
}

class HeartbeatReportingSyncRunner(
    private val delegate: SnapshotSyncRunner,
    private val report: suspend (SnapshotSyncResult) -> Unit,
) : SnapshotSyncRunner {
    override suspend fun run(): SnapshotSyncResult {
        val result = delegate.run()
        try {
            report(result)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Heartbeat is operational telemetry; sync/display success is authoritative.
        }
        return result
    }
}

@Serializable
private data class HeartbeatPayload(
    @SerialName("sent_at") val sentAt: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("os_version") val osVersion: String,
    val model: String,
    @SerialName("active_snapshot_id") val activeSnapshotId: String,
    @SerialName("sync_status") val syncStatus: HeartbeatSyncStatus,
    @SerialName("coverage_days_remaining") val coverageDaysRemaining: Int,
    @SerialName("clock_mismatch") val clockMismatch: Boolean,
    @SerialName("timezone_mismatch") val timezoneMismatch: Boolean,
    @SerialName("storage_health") val storageHealth: HeartbeatHealth,
    @SerialName("memory_health") val memoryHealth: HeartbeatHealth,
    @SerialName("boot_mode") val bootMode: HeartbeatBootMode,
    @SerialName("kiosk_mode") val kioskMode: HeartbeatKioskMode,
)

private val heartbeatJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
    coerceInputValues = false
    explicitNulls = false
}

private fun heartbeatEndpoint(credentials: DeviceSyncCredentials): String? {
    return try {
        if (credentials.deviceId.length !in 8..128 || credentials.token.length !in 16..4096) return null
        val manifest = URI(credentials.manifestUrl)
        if (manifest.scheme != "https" || manifest.host == null || manifest.userInfo != null ||
            manifest.query != null || manifest.fragment != null
        ) return null
        val encodedDeviceId = URLEncoder.encode(
            credentials.deviceId,
            StandardCharsets.UTF_8.toString(),
        ).replace("+", "%20")
        if (manifest.rawPath != "/v1/devices/$encodedDeviceId/manifest") return null
        val origin = URI("https", null, manifest.host, manifest.port, null, null, null).toString()
        "$origin/v1/devices/$encodedDeviceId/heartbeat"
    } catch (_: Exception) {
        null
    }
}

private fun DeviceHeartbeatState.isValid(): Boolean =
    appVersion.length in 1..64 && osVersion.length <= 128 && model.length <= 240 &&
        (activeSnapshotId.isEmpty() || activeSnapshotId.length in 8..128) &&
        coverageDaysRemaining in 0..732
