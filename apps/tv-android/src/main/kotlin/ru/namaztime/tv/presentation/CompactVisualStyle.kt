package ru.namaztime.tv.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Explicit opt-in: never replaces the shared STANDARD, Settings or Donation palette. */
internal object CompactVisualStyle {
    val surfaceTop = Color(0xB535414F)
    val surfaceBottom = Color(0xD1182431)
    val heroSurfaceTop = Color(0xC0414B58)
    val heroSurfaceBottom = Color(0xD9232D39)
    val campaignSurfaceTop = Color(0xAD303A47)
    val campaignSurfaceBottom = Color(0xD6131E2B)
    val surfaceStrong = Color(0xA12D3947)
    val surfaceOutline = Color(0x60D8DCE0)
    val topHighlight = Color(0xA0FFF0D2)
    val textPrimary = Color(0xFFF7F7F4)
    val textSecondary = Color(0xFFD0D4DA)
    val accent = Color(0xFFF7CC6D)
    val accentOutline = Color(0xDDEBC67A)
    val separator = Color(0x30D9DDE1)
    val activeGlow = Color(0x45FFD17B)
    val activeLeading = Color(0xB86E4A22)
    val activeMiddle = Color(0x704F3A24)
    val activeTrailing = Color(0x12604A31)
    val activeOutline = Color(0xE6E8B966)
    val glow = Color(0x0DFFE0A0)
    val warningSurface = Color(0xB3362024)
    val warningOutline = Color(0xD9D89288)
    val cinematicScrim = Color(0xFF07111F)
    const val globalScrimAlpha = .12f
    const val railScrimAlpha = .54f
}

internal enum class CompactGlassRole {
    STANDARD,
    HERO,
    DEEP,
}

/** Translucent layers and an inset top reflection; no blur or off-screen shadow raster. */
@Composable
internal fun CompactGlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp,
    role: CompactGlassRole = CompactGlassRole.STANDARD,
    accented: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = CompactVisualStyle
    val shape = RoundedCornerShape(radius)
    val (surfaceTop, surfaceBottom) = when (role) {
        CompactGlassRole.STANDARD -> style.surfaceTop to style.surfaceBottom
        CompactGlassRole.HERO -> style.heroSurfaceTop to style.heroSurfaceBottom
        CompactGlassRole.DEEP -> style.campaignSurfaceTop to style.campaignSurfaceBottom
    }
    Box(modifier.clip(shape)
        .background(Brush.verticalGradient(listOf(surfaceTop, surfaceBottom)))
        .border(.45.dp, if (accented) style.accentOutline else style.surfaceOutline, shape)
        .drawWithContent {
            drawRect(Brush.verticalGradient(listOf(style.glow, Color.Transparent), endY = size.height * .4f))
            drawContent()
            val inset = radius.toPx() * .78f
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, style.topHighlight, Color.Transparent)),
                Offset(inset, 1.15.dp.toPx()), Offset(size.width - inset, 1.15.dp.toPx()), .65.dp.toPx())
        }, content = content)
}
