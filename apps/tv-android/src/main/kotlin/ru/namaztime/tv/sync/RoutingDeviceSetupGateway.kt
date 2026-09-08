package ru.namaztime.tv.sync

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Absence of credentials is the only local-route condition. Failures never change routes. */
internal class RoutingDeviceSetupGateway(
    private val provisioningStore: DeviceProvisioningStore,
    private val remote: DeviceSetupGateway,
    private val local: DeviceSetupGateway,
) : DeviceSetupGateway {
    override suspend fun searchCities(query: String) = route { searchCities(query) }
    override suspend fun loadScheduleChoices(cityId: String, date: LocalDate) = route { loadScheduleChoices(cityId, date) }
    override suspend fun requestScheduleChoice(cityId: String, choiceId: String, date: LocalDate, interactionId: String) =
        route { requestScheduleChoice(cityId, choiceId, date, interactionId) }
    override suspend fun activateScheduleChoice(cityId: String, choiceId: String, date: LocalDate) =
        route { activateScheduleChoice(cityId, choiceId, date) }

    private suspend fun <T> route(action: suspend DeviceSetupGateway.() -> DeviceSetupResult<T>): DeviceSetupResult<T> {
        val provisioned = try {
            withContext(Dispatchers.IO) { provisioningStore.load() != null }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return DeviceSetupResult.Failure("provisioning_store_io", retryable = true)
        }
        return action(if (provisioned) remote else local)
    }
}
