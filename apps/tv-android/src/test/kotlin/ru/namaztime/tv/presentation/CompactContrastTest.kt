package ru.namaztime.tv.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactContrastTest {
    @Test
    fun glassTextRemainsReadableEvenOverAWhiteCustomImage() {
        val style = CompactVisualStyle
        // Glass is only used in the right rail, where the localized scrim reaches full strength.
        val background = style.cinematicScrim.copy(alpha = style.railScrimAlpha).compositeOver(
            style.cinematicScrim.copy(alpha = style.globalScrimAlpha).compositeOver(Color.White))
        assertTrue("header and locality on a white image", (style.textPrimary.luminance() + .05f) / (background.luminance() + .05f) >= 4.5f)
        for (surface in listOf(
            style.surfaceTop,
            style.surfaceBottom,
            style.heroSurfaceTop,
            style.heroSurfaceBottom,
            style.campaignSurfaceTop,
            style.campaignSurfaceBottom,
        )) {
            val glass = style.glow.compositeOver(surface.compositeOver(background))
            for (text in listOf(style.textPrimary, style.textSecondary, style.accent)) {
                val ratio = (text.luminance() + .05f) / (glass.luminance() + .05f)
                assertTrue("text $text against worst-case glass: $ratio", ratio >= 4.5f)
            }
            val active = style.activeLeading.compositeOver(glass)
            val ratio = (style.textPrimary.luminance() + .05f) / (active.luminance() + .05f)
            assertTrue("active prayer name/time: $ratio", ratio >= 4.5f)
        }
    }

    @Test
    fun semanticRolesCreateWarmAccentDepthAndAStrongActiveFocalState() {
        val style = CompactVisualStyle
        assertTrue("accent remains warm amber", style.accent.red > style.accent.green && style.accent.green > style.accent.blue)
        assertTrue("accent is saturated rather than pale beige", style.accent.red - style.accent.blue >= .50f)
        assertTrue("perimeter is softer than the inner top highlight", style.surfaceOutline.alpha < style.topHighlight.alpha)
        assertTrue("hero glass is one tonal step lighter", style.heroSurfaceTop.luminance() > style.surfaceTop.luminance())
        assertTrue("campaign glass is one tonal step deeper", style.campaignSurfaceBottom.luminance() < style.surfaceBottom.luminance())
        assertTrue("active fill has substantial left-side presence", style.activeLeading.alpha >= .70f)
        assertTrue("active fill fades toward the trailing edge", style.activeLeading.alpha > style.activeMiddle.alpha)
        assertTrue(style.activeMiddle.alpha > style.activeTrailing.alpha)
        assertTrue("active outline is stronger than the standard perimeter", style.activeOutline.alpha > style.surfaceOutline.alpha)
        assertEquals(CompactGlassRole.HERO, CompactGlassRole.valueOf("HERO"))
    }
}
