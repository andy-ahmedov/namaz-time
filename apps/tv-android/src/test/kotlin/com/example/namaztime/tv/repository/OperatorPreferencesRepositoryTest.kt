package com.example.namaztime.tv.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun imageBackgroundHasAnExplicitSafeDefault() = runTest {
        val preferences = repositoryFor(this).preferences.first()

        assertEquals(DEFAULT_BACKGROUND_STYLE_ID, preferences.backgroundStyleId)
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

    @Test
    fun imageBackgroundChoicePersists() = runTest {
        val repository = repositoryFor(this)

        repository.setBackgroundStyleId(BLUE_HOUR_BACKGROUND_STYLE_ID)

        assertEquals(
            BLUE_HOUR_BACKGROUND_STYLE_ID,
            repository.preferences.first().backgroundStyleId,
        )
    }

    @Test
    fun qrConfigurationPersistsAsOneOperatorPreference() = runTest {
        val repository = repositoryFor(this)
        val configuration = OperatorQrConfiguration(
            httpsUrl = "https://example.org/sadaqah",
            title = "На ремонт мечети",
            message = "Лучшее пожертвование — то, которое принесло пользу.",
        )

        repository.setQrConfiguration(configuration)

        assertEquals(configuration, repository.preferences.first().qrConfiguration)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsafeQrUrlCannotBeStored() = runTest {
        repositoryFor(this).setQrConfiguration(
            OperatorQrConfiguration(
                httpsUrl = "http://example.org/sadaqah",
                title = "На ремонт мечети",
                message = "",
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun qrUrlThatExceedsHighCorrectionCapacityCannotBeStored() = runTest {
        val prefix = "https://example.org/"

        repositoryFor(this).setQrConfiguration(
            OperatorQrConfiguration(
                httpsUrl = prefix + "a".repeat(2_048 - prefix.length),
                title = "На ремонт мечети",
                message = "",
            ),
        )
    }

    @Test
    fun iqamahTimesPersistIndependentlyForEveryCollectivePrayer() = runTest {
        val repository = repositoryFor(this)
        val expected = OperatorIqamahTimes(
            fajr = "05:30",
            dhuhr = "13:30",
            asr = "17:45",
            maghrib = "20:15",
            isha = "22:10",
        )

        repository.setIqamahTimes(expected)

        val actual = repository.preferences.first().iqamahTimes
        assertEquals(expected, actual)
        assertNull(actual.forPrayer("sunrise"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidIqamahTimeCannotBeStored() = runTest {
        repositoryFor(this).setIqamahTime("fajr", "5:75")
    }

    @Test(expected = IllegalArgumentException::class)
    fun sunriseCannotReceiveIqamah() = runTest {
        repositoryFor(this).setIqamahTime("sunrise", "06:30")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedLanguageCannotBeStored() = runTest {
        repositoryFor(this).setLanguageTag("unexpected")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedImageBackgroundCannotBeStored() = runTest {
        repositoryFor(this).setBackgroundStyleId("unexpected")
    }

    private fun repositoryFor(scope: TestScope): OperatorPreferencesRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(scope.testScheduler)),
            produceFile = { File(temporaryFolder.root, "operator.preferences_pb") },
        )
        return DataStoreOperatorPreferencesRepository(dataStore)
    }
}
