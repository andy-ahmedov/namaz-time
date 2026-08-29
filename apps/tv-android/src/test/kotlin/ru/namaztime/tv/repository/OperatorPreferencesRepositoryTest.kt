package ru.namaztime.tv.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    fun localMosquePresentationIdentityPersistsWithoutCanonicalIdentifiers() = runTest {
        val repository = repositoryFor(this)
        val expected = OperatorMosquePresentationIdentity(
            displayName = "Мечеть нашего района",
            displayAddress = "ул. Мира, 10",
        )

        repository.setMosquePresentationIdentity(expected)

        assertEquals(expected, repository.preferences.first().mosquePresentationIdentity)
    }

    @Test
    fun blankMosquePresentationIdentityIsTheSafeFallbackDefault() = runTest {
        val repository = repositoryFor(this)

        repository.setMosquePresentationIdentity(
            OperatorMosquePresentationIdentity(displayName = "   ", displayAddress = " "),
        )

        assertEquals(
            OperatorMosquePresentationIdentity(),
            repository.preferences.first().mosquePresentationIdentity,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun controlCharactersCannotBeStoredInMosquePresentationIdentity() = runTest {
        repositoryFor(this).setMosquePresentationIdentity(
            OperatorMosquePresentationIdentity(displayName = "Mosque\nInjected"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlongMosquePresentationNameCannotBeStored() = runTest {
        repositoryFor(this).setMosquePresentationIdentity(
            OperatorMosquePresentationIdentity(
                displayName = "М".repeat(MAX_MOSQUE_DISPLAY_NAME_LENGTH + 1),
            ),
        )
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
            recipient = "Местная религиозная организация",
            bank = "Тестовый банк",
            cardNumber = "0000 0000",
            phone = "+7 000 000-00-00",
            collectionUrl = "https://example.org/collection",
            imageStyleId = DONATION_IMAGE_LANTERN_STYLE_ID,
        )

        repository.setDonationConfiguration(configuration)
        repository.setDisplayMode(OperatorDisplayMode.DONATION)

        val preferences = repository.preferences.first()
        assertEquals(configuration, preferences.donationConfiguration)
        assertEquals(OperatorDisplayMode.DONATION, preferences.displayMode)
    }

    @Test
    fun donationGratitudePersistsTrimmedAndBlankResetsToFallbackMarker() = runTest {
        val repository = repositoryFor(this)
        val configuration = OperatorDonationConfiguration(
            gratitudeMessage = "  Благодарим за поддержку  ",
        )

        repository.setDonationConfiguration(configuration)

        assertEquals(
            "Благодарим за поддержку",
            repository.preferences.first().donationConfiguration.gratitudeMessage,
        )

        repository.setDonationConfiguration(configuration.copy(gratitudeMessage = "  "))

        assertEquals("", repository.preferences.first().donationConfiguration.gratitudeMessage)
    }

    @Test
    fun donationGratitudeAcceptsBoundedUnicodeCodePoints() = runTest {
        val repository = repositoryFor(this)
        val message = "🤲".repeat(MAX_DONATION_GRATITUDE_LENGTH)

        repository.setDonationConfiguration(
            OperatorDonationConfiguration(gratitudeMessage = message),
        )

        assertEquals(
            message,
            repository.preferences.first().donationConfiguration.gratitudeMessage,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun donationGratitudeRejectsTextBeyondCodePointBound() = runTest {
        repositoryFor(this).setDonationConfiguration(
            OperatorDonationConfiguration(
                gratitudeMessage = "а".repeat(MAX_DONATION_GRATITUDE_LENGTH + 1),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun donationGratitudeRejectsControlCharacters() = runTest {
        repositoryFor(this).setDonationConfiguration(
            OperatorDonationConfiguration(gratitudeMessage = "Первая строка\nВторая строка"),
        )
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
                recipient = "Мечеть",
            ),
        )
    }

    @Test
    fun legacyLabeledDonationBlobMapsIntoStructuredFields() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { File(temporaryFolder.root, "legacy_donation.preferences_pb") },
        )
        dataStore.edit { values ->
            values[stringPreferencesKey("operator_donation_https_url")] =
                "https://example.org/donate"
            values[stringPreferencesKey("operator_donation_transfer_details")] = """
                Получатель: Местная религиозная организация
                Банк: Тестовый банк
                Номер карты: 0000 0000
                СБП / Телефон: +7 000 000-00-00
                Ссылка на сбор: https://example.org/collection
            """.trimIndent()
        }

        val configuration = DataStoreOperatorPreferencesRepository(dataStore)
            .preferences
            .first()
            .donationConfiguration

        assertEquals("Местная религиозная организация", configuration.recipient)
        assertEquals("Тестовый банк", configuration.bank)
        assertEquals("0000 0000", configuration.cardNumber)
        assertEquals("+7 000 000-00-00", configuration.phone)
        assertEquals("https://example.org/collection", configuration.collectionUrl)
    }

    @Test
    fun legacyEnglishDonationLabelsMapIntoStructuredFields() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { File(temporaryFolder.root, "legacy_english_donation.preferences_pb") },
        )
        dataStore.edit { values ->
            values[stringPreferencesKey("operator_donation_https_url")] =
                "https://example.org/donate"
            values[stringPreferencesKey("operator_donation_transfer_details")] = """
                Recipient: Synthetic mosque fixture
                Bank: Synthetic bank
                Card number: 0000 0000
                Phone: +0 000 000-00-00
                Collection link: https://example.org/collection
            """.trimIndent()
        }

        val configuration = DataStoreOperatorPreferencesRepository(dataStore)
            .preferences
            .first()
            .donationConfiguration

        assertEquals("Synthetic mosque fixture", configuration.recipient)
        assertEquals("Synthetic bank", configuration.bank)
        assertEquals("0000 0000", configuration.cardNumber)
        assertEquals("+0 000 000-00-00", configuration.phone)
        assertEquals("https://example.org/collection", configuration.collectionUrl)
    }

    @Test
    fun unlabelledLegacyDonationBlobIsPreservedAsRecipient() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { File(temporaryFolder.root, "legacy_unlabelled_donation.preferences_pb") },
        )
        dataStore.edit { values ->
            values[stringPreferencesKey("operator_donation_https_url")] =
                "https://example.org/donate"
            values[stringPreferencesKey("operator_donation_transfer_details")] =
                "Operator-provided legacy details"
        }

        val configuration = DataStoreOperatorPreferencesRepository(dataStore)
            .preferences
            .first()
            .donationConfiguration

        assertEquals("Operator-provided legacy details", configuration.recipient)
        assertEquals("", configuration.bank)
        assertEquals("", configuration.cardNumber)
        assertEquals("", configuration.phone)
        assertEquals("", configuration.collectionUrl)
    }

    @Test
    fun savingStructuredDonationFieldsSupersedesLegacyBlob() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { File(temporaryFolder.root, "replace_legacy_donation.preferences_pb") },
        )
        dataStore.edit { values ->
            values[stringPreferencesKey("operator_donation_transfer_details")] = "Legacy details"
        }
        val repository = DataStoreOperatorPreferencesRepository(dataStore)
        val configuration = OperatorDonationConfiguration(
            httpsUrl = "https://example.org/donate",
            recipient = "New recipient",
        )

        repository.setDonationConfiguration(configuration)

        assertEquals(configuration, repository.preferences.first().donationConfiguration)
        assertNull(
            dataStore.data.first()[stringPreferencesKey("operator_donation_transfer_details")],
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
    fun iqamahConfigurationPersistsOffsetsAndFixedDhuhrTimeIndependently() = runTest {
        val repository = repositoryFor(this)
        val expected = OperatorIqamahConfiguration(
            fajrOffsetMinutes = 5,
            dhuhrFixedTimeMinutes = 13 * 60 + 15,
            asrOffsetMinutes = 15,
            maghribOffsetMinutes = 7,
            ishaOffsetMinutes = 20,
        )

        repository.setIqamahConfiguration(expected)

        val actual = repository.preferences.first().iqamahConfiguration
        assertEquals(expected, actual)
        assertNull(actual.offsetForPrayer("sunrise"))
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
    fun legacyDhuhrOffsetAndLegacyFixedTimesAreNotReinterpreted() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { File(temporaryFolder.root, "legacy.preferences_pb") },
        )
        dataStore.edit { values ->
            values[stringPreferencesKey("operator_iqamah_fajr")] = "05:30"
            values[stringPreferencesKey("operator_iqamah_dhuhr")] = "13:30"
            values[intPreferencesKey("operator_iqamah_offset_dhuhr")] = 10
        }

        val preferences = DataStoreOperatorPreferencesRepository(dataStore).preferences.first()

        assertEquals(OperatorIqamahConfiguration(), preferences.iqamahConfiguration)
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
