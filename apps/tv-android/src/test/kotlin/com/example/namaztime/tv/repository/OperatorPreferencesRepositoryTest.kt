package com.example.namaztime.tv.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class OperatorPreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun lastSettingsDestinationPersistsInDataStore() = runTest {
        val repository = repositoryFor(this)

        repository.setLastSettingsDestination("diagnostics")

        assertEquals("diagnostics", repository.preferences.first().lastSettingsDestination)
    }

    @Test
    fun russianAndScreenRetentionProtectionAreSafeDefaults() = runTest {
        val repository = repositoryFor(this)

        val preferences = repository.preferences.first()

        assertEquals("ru", preferences.languageTag)
        assertEquals(true, preferences.screenRetentionShiftEnabled)
    }

    @Test
    fun languageAndScreenRetentionChoicePersist() = runTest {
        val repository = repositoryFor(this)

        repository.setLanguageTag("en")
        repository.setScreenRetentionShiftEnabled(false)

        val preferences = repository.preferences.first()
        assertEquals("en", preferences.languageTag)
        assertEquals(false, preferences.screenRetentionShiftEnabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedLanguageCannotBeStored() = runTest {
        repositoryFor(this).setLanguageTag("unexpected")
    }

    private fun repositoryFor(scope: TestScope): OperatorPreferencesRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(scope.testScheduler)),
            produceFile = { File(temporaryFolder.root, "operator.preferences_pb") },
        )
        return DataStoreOperatorPreferencesRepository(dataStore)
    }
}
