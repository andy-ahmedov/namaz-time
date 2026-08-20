package com.example.namaztime.tv.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePairingClientTest {
    @Test
    fun validFixturePairingPersistsScopedProvisioningWithoutLoggingOrReturningToken() = runTest {
        val response = """
            {
              "device_id": "device-fixture-0001",
              "device_token": "fixture-device-token-not-production",
              "manifest_url": "/v1/devices/device-fixture-0001/manifest",
              "mosque": {
                "id": "mosque-ulsk-second-cathedral",
                "name": "Second Cathedral Mosque of Ulyanovsk",
                "timezone": "Europe/Ulyanovsk"
              }
            }
        """.trimIndent().encodeToByteArray()
        val transport = PairingRecordingTransport(SyncHttpResponse(200, emptyMap(), response))
        val store = RecordingProvisioningStore()
        val client = DevicePairingClient(transport, store)

        val result = client.pair(
            pairUrl = "https://api.example.invalid/v1/devices/pair",
            pairingCode = "ULSK-TEST-2026",
            device = PairingDeviceInfo(
                appVersion = "0.2.0-shell",
                osVersion = "35",
                model = "Robolectric",
                capabilities = listOf("tv", "dpad"),
            ),
        )

        assertEquals(PairingResult.Paired("device-fixture-0001", "mosque-ulsk-second-cathedral"), result)
        assertEquals(
            DeviceSyncCredentials(
                deviceId = "device-fixture-0001",
                token = "fixture-device-token-not-production",
                manifestUrl = "https://api.example.invalid/v1/devices/device-fixture-0001/manifest",
                mosqueId = "mosque-ulsk-second-cathedral",
                mosqueTimezone = "Europe/Ulyanovsk",
            ),
            store.saved?.credentials,
        )
        assertEquals("Europe/Ulyanovsk", store.saved?.mosqueTimezone)
        val request = transport.request ?: throw AssertionError("pair request missing")
        assertEquals("POST", request.method)
        assertEquals("application/json", request.contentType)
        assertEquals(null, request.ifNoneMatch)
        val requestJson = Json.parseToJsonElement(request.body!!.decodeToString()).jsonObject
        assertEquals("ULSK-TEST-2026", requestJson.getValue("pairing_code").jsonPrimitive.content)
        assertTrue("fixture-device-token-not-production" !in result.toString())
    }

    @Test
    fun rejectedOrMalformedPairResponseNeverPersistsCredentials() = runTest {
        val cases = listOf(
            SyncHttpResponse(400, emptyMap(), byteArrayOf()) to PairingResult.Rejected("pairing_rejected"),
            SyncHttpResponse(
                200,
                emptyMap(),
                """{"device_id":"device-fixture-0001","device_token":"fixture-device-token-not-production","manifest_url":"/v1/devices/device-fixture-0001/manifest","mosque":{"id":"mosque-ulsk-second-cathedral","name":"Mosque","timezone":"+04:00"}}""".encodeToByteArray(),
            ) to PairingResult.Rejected("pairing_response_invalid"),
            SyncHttpResponse(
                200,
                emptyMap(),
                """{"device_id":"device-fixture-0001","device_token":"fixture-device-token-not-production","manifest_url":"/v1/devices/device-fixture-0001/manifest","mosque":{"id":"mosque-ulsk-second-cathedral","name":"Mosque","timezone":"Europe/Ulyanovsk"},"unexpected":true}""".encodeToByteArray(),
            ) to PairingResult.Rejected("pairing_response_invalid"),
        )
        cases.forEach { (response, expected) ->
            val store = RecordingProvisioningStore()
            val result = DevicePairingClient(PairingRecordingTransport(response), store).pair(
                pairUrl = "https://api.example.invalid/v1/devices/pair",
                pairingCode = "ULSK-TEST-2026",
                device = PairingDeviceInfo("0.2.0-shell", "35", "Robolectric"),
            )

            assertEquals(expected, result)
            assertEquals(null, store.saved)
        }
    }
}

private class PairingRecordingTransport(private val response: SyncHttpResponse) : DeviceSyncTransport {
    var request: SyncHttpRequest? = null

    override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse {
        this.request = request
        return response
    }
}

private class RecordingProvisioningStore : DeviceProvisioningStore {
    var saved: DeviceProvisioning? = null

    override fun load(): DeviceProvisioning? = saved

    override fun save(provisioning: DeviceProvisioning) {
        saved = provisioning
    }

    override fun clear() {
        saved = null
    }
}
