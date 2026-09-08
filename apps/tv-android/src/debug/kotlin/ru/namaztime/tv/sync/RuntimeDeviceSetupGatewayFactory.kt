package ru.namaztime.tv.sync

import android.content.Context
import java.io.File
import ru.namaztime.tv.data.local.NamazDatabase
import ru.namaztime.tv.repository.PrayerScheduleRepository

internal fun createRuntimeDeviceSetup(
    context: Context,
    transport: DeviceSyncTransport,
    provisioningStore: DeviceProvisioningStore,
    baseScheduleRepository: PrayerScheduleRepository,
): RuntimeDeviceSetup {
    val application = context.applicationContext
    // Existing pilot trust assets remain the cold-start anchor. The local bundle
    // loader independently requires byte-identical revision-3 trust and never
    // replaces trust or the active Room snapshot on a discovery failure.
    val loader = LocalSetupBundleLoader(
        AssetLocalSetupBundleFiles(application.assets),
        File(application.filesDir, "public-setup-index"),
    )
    return RuntimeDeviceSetup(
        gateway = RoutingDeviceSetupGateway(
            provisioningStore,
            DeviceSetupClient(transport, provisioningStore),
            LocalBundleDeviceSetupGateway(loader::load, NamazDatabase.open(application)),
        ),
        scheduleRepository = baseScheduleRepository,
    )
}
