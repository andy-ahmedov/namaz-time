package com.example.namaztime.tv.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
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
import androidx.tv.material3.MaterialTheme
import com.example.namaztime.tv.repository.OperatorPreferences
import com.example.namaztime.tv.repository.OperatorPreferencesRepository
import com.example.namaztime.tv.repository.LocalPrayerDay
import com.example.namaztime.tv.repository.LocalPrayerSchedule
import com.example.namaztime.tv.repository.LocalSnapshotDiagnostics
import com.example.namaztime.tv.repository.CorruptLocalSnapshotException
import com.example.namaztime.tv.repository.PrayerScheduleRepository
import com.example.namaztime.tv.data.snapshot.SnapshotBootstrapState
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Assert.assertEquals
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
    fun dpadOpensSettingsAndRestoresDisplayFocusOnExit() {
        compose.setContent { NamazTvApp(FakeOperatorPreferencesRepository()) }

        compose.onNodeWithText("Настройки").assertIsFocused().performKeyInput {
            pressKey(Key.Enter)
        }
        compose.onNodeWithTag(SettingsDestination.initial.navigationTestTag).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).assertIsFocused().performKeyInput {
            pressKey(Key.Enter)
        }

        compose.onNodeWithText("Настройки").assertIsFocused()
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
            )
        }

        compose.onNodeWithText("Синтетическая демонстрационная мечеть").assertExists()
        compose.onNodeWithText("Азан").assertExists()
        compose.onNodeWithText("Икамат").assertExists()
        compose.onNodeWithText("Фаджр").assertExists()
        compose.onNodeWithText("Восход").assertExists()
        compose.onNodeWithText("Зухр").assertExists()
        compose.onNodeWithText("Аср").assertExists()
        compose.onNodeWithText("Магриб").assertExists()
        compose.onNodeWithText("Иша").assertExists()
        compose.onNodeWithText("Следующий намаз").assertExists()
        compose.onNodeWithText("ТЕСТОВЫЕ ДАННЫЕ").assertExists()
        compose.onNodeWithContentDescription(
            "Фаджр, азан 03:12, икамат не указана",
        ).assertExists()
        compose.onNodeWithContentDescription(
            "Восход, азан 05:21, икамат не предусмотрена",
        ).assertExists()
        compose.onNodeWithText("Prayer schedule unavailable").assertDoesNotExist()
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
        val state = mutableStateOf(
            schedule().toPrayerDisplayUiState("2026-08-19").copy(countdown = "—:——:——"),
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
            )
        }

        compose.onNodeWithText("Prayer schedule unavailable").assertExists()
        compose.onNodeWithText(
            "Support code: SNAPSHOT_LOCAL_INVALID_PRAYER_DAY_FLAGS",
        ).assertExists()
        compose.onNodeWithText("Настройки").assertIsFocused()
    }

    private fun assertResponsiveDisplayIsVisible() {
        compose.setContent {
            NamazTvApp(
                operatorPreferencesRepository = FakeOperatorPreferencesRepository(),
                prayerScheduleRepository = FakePrayerScheduleRepository(schedule()),
                bootstrapState = MutableStateFlow(
                    SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
                ),
            )
        }

        val rootBounds = compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
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
        ),
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
