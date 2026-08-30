package ru.namaztime.tv.sync

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface DeviceSetupResult<out T> {
    data class Success<T>(val value: T) : DeviceSetupResult<T>
    data object NotProvisioned : DeviceSetupResult<Nothing>
    data object Unauthorized : DeviceSetupResult<Nothing>
    data class Failure(val code: String, val retryable: Boolean) : DeviceSetupResult<Nothing>
}

data class CanonicalCityCandidate(
    val id: String,
    val canonicalName: String,
    val aliases: List<String>,
    val federalSubjectCode: String,
    val federalSubjectName: String,
    val settlementType: String,
    val timezone: String,
    val latitude: Double,
    val longitude: Double,
    val geographicSourceId: String,
    val geographicRevision: String,
    val geographicLicense: String,
)

fun interface DeviceCitySearchGateway {
    suspend fun searchCities(query: String): DeviceSetupResult<List<CanonicalCityCandidate>>
}

class DeviceSetupClient(
    private val transport: DeviceSyncTransport,
    private val provisioningStore: DeviceProvisioningStore,
) : DeviceCitySearchGateway {
    override suspend fun searchCities(
        query: String,
    ): DeviceSetupResult<List<CanonicalCityCandidate>> {
        if (!validSetupQuery(query)) {
            return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        }
        val provisioning = when (val loaded = loadProvisioning()) {
            is ProvisioningLoad.Available -> loaded.value
            ProvisioningLoad.Missing -> return DeviceSetupResult.NotProvisioned
            ProvisioningLoad.Failed -> {
                return DeviceSetupResult.Failure("provisioning_store_io", retryable = true)
            }
        }
        val url = deviceSetupUrl(
            provisioning = provisioning,
            suffix = "cities",
            query = "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name()),
        ) ?: return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        val response = try {
            transport.execute(
                SyncHttpRequest(
                    url = url,
                    bearerToken = provisioning.credentials.token,
                    maximumBodyBytes = MAX_DEVICE_SETUP_RESPONSE_BYTES,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return DeviceSetupResult.Failure("setup_io", retryable = true)
        }
        when (response.statusCode) {
            401, 403 -> return DeviceSetupResult.Unauthorized
            in 500..599 -> return DeviceSetupResult.Failure("setup_server_error", retryable = true)
            200 -> Unit
            else -> return DeviceSetupResult.Failure("setup_unavailable", retryable = false)
        }
        val document = try {
            deviceSetupJson.decodeFromString<DeviceCitySearchDocument>(
                response.body.decodeToString(throwOnInvalidSequence = true),
            )
        } catch (_: Exception) {
            return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        }
        if (document.schemaVersion != DEVICE_CITY_SEARCH_SCHEMA || document.query != query) {
            return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        }
        val candidates = document.candidates.map(DeviceCityCandidateDocument::toCandidate)
        if (candidates.any { !it.isValid() } || candidates.map { it.id }.distinct().size != candidates.size) {
            return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        }
        return DeviceSetupResult.Success(candidates)
    }

    private suspend fun loadProvisioning(): ProvisioningLoad = try {
        withContext(Dispatchers.IO) {
            provisioningStore.load()?.let(ProvisioningLoad::Available) ?: ProvisioningLoad.Missing
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        ProvisioningLoad.Failed
    }
}

private sealed interface ProvisioningLoad {
    data class Available(val value: DeviceProvisioning) : ProvisioningLoad
    data object Missing : ProvisioningLoad
    data object Failed : ProvisioningLoad
}

@Serializable
private data class DeviceCitySearchDocument(
    @SerialName("schema_version") val schemaVersion: String,
    val query: String,
    val candidates: List<DeviceCityCandidateDocument>,
)

@Serializable
private data class DeviceCityCandidateDocument(
    @SerialName("city_id") val cityId: String,
    @SerialName("canonical_name") val canonicalName: String,
    val aliases: List<String>,
    @SerialName("federal_subject_code") val federalSubjectCode: String,
    @SerialName("federal_subject_name") val federalSubjectName: String,
    @SerialName("settlement_type") val settlementType: String,
    val timezone: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("geographic_source_id") val geographicSourceId: String,
    @SerialName("geographic_revision") val geographicRevision: String,
    @SerialName("geographic_license") val geographicLicense: String,
) {
    fun toCandidate() = CanonicalCityCandidate(
        id = cityId,
        canonicalName = canonicalName,
        aliases = aliases.toList(),
        federalSubjectCode = federalSubjectCode,
        federalSubjectName = federalSubjectName,
        settlementType = settlementType,
        timezone = timezone,
        latitude = latitude,
        longitude = longitude,
        geographicSourceId = geographicSourceId,
        geographicRevision = geographicRevision,
        geographicLicense = geographicLicense,
    )
}

private fun CanonicalCityCandidate.isValid(): Boolean =
    id.isBoundedText(1, 160) && canonicalName.isBoundedText(1, 240) &&
        aliases.size == aliases.distinct().size && aliases.all { it.isBoundedText(1, 240) } &&
        federalSubjectCode.matches(Regex("^RU-[A-Z]{2,3}$")) &&
        federalSubjectName.isBoundedText(1, 240) && settlementType.isBoundedText(1, 32) &&
        isNamedSetupZone(timezone) && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        geographicSourceId.isBoundedText(1, 300) &&
        geographicRevision.isBoundedText(1, 200) && geographicLicense.isBoundedText(1, 300)

private fun String.isBoundedText(minimum: Int, maximum: Int): Boolean =
    length in minimum..maximum && trim() == this && none { it == '\u0000' || it == '\r' || it == '\n' }

private fun validSetupQuery(value: String): Boolean =
    value.isBoundedText(1, 400) && value.codePointCount(0, value.length) <= 200

private fun isNamedSetupZone(value: String): Boolean = try {
    ZoneId.getAvailableZoneIds().contains(value) && ZoneId.of(value).id == value
} catch (_: Exception) {
    false
}

private fun deviceSetupUrl(
    provisioning: DeviceProvisioning,
    suffix: String,
    query: String,
): String? = try {
    val manifest = URI(provisioning.credentials.manifestUrl)
    if (manifest.scheme != "https" || manifest.host == null || manifest.userInfo != null ||
        manifest.fragment != null || provisioning.credentials.deviceId.length !in 8..128 ||
        suffix !in setOf("cities", "schedule-choices", "schedule-choice-requests")
    ) {
        null
    } else {
        val origin = URI("https", null, manifest.host, manifest.port, null, null, null).toASCIIString()
        val deviceSegment = percentEncodePathSegment(provisioning.credentials.deviceId)
        "$origin/v1/devices/$deviceSegment/setup/$suffix?$query"
    }
} catch (_: Exception) {
    null
}

private fun percentEncodePathSegment(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val unsigned = byte.toInt() and 0xff
        val character = unsigned.toChar()
        if ((character in 'a'..'z') || (character in 'A'..'Z') ||
            (character in '0'..'9') || character in "-._~"
        ) {
            append(character)
        } else {
            append('%')
            append(HEX_DIGITS[unsigned ushr 4])
            append(HEX_DIGITS[unsigned and 0x0f])
        }
    }
}

private val deviceSetupJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
    coerceInputValues = false
    explicitNulls = false
}

private const val DEVICE_CITY_SEARCH_SCHEMA = "device-city-search/v1"
private const val MAX_DEVICE_SETUP_RESPONSE_BYTES = 512 * 1024
private const val HEX_DIGITS = "0123456789ABCDEF"
