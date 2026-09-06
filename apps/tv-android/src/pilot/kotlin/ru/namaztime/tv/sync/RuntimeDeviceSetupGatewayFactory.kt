package ru.namaztime.tv.sync

import android.content.Context
import ru.namaztime.tv.repository.PrayerScheduleRepository

internal fun createRuntimeDeviceSetup(
    @Suppress("UNUSED_PARAMETER") context: Context,
    transport: DeviceSyncTransport,
    provisioningStore: DeviceProvisioningStore,
    baseScheduleRepository: PrayerScheduleRepository,
): RuntimeDeviceSetup = RuntimeDeviceSetup(
    gateway = DeviceSetupClient(transport, provisioningStore),
    scheduleRepository = baseScheduleRepository,
)
