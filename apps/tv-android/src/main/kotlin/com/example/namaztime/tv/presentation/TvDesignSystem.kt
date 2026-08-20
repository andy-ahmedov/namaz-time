package com.example.namaztime.tv.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.example.namaztime.tv.R
import com.example.namaztime.tv.repository.BLUE_HOUR_BACKGROUND_STYLE_ID
import com.example.namaztime.tv.repository.DEFAULT_BACKGROUND_STYLE_ID

const val TV_ATMOSPHERIC_BACKGROUND_TAG = "tv-atmospheric-background"
const val TV_BACKGROUND_STYLE_TAG_PREFIX = "tv-background-style-"

internal enum class TvBackgroundStyle(
    val id: String,
    @DrawableRes val drawableRes: Int,
    @StringRes val labelRes: Int,
    val scrimAlpha: Float,
) {
    GOLDEN_DUSK(
        DEFAULT_BACKGROUND_STYLE_ID,
        R.drawable.tv_background_golden_dusk,
        R.string.value_background_golden_dusk,
        0.18f,
    ),
    BLUE_HOUR(
        BLUE_HOUR_BACKGROUND_STYLE_ID,
        R.drawable.tv_background_blue_hour,
        R.string.value_background_blue_hour,
        0.16f,
    ),
    ;

    val next: TvBackgroundStyle
        get() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromId(id: String): TvBackgroundStyle = entries.firstOrNull { it.id == id } ?: GOLDEN_DUSK
    }
}

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
    backgroundTop = Color(0xFF14233A),
    backgroundBottom = Color(0xFF07111F),
    backgroundGlow = Color(0xFFB57936),
    surfaceTop = Color(0x8F2B3341),
    surfaceBottom = Color(0xB5161C28),
    surfaceStrong = Color(0xB0161C27),
    surfaceOutline = Color(0x59FFF4DC),
    textPrimary = Color(0xFFFFFBF2),
    textSecondary = Color(0xFFD5D9DE),
    accent = Color(0xFFFFCF7A),
    accentSoft = Color(0x4DCB8730),
    accentOutline = Color(0xCCFFD38A),
    focus = Color(0xFFFFFFFF),
    separator = Color(0x2BFFFFFF),
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

internal val TvMaterialTypography = Typography().copy(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 58.sp,
        lineHeight = 64.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
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
            typography = TvMaterialTypography,
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
internal fun TvAtmosphericBackground(
    styleId: String = DEFAULT_BACKGROUND_STYLE_ID,
    modifier: Modifier = Modifier,
) {
    val style = TvBackgroundStyle.fromId(styleId)
    Box(modifier = modifier.fillMaxSize().testTag(TV_ATMOSPHERIC_BACKGROUND_TAG)) {
        Image(
            painter = painterResource(style.drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().testTag("$TV_BACKGROUND_STYLE_TAG_PREFIX${style.id}"),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = style.scrimAlpha)))
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
            .clip(shape)
            .background(
                color = if (accented) colors.surfaceBottom else colors.surfaceTop,
                shape = shape,
            )
            .border(
                width = if (accented) 1.dp else 0.75.dp,
                color = if (accented) colors.accentOutline else colors.surfaceOutline,
                shape = shape,
            ),
        content = content,
    )
}
