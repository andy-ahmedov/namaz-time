package ru.namaztime.tv.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class ScheduleBlockTransparencyTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun arrowsAdjustFivePercentClampAndDownLeavesSlider() {
        val value = mutableIntStateOf(95)
        val first = FocusRequester()
        compose.setContent {
            NamazTvTheme {
                Column {
                    ScheduleBlockTransparencySlider(
                        value.intValue, { value.intValue = it }, Modifier.focusRequester(first),
                    )
                    ScheduleBlockTransparencySlider(20, {}, Modifier)
                }
            }
        }
        compose.runOnIdle { first.requestFocus() }
        val slider = compose.onAllNodes(
            androidx.compose.ui.test.hasTestTag(SCHEDULE_BLOCK_TRANSPARENCY_TAG),
        )[0]
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(100, value.intValue) }
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(100, value.intValue) }
        slider.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertEquals(95, value.intValue) }
        slider.performKeyInput { repeat(19) { pressKey(Key.DirectionLeft) } }
        slider.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertEquals(0, value.intValue) }
        slider.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onAllNodes(
            androidx.compose.ui.test.hasTestTag(SCHEDULE_BLOCK_TRANSPARENCY_TAG),
        )[1].assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun rapidKeysAccumulateWhilePersistenceHasNotEmitted() {
        val requested = mutableListOf<Int>()
        val focus = FocusRequester()
        compose.setContent {
            NamazTvTheme {
                ScheduleBlockTransparencySlider(20, { requested.add(it) }, Modifier.focusRequester(focus))
            }
        }
        compose.runOnIdle { focus.requestFocus() }
        compose.onNodeWithTag(SCHEDULE_BLOCK_TRANSPARENCY_TAG)
            .performKeyInput { repeat(6) { pressKey(Key.DirectionRight) } }
        compose.runOnIdle { assertEquals(listOf(25, 30, 35, 40, 45, 50), requested) }
    }

    @Test
    fun surfaceAlphaUsesEndpointsAndOtherScreensRetainOriginalColor() {
        val original = Color(0xAD303A47)
        val values = mutableListOf<Color>()
        compose.setContent {
            values.clear()
            values.add(original.scheduleBlockColor())
            listOf(0, 20, 100).forEach { percent ->
                CompositionLocalProvider(LocalScheduleBlockTransparency provides percent) {
                    values.add(original.scheduleBlockColor())
                }
            }
        }
        compose.runOnIdle {
            assertEquals(original, values[0])
            assertEquals(1f, values[1].alpha, 0.001f)
            assertEquals(0.8f, values[2].alpha, 0.005f)
            assertEquals(0f, values[3].alpha, 0.001f)
            values.forEach { assertEquals(original.red, it.red, 0.001f) }
        }
    }
}
