package com.example.namaztime.tv.presentation

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class SettingsShellUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun dpadReachesEveryNavigationItemAndSelectedPageAction() {
        compose.setContent {
            SettingsShell(
                initialDestination = SettingsDestination.initial,
                onDestinationChanged = {},
                onExit = {},
            )
        }

        SettingsDestination.entries.forEachIndexed { index, destination ->
            compose.onNodeWithTag(destination.navigationTestTag)
                .assertIsFocused()
                .assertIsSelected()
            if (index < SettingsDestination.entries.lastIndex) {
                compose.onNodeWithTag(destination.navigationTestTag).performKeyInput {
                    pressKey(Key.DirectionDown)
                }
            }
        }

        compose.onNodeWithTag(SettingsDestination.entries.last().navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).assertIsFocused()
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        compose.onNodeWithTag(SettingsDestination.entries.last().navigationTestTag).assertIsFocused()
    }
}
