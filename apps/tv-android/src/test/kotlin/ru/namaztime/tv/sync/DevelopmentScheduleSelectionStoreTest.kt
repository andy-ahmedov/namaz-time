package ru.namaztime.tv.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.repository.LocalPrayerDay
import ru.namaztime.tv.repository.LocalPrayerSchedule
import ru.namaztime.tv.repository.PrayerScheduleRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DevelopmentScheduleSelectionStoreTest {
    @Test
    fun explicitSyntheticRetainedGatewayUpdatesRecreatedRepositoryWithoutChangingLastKnownGood() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("development_schedule_selection", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val selections = SharedPreferencesDevelopmentScheduleSelectionStore(context)
        val lastKnownGood = lastKnownGoodFixture()
        val baseRepository = object : PrayerScheduleRepository {
            override fun observeActiveSchedule() = flowOf(lastKnownGood)
        }
        // Synthetic composition is explicit in this test/evidence scenario;
        // the normal debug factory must never create this overlay.
        fun syntheticFixture() = RuntimeDeviceSetup(
            gateway = DevelopmentDeviceSetupGateway(selections),
            scheduleRepository = DevelopmentPrayerScheduleRepository(baseRepository, selections, FIXED_CLOCK),
        )
        val retainedGateway = syntheticFixture().gateway
        val recreatedRepository = syntheticFixture().scheduleRepository
        assertSame(lastKnownGood, recreatedRepository.observeActiveSchedule().first())
        val city = (retainedGateway.searchCities("Omsk") as DeviceSetupResult.Success)
            .value.single()
        val choice = (retainedGateway.loadScheduleChoices(city.id, DATE) as DeviceSetupResult.Success)
            .value.choices.single()

        assertEquals(DeviceSetupResult.Success(Unit), retainedGateway.activateScheduleChoice(city.id, choice.id, DATE))

        val selected = requireNotNull(recreatedRepository.observeActiveSchedule().first())
        assertEquals("Омск", selected.locality)
        assertEquals("synthetic", selected.diagnostics?.dataClassification)
        assertSame(lastKnownGood, baseRepository.observeActiveSchedule().first())
        val reopenedStore = SharedPreferencesDevelopmentScheduleSelectionStore(context)
        assertEquals(DevelopmentScheduleSelection(city.id, choice.id), reopenedStore.selection.value)
        assertEquals(selected, DevelopmentPrayerScheduleRepository(baseRepository, reopenedStore, FIXED_CLOCK)
            .observeActiveSchedule().first())

        assertEquals(DeviceSetupResult.Failure("setup_request_invalid", false),
            retainedGateway.activateScheduleChoice(city.id, "unknown-choice", DATE))
        assertEquals(selected, recreatedRepository.observeActiveSchedule().first())
        assertSame(lastKnownGood, baseRepository.observeActiveSchedule().first())
    }

    @Test
    fun selectedDebugScheduleSurvivesStoreRecreation() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("development_schedule_selection", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val selection = DevelopmentScheduleSelection(
            cityId = "synthetic-debug-omsk",
            choiceId = "schedule-choice-${"1".repeat(64)}",
        )

        SharedPreferencesDevelopmentScheduleSelectionStore(context).save(selection)
        val recreated = SharedPreferencesDevelopmentScheduleSelectionStore(context)

        assertEquals(selection, recreated.selection.value)
    }

    @Test
    fun incompletePersistedSelectionFailsClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("development_schedule_selection", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString("city_id", "synthetic-debug-omsk")
            .commit()

        val recreated = SharedPreferencesDevelopmentScheduleSelectionStore(context)

        assertNull(recreated.selection.value)
    }

    @Test
    fun normalDebugRuntimeRoutesWithoutOverlayingPersistedSyntheticSelection() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val syntheticStore = SharedPreferencesDevelopmentScheduleSelectionStore(context)
        val explicitFixtureGateway = DevelopmentDeviceSetupGateway(syntheticStore)
        val city = (explicitFixtureGateway.searchCities("Omsk") as DeviceSetupResult.Success).value.single()
        val choice = (explicitFixtureGateway.loadScheduleChoices(city.id, DATE) as DeviceSetupResult.Success)
            .value.choices.single()
        assertEquals(DeviceSetupResult.Success(Unit), explicitFixtureGateway.activateScheduleChoice(city.id, choice.id, DATE))
        val persistedSelection = syntheticStore.selection.value
        val transport = DeviceSyncTransport {
            throw AssertionError("constructing setup must not use network")
        }
        val provisioningStore = object : DeviceProvisioningStore {
            override fun load(): DeviceProvisioning? = null
            override fun save(provisioning: DeviceProvisioning) = Unit
            override fun clear() = Unit
        }
        val lastKnownGood = lastKnownGoodFixture()
        val baseRepository = object : PrayerScheduleRepository {
            override fun observeActiveSchedule() = flowOf(lastKnownGood)
        }

        val runtime = createRuntimeDeviceSetup(
            context = context,
            transport = transport,
            provisioningStore = provisioningStore,
            baseScheduleRepository = baseRepository,
        )

        assertTrue(runtime.gateway is RoutingDeviceSetupGateway)
        assertSame(baseRepository, runtime.scheduleRepository)
        assertSame(lastKnownGood, runtime.scheduleRepository.observeActiveSchedule().first())
        assertEquals(persistedSelection, SharedPreferencesDevelopmentScheduleSelectionStore(context).selection.value)
    }

    private fun lastKnownGoodFixture() = LocalPrayerSchedule(
        snapshotId = "synthetic-last-known-good", mosqueId = "synthetic-existing-mosque",
        mosqueName = "Synthetic retained schedule", locality = "Synthetic existing locality",
        timezoneId = "Europe/Moscow", sourceKind = "manual_import", authorityName = "Synthetic fixture",
        coverageFrom = DATE.toString(), coverageTo = DATE.toString(),
        days = listOf(LocalPrayerDay(DATE.toString(), "04:00", "06:00", "12:00", "16:00", "19:00", "21:00")),
    )

    private companion object {
        val DATE: LocalDate = LocalDate.parse("2026-09-03")
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-03T09:00:00Z"), ZoneOffset.UTC)
    }
}
