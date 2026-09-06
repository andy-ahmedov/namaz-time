package ru.namaztime.tv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.namaztime.tv.data.local.NamazDatabase
import ru.namaztime.tv.data.local.SnapshotImporter
import ru.namaztime.tv.data.local.SnapshotReplacementPolicy
import ru.namaztime.tv.data.local.SnapshotSelectionGuard
import ru.namaztime.tv.data.snapshot.AndroidSnapshotAssetSource
import ru.namaztime.tv.data.snapshot.BundledSnapshotBootstrapper
import ru.namaztime.tv.data.snapshot.PILOT_LOCAL_SNAPSHOT_ASSET
import ru.namaztime.tv.data.snapshot.PILOT_LOCAL_PREDECESSOR_SNAPSHOT_IDS
import ru.namaztime.tv.data.snapshot.PILOT_LOCAL_SNAPSHOT_ID
import ru.namaztime.tv.data.snapshot.PILOT_LOCAL_SNAPSHOT_ID_PREFIX
import ru.namaztime.tv.data.snapshot.PilotLocalSnapshotTrust
import ru.namaztime.tv.data.snapshot.SnapshotActivationGate
import ru.namaztime.tv.presentation.NamazTvApp
import ru.namaztime.tv.presentation.DeviceSetupViewModel
import ru.namaztime.tv.repository.DataStoreOperatorPreferencesRepository
import ru.namaztime.tv.repository.RoomPrayerScheduleRepository
import ru.namaztime.tv.sync.EncryptedDeviceProvisioningStore
import ru.namaztime.tv.sync.HttpUrlConnectionDeviceSyncTransport
import ru.namaztime.tv.sync.createRuntimeDeviceSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val Context.operatorPreferencesDataStore by preferencesDataStore(
    name = "operator_preferences",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferencesRepository = DataStoreOperatorPreferencesRepository(
            operatorPreferencesDataStore,
        )
        val database = NamazDatabase.open(applicationContext)
        val baseScheduleRepository = RoomPrayerScheduleRepository(database.snapshotDao())
        val setupRuntime = createRuntimeDeviceSetup(
            context = applicationContext,
            transport = HttpUrlConnectionDeviceSyncTransport(),
            provisioningStore = EncryptedDeviceProvisioningStore(applicationContext),
            baseScheduleRepository = baseScheduleRepository,
        )
        val setupViewModel = ViewModelProvider(
            this,
            viewModelFactory {
                initializer {
                    DeviceSetupViewModel(
                        setupGateway = setupRuntime.gateway,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            },
        )[DeviceSetupViewModel::class.java]
        val pilotLocalVerifier = if (BuildConfig.PILOT_LOCAL_RUNTIME) {
            PilotLocalSnapshotTrust.verifier(applicationContext)
        } else {
            null
        }
        val bootstrapper = if (pilotLocalVerifier != null) {
            BundledSnapshotBootstrapper(
                selectionGuard = SnapshotSelectionGuard(
                    database,
                    pilotLocalVerifier.selectionTrust(),
                ),
                importer = SnapshotImporter(database),
                assetSource = AndroidSnapshotAssetSource(
                    applicationContext,
                    PILOT_LOCAL_SNAPSHOT_ASSET,
                ),
                activationGate = { bytes ->
                    SnapshotActivationGate.authenticated(bytes, pilotLocalVerifier)
                },
                replacementPolicy = SnapshotReplacementPolicy.pilotLocal(
                    currentSnapshotId = PILOT_LOCAL_SNAPSHOT_ID,
                    predecessorSnapshotIds = PILOT_LOCAL_PREDECESSOR_SNAPSHOT_IDS,
                    snapshotIdPrefix = PILOT_LOCAL_SNAPSHOT_ID_PREFIX,
                ),
            )
        } else {
            BundledSnapshotBootstrapper(
                selectionGuard = SnapshotSelectionGuard(database),
                importer = SnapshotImporter(database),
                assetSource = null,
            )
        }
        setContent {
            NamazTvApp(
                operatorPreferencesRepository = preferencesRepository,
                prayerScheduleRepository = setupRuntime.scheduleRepository,
                bootstrapState = bootstrapper.state,
                deviceSetupController = setupViewModel,
            )
        }
        lifecycleScope.launch(Dispatchers.IO) {
            bootstrapper.bootstrapIfNeeded()
        }
    }
}
