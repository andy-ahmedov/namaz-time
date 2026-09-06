package ru.namaztime.tv.presentation

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
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
import ru.namaztime.tv.R
import ru.namaztime.tv.repository.BLUE_HOUR_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.AUTUMN_COURTYARD_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.CELESTIAL_NAVY_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.CUSTOM_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.DEFAULT_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.DESERT_DAWN_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.EMERALD_MOSQUE_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.NIGHT_MINARET_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.OperatorImageAssetStore
import ru.namaztime.tv.repository.OperatorImageSlot
import ru.namaztime.tv.repository.WINTER_TWILIGHT_BACKGROUND_STYLE_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val TV_ATMOSPHERIC_BACKGROUND_TAG = "tv-atmospheric-background"
const val TV_BACKGROUND_STYLE_TAG_PREFIX = "tv-background-style-"

internal enum class TvBackgroundStyle(
    val id: String,
    @param:DrawableRes val drawableRes: Int,
    @param:StringRes val labelRes: Int,
    val scrimAlpha: Float,
) {
    GOLDEN_DUSK(
        DEFAULT_BACKGROUND_STYLE_ID,
        R.drawable.tv_background_golden_dusk,
        R.string.value_background_golden_dusk,
        0.40f,
    ),
    BLUE_HOUR(
        BLUE_HOUR_BACKGROUND_STYLE_ID,
        R.drawable.tv_background_blue_hour,
        R.string.value_background_blue_hour,
        0.38f,
    ),
    NIGHT_MINARET(
        NIGHT_MINARET_BACKGROUND_STYLE_ID,
        R.drawable.tv_background_night_minaret,
        R.string.value_background_night_minaret,
        0.34f,
    ),
    DESERT_DAWN(
        DESERT_DAWN_BACKGROUND_STYLE_ID,
        R.drawable.tv_background_desert_dawn,
        R.string.value_background_desert_dawn,
        0.40f,
    ),
    EMERALD_MOSQUE(
        EMERALD_MOSQUE_BACKGROUND_STYLE_ID,
        R.drawable.tv_background_emerald_mosque,
        R.string.value_background_emerald_mosque,
        0.38f,
    ),
    WINTER_TWILIGHT(
        WINTER_TWILIGHT_BACKGROUND_STYLE_ID,
        R.drawable.tv_background_winter_twilight,
        R.string.value_background_winter_twilight,
        0.42f,
    ),
    AUTUMN_COURTYARD(
        AUTUMN_COURTYARD_BACKGROUND_STYLE_ID,
        R.drawable.tv_background_autumn_courtyard,
        R.string.value_background_autumn_courtyard,
        0.40f,
    ),
    CELESTIAL_NAVY(
        CELESTIAL_NAVY_BACKGROUND_STYLE_ID,
        R.drawable.tv_background_celestial_navy,
        R.string.value_background_celestial_navy,
        0.34f,
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
    surfaceTop = Color(0xE8232D3B),
    surfaceBottom = Color(0xED151E2C),
    surfaceStrong = Color(0xF0141D2A),
    surfaceOutline = Color(0x668F99A6),
    textPrimary = Color(0xFFE9EAE7),
    textSecondary = Color(0xFFAEB6BF),
    accent = Color(0xFFD6B172),
    accentSoft = Color(0xFF4A3826),
    accentOutline = Color(0xCCC6A76F),
    focus = Color(0xFFD6B172),
    separator = Color(0x1FBCC3CA),
    warning = Color(0xFFD9A767),
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
        fontWeight = FontWeight.Light,
        fontSize = 58.sp,
        lineHeight = 64.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 46.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
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
        fontWeight = FontWeight.Medium,
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
    customAssetVersion: Long = 0L,
    compact: Boolean = false,
) {
    val style = TvBackgroundStyle.fromId(styleId)
    val colors = NamazTvTheme.colors
    val customImage = rememberOperatorImageBitmap(
        slot = OperatorImageSlot.BACKGROUND,
        assetVersion = customAssetVersion,
        enabled = styleId == CUSTOM_BACKGROUND_STYLE_ID,
    )
    val customAvailable = styleId == CUSTOM_BACKGROUND_STYLE_ID && customImage != null
    val renderedStyleId = if (customAvailable) CUSTOM_BACKGROUND_STYLE_ID else style.id
    val painter = customImage?.let(::BitmapPainter) ?: painterResource(style.drawableRes)
    Box(modifier = modifier.fillMaxSize().testTag(TV_ATMOSPHERIC_BACKGROUND_TAG)) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().testTag("$TV_BACKGROUND_STYLE_TAG_PREFIX$renderedStyleId"),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    colors.backgroundBottom.copy(
                        alpha = if (compact) CompactVisualStyle.globalScrimAlpha else if (customAvailable) 0.44f else style.scrimAlpha,
                    ),
                ),
        )
        if (compact) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                0f to Color.Transparent,
                .35f to Color.Transparent,
                .50f to CompactVisualStyle.cinematicScrim.copy(alpha = CompactVisualStyle.railScrimAlpha),
                1f to CompactVisualStyle.cinematicScrim.copy(alpha = CompactVisualStyle.railScrimAlpha),
            )))
        }
    }
}

@Composable
internal fun rememberOperatorImageBitmap(
    slot: OperatorImageSlot,
    assetVersion: Long = 0L,
    enabled: Boolean = true,
): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    var image by remember(slot, assetVersion, enabled) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(slot, assetVersion, enabled) {
        image = if (enabled) {
            withContext(Dispatchers.IO) {
                OperatorImageAssetStore(context.filesDir)
                    .resolve(slot)
                    ?.let { BitmapFactory.decodeFile(it.path) }
                    ?.asImageBitmap()
            }
        } else {
            null
        }
    }
    return image
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
                width = if (accented) 0.75.dp else 0.65.dp,
                color = colors.surfaceOutline,
                shape = shape,
            ),
        content = content,
    )
}

