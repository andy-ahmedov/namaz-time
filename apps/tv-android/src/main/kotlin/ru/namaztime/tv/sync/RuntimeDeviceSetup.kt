package ru.namaztime.tv.sync

import ru.namaztime.tv.repository.PrayerScheduleRepository

internal data class RuntimeDeviceSetup(
    val gateway: DeviceSetupGateway,
    val scheduleRepository: PrayerScheduleRepository,
)
