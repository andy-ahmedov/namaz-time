package ru.namaztime.tv.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.data.local.NamazDatabase
import ru.namaztime.tv.repository.RoomPrayerScheduleRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class NormalDebugDeviceSetupFactoryTest {
    @Test
    fun missingBundleIsExplicitlyNotConfiguredAndNeverReadsOrCreatesDemoSelection() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NamazDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = RoomPrayerScheduleRepository(db.snapshotDao())
            val runtime = createRuntimeDeviceSetup(context, object : DeviceSyncTransport {
                override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse = error("unprovisioned must not contact HTTP")
            }, object : DeviceProvisioningStore {
                override fun load(): DeviceProvisioning? = null
                override fun save(provisioning: DeviceProvisioning) = error("must not provision")
                override fun clear() = error("must not clear")
            }, repository)
            // This CI test is specifically the absent-external-assets build mode.
            org.junit.Assume.assumeTrue(context.assets.list("public-setup").isNullOrEmpty())
            assertSame(repository, runtime.scheduleRepository)
            assertEquals(DeviceSetupResult.Failure("setup_local_not_configured", false), runtime.gateway.searchCities("Москва"))
            assertNull(db.snapshotDao().getSelection())
        } finally { db.close() }
    }

    @Test
    fun normalDebugNeverOverlaysDevelopmentPreferenceAndProvisionedUsesActualHttpGateway() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("development_schedule_selection", Context.MODE_PRIVATE).edit()
            .putString("city_id", "dev-city-moscow").putString("choice_id", "dev-moscow-0").commit()
        val db = Room.inMemoryDatabaseBuilder(context, NamazDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = RoomPrayerScheduleRepository(db.snapshotDao())
            var requests = 0
            val provisioning = DeviceProvisioning(DeviceSyncCredentials("synthetic-device", "fixture-only-token",
                "https://example.invalid/v1/devices/synthetic-device/manifest", "mosque-fixture", "Europe/Moscow"),
                "mosque-fixture", "Synthetic mosque", "Europe/Moscow")
            val runtime = createRuntimeDeviceSetup(context, object : DeviceSyncTransport {
                override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse {
                    requests++; return SyncHttpResponse(401, emptyMap(), byteArrayOf())
                }
            }, object : DeviceProvisioningStore {
                override fun load() = provisioning
                override fun save(provisioning: DeviceProvisioning) = Unit
                override fun clear() = Unit
            }, repository)
            assertSame("normal setup must use immutable Room state, not synthetic overlay", repository, runtime.scheduleRepository)
            assertEquals(DeviceSetupResult.Unauthorized, runtime.gateway.searchCities("Москва"))
            assertEquals(1, requests)
            assertNull(db.snapshotDao().getSelection())
        } finally { db.close() }
    }
}
