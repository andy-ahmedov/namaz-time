package ru.namaztime.tv.sync

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal class SharedPreferencesDevelopmentScheduleSelectionStore(
    context: Context,
) : DevelopmentScheduleSelectionStore {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val mutableSelection = MutableStateFlow(preferences.readSelection())

    override val selection: StateFlow<DevelopmentScheduleSelection?> =
        mutableSelection.asStateFlow()

    override suspend fun save(selection: DevelopmentScheduleSelection) {
        val persisted = withContext(Dispatchers.IO) {
            preferences.edit()
                .putString(CITY_ID, selection.cityId)
                .putString(CHOICE_ID, selection.choiceId)
                .commit()
        }
        if (!persisted) throw IOException("debug schedule selection was not persisted")
        mutableSelection.value = selection
    }

    private fun android.content.SharedPreferences.readSelection(): DevelopmentScheduleSelection? {
        val cityId = getString(CITY_ID, null)?.takeIf(String::isNotBlank) ?: return null
        val choiceId = getString(CHOICE_ID, null)?.takeIf(String::isNotBlank) ?: return null
        return DevelopmentScheduleSelection(cityId, choiceId)
    }

    companion object {
        // A retained setup ViewModel and a recreated Activity must observe the same selection.
        @Volatile
        private var instance: SharedPreferencesDevelopmentScheduleSelectionStore? = null

        fun getInstance(context: Context): SharedPreferencesDevelopmentScheduleSelectionStore =
            instance ?: synchronized(this) {
                instance ?: SharedPreferencesDevelopmentScheduleSelectionStore(
                    context.applicationContext,
                ).also { instance = it }
            }

        private const val PREFERENCES_NAME = "development_schedule_selection"
        private const val CITY_ID = "city_id"
        private const val CHOICE_ID = "choice_id"
    }
}
