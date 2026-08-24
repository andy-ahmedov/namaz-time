package com.example.namaztime.tv.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun appearanceAllowlistContainsEightBuiltInsAndOneCustomSlot() = runTest {
        assertEquals(8, BUILT_IN_BACKGROUND_STYLE_IDS.size)
        assertTrue(CUSTOM_BACKGROUND_STYLE_ID in SELECTABLE_BACKGROUND_STYLE_IDS)

        val repository = repositoryFor(this)
        repository.setBackgroundStyleId(CUSTOM_BACKGROUND_STYLE_ID)

        assertEquals(CUSTOM_BACKGROUND_STYLE_ID, repository.preferences.first().backgroundStyleId)
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

    @Test
    fun donationScreenConfigurationAndModePersistIndependently() = runTest {
        val repository = repositoryFor(this)
        val configuration = OperatorDonationConfiguration(
            httpsUrl = "https://example.org/donate",
            transferDetails = "Получатель: Местная религиозная организация\nСчёт: 0000 0000",
            message = "Поддержите мечеть",
            imageStyleId = DONATION_IMAGE_LANTERN_STYLE_ID,
        )

        repository.setDonationConfiguration(configuration)
        repository.setDisplayMode(OperatorDisplayMode.DONATION)

        val preferences = repository.preferences.first()
        assertEquals(configuration, preferences.donationConfiguration)
        assertEquals(OperatorDisplayMode.DONATION, preferences.displayMode)
    }

    @Test
    fun donationImageAllowlistContainsFiveBuiltInsAndOneCustomSlot() = runTest {
        assertEquals(5, BUILT_IN_DONATION_IMAGE_STYLE_IDS.size)
        assertTrue(CUSTOM_DONATION_IMAGE_STYLE_ID in SELECTABLE_DONATION_IMAGE_STYLE_IDS)

        val repository = repositoryFor(this)
        repository.setDonationImageStyleId(CUSTOM_DONATION_IMAGE_STYLE_ID)

        assertEquals(
            CUSTOM_DONATION_IMAGE_STYLE_ID,
            repository.preferences.first().donationConfiguration.imageStyleId,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun donationModeCannotBeEnabledWithoutValidContent() = runTest {
        repositoryFor(this).setDisplayMode(OperatorDisplayMode.DONATION)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsafeDonationQrCannotBeStored() = runTest {
        repositoryFor(this).setDonationConfiguration(
            OperatorDonationConfiguration(
                httpsUrl = "http://example.org/donate",
                transferDetails = "Получатель: мечеть",
                message = "Поддержите мечеть",
            ),
        )
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
    fun iqamahOffsetsPersistIndependentlyForEveryCollectivePrayer() = runTest {
        val repository = repositoryFor(this)
        val expected = OperatorIqamahOffsets(
            fajr = 5,
            dhuhr = 10,
            asr = 15,
            maghrib = 7,
            isha = 20,
        )

        repository.setIqamahOffsets(expected)

        val actual = repository.preferences.first().iqamahOffsets
        assertEquals(expected, actual)
        assertNull(actual.forPrayer("sunrise"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun iqamahOffsetOverThreeHoursCannotBeStored() = runTest {
        repositoryFor(this).setIqamahOffset("fajr", 181)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sunriseCannotReceiveIqamah() = runTest {
        repositoryFor(this).setIqamahOffset("sunrise", 5)
    }

    @Test
    fun legacyFixedIqamahTimesAreNotGuessedIntoOffsets() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { File(temporaryFolder.root, "legacy.preferences_pb") },
        )
        dataStore.edit { values ->
            values[stringPreferencesKey("operator_iqamah_fajr")] = "05:30"
            values[stringPreferencesKey("operator_iqamah_dhuhr")] = "13:30"
        }

        val preferences = DataStoreOperatorPreferencesRepository(dataStore).preferences.first()

        assertEquals(OperatorIqamahOffsets(), preferences.iqamahOffsets)
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
