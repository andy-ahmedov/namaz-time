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
    val surfaceTop = Color(0xB83B4554)
    val surfaceBottom = Color(0xD11B2533)
    val surfaceStrong = Color(0x99313C4C)
    val surfaceOutline = Color(0xA6D8D6CF)
    val topHighlight = Color(0x66FFF4DF)
    val textPrimary = Color(0xFFF7F7F4)
    val textSecondary = Color(0xFFD0D4DA)
    val accent = Color(0xFFF4CE86)
    val accentOutline = Color(0xD9E8C98E)
    val separator = Color(0x38D9DDE1)
    val activeLeading = Color(0x809C7941)
    val activeTrailing = Color(0x267B6343)
    val activeOutline = Color(0xB8EAC687)
    val glow = Color(0x09FFE2AA)
    val cinematicScrim = Color(0xFF07111F)
    const val globalScrimAlpha = .12f
    const val railScrimAlpha = .54f
}

/** Translucent layers and an inset top reflection; no blur or off-screen shadow raster. */
@Composable
internal fun CompactGlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp,
    accented: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = CompactVisualStyle
    val shape = RoundedCornerShape(radius)
    Box(modifier.clip(shape)
        .background(Brush.verticalGradient(listOf(style.surfaceTop, style.surfaceBottom)))
        .border(.7.dp, if (accented) style.accentOutline else style.surfaceOutline, shape)
        .drawWithContent {
            drawRect(Brush.verticalGradient(listOf(style.glow, Color.Transparent), endY = size.height * .4f))
            drawContent()
            val inset = radius.toPx()
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, style.topHighlight, Color.Transparent)),
                Offset(inset, 1.dp.toPx()), Offset(size.width - inset, 1.dp.toPx()), .5.dp.toPx())
        }, content = content)
}
