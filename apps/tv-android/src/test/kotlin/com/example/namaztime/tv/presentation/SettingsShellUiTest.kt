package com.example.namaztime.tv.presentation

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
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

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun settingsPanelsFit720pSafeFrame() = assertSettingsPanelsFit()

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun settingsPanelsFit1080pDensitySafeFrame() = assertSettingsPanelsFit()

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun settingsPanelsFit4kDensitySafeFrame() = assertSettingsPanelsFit()

    private fun assertSettingsPanelsFit() {
        compose.setContent {
            NamazTvTheme {
                SettingsShell(
                    initialDestination = SettingsDestination.initial,
                    onDestinationChanged = {},
                    onExit = {},
                )
            }
        }

        val shell = compose.onNodeWithTag(SETTINGS_SHELL_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val rootWidth = root.right - root.left
        val rootHeight = root.bottom - root.top
        assert(shell.left - root.left >= rootWidth * 0.04f)
        assert(root.right - shell.right >= rootWidth * 0.04f)
        assert(shell.top - root.top >= rootHeight * 0.04f)
        assert(root.bottom - shell.bottom >= rootHeight * 0.04f)
        val navigation = compose.onNodeWithTag(SETTINGS_NAVIGATION_PANEL_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag(SETTINGS_CONTENT_PANEL_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        listOf(navigation, content).forEach { panel ->
            assert(panel.left >= shell.left && panel.right <= shell.right)
            assert(panel.top >= shell.top && panel.bottom <= shell.bottom)
        }
        assert(navigation.right < content.left)
    }
}
