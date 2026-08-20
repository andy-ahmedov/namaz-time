package com.example.namaztime.tv.sync

import java.io.IOException
import java.net.URI
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PairingDeviceInfo(
    @SerialName("app_version") val appVersion: String,
    @SerialName("os_version") val osVersion: String,
    val model: String,
    val capabilities: List<String> = emptyList(),
)

@Serializable
data class DeviceProvisioning(
    val credentials: DeviceSyncCredentials,
    val mosqueId: String,
    val mosqueName: String,
    val mosqueTimezone: String,
)

interface DeviceProvisioningStore {
    @Throws(IOException::class)
    fun load(): DeviceProvisioning?

    @Throws(IOException::class)
    fun save(provisioning: DeviceProvisioning)

    @Throws(IOException::class)
    fun clear()
}

sealed interface PairingResult {
    data class Paired(val deviceId: String, val mosqueId: String) : PairingResult
    data class Rejected(val code: String) : PairingResult
    data class Retry(val code: String) : PairingResult
}

class DevicePairingClient(
    private val transport: DeviceSyncTransport,
    private val provisioningStore: DeviceProvisioningStore,
) {
    suspend fun pair(
        pairUrl: String,
        pairingCode: String,
        device: PairingDeviceInfo,
    ): PairingResult {
        if (!isSafeHttpsUrlForPairing(pairUrl) || !validPairingInput(pairingCode, device)) {
            return PairingResult.Rejected("pairing_request_invalid")
        }
        val body = pairingJson.encodeToString(
            PairRequest(pairingCode = pairingCode, device = device),
        ).encodeToByteArray()
        val response = try {
            transport.execute(
                SyncHttpRequest(
                    url = pairUrl,
                    bearerToken = "",
                    maximumBodyBytes = 32 * 1024,
                    method = "POST",
                    contentType = "application/json",
                    body = body,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return PairingResult.Retry("pairing_io")
        }
        when (response.statusCode) {
            429 -> return PairingResult.Retry("pairing_rate_limited")
            in 500..599 -> return PairingResult.Retry("pairing_server_error")
            200 -> Unit
            else -> return PairingResult.Rejected("pairing_rejected")
        }
        val decoded = try {
            pairingJson.decodeFromString<PairResponse>(
                response.body.decodeToString(throwOnInvalidSequence = true),
            )
        } catch (_: Exception) {
            return PairingResult.Rejected("pairing_response_invalid")
        }
        val manifestUrl = resolveManifestUrl(pairUrl, decoded.manifestUrl)
            ?: return PairingResult.Rejected("pairing_response_invalid")
        if (!validPairingResponse(decoded) || !samePairingOrigin(pairUrl, manifestUrl)) {
            return PairingResult.Rejected("pairing_response_invalid")
        }
        val provisioning = DeviceProvisioning(
            credentials = DeviceSyncCredentials(
                deviceId = decoded.deviceId,
                token = decoded.deviceToken,
                manifestUrl = manifestUrl,
                mosqueId = decoded.mosque.id,
                mosqueTimezone = decoded.mosque.timezone,
            ),
            mosqueId = decoded.mosque.id,
            mosqueName = decoded.mosque.name,
            mosqueTimezone = decoded.mosque.timezone,
        )
        try {
            provisioningStore.save(provisioning)
        } catch (_: IOException) {
            return PairingResult.Retry("provisioning_store_io")
        }
        return PairingResult.Paired(decoded.deviceId, decoded.mosque.id)
    }
}

@Serializable
private data class PairRequest(
    @SerialName("pairing_code") val pairingCode: String,
    val device: PairingDeviceInfo,
)

@Serializable
private data class PairResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_token") val deviceToken: String,
    @SerialName("manifest_url") val manifestUrl: String,
    val mosque: PairMosque,
)

@Serializable
private data class PairMosque(val id: String, val name: String, val timezone: String)

private val pairingJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
    coerceInputValues = false
    explicitNulls = false
}

private fun validPairingInput(code: String, device: PairingDeviceInfo): Boolean {
    if (code.length !in 6..32 || device.appVersion.length !in 1..64 ||
        device.osVersion.length !in 1..128 || device.model.length !in 1..240
    ) return false
    return device.capabilities.size == device.capabilities.distinct().size &&
        device.capabilities.all { it.length in 1..128 }
}

private fun validPairingResponse(response: PairResponse): Boolean =
    response.deviceId.length in 8..128 && response.deviceToken.length in 16..4096 &&
        response.mosque.id.length in 1..128 && response.mosque.name.length in 1..240 &&
        isNamedIanaZone(response.mosque.timezone)

private fun isNamedIanaZone(value: String): Boolean = try {
    ZoneId.getAvailableZoneIds().contains(value) && ZoneId.of(value).id == value
} catch (_: Exception) {
    false
}

private fun resolveManifestUrl(pairUrl: String, manifestUrl: String): String? = try {
    URI(pairUrl).resolve(manifestUrl).toString().takeIf(::isSafeHttpsUrlForPairing)
} catch (_: Exception) {
    null
}

private fun isSafeHttpsUrlForPairing(value: String): Boolean = try {
    val uri = URI(value)
    uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null
} catch (_: Exception) {
    false
}

private fun samePairingOrigin(first: String, second: String): Boolean = try {
    val left = URI(first)
    val right = URI(second)
    left.scheme == "https" && right.scheme == "https" &&
        left.host.equals(right.host, ignoreCase = true) &&
        (if (left.port == -1) 443 else left.port) == (if (right.port == -1) 443 else right.port)
} catch (_: Exception) {
    false
}
