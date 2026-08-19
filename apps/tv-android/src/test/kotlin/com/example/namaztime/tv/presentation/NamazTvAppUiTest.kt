package com.example.namaztime.tv.presentation

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.example.namaztime.tv.repository.OperatorPreferences
import com.example.namaztime.tv.repository.OperatorPreferencesRepository
import com.example.namaztime.tv.repository.LocalPrayerDay
import com.example.namaztime.tv.repository.LocalPrayerSchedule
import com.example.namaztime.tv.repository.CorruptLocalSnapshotException
import com.example.namaztime.tv.repository.PrayerScheduleRepository
import com.example.namaztime.tv.data.snapshot.SnapshotBootstrapState
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
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

        compose.onNodeWithText("Open settings").assertIsFocused().performKeyInput {
            pressKey(Key.Enter)
        }
        compose.onNodeWithTag(SettingsDestination.initial.navigationTestTag).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).assertIsFocused().performKeyInput {
            pressKey(Key.Enter)
        }

        compose.onNodeWithText("Open settings").assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun preferenceWriteFailureDoesNotInterruptDpadNavigation() {
        compose.setContent {
            NamazTvApp(FakeOperatorPreferencesRepository(failWrites = true))
        }

        compose.onNodeWithText("Open settings").performKeyInput { pressKey(Key.Enter) }
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
        compose.onNodeWithText("Synthetic test fixture — not an official authority").assertExists()
        compose.onNodeWithText("Prayer schedule unavailable").assertDoesNotExist()
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
        compose.onNodeWithText("Open settings").assertIsFocused()
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
        compose.onNodeWithText("Open settings").assertIsFocused()
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
