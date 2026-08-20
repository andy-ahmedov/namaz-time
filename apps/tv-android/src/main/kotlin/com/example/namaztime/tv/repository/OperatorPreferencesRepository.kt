package com.example.namaztime.tv.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class OperatorPreferences(
    val lastSettingsDestination: String? = null,
    val reducedMotion: Boolean = true,
    val languageTag: String = DEFAULT_LANGUAGE_TAG,
    val screenRetentionShiftEnabled: Boolean = true,
)

interface OperatorPreferencesRepository {
    val preferences: Flow<OperatorPreferences>

    suspend fun setLastSettingsDestination(route: String)

    suspend fun setReducedMotion(enabled: Boolean)

    suspend fun setLanguageTag(languageTag: String)

    suspend fun setScreenRetentionShiftEnabled(enabled: Boolean)
}

class DataStoreOperatorPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : OperatorPreferencesRepository {
    override val preferences: Flow<OperatorPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { values ->
            OperatorPreferences(
                lastSettingsDestination = values[LAST_SETTINGS_DESTINATION],
                reducedMotion = values[REDUCED_MOTION] ?: true,
                languageTag = values[LANGUAGE_TAG]
                    ?.takeIf(SUPPORTED_LANGUAGE_TAGS::contains)
                    ?: DEFAULT_LANGUAGE_TAG,
                screenRetentionShiftEnabled = values[SCREEN_RETENTION_SHIFT_ENABLED] ?: true,
            )
        }

    override suspend fun setLastSettingsDestination(route: String) {
        dataStore.edit { it[LAST_SETTINGS_DESTINATION] = route }
    }

    override suspend fun setReducedMotion(enabled: Boolean) {
        dataStore.edit { it[REDUCED_MOTION] = enabled }
    }

    override suspend fun setLanguageTag(languageTag: String) {
        require(languageTag in SUPPORTED_LANGUAGE_TAGS) { "unsupported language tag" }
        dataStore.edit { it[LANGUAGE_TAG] = languageTag }
    }

    override suspend fun setScreenRetentionShiftEnabled(enabled: Boolean) {
        dataStore.edit { it[SCREEN_RETENTION_SHIFT_ENABLED] = enabled }
    }

    private companion object {
        val LAST_SETTINGS_DESTINATION = stringPreferencesKey("last_settings_destination")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val SCREEN_RETENTION_SHIFT_ENABLED = booleanPreferencesKey(
            "screen_retention_shift_enabled",
        )
    }
}

const val DEFAULT_LANGUAGE_TAG = "ru"
val SUPPORTED_LANGUAGE_TAGS = setOf(DEFAULT_LANGUAGE_TAG, "en")
