package ru.namaztime.tv.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.repository.PrayerScheduleRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DevelopmentScheduleSelectionStoreTest {
    @Test
    fun retainedGatewayUpdatesRepositoryFromRecreatedActivity() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val transport = DeviceSyncTransport {
            throw AssertionError("debug setup runtime must not use network")
        }
        val provisioningStore = object : DeviceProvisioningStore {
            override fun load(): DeviceProvisioning? = null
            override fun save(provisioning: DeviceProvisioning) = Unit
            override fun clear() = Unit
        }
        val baseRepository = object : PrayerScheduleRepository {
            override fun observeActiveSchedule() = flowOf(null)
        }
        fun runtime() = createRuntimeDeviceSetup(
            context, transport, provisioningStore, baseRepository,
        )
        val retainedGateway = runtime().gateway
        val recreatedRepository = runtime().scheduleRepository
        val city = (retainedGateway.searchCities("Omsk") as DeviceSetupResult.Success)
            .value.single()
        val date = LocalDate.parse("2026-09-03")
        val choice = (retainedGateway.loadScheduleChoices(city.id, date) as DeviceSetupResult.Success)
            .value.choices.single()

        retainedGateway.activateScheduleChoice(city.id, choice.id, date)

        assertEquals("Омск", recreatedRepository.observeActiveSchedule().first()?.locality)
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
    fun debugRuntimeConnectsTheGatewayToTheLocalDisplayRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val transport = DeviceSyncTransport {
            throw AssertionError("debug setup runtime must not use network")
        }
        val provisioningStore = object : DeviceProvisioningStore {
            override fun load(): DeviceProvisioning? = null
            override fun save(provisioning: DeviceProvisioning) = Unit
            override fun clear() = Unit
        }
        val baseRepository = object : PrayerScheduleRepository {
            override fun observeActiveSchedule() = flowOf(null)
        }

        val runtime = createRuntimeDeviceSetup(
            context = context,
            transport = transport,
            provisioningStore = provisioningStore,
            baseScheduleRepository = baseRepository,
        )

        assertEquals("DevelopmentDeviceSetupGateway", runtime.gateway.javaClass.simpleName)
        assertEquals(
            "DevelopmentPrayerScheduleRepository",
            runtime.scheduleRepository.javaClass.simpleName,
        )
    }
}
