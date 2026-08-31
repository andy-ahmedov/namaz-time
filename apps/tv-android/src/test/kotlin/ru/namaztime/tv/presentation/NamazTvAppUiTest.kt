package ru.namaztime.tv.presentation

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import ru.namaztime.tv.repository.OPERATOR_IQAMAH_PRAYER_IDS
import ru.namaztime.tv.repository.OperatorIqamahConfiguration
import ru.namaztime.tv.repository.OperatorPreferences
import ru.namaztime.tv.repository.OperatorPreferencesRepository
import ru.namaztime.tv.repository.OperatorQrConfiguration
import ru.namaztime.tv.repository.OperatorQrTextFitPolicy
import ru.namaztime.tv.repository.OperatorDisplayMode
import ru.namaztime.tv.repository.OperatorDonationConfiguration
import ru.namaztime.tv.repository.OperatorImageAssetImporter
import ru.namaztime.tv.repository.OperatorImageImportResult
import ru.namaztime.tv.repository.OperatorImageReadPermission
import ru.namaztime.tv.repository.OperatorImageSelectionCapabilities
import ru.namaztime.tv.repository.OperatorImageSelectionEnvironment
import ru.namaztime.tv.repository.OperatorImageSlot
import ru.namaztime.tv.repository.OperatorMediaImage
import ru.namaztime.tv.repository.OperatorMediaImageCatalog
import ru.namaztime.tv.repository.OperatorMediaImagePage
import ru.namaztime.tv.repository.CUSTOM_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.LocalPrayerDay
import ru.namaztime.tv.repository.LocalPrayerSchedule
import ru.namaztime.tv.repository.LocalJumuahSession
import ru.namaztime.tv.repository.LocalCampaign
import ru.namaztime.tv.repository.LocalSnapshotDiagnostics
import ru.namaztime.tv.repository.CorruptLocalSnapshotException
import ru.namaztime.tv.repository.PrayerScheduleRepository
import ru.namaztime.tv.repository.toTimeEngineInput
import ru.namaztime.tv.data.snapshot.SnapshotBootstrapState
import ru.namaztime.tv.domain.CampaignEngine
import ru.namaztime.tv.domain.PrayerTimeEngine
import ru.namaztime.tv.domain.PrayerTimeResolution
import ru.namaztime.tv.domain.QrCodeGenerator
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class NamazTvAppUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun donationDisplayKeepsSettingsReachableAndCanReturnToScheduleMode() {
        val configuration = OperatorDonationConfiguration(
            httpsUrl = "https://example.org/donate",
            recipient = "Местная религиозная организация",
            bank = "Тестовый банк",
            cardNumber = "0000 0000",
            phone = "+7 000 000-00-00",
        )
        val preferences = FakeOperatorPreferencesRepository(
            initialPreferences = OperatorPreferences(
                donationConfiguration = configuration,
                displayMode = OperatorDisplayMode.DONATION,
            ),
        )
        val schedule = schedule()
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = preferences,
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule),
                bootstrapState = flowOf(SnapshotBootstrapState.Ready(schedule.snapshotId)),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithText("20 августа 2026").assertIsDisplayed()
        compose.onNodeWithText("03:20").assertIsDisplayed()
        compose.onNodeWithText("Фаджр").assertIsDisplayed()
        compose.onNodeWithTag(DONATION_DISPLAY_SETTINGS_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        repeat(SettingsDestination.DONATION.ordinal) { index ->
            compose.onNodeWithTag(SettingsDestination.entries[index].navigationTestTag)
                .performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithTag(SettingsDestination.DONATION.navigationTestTag)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(SETTINGS_DONATION_URL_FIELD_TAG).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_DONATION_RECIPIENT_FIELD_TAG).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_DONATION_CARD_NUMBER_FIELD_TAG).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_DONATION_GRATITUDE_FIELD_TAG).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(
            "$SETTINGS_DONATION_IMAGE_TAG_PREFIX${DonationImageStyle.MOSQUE.id}",
        ).assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_DONATION_PICKER_TAG).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(SETTINGS_DONATION_SAVE_TAG).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(SETTINGS_DONATION_MODE_TAG).assertIsFocused()
            .performKeyInput {
                pressKey(Key.Enter)
                pressKey(Key.DirectionDown)
            }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()

        compose.onNodeWithTag(DONATION_DISPLAY_TAG).assertDoesNotExist()
        compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).assertIsFocused()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun donationDisplayFailsClosedWithoutAnActiveLocalSchedule() {
        val configuration = OperatorDonationConfiguration(
            httpsUrl = "https://example.org/donate",
            recipient = "Местная религиозная организация",
        )
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(
                    initialPreferences = OperatorPreferences(
                        donationConfiguration = configuration,
                        displayMode = OperatorDisplayMode.DONATION,
                    ),
                ),
                prayerScheduleRepository = FakePrayerScheduleRepository(null),
                bootstrapState = flowOf(SnapshotBootstrapState.Diagnostic("NO_LOCAL_SNAPSHOT")),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithTag(DONATION_DISPLAY_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DISPLAY_UNAVAILABLE_TAG).assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun dpadOpensSettingsAndRestoresDisplayFocusOnExit() {
        compose.setContent { NamazTvApp(FakeOperatorPreferencesRepository()) }

        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).assertIsFocused().performKeyInput {
            pressKey(Key.Enter)
        }
        compose.onNodeWithTag(TV_ATMOSPHERIC_BACKGROUND_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_SHELL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_NAVIGATION_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_CONTENT_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SettingsDestination.initial.navigationTestTag).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).assertIsFocused().performKeyInput {
            pressKey(Key.Enter)
        }

        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun languageActionAppliesEnglishAcrossSettingsAndDisplayState() {
        val preferences = FakeOperatorPreferencesRepository()
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = preferences,
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).performKeyInput { pressKey(Key.Enter) }
        repeat(SettingsDestination.LANGUAGE.ordinal) { index ->
            compose.onNodeWithTag(SettingsDestination.entries[index].navigationTestTag)
                .performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithTag(SettingsDestination.LANGUAGE.navigationTestTag)
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(SETTINGS_LOCAL_ACTION_TEST_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        compose.onNodeWithTag(SettingsDestination.LANGUAGE.navigationTestTag)
            .assertTextEquals("Language")
        compose.onNodeWithText("Current language").assertIsDisplayed()
        compose.onNodeWithText("English").assertIsDisplayed()

        compose.onNodeWithTag(SETTINGS_LOCAL_ACTION_TEST_TAG).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Next prayer").assertIsDisplayed()
        compose.onNodeWithText("Until adhan").assertIsDisplayed()
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun screenOnFlagFollowsDisplayNavigationLifecycle() {
        lateinit var hostView: View
        compose.setContent {
            hostView = LocalView.current
            NamazTvApp(FakeOperatorPreferencesRepository())
        }

        compose.runOnIdle { assertTrue(hostView.keepScreenOn) }
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag(SETTINGS_SHELL_TAG).assertIsDisplayed()
        compose.runOnIdle { assertFalse(hostView.keepScreenOn) }

        compose.onNodeWithTag(SettingsDestination.initial.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).performKeyInput {
            pressKey(Key.Enter)
        }
        compose.onNodeWithTag(DISPLAY_UNAVAILABLE_TAG).assertIsDisplayed()
        compose.runOnIdle { assertTrue(hostView.keepScreenOn) }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun preferenceWriteFailureDoesNotInterruptDpadNavigation() {
        compose.setContent {
            NamazTvApp(FakeOperatorPreferencesRepository(failWrites = true))
        }

        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag(SettingsDestination.initial.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionDown)
        }

        compose.onNodeWithTag(SettingsDestination.SOURCE.navigationTestTag).assertIsFocused()
    }

    @Test
    fun activatedLocalSnapshotReplacesUnavailableDiagnostic() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithText("Синтетическая демонстрационная мечеть").assertExists()
        compose.onNodeWithText("Азан").assertExists()
        compose.onNodeWithText("Икамат").assertExists()
        listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha").forEach { prayer ->
            compose.onNodeWithTag("$PRAYER_ROW_TEST_TAG_PREFIX$prayer").assertExists()
        }
        compose.onNodeWithText("Следующий намаз").assertExists()
        compose.onNodeWithText("До следующего события").assertDoesNotExist()
        compose.onNodeWithText("03:20:00").assertExists()
        compose.onNodeWithTag(NEXT_EVENT_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(LOCAL_CLOCK_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(PRAYER_LIST_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(IQAMAH_STRIP_TAG).assertIsDisplayed()
        compose.onNodeWithText("Икамат · ближайший").assertExists()
        compose.onNodeWithText("Не указан").assertExists()
        compose.onNodeWithText("ТЕСТОВЫЕ ДАННЫЕ").assertExists()
        compose.onNodeWithContentDescription(
            "Фаджр, азан 03:14, икамат не указан",
        ).assertExists()
        compose.onNodeWithContentDescription(
            "Восход, азан 05:23, икамат не предусмотрен",
        ).assertExists()
        compose.onNodeWithText("Расписание недоступно").assertDoesNotExist()
        compose.onNodeWithTag(QR_CAMPAIGN_PANEL_TAG).assertDoesNotExist()
    }

    @Test
    fun approvedProductionScheduleKeepsTechnicalMarkersOffDisplay() {
        val approved = schedule().copy(
            mosqueId = "second-cathedral-mosque-ulyanovsk",
            mosqueName = "Вторая Соборная мечеть Ульяновска",
            locality = "Ульяновск, ул. Дзержинского, 18А",
            diagnostics = schedule().diagnostics?.copy(dataClassification = "production"),
        )
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(approved),
                bootstrapState = MutableStateFlow(SnapshotBootstrapState.Ready(approved.snapshotId)),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithText("Вторая Соборная Мечеть").assertIsDisplayed()
        compose.onNodeWithText("Ульяновск").assertIsDisplayed()
        compose.onNodeWithText("УТВЕРЖДЁННЫЕ ДАННЫЕ").assertDoesNotExist()
        compose.onNodeWithText("ТЕСТОВЫЕ ДАННЫЕ").assertDoesNotExist()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun mosqueSettingsChangeOnlyLocalDisplayedNameAndAddress() {
        val approved = schedule().copy(
            mosqueId = "second-cathedral-mosque-ulyanovsk",
            mosqueName = "Вторая Соборная мечеть Ульяновска",
            locality = "Ульяновск, ул. Дзержинского, 18А",
            diagnostics = schedule().diagnostics?.copy(dataClassification = "production"),
        )
        val preferences = FakeOperatorPreferencesRepository()
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = preferences,
                prayerScheduleRepository = FakePrayerScheduleRepository(approved),
                bootstrapState = MutableStateFlow(SnapshotBootstrapState.Ready(approved.snapshotId)),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        openSettingsDestination(SettingsDestination.MOSQUE)
        compose.onNodeWithTag(SettingsDestination.MOSQUE.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag(SETTINGS_MOSQUE_NAME_FIELD_TAG)
            .assertIsFocused()
            .performTextReplacement("Мечеть Аль-Ихлас")
        compose.onNodeWithTag(SETTINGS_MOSQUE_NAME_FIELD_TAG)
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_MOSQUE_ADDRESS_FIELD_TAG)
            .assertIsFocused()
            .performTextReplacement("ул. Мира, 10")
        compose.onNodeWithTag(SETTINGS_MOSQUE_ADDRESS_FIELD_TAG)
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_MOSQUE_IDENTITY_SAVE_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        compose.onNodeWithTag(SETTINGS_MOSQUE_IDENTITY_SAVE_TAG).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        compose.onNodeWithText("Мечеть Аль-Ихлас").assertIsDisplayed()
        compose.onNodeWithText("ул. Мира, 10").assertIsDisplayed()
        assertEquals("second-cathedral-mosque-ulyanovsk", approved.mosqueId)
        assertEquals("Europe/Ulyanovsk", approved.timezoneId)
        assertEquals("Вторая Соборная мечеть Ульяновска", approved.mosqueName)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun mosqueSettingsKeepsLocalOnlyNoteAndReadOnlyContextAboveActions() {
        val approved = schedule().copy(
            mosqueId = "second-cathedral-mosque-ulyanovsk",
            mosqueName = "Вторая Соборная мечеть Ульяновска",
            locality = "Ульяновск",
            diagnostics = schedule().diagnostics?.copy(dataClassification = "production"),
        )
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(approved),
                bootstrapState = MutableStateFlow(SnapshotBootstrapState.Ready(approved.snapshotId)),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        openSettingsDestination(SettingsDestination.MOSQUE)

        var headingFontSize = 0f
        var headingLineHeight = 0f
        var headingLineCount = 0
        var headingEllipsized = true
        compose.onNode(
            hasText("Мечеть и местоположение") and
                SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit),
        ).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            assertTrue(action(results))
            val result = results.single()
            headingFontSize = result.layoutInput.style.fontSize.value
            headingLineHeight = result.layoutInput.style.lineHeight.value
            headingLineCount = result.lineCount
            headingEllipsized = result.isLineEllipsized(0)
        }
        val note = compose.onNodeWithText(
            "Пустое поле использует утверждённое значение. Эти поля меняют только подписи на этом телевизоре.",
        ).assertIsDisplayed().getUnclippedBoundsInRoot()
        var contextLineCount = 0
        var contextLineHeight = Float.NaN
        var contextEllipsized = true
        val readOnlyContextNode = compose.onNodeWithText(
            "Источник: Ульяновск\nЧасовой пояс: Europe/Ulyanovsk (только чтение)",
        ).assertIsDisplayed()
        readOnlyContextNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            assertTrue(action(results))
            val result = results.single()
            contextLineCount = result.lineCount
            contextLineHeight = result.layoutInput.style.lineHeight.value
            contextEllipsized = (0 until result.lineCount).any(result::isLineEllipsized)
        }
        val readOnlyContext = readOnlyContextNode.getUnclippedBoundsInRoot()
        val save = compose.onNodeWithTag(SETTINGS_MOSQUE_IDENTITY_SAVE_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertEquals(1, headingLineCount)
        assertFalse(headingEllipsized)
        assertTrue("Compact Mosque heading font was ${headingFontSize}sp", headingFontSize <= 30f)
        assertTrue("Compact Mosque heading line was ${headingLineHeight}sp", headingLineHeight <= 36f)
        assertEquals(2, contextLineCount)
        assertFalse(contextEllipsized)
        assertTrue(
            "Compact read-only context line was ${contextLineHeight}sp",
            contextLineHeight.isFinite() && contextLineHeight <= 18f,
        )
        assert(note.bottom <= readOnlyContext.top)
        assert(readOnlyContext.bottom <= save.top)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun mosqueSettingsOpensDeviceScopedCanonicalCitySearch() {
        val approved = schedule().copy(
            mosqueId = "second-cathedral-mosque-ulyanovsk",
            mosqueName = "Вторая Соборная мечеть Ульяновска",
            locality = "Ульяновск, ул. Дзержинского, 18А",
        )
        val setupState = MutableStateFlow(DeviceSetupUiState())
        val setupController = object : DeviceSetupController {
            override val state = setupState

            override fun onQueryChanged(query: String) {
                setupState.value = setupState.value.copy(query = query)
            }

            override fun retrySearch() = Unit
            override fun selectCity(city: ru.namaztime.tv.sync.CanonicalCityCandidate) = Unit
            override fun retryScheduleChoices() = Unit
            override fun selectScheduleChoice(choice: ru.namaztime.tv.sync.DeviceScheduleChoice) = Unit
            override fun retryScheduleChoiceRequest() = Unit
            override fun backToSearch() = Unit
            override fun resetAfterExit() = Unit
        }
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(approved),
                bootstrapState = MutableStateFlow(SnapshotBootstrapState.Ready(approved.snapshotId)),
                deviceSetupController = setupController,
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        openSettingsDestination(SettingsDestination.MOSQUE)
        compose.onNodeWithTag(SettingsDestination.MOSQUE.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag(SETTINGS_MOSQUE_NAME_FIELD_TAG).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithTag(SETTINGS_MOSQUE_ADDRESS_FIELD_TAG).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithTag(SETTINGS_MOSQUE_IDENTITY_SAVE_TAG).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithTag(SETTINGS_DEVICE_SETUP_ACTION_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        compose.onNodeWithTag(DEVICE_SETUP_SCREEN_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DEVICE_SETUP_SEARCH_FIELD_TAG).assertIsFocused()
        compose.onNodeWithText("Ульяновск").assertIsDisplayed()
        assertEquals(approved.snapshotId, schedule().snapshotId)
    }

    @Test
    fun redesignedDisplayExposesBrandPrayerAndIqamahVisualAnchors() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithTag("brand-pill").assertIsDisplayed()
        compose.onNodeWithText("NamazTime").assertIsDisplayed()
        listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha").forEach { prayer ->
            compose.onNodeWithTag("prayer-icon-$prayer", useUnmergedTree = true).assertExists()
        }
        compose.onNodeWithTag("iqamah-icon").assertIsDisplayed()

        val sunriseTimeArea = compose.onNodeWithTag("prayer-time-area-sunrise", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val sunriseValue = compose.onNodeWithTag("sunrise-centered-time", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertEquals(
            (sunriseTimeArea.left + sunriseTimeArea.right) / 2f,
            (sunriseValue.left + sunriseValue.right) / 2f,
        )
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun displayCompositionMatchesTheReferenceProportions() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        val root = compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val composition = compose.onNodeWithTag("reference-display-composition")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val next = compose.onNodeWithTag(NEXT_EVENT_CARD_TAG).getUnclippedBoundsInRoot()
        val clock = compose.onNodeWithTag(LOCAL_CLOCK_CARD_TAG).getUnclippedBoundsInRoot()
        val prayers = compose.onNodeWithTag(PRAYER_LIST_CARD_TAG).getUnclippedBoundsInRoot()
        val strip = compose.onNodeWithTag(IQAMAH_STRIP_TAG).getUnclippedBoundsInRoot()

        val rootWidth = root.right - root.left
        val rootHeight = root.bottom - root.top
        val compositionWidth = composition.right - composition.left
        val leftWidth = next.right - next.left
        val prayerWidth = prayers.right - prayers.left
        val prayerHeight = prayers.bottom - prayers.top
        val nextHeight = next.bottom - next.top
        val clockHeight = clock.bottom - clock.top
        val settings = compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertTrue(compositionWidth >= rootWidth * 0.70f)
        assertTrue(compositionWidth <= rootWidth * 0.76f)
        assertTrue(kotlin.math.abs(leftWidth.value - prayerWidth.value) <= prayerWidth.value * 0.04f)
        assertTrue(nextHeight >= prayerHeight * 0.56f)
        assertTrue(nextHeight <= prayerHeight * 0.62f)
        assertTrue(clockHeight >= prayerHeight * 0.36f)
        assertTrue(clockHeight <= prayerHeight * 0.42f)
        assertEquals(composition.left, strip.left)
        assertEquals(composition.right, strip.right)
        assertTrue(kotlin.math.abs((settings.right - settings.left).value - (settings.bottom - settings.top).value) <= 2f)
        val settingsWidth = settings.right - settings.left
        val settingsRightMargin = root.right - settings.right
        val settingsTopMargin = settings.top - root.top
        assertTrue(settingsWidth >= rootWidth * 0.039f)
        assertTrue(settingsWidth <= rootWidth * 0.044f)
        assertTrue(settingsRightMargin >= rootWidth * 0.018f)
        assertTrue(settingsRightMargin <= rootWidth * 0.024f)
        assertTrue(settingsTopMargin >= rootHeight * 0.045f)
        assertTrue(settingsTopMargin <= rootHeight * 0.055f)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun referenceDecorativeAnchorsRemainVisibleAndSemantic() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(
                    schedule().copy(locality = "Ульяновск, ул. Дзержинского, 18А"),
                ),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithTag("mosque-location-ornament").assertIsDisplayed()
        compose.onNodeWithTag("next-event-ornament-divider").assertIsDisplayed()
        compose.onNodeWithTag("clock-ornament-divider").assertIsDisplayed()
        compose.onNodeWithTag(NEXT_EVENT_WATERMARK_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(BOTTOM_STRIP_ORNAMENT_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("calendar-icon", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Настройки").assertIsFocused()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun nextPrayerWatermarkUsesFullCardCoordinatesWithoutObscuringLongTitle() {
        assertWatermarkUsesFullCardCoordinates()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun nextPrayerWatermarkUsesFullCardCoordinatesAt720p() {
        assertWatermarkUsesFullCardCoordinates()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun nextPrayerWatermarkUsesFullCardCoordinatesAt4kDensity() {
        assertWatermarkUsesFullCardCoordinates()
    }

    private fun assertWatermarkUsesFullCardCoordinates() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(
                    schedule().copy(campaigns = listOf(campaign())),
                ),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = Clock.fixed(
                    Instant.parse("2026-08-19T19:00:00Z"),
                    ZoneOffset.UTC,
                ),
                tickIntervalMillis = null,
            )
        }

        val card = compose.onNodeWithTag(NEXT_EVENT_CARD_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val watermark = compose.onNodeWithTag(NEXT_EVENT_WATERMARK_TAG, useUnmergedTree = true)
            .assertExists()
            .getUnclippedBoundsInRoot()
        val longTitle = compose.onNodeWithTag(NEXT_EVENT_NAME_TAG)
            .assertIsDisplayed()
            .assertTextEquals("Фаджр · завтра")
            .getUnclippedBoundsInRoot()

        assertEquals(card.left, watermark.left)
        assertEquals(card.top, watermark.top)
        assertEquals(card.right, watermark.right)
        assertEquals(card.bottom, watermark.bottom)
        assertTrue(longTitle.left >= card.left && longTitle.right <= card.right)
        assertTrue(longTitle.top >= card.top && longTitle.bottom <= card.bottom)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun qrCompositionKeepsTheFullEightDigitClockRegion() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(
                    schedule().copy(campaigns = listOf(campaign())),
                ),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        val clockCard = compose.onNodeWithTag(LOCAL_CLOCK_CARD_TAG).getUnclippedBoundsInRoot()
        val clockValue = compose.onNodeWithTag(LOCAL_CLOCK_VALUE_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertTrue(clockValue.right - clockValue.left >= 148.dp)
        assertTrue(clockValue.left >= clockCard.left && clockValue.right <= clockCard.right)
    }

    @Test
    fun originalImageBackgroundAssetsArePackagedForOfflineSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val goldenDusk = context.resources.getIdentifier(
            "tv_background_golden_dusk",
            "drawable",
            context.packageName,
        )
        val blueHour = context.resources.getIdentifier(
            "tv_background_blue_hour",
            "drawable",
            context.packageName,
        )

        assertTrue("golden dusk image resource must be packaged", goldenDusk != 0)
        assertTrue("blue hour image resource must be packaged", blueHour != 0)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun appearanceCanSwitchThePersistedOfflineImageBackground() {
        val preferences = FakeOperatorPreferencesRepository()
        compose.setContent { NamazTvApp(preferences) }

        compose.onNodeWithTag("$TV_BACKGROUND_STYLE_TAG_PREFIX${ru.namaztime.tv.repository.DEFAULT_BACKGROUND_STYLE_ID}")
            .assertIsDisplayed()
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).performKeyInput { pressKey(Key.Enter) }
        repeat(SettingsDestination.APPEARANCE.ordinal) { index ->
            compose.onNodeWithTag(SettingsDestination.entries[index].navigationTestTag)
                .performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithTag(SettingsDestination.APPEARANCE.navigationTestTag)
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(
            "$SETTINGS_BACKGROUND_PREVIEW_TAG_PREFIX${ru.namaztime.tv.repository.DEFAULT_BACKGROUND_STYLE_ID}",
        ).performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(
            "$SETTINGS_BACKGROUND_PREVIEW_TAG_PREFIX${ru.namaztime.tv.repository.BLUE_HOUR_BACKGROUND_STYLE_ID}",
        )
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        compose.onNodeWithTag("$TV_BACKGROUND_STYLE_TAG_PREFIX${ru.namaztime.tv.repository.BLUE_HOUR_BACKGROUND_STYLE_ID}")
            .assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun appearanceShowsEightBuiltInPreviewsAndCustomPicker() {
        compose.setContent { NamazTvApp(FakeOperatorPreferencesRepository()) }

        openSettingsDestination(SettingsDestination.APPEARANCE)
        compose.onNodeWithTag(SETTINGS_BACKGROUND_SELECTED_PREVIEW_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_BACKGROUND_FILMSTRIP_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SettingsDestination.APPEARANCE.navigationTestTag)
            .performKeyInput { pressKey(Key.DirectionRight) }
        val builtInStyles = ru.namaztime.tv.repository.BUILT_IN_BACKGROUND_STYLE_IDS.toList()
        builtInStyles.forEachIndexed { index, styleId ->
            compose.onNodeWithTag("$SETTINGS_BACKGROUND_PREVIEW_TAG_PREFIX$styleId")
                .assertIsDisplayed()
                .assertIsFocused()
            if (index < builtInStyles.lastIndex) {
                compose.onNodeWithTag("$SETTINGS_BACKGROUND_PREVIEW_TAG_PREFIX$styleId")
                    .performKeyInput { pressKey(Key.DirectionRight) }
            }
        }
        compose.onNodeWithTag(
            "$SETTINGS_BACKGROUND_PREVIEW_TAG_PREFIX${ru.namaztime.tv.repository.CUSTOM_BACKGROUND_STYLE_ID}",
        ).assertDoesNotExist()
        compose.onNodeWithTag("$SETTINGS_BACKGROUND_PREVIEW_TAG_PREFIX${builtInStyles.last()}")
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_CUSTOM_BACKGROUND_PICKER_TAG).assertIsFocused()
    }

    @Test
    fun missingCustomBackgroundFallsBackToPackagedDefault() {
        compose.setContent {
            NamazTvApp(
                FakeOperatorPreferencesRepository(
                    initialPreferences = OperatorPreferences(
                        backgroundStyleId = ru.namaztime.tv.repository.CUSTOM_BACKGROUND_STYLE_ID,
                    ),
                ),
            )
        }

        compose.onNodeWithTag(
            "$TV_BACKGROUND_STYLE_TAG_PREFIX${ru.namaztime.tv.repository.DEFAULT_BACKGROUND_STYLE_ID}",
        ).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun activeCampaignFits1080pDensity() {
        assertActiveCampaignFitsDisplay()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun activeCampaignFits720p() {
        assertActiveCampaignFitsDisplay()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun activeCampaignFits4kDensity() {
        assertActiveCampaignFitsDisplay()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun acceptedLongQrMessageIsFullyVisibleWithoutEllipsisAt1080pDensity() {
        assertLongQrMessageIsFullyVisible()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun acceptedLongQrMessageIsFullyVisibleWithoutEllipsisAt720p() {
        assertLongQrMessageIsFullyVisible()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun acceptedLongQrMessageIsFullyVisibleWithoutEllipsisAt4kDensity() {
        assertLongQrMessageIsFullyVisible()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun activeCampaignMatchesCanonicalMainDisplayAnchors() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(
                    schedule().copy(campaigns = listOf(campaign())),
                ),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        assertReferenceBounds(MAIN_DISPLAY_COMPOSITION_TAG, 109f, 27f, 861f, 513f)
        assertReferenceBounds(NEXT_EVENT_CARD_TAG, 109f, 121f, 377f, 311f)
        assertReferenceBounds(LOCAL_CLOCK_CARD_TAG, 109f, 319f, 377f, 447f)
        assertReferenceBounds(PRAYER_LIST_CARD_TAG, 385f, 121f, 652f, 447f)
        assertReferenceBounds(IQAMAH_STRIP_TAG, 109f, 463f, 652f, 515f)
        assertReferenceBounds(QR_CAMPAIGN_PANEL_TAG, 660f, 121f, 862f, 515f)

        val prayerCard = compose.onNodeWithTag(PRAYER_LIST_CARD_TAG).getUnclippedBoundsInRoot()
        val highlight = compose.onNodeWithTag("${PRAYER_ROW_TEST_TAG_PREFIX}asr")
            .getUnclippedBoundsInRoot()
        assertTrue(highlight.left - prayerCard.left <= 6.dp)
        assertTrue(prayerCard.right - highlight.right <= 6.dp)

        val nextCard = compose.onNodeWithTag(NEXT_EVENT_CARD_TAG).getUnclippedBoundsInRoot()
        val nextLabel = compose.onNodeWithTag(NEXT_EVENT_LABEL_TAG).getUnclippedBoundsInRoot()
        val date = compose.onNodeWithTag(DATE_LABEL_TAG).getUnclippedBoundsInRoot()
        val clockCard = compose.onNodeWithTag(LOCAL_CLOCK_CARD_TAG).getUnclippedBoundsInRoot()
        assertTrue(nextLabel.top - nextCard.top in 14.dp..27.dp)
        assertTrue(date.top - clockCard.top in 10.dp..24.dp)
    }

    private fun assertActiveCampaignFitsDisplay() {
        val campaign = campaign()
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(
                    schedule().copy(campaigns = listOf(campaign)),
                ),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        val root = compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG).getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag(QR_CAMPAIGN_PANEL_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        compose.onNodeWithTag(QR_CODE_IMAGE_TAG).assertIsDisplayed()
        compose.onNodeWithText(campaign.title).assertIsDisplayed()
        compose.onNodeWithText(campaign.subtitle!!).assertIsDisplayed()
        compose.onNodeWithText(campaign.httpsUrl).assertDoesNotExist()
        assert(panel.left >= root.left && panel.right <= root.right)
        listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha").forEach { prayer ->
            compose.onNodeWithTag("$PRAYER_ROW_TEST_TAG_PREFIX$prayer").assertIsDisplayed()
        }
    }

    private fun assertLongQrMessageIsFullyVisible() {
        val message =
            "Тем же из вас, которые уверовали и расходовали, уготована великая награда."
        val preferences = FakeOperatorPreferencesRepository(
            initialPreferences = OperatorPreferences(
                qrConfiguration = OperatorQrConfiguration(
                    httpsUrl = "https://example.org/sadaqah",
                    title = "На строительство школы",
                    message = message,
                ),
            ),
        )
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = preferences,
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithTag(QR_CAMPAIGN_SUBTITLE_TAG)
            .assert(SemanticsMatcher.expectValue(QrSubtitleFullyVisibleKey, true))
        val panel = compose.onNodeWithTag(QR_CAMPAIGN_PANEL_TAG).getUnclippedBoundsInRoot()
        val subtitle = compose.onNodeWithTag(QR_CAMPAIGN_SUBTITLE_TAG).getUnclippedBoundsInRoot()
        assertTrue(subtitle.left >= panel.left && subtitle.right <= panel.right)
        assertTrue(subtitle.top >= panel.top && subtitle.bottom <= panel.bottom)
    }

    @Test
    fun expiredCampaignIsHiddenWithoutBreakingPrayerDisplay() {
        assertCampaignIsSafelyHidden(campaign().copy(endsAt = "2026-08-19T23:00:00Z"))
    }

    @Test
    fun invalidCampaignIsHiddenWithoutBreakingPrayerDisplay() {
        assertCampaignIsSafelyHidden(campaign().copy(httpsUrl = "http://example.org/not-safe"))
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun futureCampaignCanBePreviewedFromDpadSettingsWithoutActivatingOnDisplay() {
        val longMessage =
            "Тем же из вас, которые уверовали и расходовали, уготована великая награда."
        val future = campaign().copy(
            subtitle = longMessage,
            startsAt = "2026-08-21T00:00:00Z",
            endsAt = "2026-08-22T00:00:00Z",
        )
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(
                    schedule().copy(campaigns = listOf(future)),
                ),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithTag(QR_CAMPAIGN_PANEL_TAG).assertDoesNotExist()
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).performKeyInput { pressKey(Key.Enter) }
        repeat(SettingsDestination.CAMPAIGNS.ordinal) {
            compose.onNodeWithTag(SettingsDestination.entries[it].navigationTestTag)
                .performKeyInput { pressKey(Key.DirectionDown) }
        }

        compose.onNodeWithTag(SettingsDestination.CAMPAIGNS.navigationTestTag).assertIsFocused()
        val panel = compose.onNodeWithTag(QR_CAMPAIGN_PREVIEW_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val action = compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        listOf(QR_CODE_IMAGE_TAG, QR_CAMPAIGN_TITLE_TAG, QR_CAMPAIGN_SUBTITLE_TAG).forEach { tag ->
            val child = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assert(child.left >= panel.left && child.right <= panel.right)
            assert(child.top >= panel.top && child.bottom <= panel.bottom)
        }
        compose.onNodeWithText(longMessage).assertIsDisplayed()
        compose.onNodeWithTag(QR_CAMPAIGN_SUBTITLE_TAG)
            .assert(SemanticsMatcher.expectValue(QrSubtitleFullyVisibleKey, true))
        assert(panel.bottom <= action.top)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun qrMessageThatCannotFitShowsValidationAndCannotBeSaved() {
        val firstRejectedWordCount = (1..OperatorQrTextFitPolicy.MAX_INPUT_CODE_POINTS).first {
            OperatorQrTextFitPolicy.fit("Ж ".repeat(it).trim()) == null
        }
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        openSettingsDestination(SettingsDestination.CAMPAIGNS)
        compose.onNodeWithTag(SettingsDestination.CAMPAIGNS.navigationTestTag)
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(SETTINGS_QR_URL_FIELD_TAG)
            .performTextReplacement("https://example.org/sadaqah")
        compose.onNodeWithTag(SETTINGS_QR_MESSAGE_FIELD_TAG)
            .performTextReplacement("Ж ".repeat(firstRejectedWordCount).trim())

        compose.onNodeWithText("Текст слишком длинный для блока QR").assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_QR_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun operatorCanEnterQrLinkPurposeAndMessageThenShowThemOnDisplay() {
        val preferences = FakeOperatorPreferencesRepository()
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = preferences,
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        openSettingsDestination(SettingsDestination.CAMPAIGNS)
        compose.onNodeWithTag(SettingsDestination.CAMPAIGNS.navigationTestTag)
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(SETTINGS_QR_URL_FIELD_TAG)
            .assertIsFocused()
            .performTextReplacement("https://example.org/sadaqah")
        compose.onNodeWithTag(SETTINGS_QR_TITLE_FIELD_TAG)
            .performTextReplacement("На ремонт мечети")
        compose.onNodeWithTag(SETTINGS_QR_MESSAGE_FIELD_TAG)
            .performTextReplacement("Спешите к благому — садака приносит пользу людям.")
        compose.onNodeWithTag(SETTINGS_QR_MESSAGE_FIELD_TAG).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithTag(SETTINGS_QR_SAVE_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        compose.onNodeWithTag(SETTINGS_QR_SAVE_TAG).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        compose.onNodeWithTag(QR_CAMPAIGN_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithText("Садака").assertIsDisplayed()
        compose.onNodeWithTag(QR_ORNAMENT_DIVIDER_TAG).assertIsDisplayed()
        compose.onNodeWithTag(QR_ELEGANT_FRAME_TAG).assertIsDisplayed()
        compose.onNodeWithTag(QR_CENTER_BRAND_BADGE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(QR_SUPPORT_ICON_TAG).assertIsDisplayed()
        compose.onNodeWithText("На ремонт мечети").assertIsDisplayed()
        compose.onNodeWithText("Спешите к благому — садака приносит пользу людям.")
            .assertIsDisplayed()
        val qrPanel = compose.onNodeWithTag(QR_CAMPAIGN_PANEL_TAG).getUnclippedBoundsInRoot()
        val prayerPanel = compose.onNodeWithTag(PRAYER_LIST_CARD_TAG).getUnclippedBoundsInRoot()
        val strip = compose.onNodeWithTag(IQAMAH_STRIP_TAG).getUnclippedBoundsInRoot()
        assertEquals(prayerPanel.top, qrPanel.top)
        assertEquals(strip.bottom, qrPanel.bottom)
        assert(strip.right <= qrPanel.left)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun iqamahSettingsUseOneMinuteStepsAndOneFixedDhuhrJumuahSetting() {
        val preferences = FakeOperatorPreferencesRepository()
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = preferences,
                prayerScheduleRepository = FakePrayerScheduleRepository(
                    schedule().copy(
                        iqamahRules = listOf(
                            ru.namaztime.tv.repository.LocalIqamahRule(
                                id = "approved-dhuhr-1315",
                                prayer = "dhuhr",
                                validFrom = "2026-08-19",
                                validTo = "2026-08-21",
                                weekdaysMask = 127,
                                priority = 100,
                                mode = "fixed_time",
                                fixedTime = "13:15",
                                offsetMinutes = null,
                                reason = "synthetic approved policy",
                            ),
                        ),
                    ),
                ),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        openSettingsDestination(SettingsDestination.IQAMAH)
        compose.onNodeWithTag(SettingsDestination.IQAMAH.navigationTestTag)
            .assertTextEquals("Икамат")
        compose.onNodeWithText("Пятничный намаз").assertDoesNotExist()
        compose.onNodeWithText("Зухр в пятницу").assertDoesNotExist()
        compose.onNodeWithText("Заменён одним намазом Джума").assertDoesNotExist()
        val expected = mapOf(
            "fajr" to (1 to "03:15"),
            "dhuhr" to (2 to "13:17"),
            "asr" to (3 to "16:48"),
            "maghrib" to (4 to "18:55"),
            "isha" to (5 to "21:03"),
        )
        compose.onNodeWithTag(SettingsDestination.IQAMAH.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        expected.entries.forEachIndexed { index, (prayerId, expectation) ->
            val prefix = "$SETTINGS_IQAMAH_FIELD_TAG_PREFIX$prayerId"
            compose.onNodeWithTag("$SETTINGS_IQAMAH_FIELD_TAG_PREFIX$prayerId")
                .assertIsDisplayed()
            compose.onNodeWithTag("$prefix-reset")
                .assertIsFocused()
                .performKeyInput {
                    pressKey(Key.DirectionRight)
                    pressKey(Key.DirectionRight)
                }
            compose.onNodeWithTag("$prefix-increment")
                .assertIsFocused()
                .performKeyInput {
                    repeat(expectation.first) { pressKey(Key.Enter) }
                    pressKey(Key.DirectionLeft)
                    pressKey(Key.DirectionLeft)
                    if (index < expected.size - 1) pressKey(Key.DirectionDown)
                }
        }
        compose.onNodeWithTag("${SETTINGS_IQAMAH_FIELD_TAG_PREFIX}isha-reset").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithTag(SETTINGS_IQAMAH_SAVE_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        compose.onNodeWithTag(SETTINGS_IQAMAH_SAVE_TAG).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        expected.forEach { (prayerId, expectation) ->
            compose.onNodeWithTag("$PRAYER_ROW_TEST_TAG_PREFIX$prayerId")
                .assertTextContains(expectation.second)
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun compactIqamahEditorFitsAt720p() = assertCompactIqamahEditorFits()

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun compactIqamahEditorFitsAt1080pDensity() = assertCompactIqamahEditorFits()

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun compactIqamahEditorFitsAt4kDensity() = assertCompactIqamahEditorFits()

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun compactIqamahEnglishScheduleLabelsStaySingleLine() {
        setIqamahSettingsContent(
            FakeOperatorPreferencesRepository(
                initialPreferences = OperatorPreferences(languageTag = "en"),
            ),
        )
        openSettingsDestination(SettingsDestination.IQAMAH)

        compose.onNodeWithText("Configure iqamah times").assertIsDisplayed()
        compose.onNodeWithText("Use schedule · 13:15").assertIsDisplayed()
        OPERATOR_IQAMAH_PRAYER_IDS.forEach { prayerId ->
            compose.onNodeWithTag(
                "$SETTINGS_IQAMAH_FIELD_TAG_PREFIX${prayerId}-schedule-label",
                useUnmergedTree = true,
            ).assert(SemanticsMatcher.expectValue(IqamahScheduleLabelFullyVisibleKey, true))
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun everyIqamahControlIsDpadReachableWithoutUsingOrderAsAnAction() {
        setIqamahSettingsContent()
        openSettingsDestination(SettingsDestination.IQAMAH)
        compose.onNodeWithTag(SettingsDestination.IQAMAH.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }

        OPERATOR_IQAMAH_PRAYER_IDS.forEachIndexed { index, prayerId ->
            val prefix = "$SETTINGS_IQAMAH_FIELD_TAG_PREFIX$prayerId"
            compose.onNodeWithTag("$prefix-reset").assertIsFocused().performKeyInput {
                pressKey(Key.DirectionRight)
            }
            compose.onNodeWithTag("$prefix-decrement").assertIsFocused().performKeyInput {
                pressKey(Key.DirectionRight)
            }
            compose.onNodeWithTag("$prefix-increment").assertIsFocused().performKeyInput {
                pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionLeft)
                if (index < OPERATOR_IQAMAH_PRAYER_IDS.lastIndex) pressKey(Key.DirectionDown)
            }
        }

        compose.onNodeWithTag("${SETTINGS_IQAMAH_FIELD_TAG_PREFIX}isha-reset")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_IQAMAH_SAVE_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun iqamahResetClearsOnlySelectedLocalOverridesBeforeSave() {
        val initial = OperatorIqamahConfiguration(
            fajrOffsetMinutes = 7,
            dhuhrFixedTimeMinutes = 13 * 60 + 20,
            asrOffsetMinutes = 9,
            maghribOffsetMinutes = 4,
            ishaOffsetMinutes = 11,
        )
        val preferences = FakeOperatorPreferencesRepository(
            initialPreferences = OperatorPreferences(iqamahConfiguration = initial),
        )
        setIqamahSettingsContent(preferences)
        openSettingsDestination(SettingsDestination.IQAMAH)
        compose.onNodeWithTag(SettingsDestination.IQAMAH.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag("${SETTINGS_IQAMAH_FIELD_TAG_PREFIX}fajr-reset")
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.Enter)
                pressKey(Key.DirectionDown)
            }
        compose.onNodeWithTag("${SETTINGS_IQAMAH_FIELD_TAG_PREFIX}dhuhr-reset")
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.Enter)
                repeat(4) { pressKey(Key.DirectionDown) }
            }
        compose.onNodeWithTag(SETTINGS_IQAMAH_SAVE_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()

        assertEquals(
            initial.copy(fajrOffsetMinutes = null, dhuhrFixedTimeMinutes = null),
            preferences.currentPreferences.iqamahConfiguration,
        )
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun iqamahPlusMinusUseOneMinuteAndResetRestoresScheduleState() {
        setIqamahSettingsContent()
        openSettingsDestination(SettingsDestination.IQAMAH)
        val prefix = "${SETTINGS_IQAMAH_FIELD_TAG_PREFIX}fajr"
        compose.onNodeWithTag(SettingsDestination.IQAMAH.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag("$prefix-reset").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag("$prefix-increment").assertIsFocused().performKeyInput {
            pressKey(Key.Enter)
            pressKey(Key.DirectionLeft)
        }
        compose.onNodeWithTag("$prefix-value").assertTextEquals("+1 мин")
        compose.onNodeWithTag("$prefix-decrement").assertIsFocused().performKeyInput {
            pressKey(Key.Enter)
            pressKey(Key.DirectionLeft)
        }
        compose.onNodeWithTag("$prefix-value").assertTextEquals("+0 мин")
        compose.onNodeWithTag("$prefix-reset")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
            .assertIsSelected()
        compose.onNodeWithTag("$prefix-value").assertTextEquals("—")
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun mediaStoreFallbackImportsBackgroundAndRestoresAppearanceFocus() {
        val preferences = FakeOperatorPreferencesRepository(
            initialPreferences = OperatorPreferences(lastSettingsDestination = "appearance"),
        )
        val importer = FakeOperatorImageAssetImporter(OperatorImageImportResult.Imported)
        setImageSelectionContent(preferences, importer)
        openPersistedSettingsDestination(SettingsDestination.APPEARANCE)
        compose.onNodeWithTag(SettingsDestination.APPEARANCE.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag("${SETTINGS_BACKGROUND_PREVIEW_TAG_PREFIX}golden_dusk")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_CUSTOM_BACKGROUND_PICKER_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        compose.onNodeWithTag("${MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX}1")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()

        compose.onNodeWithText("Изображение выбрано").assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_CUSTOM_BACKGROUND_PICKER_TAG).assertIsFocused()
        assertEquals(CUSTOM_BACKGROUND_STYLE_ID, preferences.currentPreferences.backgroundStyleId)
        assertEquals(listOf(OperatorImageSlot.BACKGROUND), importer.importedSlots)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun donationImageFailureIsLocalizedAndKeepsTheCurrentChoice() {
        val initial = OperatorPreferences(lastSettingsDestination = "donation")
        val preferences = FakeOperatorPreferencesRepository(
            initialPreferences = initial,
        )
        val importer = FakeOperatorImageAssetImporter(OperatorImageImportResult.TooLarge)
        setImageSelectionContent(preferences, importer)
        openPersistedSettingsDestination(SettingsDestination.DONATION)
        compose.onNodeWithTag(SETTINGS_DONATION_PICKER_TAG)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        compose.onNodeWithTag("${MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX}1")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()

        compose.onNodeWithTag(OPERATOR_IMAGE_FEEDBACK_TAG).assertIsDisplayed()
        compose.onNodeWithText("Файл слишком большой").assertExists()
        compose.onNodeWithTag(SETTINGS_DONATION_PICKER_TAG).assertIsFocused()
        assertEquals(
            initial.donationConfiguration.imageStyleId,
            preferences.currentPreferences.donationConfiguration.imageStyleId,
        )
        assertEquals(listOf(OperatorImageSlot.DONATION), importer.importedSlots)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun mediaStorePickerCancelIsSilentAndRestoresTheInvokingFocus() {
        val preferences = FakeOperatorPreferencesRepository(
            initialPreferences = OperatorPreferences(lastSettingsDestination = "appearance"),
        )
        setImageSelectionContent(
            preferences,
            FakeOperatorImageAssetImporter(OperatorImageImportResult.Imported),
        )
        openPersistedSettingsDestination(SettingsDestination.APPEARANCE)
        compose.onNodeWithTag(SETTINGS_CUSTOM_BACKGROUND_PICKER_TAG)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        compose.onNodeWithTag("${MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX}1")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag(MEDIA_IMAGE_PICKER_CANCEL_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()

        compose.onNodeWithTag(MEDIA_IMAGE_PICKER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(OPERATOR_IMAGE_FEEDBACK_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SETTINGS_CUSTOM_BACKGROUND_PICKER_TAG).assertIsFocused()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun mainDisplayFits720pAndKeepsSettingsFocused() {
        assertResponsiveDisplayIsVisible()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun mainDisplayFits1080pDensityAndKeepsSettingsFocused() {
        assertResponsiveDisplayIsVisible()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun mainDisplayFits4kDensityAndKeepsSettingsFocused() {
        assertResponsiveDisplayIsVisible()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun retentionCycleMovesOnlySafeForegroundAndPreservesFocus() {
        val currentInstant = mutableStateOf(fixedClock.instant())
        val schedule = schedule()
        compose.setContent {
            NamazTvTheme {
                ConnectedDisplayContent(
                    schedule = schedule,
                    currentInstant = currentInstant.value,
                    bootstrapState = SnapshotBootstrapState.Ready(schedule.snapshotId),
                    campaignEngine = CampaignEngine(),
                    qrCodeGenerator = QrCodeGenerator(),
                    onOpenSettings = {},
                )
            }
        }

        val rootBefore = compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG)
            .getUnclippedBoundsInRoot()
        val safeBefore = compose.onNodeWithTag(MAIN_DISPLAY_SAFE_CONTENT_TAG)
            .getUnclippedBoundsInRoot()
        compose.runOnIdle {
            currentInstant.value = currentInstant.value.plusSeconds(600L)
        }
        val rootAfter = compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG)
            .getUnclippedBoundsInRoot()
        val safeAfter = compose.onNodeWithTag(MAIN_DISPLAY_SAFE_CONTENT_TAG)
            .getUnclippedBoundsInRoot()

        assertEquals(rootBefore, rootAfter)
        assertEquals(safeBefore.left - 4.dp, safeAfter.left)
        assertEquals(safeBefore.top, safeAfter.top)
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).assertIsFocused()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun everyRetentionPositionStaysInside720pSafeFrame() {
        assertEveryRetentionPositionStaysInsideSafeFrame()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun everyRetentionPositionStaysInside1080pDensitySafeFrame() {
        assertEveryRetentionPositionStaysInsideSafeFrame()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun everyRetentionPositionStaysInside4kDensitySafeFrame() {
        assertEveryRetentionPositionStaysInsideSafeFrame()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun acceleratedOfflineWeekRollsLocalDateAndFailsClosedAfterCoverage() {
        val localSchedule = schedule().copy(
            coverageFrom = "2026-08-20",
            coverageTo = "2026-08-26",
            days = (20..26).map { day ->
                LocalPrayerDay(
                    "2026-08-$day",
                    "03:14",
                    "05:23",
                    "12:08",
                    "16:45",
                    "18:51",
                    "20:58",
                )
            },
        )
        val currentInstant = mutableStateOf(Instant.parse("2026-08-20T08:00:00Z"))
        val campaignEngine = CampaignEngine()
        val qrCodeGenerator = QrCodeGenerator()
        compose.setContent {
            NamazTvTheme {
                ConnectedDisplayContent(
                    schedule = localSchedule,
                    currentInstant = currentInstant.value,
                    bootstrapState = SnapshotBootstrapState.Ready(localSchedule.snapshotId),
                    campaignEngine = campaignEngine,
                    qrCodeGenerator = qrCodeGenerator,
                    onOpenSettings = {},
                )
            }
        }

        (20..26).forEach { day ->
            compose.runOnIdle {
                currentInstant.value = Instant.parse("2026-08-${day}T08:00:00Z")
            }
            compose.onNodeWithText("$day августа 2026").assertIsDisplayed()
            compose.onNodeWithText("12:00:00").assertIsDisplayed()
            compose.onNodeWithTag(DISPLAY_UNAVAILABLE_TAG).assertDoesNotExist()

            val root = compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG)
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            val safeContent = compose.onNodeWithTag(MAIN_DISPLAY_SAFE_CONTENT_TAG)
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            val rootWidth = root.right - root.left
            val rootHeight = root.bottom - root.top
            assert(safeContent.left - root.left >= rootWidth * 0.04f)
            assert(root.right - safeContent.right >= rootWidth * 0.04f)
            assert(safeContent.top - root.top >= rootHeight * 0.04f)
            assert(root.bottom - safeContent.bottom >= rootHeight * 0.04f)
        }

        compose.runOnIdle {
            currentInstant.value = Instant.parse("2026-08-27T08:00:00Z")
        }
        compose.onNodeWithText("Расписание недоступно").assertIsDisplayed()
        compose.onNodeWithText(
            "Код поддержки: SCHEDULE_DATE_OUTSIDE_COVERAGE",
        ).assertIsDisplayed()
        compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun fridayJumuahSummaryKeepsAllPrayerRowsInBounds() {
        assertResponsiveDisplayIsVisible(
            localSchedule = schedule().copy(
                jumuahSessions = listOf(
                    LocalJumuahSession(
                        id = "first",
                        label = "Первая",
                        khutbahTime = "12:40",
                        salahTime = "13:00",
                        validFrom = "2026-08-01",
                        validTo = "2026-08-31",
                    ),
                    LocalJumuahSession(
                        id = "second",
                        label = "Вторая",
                        khutbahTime = "13:40",
                        salahTime = "14:00",
                        validFrom = "2026-08-01",
                        validTo = "2026-08-31",
                    ),
                ),
            ),
            displayClock = Clock.fixed(
                Instant.parse("2026-08-21T08:30:00Z"),
                ZoneOffset.UTC,
            ),
        )
        val root = compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG).getUnclippedBoundsInRoot()
        listOf("first", "second").forEach { id ->
            val bounds = compose.onNodeWithTag("$JUMUAH_SESSION_TEST_TAG_PREFIX$id")
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            assert(bounds.left >= root.left && bounds.right <= root.right)
            assert(bounds.top >= root.top && bounds.bottom <= root.bottom)
        }
    }

    @Test
    fun longMixedArabicRussianMosqueNameDoesNotHidePrimaryAction() {
        val mixedName = "المسجد الجامع الثاني — Вторая Соборная мечеть Ульяновска"
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(
                    schedule().copy(mosqueName = mixedName),
                ),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithText(mixedName).assertExists()
        val mosqueBounds = compose.onNodeWithTag(MOSQUE_NAME_TEST_TAG).getUnclippedBoundsInRoot()
        val settingsBounds = compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG)
            .getUnclippedBoundsInRoot()
        assert(mosqueBounds.right <= settingsBounds.left)
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun countdownRegionKeepsStableWidthAcrossRepresentativeValues() {
        val schedule = schedule()
        val resolution = PrayerTimeEngine().resolve(
            schedule.toTimeEngineInput(),
            fixedClock.instant(),
        ) as PrayerTimeResolution.Available
        val state = mutableStateOf(
            schedule.toPrayerDisplayUiState(
                resolution,
                appStringsFor(ApplicationProvider.getApplicationContext<Context>(), AppLanguage.RUSSIAN),
            ).copy(countdown = "—:——:——"),
        )
        compose.setContent {
            MaterialTheme {
                MainPrayerDisplay(
                    state = state.value,
                    onOpenSettings = {},
                    requestInitialFocus = false,
                )
            }
        }
        val placeholderBounds = compose.onNodeWithTag(COUNTDOWN_TEST_TAG)
            .getUnclippedBoundsInRoot()
        val placeholderWidth = placeholderBounds.right - placeholderBounds.left

        compose.runOnIdle {
            state.value = state.value.copy(countdown = "12:34:56")
        }

        val valueBounds = compose.onNodeWithTag(COUNTDOWN_TEST_TAG)
            .getUnclippedBoundsInRoot()
        val valueWidth = valueBounds.right - valueBounds.left
        assertEquals(placeholderWidth, valueWidth)
    }

    @Test
    fun corruptBundledSnapshotShowsSafeSupportCodeAndKeepsSettingsReachable() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(null),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Diagnostic("SNAPSHOT_INVALID_TIMEZONE"),
                ),
            )
        }

        compose.onNodeWithText("Расписание недоступно").assertExists()
        compose.onNodeWithText("Код поддержки: SNAPSHOT_INVALID_TIMEZONE").assertExists()
        compose.onNodeWithTag(DISPLAY_UNAVAILABLE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(UNAVAILABLE_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).assertIsFocused()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun unavailableScreenKeeps720pSafeFrame() = assertUnavailableScreenSafeFrame()

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun unavailableScreenKeeps1080pDensitySafeFrame() = assertUnavailableScreenSafeFrame()

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun unavailableScreenKeeps4kDensitySafeFrame() = assertUnavailableScreenSafeFrame()

    @Test
    fun mosqueLocalDateOutsideCoverageShowsSafeDiagnosticAndSettings() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = Clock.fixed(
                    Instant.parse("2026-08-22T00:00:00Z"),
                    ZoneOffset.UTC,
                ),
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithText("Расписание недоступно").assertExists()
        compose.onNodeWithText("Код поддержки: SCHEDULE_DATE_OUTSIDE_COVERAGE").assertExists()
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).assertIsFocused()
    }

    @Test
    fun previousSnapshotRecoveryIsVisibleWithoutHidingLocalSchedule() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready(
                        "synthetic-ulsk-demo-2026-08-v1",
                        recoveryCode = "SNAPSHOT_PREVIOUS_RESTORED",
                    ),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithText("Синтетическая демонстрационная мечеть").assertExists()
        compose.onNodeWithText("Код поддержки: SNAPSHOT_PREVIOUS_RESTORED").assertExists()
    }

    @Test
    fun corruptLocalScheduleFlowShowsBoundedSupportCode() {
        val corruptRepository = object : PrayerScheduleRepository {
            override fun observeActiveSchedule(): Flow<LocalPrayerSchedule?> = flow {
                throw CorruptLocalSnapshotException("invalid_prayer_day_flags")
            }
        }

        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = corruptRepository,
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithText("Расписание недоступно").assertExists()
        compose.onNodeWithText(
            "Код поддержки: SNAPSHOT_LOCAL_INVALID_PRAYER_DAY_FLAGS",
        ).assertExists()
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).assertIsFocused()
    }

    private fun assertCompactIqamahEditorFits() {
        setIqamahSettingsContent()
        openSettingsDestination(SettingsDestination.IQAMAH)
        compose.onNodeWithText("Настроить время Икамата").assertIsDisplayed()
        val panel = compose.onNodeWithTag(SETTINGS_CONTENT_PANEL_TAG).getUnclippedBoundsInRoot()
        OPERATOR_IQAMAH_PRAYER_IDS.forEach { prayerId ->
            val prefix = "$SETTINGS_IQAMAH_FIELD_TAG_PREFIX$prayerId"
            listOf(prefix, "$prefix-reset", "$prefix-decrement", "$prefix-value", "$prefix-increment")
                .forEach { tag ->
                    val bounds = compose.onNodeWithTag(tag)
                        .assertIsDisplayed()
                        .getUnclippedBoundsInRoot()
                    assertTrue("$tag left", bounds.left >= panel.left)
                    assertTrue("$tag right", bounds.right <= panel.right)
                    assertTrue("$tag top", bounds.top >= panel.top)
                    assertTrue("$tag bottom", bounds.bottom <= panel.bottom)
                }
            compose.onNodeWithTag("$prefix-schedule-label", useUnmergedTree = true)
                .assert(SemanticsMatcher.expectValue(IqamahScheduleLabelFullyVisibleKey, true))
        }
        compose.onNodeWithText("По расписанию · 13:15").assertIsDisplayed()
        listOf(SETTINGS_IQAMAH_SAVE_TAG, SETTINGS_PAGE_ACTION_TEST_TAG).forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$tag left", bounds.left >= panel.left)
            assertTrue("$tag right", bounds.right <= panel.right)
            assertTrue("$tag top", bounds.top >= panel.top)
            assertTrue("$tag bottom", bounds.bottom <= panel.bottom)
        }
    }

    private fun setIqamahSettingsContent(
        preferences: FakeOperatorPreferencesRepository = FakeOperatorPreferencesRepository(),
    ) {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = preferences,
                prayerScheduleRepository = FakePrayerScheduleRepository(scheduleWithApprovedDhuhr()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }
    }

    private fun setImageSelectionContent(
        preferences: FakeOperatorPreferencesRepository,
        importer: FakeOperatorImageAssetImporter,
    ) {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = preferences,
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
                imageSelectionEnvironment = FakeOperatorImageSelectionEnvironment,
                imageAssetImporter = importer,
                mediaImageCatalog = FakeOperatorMediaImageCatalog,
            )
        }
    }

    private fun scheduleWithApprovedDhuhr() = schedule().copy(
        iqamahRules = listOf(
            ru.namaztime.tv.repository.LocalIqamahRule(
                id = "approved-dhuhr-1315",
                prayer = "dhuhr",
                validFrom = "2026-08-19",
                validTo = "2026-08-21",
                weekdaysMask = 127,
                priority = 100,
                mode = "fixed_time",
                fixedTime = "13:15",
                offsetMinutes = null,
                reason = "synthetic approved policy",
            ),
        ),
    )

    private fun assertResponsiveDisplayIsVisible(
        localSchedule: LocalPrayerSchedule = schedule(),
        displayClock: Clock = fixedClock,
    ) {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(localSchedule),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = displayClock,
                tickIntervalMillis = null,
            )
        }

        val rootBounds = compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val contentBounds = compose.onNodeWithTag(MAIN_DISPLAY_SAFE_CONTENT_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val rootWidth = rootBounds.right - rootBounds.left
        val rootHeight = rootBounds.bottom - rootBounds.top
        assert(contentBounds.left - rootBounds.left >= rootWidth * 0.04f)
        assert(rootBounds.right - contentBounds.right >= rootWidth * 0.04f)
        assert(contentBounds.top - rootBounds.top >= rootHeight * 0.04f)
        assert(rootBounds.bottom - contentBounds.bottom >= rootHeight * 0.04f)
        listOf(
            NEXT_EVENT_CARD_TAG,
            LOCAL_CLOCK_CARD_TAG,
            PRAYER_LIST_CARD_TAG,
            IQAMAH_STRIP_TAG,
        ).forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assert(bounds.left >= contentBounds.left && bounds.right <= contentBounds.right)
            assert(bounds.top >= contentBounds.top && bounds.bottom <= contentBounds.bottom)
        }
        var previousBounds: DpRect? = null
        listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha").forEach { prayer ->
            val rowBounds = compose.onNodeWithTag("$PRAYER_ROW_TEST_TAG_PREFIX$prayer")
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            assert(rowBounds.left >= rootBounds.left)
            assert(rowBounds.right <= rootBounds.right)
            assert(rowBounds.top >= rootBounds.top)
            assert(rowBounds.bottom <= rootBounds.bottom)
            previousBounds?.let { previous -> assert(previous.bottom <= rowBounds.top) }
            previousBounds = rowBounds
        }
        val settingsBounds = compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG)
            .assertIsDisplayed()
            .assertIsFocused()
            .getUnclippedBoundsInRoot()
        assert(settingsBounds.left >= rootBounds.left && settingsBounds.right <= rootBounds.right)
        assert(settingsBounds.top >= rootBounds.top && settingsBounds.bottom <= rootBounds.bottom)
    }

    private fun assertEveryRetentionPositionStaysInsideSafeFrame() {
        val currentInstant = mutableStateOf(Instant.parse("2026-08-19T23:00:00Z"))
        val schedule = schedule()
        compose.setContent {
            NamazTvTheme {
                ConnectedDisplayContent(
                    schedule = schedule,
                    currentInstant = currentInstant.value,
                    bootstrapState = SnapshotBootstrapState.Ready(schedule.snapshotId),
                    campaignEngine = CampaignEngine(),
                    qrCodeGenerator = QrCodeGenerator(),
                    onOpenSettings = {},
                )
            }
        }

        val root = compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG)
            .getUnclippedBoundsInRoot()
        val insets = TvSafeFrameInsets.forSize(
            width = root.right - root.left,
            height = root.bottom - root.top,
        )
        repeat(6) { slot ->
            compose.runOnIdle {
                currentInstant.value = Instant.parse("2026-08-19T23:00:00Z")
                    .plusSeconds(slot * 600L)
            }
            val safeContent = compose.onNodeWithTag(MAIN_DISPLAY_SAFE_CONTENT_TAG)
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            assert(safeContent.left >= root.left + insets.horizontal)
            assert(safeContent.right <= root.right - insets.horizontal)
            assert(safeContent.top >= root.top + insets.vertical)
            assert(safeContent.bottom <= root.bottom - insets.vertical)
            compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).assertIsFocused()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun openSettingsDestination(destination: SettingsDestination) {
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).performKeyInput { pressKey(Key.Enter) }
        repeat(destination.ordinal) { index ->
            compose.onNodeWithTag(SettingsDestination.entries[index].navigationTestTag)
                .performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithTag(destination.navigationTestTag).assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun openPersistedSettingsDestination(destination: SettingsDestination) {
        compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag(destination.navigationTestTag).assertIsFocused()
    }

    private fun assertUnavailableScreenSafeFrame() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(null),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Diagnostic("SNAPSHOT_INVALID_TIMEZONE"),
                ),
            )
        }

        val root = compose.onNodeWithTag(TV_ATMOSPHERIC_BACKGROUND_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val safeContent = compose.onNodeWithTag(DISPLAY_UNAVAILABLE_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val rootWidth = root.right - root.left
        val rootHeight = root.bottom - root.top
        assert(safeContent.left - root.left >= rootWidth * 0.04f)
        assert(root.right - safeContent.right >= rootWidth * 0.04f)
        assert(safeContent.top - root.top >= rootHeight * 0.04f)
        assert(root.bottom - safeContent.bottom >= rootHeight * 0.04f)
        val panel = compose.onNodeWithTag(UNAVAILABLE_PANEL_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assert(panel.left >= safeContent.left && panel.right <= safeContent.right)
        assert(panel.top >= safeContent.top && panel.bottom <= safeContent.bottom)
    }

    private fun schedule() = LocalPrayerSchedule(
        snapshotId = "synthetic-ulsk-demo-2026-08-v1",
        mosqueId = "mosque-demo-ulsk",
        mosqueName = "Синтетическая демонстрационная мечеть",
        timezoneId = "Europe/Ulyanovsk",
        sourceKind = "manual_import",
        authorityName = "Synthetic test fixture — not an official authority",
        coverageFrom = "2026-08-19",
        coverageTo = "2026-08-21",
        diagnostics = LocalSnapshotDiagnostics(
            dataClassification = "synthetic",
            rawSha256 = "1".repeat(64),
            parserVersion = "synthetic/1",
            approvalId = "approval-test",
            approvalStatus = "approved",
            signingKeyId = "test-placeholder-key",
        ),
        days = listOf(
            LocalPrayerDay("2026-08-19", "03:12", "05:21", "12:08", "16:47", "18:53", "21:01"),
            LocalPrayerDay("2026-08-20", "03:14", "05:23", "12:08", "16:45", "18:51", "20:58"),
            LocalPrayerDay("2026-08-21", "03:16", "05:25", "12:08", "16:43", "18:48", "20:55"),
        ),
    )

    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-08-19T23:20:00Z"),
        ZoneOffset.UTC,
    )

    private fun assertCampaignIsSafelyHidden(campaign: LocalCampaign) {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(
                    schedule().copy(campaigns = listOf(campaign)),
                ),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
                clock = fixedClock,
                tickIntervalMillis = null,
            )
        }

        compose.onNodeWithTag(QR_CAMPAIGN_PANEL_TAG).assertDoesNotExist()
        compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG).assertIsDisplayed()
    }

    private fun campaign() = LocalCampaign(
        id = "campaign",
        kind = "website",
        httpsUrl = "https://example.org/mosque",
        title = "Расписание мечети",
        subtitle = "Откройте на телефоне",
        startsAt = "2026-08-19T00:00:00Z",
        endsAt = "2026-08-21T00:00:00Z",
        placement = "with_prayer_times",
    )

    private fun assertReferenceBounds(
        tag: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        tolerance: Float = 8f,
    ) {
        val actual = compose.onNodeWithTag(tag)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue("$tag left=${actual.left.value}", kotlin.math.abs(actual.left.value - left) <= tolerance)
        assertTrue("$tag top=${actual.top.value}", kotlin.math.abs(actual.top.value - top) <= tolerance)
        assertTrue("$tag right=${actual.right.value}", kotlin.math.abs(actual.right.value - right) <= tolerance)
        assertTrue("$tag bottom=${actual.bottom.value}", kotlin.math.abs(actual.bottom.value - bottom) <= tolerance)
    }
}

private class FakeOperatorPreferencesRepository(
    private val failWrites: Boolean = false,
    initialPreferences: OperatorPreferences = OperatorPreferences(),
) : OperatorPreferencesRepository {
    private val state = MutableStateFlow(initialPreferences)

    override val preferences: Flow<OperatorPreferences> = state

    val currentPreferences: OperatorPreferences
        get() = state.value

    override suspend fun setLastSettingsDestination(route: String) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(lastSettingsDestination = route)
    }

    override suspend fun setReducedMotion(enabled: Boolean) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(reducedMotion = enabled)
    }

    override suspend fun setLanguageTag(languageTag: String) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(languageTag = languageTag)
    }

    override suspend fun setScreenRetentionShiftEnabled(enabled: Boolean) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(screenRetentionShiftEnabled = enabled)
    }

    override suspend fun setMosquePresentationIdentity(
        identity: ru.namaztime.tv.repository.OperatorMosquePresentationIdentity,
    ) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(mosquePresentationIdentity = identity)
    }

    override suspend fun setBackgroundStyleId(styleId: String) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(backgroundStyleId = styleId)
    }

    override suspend fun setQrConfiguration(configuration: OperatorQrConfiguration) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(qrConfiguration = configuration)
    }

    override suspend fun setIqamahOffset(prayerId: String, offsetMinutes: Int?) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(
            iqamahConfiguration = state.value.iqamahConfiguration
                .withEditorValue(prayerId, offsetMinutes),
        )
    }

    override suspend fun setIqamahConfiguration(
        configuration: ru.namaztime.tv.repository.OperatorIqamahConfiguration,
    ) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(iqamahConfiguration = configuration)
    }

    override suspend fun setDonationConfiguration(
        configuration: ru.namaztime.tv.repository.OperatorDonationConfiguration,
    ) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(donationConfiguration = configuration)
    }

    override suspend fun setDonationImageStyleId(styleId: String) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(
            donationConfiguration = state.value.donationConfiguration.copy(imageStyleId = styleId),
        )
    }

    override suspend fun setDisplayMode(
        mode: ru.namaztime.tv.repository.OperatorDisplayMode,
    ) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(displayMode = mode)
    }
}

private class FakePrayerScheduleRepository(schedule: LocalPrayerSchedule?) : PrayerScheduleRepository {
    private val state = MutableStateFlow(schedule)

    override fun observeActiveSchedule(): Flow<LocalPrayerSchedule?> = state
}

private object FakeOperatorImageSelectionEnvironment : OperatorImageSelectionEnvironment {
    override fun capabilities() = OperatorImageSelectionCapabilities(
        sdkInt = 35,
        openDocumentResolvable = false,
        photoPickerAvailable = false,
        mediaReadPermissionGranted = true,
    )

    override fun permissionName(permission: OperatorImageReadPermission): String =
        "synthetic.permission.${permission.name}"
}

private object FakeOperatorMediaImageCatalog : OperatorMediaImageCatalog {
    override suspend fun loadPage(offset: Int, limit: Int): OperatorMediaImagePage =
        OperatorMediaImagePage(
            items = if (offset == 0) {
                listOf(
                    OperatorMediaImage(
                        id = "1",
                        contentUri = "content://synthetic/images/1",
                        displayName = "owner-image.jpg",
                        bucketId = "pictures",
                        bucketName = "Pictures",
                    ),
                )
            } else {
                emptyList()
            },
            nextOffset = null,
        )
}

private class FakeOperatorImageAssetImporter(
    private val result: OperatorImageImportResult,
) : OperatorImageAssetImporter {
    val importedSlots = mutableListOf<OperatorImageSlot>()

    override suspend fun import(slot: OperatorImageSlot, uri: Uri): OperatorImageImportResult {
        importedSlots += slot
        return result
    }
}