internal enum class OrnamentDiamondPosition {
    START,
    CENTER,
    END,
}

@Composable
internal fun TvFadingDiamondDivider(
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accentOutline,
    diamondPosition: OrnamentDiamondPosition = OrnamentDiamondPosition.CENTER,
    filledDiamond: Boolean = false,
) {
    Canvas(modifier) {
        val centerY = size.height / 2f
        val diamondRadius = size.height * 0.30f
        val centerX = when (diamondPosition) {
            OrnamentDiamondPosition.START -> diamondRadius + 1.dp.toPx()
            OrnamentDiamondPosition.CENTER -> size.width / 2f
            OrnamentDiamondPosition.END -> size.width - diamondRadius - 1.dp.toPx()
        }
        val gap = diamondRadius * 1.8f
        val colorStops = when (diamondPosition) {
            OrnamentDiamondPosition.START -> arrayOf(
                0f to tint.copy(alpha = 0.68f),
                0.46f to tint.copy(alpha = 0.50f),
                1f to Color.Transparent,
            )
            OrnamentDiamondPosition.CENTER -> arrayOf(
                0f to Color.Transparent,
                0.18f to tint.copy(alpha = 0.48f),
                0.50f to tint.copy(alpha = 0.68f),
                0.82f to tint.copy(alpha = 0.48f),
                1f to Color.Transparent,
            )
            OrnamentDiamondPosition.END -> arrayOf(
                0f to Color.Transparent,
                0.54f to tint.copy(alpha = 0.50f),
                1f to tint.copy(alpha = 0.68f),
            )
        }
        val lineBrush = Brush.horizontalGradient(colorStops = colorStops)
        val glowBrush = Brush.horizontalGradient(
            colorStops = colorStops.map { (stop, color) ->
                stop to color.copy(alpha = color.alpha * 0.20f)
            }.toTypedArray(),
        )
        fun line(startX: Float, endX: Float) {
            if (endX <= startX) return
            drawLine(
                brush = glowBrush,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                brush = lineBrush,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = 0.65.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        line(0f, (centerX - gap).coerceAtLeast(0f))
        line((centerX + gap).coerceAtMost(size.width), size.width)

        val diamond = Path().apply {
            moveTo(centerX, centerY - diamondRadius)
            lineTo(centerX + diamondRadius, centerY)
            lineTo(centerX, centerY + diamondRadius)
            lineTo(centerX - diamondRadius, centerY)
            close()
        }
        if (filledDiamond) drawPath(diamond, tint)
        drawPath(
            diamond,
            tint.copy(alpha = 0.12f),
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            diamond,
            tint.copy(alpha = 0.78f),
            style = Stroke(0.7.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
internal fun TvFadingHairline(
    modifier: Modifier = Modifier,
    color: Color = NamazTvTheme.colors.separator,
) {
    Canvas(modifier) {
        val centerY = size.height / 2f
        drawLine(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.14f to color,
                0.86f to color,
                1f to Color.Transparent,
            ),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 0.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun TvIslamicGeometricPattern(
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accentOutline,
    intensity: Float = 0.10f,
) {
    Canvas(modifier) {
        val cell = (size.height * 0.62f).coerceAtLeast(18.dp.toPx())
        val columns = (size.width / cell).toInt() + 2
        val rows = (size.height / (cell * 0.72f)).toInt() + 2
        val stroke = Stroke(0.48.dp.toPx(), cap = StrokeCap.Round)
        repeat(columns) { column ->
            repeat(rows) { row ->
                val centerX = (column - 0.35f) * cell + if (row % 2 == 0) 0f else cell / 2f
                val centerY = row * cell * 0.72f
                val radius = cell * 0.42f
                val edgeFade = (1f - centerY / size.height).coerceIn(0.28f, 1f)
                val alpha = intensity * edgeFade
                val star = Path()
                repeat(16) { point ->
                    val angle = Math.PI * point / 8.0 - Math.PI / 2.0
                    val pointRadius = radius * if (point % 2 == 0) 1f else 0.42f
                    val x = centerX + kotlin.math.cos(angle).toFloat() * pointRadius
                    val y = centerY + kotlin.math.sin(angle).toFloat() * pointRadius
                    if (point == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                val lattice = Path().apply {
                    moveTo(centerX, centerY - radius * 1.04f)
                    lineTo(centerX + radius * 1.04f, centerY)
                    lineTo(centerX, centerY + radius * 1.04f)
                    lineTo(centerX - radius * 1.04f, centerY)
                    close()
                }
                drawPath(
                    lattice,
                    tint.copy(alpha = alpha * 0.54f),
                    style = stroke,
                )
                drawPath(star, tint.copy(alpha = alpha), style = stroke)
                drawCircle(
                    tint.copy(alpha = alpha * 0.72f),
                    radius * 0.72f,
                    Offset(centerX, centerY),
                    style = stroke,
                )
                repeat(8) { point ->
                    val angle = Math.PI * point / 4.0
                    drawLine(
                        tint.copy(alpha = alpha * 0.62f),
                        Offset(
                            centerX + kotlin.math.cos(angle).toFloat() * radius * 0.28f,
                            centerY + kotlin.math.sin(angle).toFloat() * radius * 0.28f,
                        ),
                        Offset(
                            centerX + kotlin.math.cos(angle).toFloat() * radius * 0.72f,
                            centerY + kotlin.math.sin(angle).toFloat() * radius * 0.72f,
                        ),
                        stroke.width,
                        StrokeCap.Round,
                    )
                }
            }
        }
    }
}
