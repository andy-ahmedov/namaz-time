package ru.namaztime.tv.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
        val radius = size.minDimension * 0.22f
        fun horizon(y: Float = size.height * 0.68f) {
            drawLine(tint, Offset(size.width * 0.14f, y), Offset(size.width * 0.86f, y), stroke.width)
        }
        fun rays(origin: Offset, angles: List<Float>, length: Float = size.minDimension * 0.18f) {
            angles.forEach { degrees ->
                val radians = Math.toRadians(degrees.toDouble())
                val start = Offset(
                    origin.x + kotlin.math.cos(radians).toFloat() * radius * 1.45f,
                    origin.y + kotlin.math.sin(radians).toFloat() * radius * 1.45f,
                )
                val end = Offset(
                    start.x + kotlin.math.cos(radians).toFloat() * length,
                    start.y + kotlin.math.sin(radians).toFloat() * length,
                )
                drawLine(tint, start, end, stroke.width, StrokeCap.Round)
            }
        }
        when (prayerId) {
            "fajr" -> {
                drawArc(
                    tint,
                    startAngle = 65f,
                    sweepAngle = 235f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.22f, size.height * 0.16f),
                    size = Size(size.width * 0.45f, size.height * 0.45f),
                    style = stroke,
                )
                horizon()
                rays(Offset(size.width * 0.68f, size.height * 0.68f), listOf(225f, 270f, 315f), radius * 0.6f)
            }
            "sunrise" -> {
                horizon()
                drawArc(tint, 180f, 180f, false, Offset(center.x - radius, size.height * 0.46f), Size(radius * 2, radius * 2), style = stroke)
                rays(Offset(center.x, size.height * 0.68f), listOf(205f, 235f, 270f, 305f, 335f))
            }
            "dhuhr" -> {
                drawCircle(tint, radius, center, style = stroke)
                rays(center, listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f), radius * 0.55f)
            }
            "asr" -> {
                val sun = Offset(size.width * 0.42f, size.height * 0.43f)
                drawCircle(tint, radius * 0.88f, sun, style = stroke)
                rays(sun, listOf(10f, 65f, 120f, 175f, 230f), radius * 0.45f)
                drawLine(tint, Offset(size.width * 0.23f, size.height * 0.75f), Offset(size.width * 0.78f, size.height * 0.75f), stroke.width)
                drawLine(tint, Offset(size.width * 0.65f, size.height * 0.62f), Offset(size.width * 0.78f, size.height * 0.75f), stroke.width)
            }
            "maghrib" -> {
                horizon(size.height * 0.58f)
                drawArc(tint, 0f, 180f, false, Offset(center.x - radius, size.height * 0.36f), Size(radius * 2, radius * 2), style = stroke)
                drawLine(tint, Offset(size.width * 0.30f, size.height * 0.73f), Offset(size.width * 0.70f, size.height * 0.73f), stroke.width)
                drawLine(tint, Offset(size.width * 0.38f, size.height * 0.84f), Offset(size.width * 0.62f, size.height * 0.84f), stroke.width)
            }
            else -> {
                drawArc(tint, 55f, 250f, false, Offset(size.width * 0.18f, size.height * 0.14f), Size(size.width * 0.56f, size.height * 0.56f), style = stroke)
                val star = Offset(size.width * 0.72f, size.height * 0.30f)
                drawLine(tint, Offset(star.x - radius * 0.28f, star.y), Offset(star.x + radius * 0.28f, star.y), stroke.width)
                drawLine(tint, Offset(star.x, star.y - radius * 0.28f), Offset(star.x, star.y + radius * 0.28f), stroke.width)
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
        drawArc(tint, 180f, 180f, false, Offset(size.width * 0.17f, size.height * 0.10f), Size(size.width * 0.66f, size.height * 0.72f), style = stroke)
        drawLine(tint, Offset(size.width * 0.17f, size.height * 0.46f), Offset(size.width * 0.17f, size.height * 0.86f), width)
        drawLine(tint, Offset(size.width * 0.83f, size.height * 0.46f), Offset(size.width * 0.83f, size.height * 0.86f), width)
        drawCircle(tint, size.minDimension * 0.09f, Offset(size.width * 0.40f, size.height * 0.55f), style = stroke)
        drawCircle(tint, size.minDimension * 0.09f, Offset(size.width * 0.62f, size.height * 0.55f), style = stroke)
        drawLine(tint, Offset(size.width * 0.30f, size.height * 0.82f), Offset(size.width * 0.70f, size.height * 0.82f), width)
    }
}

@Composable
internal fun BrandMark(
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accent,
) {
    Canvas(modifier) {
        val stroke = Stroke(size.minDimension * 0.08f, cap = StrokeCap.Round)
        drawArc(tint, 55f, 250f, false, Offset(size.width * 0.08f, size.height * 0.08f), Size(size.width * 0.72f, size.height * 0.72f), style = stroke)
        drawCircle(tint, size.minDimension * 0.055f, Offset(size.width * 0.76f, size.height * 0.25f))
    }
}
