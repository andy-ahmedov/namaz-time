package ru.namaztime.tv.data.snapshot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ru.namaztime.tv.data.local.NamazDatabase
import ru.namaztime.tv.data.local.SnapshotImporter
import ru.namaztime.tv.data.local.SnapshotSelectionGuard
import ru.namaztime.tv.data.local.SnapshotReplacementPolicy
import ru.namaztime.tv.domain.PrayerTimeEngine
import ru.namaztime.tv.domain.PrayerTimeResolution
import ru.namaztime.tv.repository.RoomPrayerScheduleRepository
import ru.namaztime.tv.repository.toTimeEngineInput
import java.time.Instant
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class PilotLocalBootstrapTest {
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
    fun authenticatedPilotAssetActivatesApprovedEffectiveScheduleInRoom() = runTest {
        val verifier = PilotLocalSnapshotTrust.verifier(context)
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database, verifier.selectionTrust()),
            importer = SnapshotImporter(database),
            assetSource = AndroidSnapshotAssetSource(context, PILOT_LOCAL_SNAPSHOT_ASSET),
            activationGate = { bytes -> SnapshotActivationGate.authenticated(bytes, verifier) },
        )

        bootstrapper.bootstrapIfNeeded()

        assertEquals(
            SnapshotBootstrapState.Ready("ulyanovsk-second-cathedral-2026-pilot-local-v1"),
            bootstrapper.state.value,
        )
        val schedule = RoomPrayerScheduleRepository(database.snapshotDao())
            .observeActiveSchedule()
            .first { it != null }!!
        assertEquals("Вторая Соборная мечеть Ульяновска", schedule.mosqueName)
        assertEquals("Europe/Ulyanovsk", schedule.timezoneId)
        assertEquals(365, schedule.days.size)
        assertEquals("13:15", schedule.days.single { it.localDate == "2026-08-20" }.dhuhr)
        assertEquals("13:53", schedule.days.single { it.localDate == "2026-08-24" }.dhuhr)
        assertEquals(listOf(5, 5, 5, 5, 5), schedule.iqamahRules.map { it.offsetMinutes })
        assertEquals("13:15", schedule.jumuahSessions.single().salahTime)
        assertEquals("approved", schedule.diagnostics?.approvalStatus)
        assertEquals("production", schedule.diagnostics?.dataClassification)
    }

    @Test
    fun knownLegacySyntheticInstallIsAtomicallyReplacedWithoutSyntheticRollback() = runTest {
        SnapshotImporter(database).importAndActivate(SnapshotDecoder.decode(syntheticSnapshotBytes()))
        val verifier = PilotLocalSnapshotTrust.verifier(context)
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database, verifier.selectionTrust()),
            importer = SnapshotImporter(database),
            assetSource = AndroidSnapshotAssetSource(context, PILOT_LOCAL_SNAPSHOT_ASSET),
            activationGate = { bytes -> SnapshotActivationGate.authenticated(bytes, verifier) },
            replacementPolicy = SnapshotReplacementPolicy.pilotLocal(
                currentSnapshotId = PILOT_LOCAL_SNAPSHOT_ID,
                predecessorSnapshotIds = PILOT_LOCAL_PREDECESSOR_SNAPSHOT_IDS,
                snapshotIdPrefix = PILOT_LOCAL_SNAPSHOT_ID_PREFIX,
            ),
        )

        bootstrapper.bootstrapIfNeeded()

        val selection = database.snapshotDao().getSelection()
        assertEquals(PILOT_LOCAL_SNAPSHOT_ID, selection?.activeSnapshotId)
        assertEquals(null, selection?.previousSnapshotId)
        assertEquals(false, database.snapshotDao().snapshotExists(LEGACY_SYNTHETIC_SNAPSHOT_ID))
        assertEquals(SnapshotBootstrapState.Ready(PILOT_LOCAL_SNAPSHOT_ID), bootstrapper.state.value)
    }

    @Test
    fun d014KeepsLastKnownGoodOnlyInsideCoverageAndFailsClosedAfterYearEnd() = runTest {
        val verifier = PilotLocalSnapshotTrust.verifier(context)
        val bytes = AndroidSnapshotAssetSource(context, PILOT_LOCAL_SNAPSHOT_ASSET).read()
        SnapshotImporter(database).importAndActivate(
            SnapshotActivationGate.authenticated(bytes, verifier),
        )
        val schedule = RoomPrayerScheduleRepository(database.snapshotDao())
            .observeActiveSchedule()
            .first { it != null }!!
        val engine = PrayerTimeEngine()

        assertTrue(
            engine.resolve(
                schedule.toTimeEngineInput(),
                Instant.parse("2026-12-30T20:01:00Z"),
            ) is PrayerTimeResolution.Available,
        )
        val expired = engine.resolve(
            schedule.toTimeEngineInput(),
            Instant.parse("2026-12-31T20:01:00Z"),
        )
        assertEquals(
            PrayerTimeResolution.Unavailable(
                "SCHEDULE_DATE_OUTSIDE_COVERAGE",
                java.time.LocalDate.parse("2027-01-01"),
            ),
            expired,
        )
    }

    @Test
    fun tamperedPilotAssetNeverActivates() = runTest {
        val verifier = PilotLocalSnapshotTrust.verifier(context)
        val tampered = AndroidSnapshotAssetSource(context, PILOT_LOCAL_SNAPSHOT_ASSET)
            .read()
            .decodeToString()
            .replaceFirst("\"dhuhr\": \"13:53\"", "\"dhuhr\": \"13:54\"")
            .encodeToByteArray()
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database, verifier.selectionTrust()),
            importer = SnapshotImporter(database),
            assetSource = SnapshotAssetSource { tampered },
            activationGate = { bytes -> SnapshotActivationGate.authenticated(bytes, verifier) },
        )

        bootstrapper.bootstrapIfNeeded()

        assertEquals(
            SnapshotBootstrapState.Diagnostic("SNAPSHOT_IMPORT_FAILED"),
            bootstrapper.state.value,
        )
        assertEquals(null, database.snapshotDao().getSelection())
    }
}
