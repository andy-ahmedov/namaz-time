package com.example.namaztime.tv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.datastore.preferences.preferencesDataStore
import com.example.namaztime.tv.presentation.NamazTvApp
import com.example.namaztime.tv.repository.DataStoreOperatorPreferencesRepository

private val Context.operatorPreferencesDataStore by preferencesDataStore(
    name = "operator_preferences",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferencesRepository = DataStoreOperatorPreferencesRepository(
            operatorPreferencesDataStore,
        )
        setContent {
            NamazTvApp(preferencesRepository)
        }
    }
}
