package ru.namaztime.tv.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.data.local.NamazDatabase
import ru.namaztime.tv.data.local.SnapshotImporter
import ru.namaztime.tv.data.snapshot.SnapshotActivationGate
import ru.namaztime.tv.data.snapshot.SnapshotAuthenticityVerifier
import ru.namaztime.tv.data.local.SnapshotSelectionGuard
import ru.namaztime.tv.data.local.SnapshotSelectionResolution
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import ru.namaztime.tv.repository.DataStoreOperatorPreferencesRepository
import ru.namaztime.tv.repository.OperatorIqamahConfiguration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class LocalBundleDeviceSetupGatewayTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, NamazDatabase::class.java).allowMainThreadQueries().build()
    private val directory = Files.createTempDirectory("local-setup-gateway-").toFile()
    private val fixture = LocalSetupBundleFixture(directory)
    private val clock = MutableSetupClock(Instant.parse("2026-09-08T12:05:00Z"))
    private val bundle by lazy { LocalSetupBundleLoader(fixture.source(), directory, fixture.anchors).load() }

    @After fun close() { db.close() }

    @Test
    fun authenticatedPreviewNeverMutatesRoomAndExplicitActivationRetainsPrevious() = runTest {
        val original = SnapshotActivationGate.authenticated(fixture.signer.sign(), fixture.signer.verifier())
        SnapshotImporter(db).importAndActivate(original)
        val gateway = LocalBundleDeviceSetupGateway({ bundle }, db, clock)
        val result = gateway.loadScheduleChoices(fixture.cityId, DATE)
        assertTrue("preview failure: $result", result is DeviceSetupResult.Success<*>)
        val set = (result as DeviceSetupResult.Success).value
        assertTrue(set.selectionRequired)
        val choice = set.choices.first()
        assertTrue(choice.activationAllowed)
        assertEquals("production", choice.localPreview?.dataClassification)
        assertEquals("04:00", choice.localPreview?.rows?.first()?.adhan.toString())
        assertTrue(choice.localPreview!!.rows.all { it.iqamah == null })
        assertEquals(original.payload.snapshotId, db.snapshotDao().getSelection()?.activeSnapshotId)

        assertEquals(DeviceSetupResult.Success(Unit), gateway.activateScheduleChoice(fixture.cityId, choice.id, DATE))
        assertEquals(choice.publishedSnapshotId, db.snapshotDao().getSelection()?.activeSnapshotId)
        assertEquals(original.payload.snapshotId, db.snapshotDao().getSelection()?.previousSnapshotId)
        assertTrue(db.snapshotDao().getPrayerDays(original.payload.snapshotId).isNotEmpty())
    }

    @Test
    fun unseenChoiceStalePreviewAndMidnightRolloverCannotChangeSelection() = runTest {
        val gateway = LocalBundleDeviceSetupGateway({ bundle }, db, clock)
        assertTrue(gateway.activateScheduleChoice(fixture.cityId, fixture.choiceId(0), DATE) is DeviceSetupResult.Failure)
        gateway.loadScheduleChoices(fixture.cityId, DATE)
        val concurrent = SnapshotActivationGate.authenticated(fixture.signer.sign(), fixture.signer.verifier())
        SnapshotImporter(db).importAndActivate(concurrent)
        assertEquals(DeviceSetupResult.Failure("setup_local_selection_changed", false),
            gateway.activateScheduleChoice(fixture.cityId, fixture.choiceId(0), DATE))
        gateway.loadScheduleChoices(fixture.cityId, DATE)
        clock.current = Instant.parse("2026-09-08T21:00:00Z")
        assertEquals(DeviceSetupResult.Failure("setup_local_preview_expired", false),
            gateway.activateScheduleChoice(fixture.cityId, fixture.choiceId(0), DATE))
        assertEquals(concurrent.payload.snapshotId, db.snapshotDao().getSelection()?.activeSnapshotId)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun selectedSnapshotAndDeviceIqamahSurviveIndependentDatabaseAndPreferenceReopen() = runTest {
        val databaseFile = java.io.File(directory, "selection-reopen.db")
        fun openDatabase() = Room.databaseBuilder(context, NamazDatabase::class.java, databaseFile.path).allowMainThreadQueries().build()
        val preferencesFile = java.io.File(directory, "operator.preferences_pb")
        val iqamah = OperatorIqamahConfiguration(fajrOffsetMinutes = 17, dhuhrFixedTimeMinutes = 795)
        val preferenceJob = SupervisorJob()
        val preferences = DataStoreOperatorPreferencesRepository(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(preferenceJob + UnconfinedTestDispatcher(testScheduler)), produceFile = { preferencesFile },
        ))
        preferences.setIqamahConfiguration(iqamah)
        val first = openDatabase()
        val gateway = LocalBundleDeviceSetupGateway({ bundle }, first, clock)
        val set = (gateway.loadScheduleChoices(fixture.cityId, DATE) as DeviceSetupResult.Success).value
        val selected = set.choices.first()
        assertEquals(DeviceSetupResult.Success(Unit), gateway.activateScheduleChoice(fixture.cityId, selected.id, DATE))
        val metadata = first.snapshotDao().getSnapshot(selected.publishedSnapshotId!!)
        assertEquals(iqamah, preferences.preferences.first().iqamahConfiguration)
        first.close()
        preferenceJob.cancel(); preferenceJob.join()

        val trust = fixture.files
        val verifier = SnapshotAuthenticityVerifier(trust.getValue("trust/production.json"), 3,
            trust.getValue("trust/previous-production.json"), listOf(trust.getValue("trust/test.json"), trust.getValue("trust/staging.json")))
        val reopened = openDatabase()
        try {
            assertEquals(SnapshotSelectionResolution.Active(selected.publishedSnapshotId), SnapshotSelectionGuard(reopened, verifier.selectionTrust()).resolve())
            assertEquals(metadata, reopened.snapshotDao().getSnapshot(selected.publishedSnapshotId))
            assertEquals(30, reopened.snapshotDao().countPrayerDays(selected.publishedSnapshotId))
        } finally { reopened.close() }
        val reopenedJob = SupervisorJob()
        val reopenedPreferences = DataStoreOperatorPreferencesRepository(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(reopenedJob + UnconfinedTestDispatcher(testScheduler)), produceFile = { preferencesFile },
        ))
        try { assertEquals(iqamah, reopenedPreferences.preferences.first().iqamahConfiguration) }
        finally { reopenedJob.cancel(); reopenedJob.join() }
    }

    @Test
    fun configuredBundleFailureDoesNotChangeLastKnownGoodOrBecomeDemo() = runTest {
        val original = SnapshotActivationGate.authenticated(fixture.signer.sign(), fixture.signer.verifier())
        SnapshotImporter(db).importAndActivate(original)
        val gateway = LocalBundleDeviceSetupGateway({ throw IOException("synthetic missing asset") }, db, clock)
        assertEquals(DeviceSetupResult.Failure("setup_local_io", true), gateway.searchCities("Synthetic city"))
        assertEquals(original.payload.snapshotId, db.snapshotDao().getSelection()?.activeSnapshotId)
        assertTrue(db.snapshotDao().getPrayerDays(original.payload.snapshotId).isNotEmpty())
    }

    companion object { val DATE: LocalDate = LocalDate.parse("2026-09-08") }
}

internal class MutableSetupClock(var current: Instant) : Clock() {
    override fun instant(): Instant = current
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
}
