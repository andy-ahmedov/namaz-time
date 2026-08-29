package ru.namaztime.tv.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag

const val PRAYER_ICON_TEST_TAG_PREFIX = "prayer-icon-"
const val IQAMAH_ICON_TEST_TAG = "iqamah-icon"
internal const val TV_ICON_STROKE_FRACTION = 0.045f

@Composable
internal fun PrayerIcon(
    prayerId: String,
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accent,
    exposeTestTag: Boolean = true,
) {
    Canvas(
        if (exposeTestTag) modifier.testTag("$PRAYER_ICON_TEST_TAG_PREFIX$prayerId") else modifier,
    ) {
        val stroke = Stroke(width = size.minDimension * TV_ICON_STROKE_FRACTION, cap = StrokeCap.Round)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.205f
        fun horizon(y: Float, startX: Float = 0.16f, endX: Float = 0.84f) {
            drawLine(
                tint,
                Offset(size.width * startX, y),
                Offset(size.width * endX, y),
                stroke.width,
                StrokeCap.Round,
            )
        }
        fun rays(
            origin: Offset,
            angles: List<Float>,
            rayStart: Float,
            rayEnd: Float,
        ) {
            angles.forEach { degrees ->
                val radians = Math.toRadians(degrees.toDouble())
                val start = Offset(
                    origin.x + kotlin.math.cos(radians).toFloat() * rayStart,
                    origin.y + kotlin.math.sin(radians).toFloat() * rayStart,
                )
                val end = Offset(
                    origin.x + kotlin.math.cos(radians).toFloat() * rayEnd,
                    origin.y + kotlin.math.sin(radians).toFloat() * rayEnd,
                )
                drawLine(tint, start, end, stroke.width, StrokeCap.Round)
            }
        }
        when (prayerId) {
            "fajr" -> {
                val sunCenter = Offset(center.x, size.height * 0.67f)
                horizon(sunCenter.y)
                drawArc(
                    tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(sunCenter.x - radius, sunCenter.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = stroke,
                )
                rays(
                    origin = sunCenter,
                    angles = listOf(205f, 235f, 270f, 305f, 335f),
                    rayStart = radius * 1.48f,
                    rayEnd = radius * 1.88f,
                )
            }
            "sunrise" -> {
                val sunCenter = Offset(center.x, size.height * 0.69f)
                horizon(sunCenter.y)
                drawArc(
                    tint,
                    180f,
                    180f,
                    false,
                    Offset(sunCenter.x - radius, sunCenter.y - radius),
                    Size(radius * 2f, radius * 2f),
                    style = stroke,
                )
                rays(
                    origin = sunCenter,
                    angles = listOf(205f, 235f, 270f, 305f, 335f),
                    rayStart = radius * 1.48f,
                    rayEnd = radius * 1.88f,
                )
            }
            "dhuhr" -> {
                drawCircle(tint, radius, center, style = stroke)
                rays(
                    origin = center,
                    angles = (0 until 12).map { it * 30f },
                    rayStart = radius * 1.48f,
                    rayEnd = radius * 1.88f,
                )
            }
            "asr" -> {
                drawCircle(tint, radius, center, style = stroke)
                rays(
                    origin = center,
                    angles = (0 until 8).map { it * 45f },
                    rayStart = radius * 1.48f,
                    rayEnd = radius * 1.88f,
                )
            }
            "maghrib" -> {
                val sunCenter = Offset(center.x, size.height * 0.57f)
                horizon(sunCenter.y)
                drawArc(
                    tint,
                    180f,
                    180f,
                    false,
                    Offset(sunCenter.x - radius, sunCenter.y - radius),
                    Size(radius * 2f, radius * 2f),
                    style = stroke,
                )
                rays(
                    origin = sunCenter,
                    angles = listOf(205f, 270f, 335f),
                    rayStart = radius * 1.48f,
                    rayEnd = radius * 1.88f,
                )
                horizon(size.height * 0.77f, 0.28f, 0.72f)
            }
            else -> {
                drawCrescentStars(tint = tint, strokeWidth = stroke.width)
            }
        }
    }
}

@Composable
internal fun IqamahIcon(
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accent,
    exposeTestTag: Boolean = false,
) {
    Canvas(if (exposeTestTag) modifier.testTag(IQAMAH_ICON_TEST_TAG) else modifier) {
        val width = size.minDimension * TV_ICON_STROKE_FRACTION
        val stroke = Stroke(width, cap = StrokeCap.Round)
        val headRadius = size.minDimension * 0.105f
        drawCircle(tint, headRadius, Offset(size.width * 0.50f, size.height * 0.29f), style = stroke)
        drawCircle(tint, headRadius * 0.82f, Offset(size.width * 0.29f, size.height * 0.34f), style = stroke)
        drawCircle(tint, headRadius * 0.82f, Offset(size.width * 0.71f, size.height * 0.34f), style = stroke)
        drawArc(
            tint,
            190f,
            160f,
            false,
            Offset(size.width * 0.20f, size.height * 0.43f),
            Size(size.width * 0.60f, size.height * 0.43f),
            style = stroke,
        )
        drawArc(
            tint,
            186f,
            168f,
            false,
            Offset(size.width * 0.08f, size.height * 0.49f),
            Size(size.width * 0.42f, size.height * 0.34f),
            style = stroke,
        )
        drawArc(
            tint,
            186f,
            168f,
            false,
            Offset(size.width * 0.50f, size.height * 0.49f),
            Size(size.width * 0.42f, size.height * 0.34f),
            style = stroke,
        )
    }
}

@Composable
internal fun BrandMark(
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accent,
) {
    Canvas(modifier) {
        drawCrescentStars(tint = tint, strokeWidth = size.minDimension * 0.06f)
    }
}

internal fun DrawScope.drawCrescentStars(
    tint: Color,
    strokeWidth: Float,
) {
    val crescent = Path().apply {
        moveTo(size.width * 0.53f, size.height * 0.14f)
        cubicTo(
            size.width * 0.19f,
            size.height * 0.23f,
            size.width * 0.18f,
            size.height * 0.76f,
            size.width * 0.54f,
            size.height * 0.87f,
        )
        cubicTo(
            size.width * 0.72f,
            size.height * 0.92f,
            size.width * 0.84f,
            size.height * 0.79f,
            size.width * 0.88f,
            size.height * 0.65f,
        )
        cubicTo(
            size.width * 0.67f,
            size.height * 0.80f,
            size.width * 0.42f,
            size.height * 0.68f,
            size.width * 0.39f,
            size.height * 0.44f,
        )
        cubicTo(
            size.width * 0.37f,
            size.height * 0.29f,
            size.width * 0.43f,
            size.height * 0.20f,
            size.width * 0.53f,
            size.height * 0.14f,
        )
    }
    drawPath(crescent, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
    drawFourPointStar(
        center = Offset(size.width * 0.71f, size.height * 0.30f),
        radius = size.minDimension * 0.095f,
        tint = tint,
    )
    drawFourPointStar(
        center = Offset(size.width * 0.82f, size.height * 0.47f),
        radius = size.minDimension * 0.055f,
        tint = tint,
    )
}

private fun DrawScope.drawFourPointStar(
    center: Offset,
    radius: Float,
    tint: Color,
) {
    val star = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * 0.24f, center.y - radius * 0.24f)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + radius * 0.24f, center.y + radius * 0.24f)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius * 0.24f, center.y + radius * 0.24f)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - radius * 0.24f, center.y - radius * 0.24f)
        close()
    }
    drawPath(star, tint)
}
