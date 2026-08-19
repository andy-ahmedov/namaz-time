package com.example.namaztime.tv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.example.namaztime.tv.data.local.NamazDatabase
import com.example.namaztime.tv.data.local.SnapshotImporter
import com.example.namaztime.tv.data.local.SnapshotSelectionGuard
import com.example.namaztime.tv.data.snapshot.AndroidSnapshotAssetSource
import com.example.namaztime.tv.data.snapshot.BundledSnapshotBootstrapper
import com.example.namaztime.tv.presentation.NamazTvApp
import com.example.namaztime.tv.repository.DataStoreOperatorPreferencesRepository
import com.example.namaztime.tv.repository.RoomPrayerScheduleRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

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
        val scheduleRepository = RoomPrayerScheduleRepository(database.snapshotDao())
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database),
            importer = SnapshotImporter(database),
            assetSource = AndroidSnapshotAssetSource(applicationContext),
        )
        setContent {
            NamazTvApp(
                operatorPreferencesRepository = preferencesRepository,
                prayerScheduleRepository = scheduleRepository,
                bootstrapState = bootstrapper.state,
            )
        }
        lifecycleScope.launch(Dispatchers.IO) {
            bootstrapper.bootstrapIfNeeded()
        }
    }
}
