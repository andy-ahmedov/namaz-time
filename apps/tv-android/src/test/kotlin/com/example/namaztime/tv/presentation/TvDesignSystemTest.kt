package com.example.namaztime.tv.presentation

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class TvDesignSystemTest {
    @Test
    fun focusIndicatorUsesTheSingleGoldAccentToken() {
        assertEquals(DarkTvColors.accent, DarkTvColors.focus)
    }

    @Test
    fun photographicBackgroundUsesMattePanelsAndABoundedDimTreatment() {
        assertTrue(DarkTvColors.surfaceTop.alpha >= 0.84f)
        assertTrue(DarkTvColors.surfaceBottom.alpha >= 0.88f)
        assertTrue(DarkTvColors.surfaceStrong.alpha >= 0.88f)
        TvBackgroundStyle.entries.forEach { style ->
            assertTrue("${style.id} scrim must subordinate the photo", style.scrimAlpha >= 0.32f)
        }
    }

    @Test
    fun textAndFocusTokensMeetDarkSurfaceContrastFloors() {
        val surface = composite(DarkTvColors.surfaceTop, DarkTvColors.backgroundTop)
        val accentedSurface = composite(DarkTvColors.accentSoft, DarkTvColors.backgroundTop)
        val pairs = listOf(
            "primary text" to (DarkTvColors.textPrimary to 7.0),
            "secondary text" to (DarkTvColors.textSecondary to 4.5),
            "accent labels" to (DarkTvColors.accent to 4.5),
            "warning labels" to (DarkTvColors.warning to 4.5),
            "focus indicator" to (DarkTvColors.focus to 3.0),
        )

        pairs.forEach { (name, pair) ->
            val (foreground, minimum) = pair
            listOf("standard" to surface, "accented" to accentedSurface).forEach {
                    (surfaceName, background) ->
                val ratio = contrastRatio(foreground, background)
                assertTrue(
                    "$name on $surfaceName contrast $ratio must be >= $minimum",
                    ratio >= minimum,
                )
            }
        }
    }

    private fun composite(foreground: Color, background: Color): Color {
        val alpha = foreground.alpha
        return Color(
            red = foreground.red * alpha + background.red * (1f - alpha),
            green = foreground.green * alpha + background.green * (1f - alpha),
            blue = foreground.blue * alpha + background.blue * (1f - alpha),
            alpha = 1f,
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val high = max(luminance(first), luminance(second))
        val low = min(luminance(first), luminance(second))
        return (high + 0.05) / (low + 0.05)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun linear(value: Float): Double = if (value <= 0.04045f) {
        value / 12.92
    } else {
        ((value + 0.055) / 1.055).pow(2.4)
    }
}
