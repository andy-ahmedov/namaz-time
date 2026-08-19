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

    private fun repositoryFor(scope: TestScope): OperatorPreferencesRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(scope.testScheduler)),
            produceFile = { File(temporaryFolder.root, "operator.preferences_pb") },
        )
        return DataStoreOperatorPreferencesRepository(dataStore)
    }
}
