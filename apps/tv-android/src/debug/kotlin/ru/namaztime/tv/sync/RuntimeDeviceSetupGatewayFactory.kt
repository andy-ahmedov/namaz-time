package ru.namaztime.tv.sync

import android.content.Context
import ru.namaztime.tv.repository.PrayerScheduleRepository

internal fun createRuntimeDeviceSetup(
    context: Context,
    @Suppress("UNUSED_PARAMETER") transport: DeviceSyncTransport,
    @Suppress("UNUSED_PARAMETER") provisioningStore: DeviceProvisioningStore,
    baseScheduleRepository: PrayerScheduleRepository,
): RuntimeDeviceSetup {
    val selectionStore = SharedPreferencesDevelopmentScheduleSelectionStore.getInstance(context)
    return RuntimeDeviceSetup(
        gateway = DevelopmentDeviceSetupGateway(selectionStore),
        scheduleRepository = DevelopmentPrayerScheduleRepository(
            baseRepository = baseScheduleRepository,
            selectionStore = selectionStore,
        ),
    )
}
