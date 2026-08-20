package com.example.namaztime.tv.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.darkColorScheme

const val TV_ATMOSPHERIC_BACKGROUND_TAG = "tv-atmospheric-background"

@Immutable
internal data class TvSafeFrameInsets(
    val horizontal: Dp,
    val vertical: Dp,
) {
    companion object {
        fun forSize(width: Dp, height: Dp): TvSafeFrameInsets = TvSafeFrameInsets(
            horizontal = if (width >= 1_200.dp) 64.dp else 48.dp,
            vertical = if (height >= 700.dp) 32.dp else 24.dp,
        )
    }
}

@Immutable
internal data class TvColorTokens(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val backgroundGlow: Color,
    val surfaceTop: Color,
    val surfaceBottom: Color,
    val surfaceStrong: Color,
    val surfaceOutline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentOutline: Color,
    val focus: Color,
    val separator: Color,
    val warning: Color,
)

internal val DarkTvColors = TvColorTokens(
    backgroundTop = Color(0xFF111B2B),
    backgroundBottom = Color(0xFF071116),
    backgroundGlow = Color(0xFF9B6340),
    surfaceTop = Color(0xE63A4050),
    surfaceBottom = Color(0xF0252935),
    surfaceStrong = Color(0xF51B202B),
    surfaceOutline = Color(0x4DFFFFFF),
    textPrimary = Color(0xFFF8F6F1),
    textSecondary = Color(0xFFD4D4D1),
    accent = Color(0xFFFFC978),
    accentSoft = Color(0x4DFFB85C),
    accentOutline = Color(0xB3FFC978),
    focus = Color(0xFFFFFFFF),
    separator = Color(0x24FFFFFF),
    warning = Color(0xFFFFD38B),
)

internal val DarkTvMaterialColorScheme = darkColorScheme(
    primary = DarkTvColors.accent,
    onPrimary = DarkTvColors.backgroundBottom,
    primaryContainer = Color(0xFF4A3926),
    onPrimaryContainer = DarkTvColors.textPrimary,
    inversePrimary = DarkTvColors.backgroundGlow,
    secondary = DarkTvColors.textSecondary,
    onSecondary = DarkTvColors.backgroundBottom,
    secondaryContainer = Color(0xFF343A47),
    onSecondaryContainer = DarkTvColors.textPrimary,
    tertiary = DarkTvColors.warning,
    onTertiary = DarkTvColors.backgroundBottom,
    tertiaryContainer = Color(0xFF493A25),
    onTertiaryContainer = DarkTvColors.textPrimary,
    background = DarkTvColors.backgroundBottom,
    onBackground = DarkTvColors.textPrimary,
    surface = Color(0xFF1B202B),
    onSurface = DarkTvColors.textPrimary,
    surfaceVariant = Color(0xFF2F3542),
    onSurfaceVariant = DarkTvColors.textSecondary,
    surfaceTint = DarkTvColors.accent,
    inverseSurface = DarkTvColors.textPrimary,
    inverseOnSurface = DarkTvColors.backgroundBottom,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    border = Color(0xFF8E929C),
    borderVariant = Color(0xFF454A57),
    scrim = Color.Black,
)

internal val TvMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LocalTvColors = staticCompositionLocalOf { DarkTvColors }

internal object NamazTvTheme {
    val colors: TvColorTokens
        @Composable get() = LocalTvColors.current
}

@Composable
internal fun NamazTvTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTvColors provides DarkTvColors) {
        MaterialTheme(
            colorScheme = DarkTvMaterialColorScheme,
            shapes = TvMaterialShapes,
            content = content,
        )
    }
}

@Composable
internal fun TvSafeFrame(
    testTag: String,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    contentOffset: DpOffset = DpOffset.Zero,
    contentShiftBudget: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val insets = TvSafeFrameInsets.forSize(maxWidth, maxHeight)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = insets.horizontal, vertical = insets.vertical)
                .padding(contentShiftBudget)
                .offset(contentOffset.x, contentOffset.y)
                .testTag(testTag),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

@Composable
internal fun TvAtmosphericBackground(modifier: Modifier = Modifier) {
    val colors = NamazTvTheme.colors
    Canvas(modifier = modifier.fillMaxSize().testTag(TV_ATMOSPHERIC_BACKGROUND_TAG)) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(colors.backgroundTop, colors.backgroundBottom),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.backgroundGlow.copy(alpha = 0.44f),
                    colors.backgroundGlow.copy(alpha = 0f),
                ),
                center = Offset(size.width * 0.86f, size.height * 0.58f),
                radius = size.minDimension * 0.86f,
            ),
            radius = size.minDimension * 0.86f,
            center = Offset(size.width * 0.86f, size.height * 0.58f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF31516C).copy(alpha = 0.34f), Color.Transparent),
                center = Offset(size.width * 0.14f, size.height * 0.12f),
                radius = size.minDimension * 0.72f,
            ),
            radius = size.minDimension * 0.72f,
            center = Offset(size.width * 0.14f, size.height * 0.12f),
        )
        val horizon = Path().apply {
            moveTo(0f, size.height * 0.88f)
            lineTo(size.width * 0.20f, size.height * 0.79f)
            lineTo(size.width * 0.38f, size.height * 0.86f)
            lineTo(size.width * 0.58f, size.height * 0.74f)
            lineTo(size.width * 0.78f, size.height * 0.84f)
            lineTo(size.width, size.height * 0.72f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(horizon, Color.Black.copy(alpha = 0.20f))
        drawRect(Color.Black.copy(alpha = 0.18f))
    }
}

@Composable
internal fun TvGlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    accented: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = NamazTvTheme.colors
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .shadow(
                elevation = if (accented) 16.dp else 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.34f),
                spotColor = if (accented) colors.accent.copy(alpha = 0.22f) else Color.Black,
            )
            .clip(shape)
            .background(
                brush = if (accented) {
                    Brush.linearGradient(
                        listOf(colors.accentSoft, colors.surfaceBottom),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(colors.surfaceTop, colors.surfaceBottom),
                    )
                },
            )
            .border(
                width = if (accented) 1.5.dp else 1.dp,
                color = if (accented) colors.accentOutline else colors.surfaceOutline,
                shape = shape,
            ),
        content = content,
    )
}
