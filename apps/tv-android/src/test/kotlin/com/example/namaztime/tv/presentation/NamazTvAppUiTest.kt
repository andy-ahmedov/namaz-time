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
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
