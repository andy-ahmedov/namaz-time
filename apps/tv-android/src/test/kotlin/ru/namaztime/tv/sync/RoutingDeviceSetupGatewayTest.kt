package ru.namaztime.tv.sync

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RoutingDeviceSetupGatewayTest {
    @Test
    fun onlyUnprovisionedRoutesToLocalAndHttpFailureNeverFallsBack() = runTest {
        val store = RoutingTestStore()
        val local = CountingSetupGateway()
        val remote = CountingSetupGateway(DeviceSetupResult.Unauthorized)
        val gateway = RoutingDeviceSetupGateway(store, remote, local)
        assertEquals(DeviceSetupResult.Success(emptyList<CanonicalCityCandidate>()), gateway.searchCities("test"))
        assertEquals(1, local.calls)
        store.provisioning = provisioning()
        assertEquals(DeviceSetupResult.Unauthorized, gateway.searchCities("test"))
        remote.result = DeviceSetupResult.Failure("setup_io", true)
        assertEquals(remote.result, gateway.searchCities("test"))
        assertEquals(1, local.calls)
        assertEquals(2, remote.calls)
    }

    @Test
    fun decryptFailureNeverRoutesAndProvisioningChangePreventsLocalActivation() = runTest {
        val store = RoutingTestStore()
        val local = CountingSetupGateway()
        val remote = CountingSetupGateway()
        val gateway = RoutingDeviceSetupGateway(store, remote, local)
        store.error = IOException("synthetic encrypted store failure")
        assertEquals(DeviceSetupResult.Failure("provisioning_store_io", true), gateway.searchCities("test"))
        assertEquals(0, local.calls + remote.calls)
        store.error = null
        gateway.loadScheduleChoices("city", LocalDate.parse("2026-09-08"))
        store.provisioning = provisioning()
        gateway.activateScheduleChoice("city", "choice", LocalDate.parse("2026-09-08"))
        assertEquals(1, local.calls)
        assertEquals(1, remote.calls)
        store.error = CancellationException("cancel")
        assertTrue(runCatching { gateway.searchCities("test") }.exceptionOrNull() is CancellationException)
    }

    private fun provisioning() = DeviceProvisioning(
        DeviceSyncCredentials("synthetic-device", "synthetic-not-a-secret", "https://example.invalid/manifest", "synthetic-mosque", "Europe/Moscow"),
        "synthetic-mosque", "Synthetic mosque", "Europe/Moscow",
    )
}

private class RoutingTestStore : DeviceProvisioningStore {
    var provisioning: DeviceProvisioning? = null
    var error: Exception? = null
    override fun load(): DeviceProvisioning? { error?.let { throw it }; return provisioning }
    override fun save(provisioning: DeviceProvisioning) { this.provisioning = provisioning }
    override fun clear() { provisioning = null }
}

private class CountingSetupGateway(var result: DeviceSetupResult<List<CanonicalCityCandidate>> = DeviceSetupResult.Success(emptyList())) : DeviceSetupGateway {
    var calls = 0
    override suspend fun searchCities(query: String): DeviceSetupResult<List<CanonicalCityCandidate>> { calls++; return result }
    override suspend fun loadScheduleChoices(cityId: String, date: LocalDate): DeviceSetupResult<DeviceCityScheduleChoiceSet> {
        calls++; return DeviceSetupResult.Failure("synthetic-empty", false)
    }
    override suspend fun requestScheduleChoice(cityId: String, choiceId: String, date: LocalDate, interactionId: String): DeviceSetupResult<PendingDeviceScheduleChoiceRequest> {
        calls++; return DeviceSetupResult.Failure("synthetic-empty", false)
    }
    override suspend fun activateScheduleChoice(cityId: String, choiceId: String, date: LocalDate): DeviceSetupResult<Unit> {
        calls++; return DeviceSetupResult.Failure("synthetic-empty", false)
    }
}
