package ru.namaztime.tv.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.data.local.NamazDatabase
import ru.namaztime.tv.data.local.SnapshotImporter
import ru.namaztime.tv.data.snapshot.AndroidSnapshotAssetSource
import ru.namaztime.tv.data.snapshot.PILOT_LOCAL_SNAPSHOT_ASSET
import ru.namaztime.tv.data.snapshot.PILOT_LOCAL_SNAPSHOT_ID
import ru.namaztime.tv.data.snapshot.PILOT_LOCAL_SNAPSHOT_SHA256
import ru.namaztime.tv.data.snapshot.PilotLocalSnapshotTrust
import ru.namaztime.tv.data.snapshot.SnapshotActivationGate
import ru.namaztime.tv.repository.RoomPrayerScheduleRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class DeviceSetupLastKnownGoodIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: NamazDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NamazDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pendingChoiceRequestLeavesSignedUlyanovskRoomSnapshotByteForByteActive() = runTest {
        val rawBytes = AndroidSnapshotAssetSource(context, PILOT_LOCAL_SNAPSHOT_ASSET).read()
        assertEquals(PILOT_LOCAL_SNAPSHOT_SHA256, rawBytes.sha256())
        val verifier = PilotLocalSnapshotTrust.verifier(context)
        SnapshotImporter(database).importAndActivate(
            SnapshotActivationGate.authenticated(rawBytes, verifier),
        )
        val beforeSelection = database.snapshotDao().getSelection()
        val beforeSchedule = RoomPrayerScheduleRepository(database.snapshotDao())
            .observeActiveSchedule()
            .first { it != null }!!

        val result = DeviceSetupClient(
            transport = DeviceSyncTransport {
                SyncHttpResponse(
                    statusCode = 201,
                    headers = emptyMap(),
                    body = pendingResponse().encodeToByteArray(),
                )
            },
            provisioningStore = FixedProvisioningStore(provisioning()),
        ).requestScheduleChoice(
            cityId = CITY_ID,
            choiceId = CHOICE_ID,
            date = LocalDate.parse("2026-08-30"),
            interactionId = INTERACTION_ID,
        )

        assertTrue(result is DeviceSetupResult.Success)
        assertEquals("pending_review", (result as DeviceSetupResult.Success).value.status)
        assertEquals(beforeSelection, database.snapshotDao().getSelection())
        assertEquals(PILOT_LOCAL_SNAPSHOT_ID, database.snapshotDao().getSelection()?.activeSnapshotId)
        val afterSchedule = RoomPrayerScheduleRepository(database.snapshotDao())
            .observeActiveSchedule()
            .first { it != null }!!
        assertEquals(beforeSchedule, afterSchedule)
        assertEquals(PILOT_LOCAL_SNAPSHOT_SHA256, rawBytes.sha256())
    }

    private fun provisioning() = DeviceProvisioning(
        credentials = DeviceSyncCredentials(
            deviceId = DEVICE_ID,
            token = "fixture-device-token-not-production",
            manifestUrl = "https://api.example.invalid/v1/devices/$DEVICE_ID/manifest",
            mosqueId = MOSQUE_ID,
            mosqueTimezone = "Europe/Ulyanovsk",
        ),
        mosqueId = MOSQUE_ID,
        mosqueName = "Synthetic device setup test mosque",
        mosqueTimezone = "Europe/Ulyanovsk",
    )

    private fun pendingResponse() = """
        {
          "schema_version":"device-schedule-choice-request/v1",
          "request":{
            "id":"device-binding-request-${"b".repeat(64)}",
            "revision_id":"synthetic-staged-revision-0001","city_id":"$CITY_ID",
            "policy_id":"synthetic-policy-0001","choice_id":"$CHOICE_ID",
            "mosque_id":"$MOSQUE_ID","device_id":"$DEVICE_ID","date":"2026-08-30",
            "resolution_tier":"exact_city_timetable","status":"pending_review",
            "selection_sha256":"${"c".repeat(64)}","origin":"local_tv_operator",
            "interaction_id":"$INTERACTION_ID","requested_at":"2026-08-30T09:00:00Z"
          }
        }
    """.trimIndent()

    private companion object {
        const val DEVICE_ID = "device-setup-0001"
        const val MOSQUE_ID = "second-cathedral-mosque-ulyanovsk"
        const val CITY_ID = "city-ulyanovsk-0001"
        const val CHOICE_ID =
            "schedule-choice-0000000000000000000000000000000000000000000000000000000000000000"
        const val INTERACTION_ID = "interaction-room-regression-0001"
    }
}

private class FixedProvisioningStore(
    private val value: DeviceProvisioning,
) : DeviceProvisioningStore {
    override fun load(): DeviceProvisioning = value
    override fun save(provisioning: DeviceProvisioning) = Unit
    override fun clear() = Unit
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte) }
