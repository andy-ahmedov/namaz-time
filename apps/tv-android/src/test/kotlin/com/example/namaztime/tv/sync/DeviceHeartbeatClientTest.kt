package com.example.namaztime.tv.sync

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHeartbeatClientTest {
    private val credentials = DeviceSyncCredentials(
        deviceId = "device-fixture-0001",
        token = "fixture-device-token-not-production",
        manifestUrl = "https://api.example.invalid/v1/devices/device-fixture-0001/manifest",
        mosqueId = "mosque-ulsk-second-cathedral",
        mosqueTimezone = "Europe/Ulyanovsk",
    )
    private val clock = Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun sendsOnlyAllowlistedHealthToProvisioningOrigin() = runTest {
        val transport = HeartbeatRecordingTransport(SyncHttpResponse(204, emptyMap(), byteArrayOf()))
        val result = DeviceHeartbeatClient(transport, clock).report(credentials, validState())

        assertEquals(DeviceHeartbeatResult.Accepted, result)
        val request = transport.request ?: throw AssertionError("heartbeat request missing")
        assertEquals("https://api.example.invalid/v1/devices/device-fixture-0001/heartbeat", request.url)
        assertEquals("POST", request.method)
        assertEquals(credentials.token, request.bearerToken)
        val encoded = request.body!!.decodeToString()
        assertFalse(encoded.contains(credentials.token))
        assertFalse(encoded.contains(credentials.mosqueId))
        val body = Json.parseToJsonElement(encoded).jsonObject
        assertEquals(
            setOf(
                "sent_at", "app_version", "os_version", "model", "active_snapshot_id",
                "sync_status", "coverage_days_remaining", "clock_mismatch", "timezone_mismatch",
                "storage_health", "memory_health", "boot_mode", "kiosk_mode",
            ),
            body.keys,
        )
        assertEquals("2026-08-20T12:00:00Z", body.getValue("sent_at").jsonPrimitive.content)
        assertEquals("rejected_snapshot", body.getValue("sync_status").jsonPrimitive.content)
    }

    @Test
    fun rejectsInvalidLocalStateWithoutNetworkAndMapsFailures() = runTest {
        val invalidTransport = HeartbeatRecordingTransport(SyncHttpResponse(204, emptyMap(), byteArrayOf()))
        val invalid = validState().copy(coverageDaysRemaining = 733)
        assertEquals(
            DeviceHeartbeatResult.Rejected("heartbeat_request_invalid"),
            DeviceHeartbeatClient(invalidTransport, clock).report(credentials, invalid),
        )
        assertEquals(null, invalidTransport.request)

        listOf(
            400 to DeviceHeartbeatResult.Rejected("heartbeat_rejected"),
            401 to DeviceHeartbeatResult.AuthFailure,
            500 to DeviceHeartbeatResult.Retry("heartbeat_server_error"),
        ).forEach { (status, expected) ->
            val result = DeviceHeartbeatClient(
                HeartbeatRecordingTransport(SyncHttpResponse(status, emptyMap(), byteArrayOf())),
                clock,
            ).report(credentials, validState())
            assertEquals(expected, result)
        }
        val io = DeviceHeartbeatClient(HeartbeatThrowingTransport(), clock)
            .report(credentials, validState())
        assertEquals(DeviceHeartbeatResult.Retry("heartbeat_io"), io)
    }

    @Test
    fun bestEffortReporterNeverChangesCompletedSyncResult() = runTest {
        val expected = SnapshotSyncResult.Updated("snapshot-fixture-0001", null)
        var attempted = false
        val runner = HeartbeatReportingSyncRunner(
            delegate = SnapshotSyncRunner { expected },
            report = {
                attempted = true
                throw IOException("network unavailable")
            },
        )

        assertEquals(expected, runner.run())
        assertTrue(attempted)
    }

    private fun validState() = DeviceHeartbeatState(
        appVersion = "0.3.0-shell",
        osVersion = "35",
        model = "Robolectric",
        activeSnapshotId = "synthetic-android-verification-v1",
        syncStatus = HeartbeatSyncStatus.REJECTED_SNAPSHOT,
        coverageDaysRemaining = 12,
        clockMismatch = false,
        timezoneMismatch = false,
        storageHealth = HeartbeatHealth.OK,
        memoryHealth = HeartbeatHealth.LOW,
        bootMode = HeartbeatBootMode.BEST_EFFORT,
        kioskMode = HeartbeatKioskMode.NONE,
    )
}

private class HeartbeatRecordingTransport(private val response: SyncHttpResponse) : DeviceSyncTransport {
    var request: SyncHttpRequest? = null

    override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse {
        this.request = request
        return response
    }
}

private class HeartbeatThrowingTransport : DeviceSyncTransport {
    override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse =
        throw IOException("network unavailable")
}
