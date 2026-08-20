package com.example.namaztime.tv.presentation

import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.example.namaztime.tv.repository.OperatorPreferences
import com.example.namaztime.tv.repository.OperatorPreferencesRepository
import com.example.namaztime.tv.repository.LocalPrayerDay
import com.example.namaztime.tv.repository.LocalPrayerSchedule
import com.example.namaztime.tv.repository.LocalJumuahSession
import com.example.namaztime.tv.repository.LocalCampaign
import com.example.namaztime.tv.repository.LocalSnapshotDiagnostics
import com.example.namaztime.tv.repository.CorruptLocalSnapshotException
import com.example.namaztime.tv.repository.PrayerScheduleRepository
import com.example.namaztime.tv.repository.toTimeEngineInput
import com.example.namaztime.tv.data.snapshot.SnapshotBootstrapState
import com.example.namaztime.tv.domain.CampaignEngine
import com.example.namaztime.tv.domain.PrayerTimeEngine
import com.example.namaztime.tv.domain.PrayerTimeResolution
import com.example.namaztime.tv.domain.QrCodeGenerator
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun dpadOpensSettingsAndRestoresDisplayFocusOnExit() {
        compose.setContent { NamazTvApp(FakeOperatorPreferencesRepository()) }

        compose.onNodeWithText("Настройки").assertIsFocused().performKeyInput {
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

        compose.onNodeWithText("Настройки").assertIsFocused()
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
        compose.onNodeWithText("Настройки").performKeyInput { pressKey(Key.Enter) }
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

        compose.onNodeWithText("Настройки").performKeyInput { pressKey(Key.Enter) }
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
        compose.onNodeWithText("До следующего события").assertExists()
        compose.onNodeWithText("03:20:00").assertExists()
        compose.onNodeWithTag(NEXT_EVENT_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(LOCAL_CLOCK_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(PRAYER_LIST_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(IQAMAH_STRIP_TAG).assertIsDisplayed()
        compose.onNodeWithText("Икамат · ближайший").assertExists()
        compose.onNodeWithText("Не указан").assertExists()
        compose.onNodeWithText("ТЕСТОВЫЕ ДАННЫЕ").assertExists()
        compose.onNodeWithContentDescription(
            "Фаджр, азан 03:14, икамат не указана",
        ).assertExists()
        compose.onNodeWithContentDescription(
            "Восход, азан 05:23, икамат не предусмотрена",
        ).assertExists()
        compose.onNodeWithText("Prayer schedule unavailable").assertDoesNotExist()
        compose.onNodeWithTag(QR_CAMPAIGN_PANEL_TAG).assertDoesNotExist()
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
        val future = campaign().copy(
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
        compose.onNodeWithText("Настройки").performKeyInput { pressKey(Key.Enter) }
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
        assert(panel.bottom <= action.top)
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
        compose.onNodeWithText("Prayer schedule unavailable").assertIsDisplayed()
        compose.onNodeWithText(
            "Support code: SCHEDULE_DATE_OUTSIDE_COVERAGE",
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
            schedule.toPrayerDisplayUiState(resolution).copy(countdown = "—:——:——"),
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

        compose.onNodeWithText("Prayer schedule unavailable").assertExists()
        compose.onNodeWithText("Support code: SNAPSHOT_INVALID_TIMEZONE").assertExists()
        compose.onNodeWithTag(DISPLAY_UNAVAILABLE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(UNAVAILABLE_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithText("Настройки").assertIsFocused()
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

        compose.onNodeWithText("Prayer schedule unavailable").assertExists()
        compose.onNodeWithText("Support code: SCHEDULE_DATE_OUTSIDE_COVERAGE").assertExists()
        compose.onNodeWithText("Настройки").assertIsFocused()
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
        compose.onNodeWithText("Support code: SNAPSHOT_PREVIOUS_RESTORED").assertExists()
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

        compose.onNodeWithText("Prayer schedule unavailable").assertExists()
        compose.onNodeWithText(
            "Support code: SNAPSHOT_LOCAL_INVALID_PRAYER_DAY_FLAGS",
        ).assertExists()
        compose.onNodeWithText("Настройки").assertIsFocused()
    }

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
}

private class FakeOperatorPreferencesRepository(
    private val failWrites: Boolean = false,
) : OperatorPreferencesRepository {
    private val state = MutableStateFlow(OperatorPreferences())

    override val preferences: Flow<OperatorPreferences> = state

    override suspend fun setLastSettingsDestination(route: String) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(lastSettingsDestination = route)
    }

    override suspend fun setReducedMotion(enabled: Boolean) {
        if (failWrites) throw IOException("synthetic preference storage failure")
        state.value = state.value.copy(reducedMotion = enabled)
    }
}

private class FakePrayerScheduleRepository(schedule: LocalPrayerSchedule?) : PrayerScheduleRepository {
    private val state = MutableStateFlow(schedule)

    override fun observeActiveSchedule(): Flow<LocalPrayerSchedule?> = state
}
