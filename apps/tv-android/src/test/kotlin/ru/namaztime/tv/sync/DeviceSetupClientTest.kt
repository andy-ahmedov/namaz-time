package ru.namaztime.tv.sync

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeviceSetupClientTest {
    @Test
    fun searchesCyrillicCanonicalCitiesOnProvisionedDeviceOrigin() = runTest {
        val transport = RecordingDeviceSetupTransport(
            response = SyncHttpResponse(
                statusCode = 200,
                headers = emptyMap(),
                body = citySearchResponse().encodeToByteArray(),
            ),
        )
        val client = DeviceSetupClient(
            transport = transport,
            provisioningStore = FixedDeviceProvisioningStore(provisioning()),
        )

        val result = client.searchCities("Киров")

        assertTrue(result is DeviceSetupResult.Success<*>)
        @Suppress("UNCHECKED_CAST")
        val success = result as DeviceSetupResult.Success<List<CanonicalCityCandidate>>
        assertEquals(2, success.value.size)
        assertEquals(listOf("RU-KIR", "RU-KLU"), success.value.map { it.federalSubjectCode })
        assertEquals("Europe/Kirov", success.value[0].timezone)
        assertEquals(1, transport.requests.size)
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("fixture-device-token-not-production", request.bearerToken)
        assertTrue(request.url.startsWith("https://api.example.invalid/v1/devices/device-setup-0001/setup/cities?q="))
        assertTrue(request.url.contains("%D0%9A%D0%B8%D1%80%D0%BE%D0%B2"))
        assertTrue(!request.url.contains("mosque") && !request.url.contains("revision"))
    }

    @Test
    fun rejectsInvalidInputAndStrictUnknownResponseWithoutPartialCandidates() = runTest {
        val transport = RecordingDeviceSetupTransport(
            response = SyncHttpResponse(
                200,
                emptyMap(),
                citySearchResponse(extra = ",\"unexpected\":true").encodeToByteArray(),
            ),
        )
        val client = DeviceSetupClient(transport, FixedDeviceProvisioningStore(provisioning()))

        val invalid = client.searchCities(" Киров ")
        assertEquals(DeviceSetupResult.Failure("setup_request_invalid", retryable = false), invalid)
        assertEquals(0, transport.requests.size)

        val unknown = client.searchCities("Киров")
        assertEquals(DeviceSetupResult.Failure("setup_response_invalid", retryable = false), unknown)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun distinguishesUnprovisionedUnauthorizedRetryAndCancellation() = runTest {
        val unprovisioned = DeviceSetupClient(
            transport = RecordingDeviceSetupTransport(),
            provisioningStore = FixedDeviceProvisioningStore(null),
        )
        assertEquals(DeviceSetupResult.NotProvisioned, unprovisioned.searchCities("Киров"))

        val unauthorized = DeviceSetupClient(
            transport = RecordingDeviceSetupTransport(SyncHttpResponse(401, emptyMap(), byteArrayOf())),
            provisioningStore = FixedDeviceProvisioningStore(provisioning()),
        )
        assertEquals(DeviceSetupResult.Unauthorized, unauthorized.searchCities("Киров"))

        val retry = DeviceSetupClient(
            transport = RecordingDeviceSetupTransport(error = IOException("offline")),
            provisioningStore = FixedDeviceProvisioningStore(provisioning()),
        )
        assertEquals(DeviceSetupResult.Failure("setup_io", retryable = true), retry.searchCities("Киров"))

        val cancelled = DeviceSetupClient(
            transport = RecordingDeviceSetupTransport(error = CancellationException("superseded")),
            provisioningStore = FixedDeviceProvisioningStore(provisioning()),
        )
        try {
            cancelled.searchCities("Киров")
            fail("cancellation was swallowed")
        } catch (_: CancellationException) {
            // Superseded searches must cancel rather than report an error state.
        }
    }

    private fun provisioning() = DeviceProvisioning(
        credentials = DeviceSyncCredentials(
            deviceId = "device-setup-0001",
            token = "fixture-device-token-not-production",
            manifestUrl = "https://api.example.invalid/v1/devices/device-setup-0001/manifest",
            mosqueId = "mosque-setup-0001",
            mosqueTimezone = "Europe/Moscow",
        ),
        mosqueId = "mosque-setup-0001",
        mosqueName = "Synthetic setup mosque",
        mosqueTimezone = "Europe/Moscow",
    )

    private fun citySearchResponse(extra: String = "") = """
        {
          "schema_version":"device-city-search/v1",
          "query":"Киров",
          "candidates":[
            {
              "city_id":"city-kirov-0001","canonical_name":"Киров","aliases":["Kirov"],
              "federal_subject_code":"RU-KIR","federal_subject_name":"Кировская область",
              "settlement_type":"PPLA","timezone":"Europe/Kirov","latitude":58.6036,
              "longitude":49.6679,"geographic_source_id":"geonames:548408",
              "geographic_revision":"2026-08-29","geographic_license":"CC BY 4.0"
            },
            {
              "city_id":"city-kirov-0002","canonical_name":"Киров","aliases":[],
              "federal_subject_code":"RU-KLU","federal_subject_name":"Калужская область",
              "settlement_type":"PPL","timezone":"Europe/Moscow","latitude":54.08,
              "longitude":34.30,"geographic_source_id":"geonames:synthetic-test-only",
              "geographic_revision":"2026-08-29","geographic_license":"CC BY 4.0"
            }
          ]$extra
        }
    """.trimIndent()
}

private class FixedDeviceProvisioningStore(
    private val provisioning: DeviceProvisioning?,
) : DeviceProvisioningStore {
    override fun load(): DeviceProvisioning? = provisioning
    override fun save(provisioning: DeviceProvisioning) = Unit
    override fun clear() = Unit
}

private class RecordingDeviceSetupTransport(
    private val response: SyncHttpResponse = SyncHttpResponse(200, emptyMap(), byteArrayOf()),
    private val error: Exception? = null,
) : DeviceSyncTransport {
    val requests = mutableListOf<SyncHttpRequest>()

    override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse {
        requests += request
        error?.let { throw it }
        return response
    }
}
