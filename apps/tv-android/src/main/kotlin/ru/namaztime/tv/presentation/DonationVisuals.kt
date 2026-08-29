package ru.namaztime.tv.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale

internal enum class DonationDetailIcon {
    RECIPIENT,
    BANK,
    CARD,
    PHONE,
    LINK,
}

@Composable
internal fun DonationBrandMark(
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accent,
) {
    Canvas(modifier) {
        val stroke = Stroke(size.minDimension * 0.055f, cap = StrokeCap.Round)
        drawArc(
            color = tint,
            startAngle = 58f,
            sweepAngle = 245f,
            useCenter = false,
            topLeft = Offset(size.width * 0.07f, size.height * 0.07f),
            size = Size(size.width * 0.72f, size.height * 0.72f),
            style = stroke,
        )
        drawArc(
            color = tint.copy(alpha = 0.42f),
            startAngle = 58f,
            sweepAngle = 245f,
            useCenter = false,
            topLeft = Offset(size.width * 0.10f, size.height * 0.10f),
            size = Size(size.width * 0.66f, size.height * 0.66f),
            style = Stroke(stroke.width * 0.45f, cap = StrokeCap.Round),
        )
        drawFourPointStar(
            center = Offset(size.width * 0.78f, size.height * 0.24f),
            radius = size.minDimension * 0.085f,
            color = tint,
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.025f,
            center = Offset(size.width * 0.89f, size.height * 0.12f),
        )
    }
}

@Composable
internal fun DonationSettingsGlyph(
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.textPrimary,
) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val strokeWidth = size.minDimension * 0.065f
        val stroke = Stroke(strokeWidth, cap = StrokeCap.Round)
        val rootRadius = size.minDimension * 0.31f
        val toothInner = rootRadius * 1.03f
        val toothOuter = rootRadius * 1.34f
        val gear = Path()
        repeat(24) { point ->
            val angle = Math.toRadians(point * 15.0 - 90.0)
            val radius = when (point % 3) {
                0 -> toothOuter
                1 -> toothOuter
                else -> toothInner
            }
            val x = center.x + kotlin.math.cos(angle).toFloat() * radius
            val y = center.y + kotlin.math.sin(angle).toFloat() * radius
            if (point == 0) gear.moveTo(x, y) else gear.lineTo(x, y)
        }
        gear.close()
        drawPath(gear, tint, style = stroke)
        drawCircle(tint, rootRadius * 0.53f, center, style = stroke)
    }
}

@Composable
internal fun DonationDetailGlyph(
    icon: DonationDetailIcon,
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accent,
) {
    Canvas(modifier) {
        val width = size.minDimension * 0.055f
        val stroke = Stroke(width, cap = StrokeCap.Round)
        when (icon) {
            DonationDetailIcon.RECIPIENT -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.18f,
                    center = Offset(size.width * 0.50f, size.height * 0.29f),
                    style = stroke,
                )
                val shoulders = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.83f)
                    cubicTo(
                        size.width * 0.20f,
                        size.height * 0.61f,
                        size.width * 0.33f,
                        size.height * 0.53f,
                        size.width * 0.50f,
                        size.height * 0.53f,
                    )
                    cubicTo(
                        size.width * 0.67f,
                        size.height * 0.53f,
                        size.width * 0.80f,
                        size.height * 0.61f,
                        size.width * 0.82f,
                        size.height * 0.83f,
                    )
                }
                drawPath(shoulders, tint, style = stroke)
                drawLine(
                    tint,
                    Offset(size.width * 0.14f, size.height * 0.86f),
                    Offset(size.width * 0.86f, size.height * 0.86f),
                    width,
                    StrokeCap.Round,
                )
            }
            DonationDetailIcon.BANK -> {
                val roof = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.35f)
                    lineTo(size.width * 0.50f, size.height * 0.10f)
                    lineTo(size.width * 0.88f, size.height * 0.35f)
                    close()
                }
                drawPath(roof, tint, style = stroke)
                drawLine(tint, Offset(size.width * 0.11f, size.height * 0.40f), Offset(size.width * 0.89f, size.height * 0.40f), width, StrokeCap.Round)
                listOf(0.26f, 0.42f, 0.58f, 0.74f).forEach { x ->
                    drawLine(tint, Offset(size.width * x, size.height * 0.46f), Offset(size.width * x, size.height * 0.76f), width, StrokeCap.Round)
                }
                drawLine(tint, Offset(size.width * 0.14f, size.height * 0.82f), Offset(size.width * 0.86f, size.height * 0.82f), width, StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.09f, size.height * 0.89f), Offset(size.width * 0.91f, size.height * 0.89f), width, StrokeCap.Round)
            }
            DonationDetailIcon.CARD -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.10f, size.height * 0.20f),
                    size = Size(size.width * 0.80f, size.height * 0.60f),
                    cornerRadius = CornerRadius(size.minDimension * 0.08f),
                    style = stroke,
                )
                drawLine(tint, Offset(size.width * 0.12f, size.height * 0.39f), Offset(size.width * 0.88f, size.height * 0.39f), width, StrokeCap.Butt)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.62f, size.height * 0.57f),
                    size = Size(size.width * 0.14f, size.height * 0.08f),
                    cornerRadius = CornerRadius(size.minDimension * 0.02f),
                    style = stroke,
                )
            }
            DonationDetailIcon.PHONE -> {
                val handset = Path().apply {
                    moveTo(size.width * 0.27f, size.height * 0.16f)
                    cubicTo(size.width * 0.20f, size.height * 0.23f, size.width * 0.19f, size.height * 0.32f, size.width * 0.24f, size.height * 0.45f)
                    cubicTo(size.width * 0.35f, size.height * 0.68f, size.width * 0.54f, size.height * 0.83f, size.width * 0.72f, size.height * 0.86f)
                    cubicTo(size.width * 0.82f, size.height * 0.88f, size.width * 0.89f, size.height * 0.80f, size.width * 0.86f, size.height * 0.72f)
                    lineTo(size.width * 0.76f, size.height * 0.57f)
                    cubicTo(size.width * 0.73f, size.height * 0.52f, size.width * 0.67f, size.height * 0.51f, size.width * 0.62f, size.height * 0.55f)
                    lineTo(size.width * 0.54f, size.height * 0.62f)
                    cubicTo(size.width * 0.45f, size.height * 0.57f, size.width * 0.37f, size.height * 0.49f, size.width * 0.32f, size.height * 0.40f)
                    lineTo(size.width * 0.40f, size.height * 0.31f)
                    cubicTo(size.width * 0.44f, size.height * 0.26f, size.width * 0.43f, size.height * 0.21f, size.width * 0.38f, size.height * 0.17f)
                    lineTo(size.width * 0.32f, size.height * 0.13f)
                    cubicTo(size.width * 0.30f, size.height * 0.12f, size.width * 0.28f, size.height * 0.14f, size.width * 0.27f, size.height * 0.16f)
                }
                drawPath(handset, tint, style = stroke)
            }
            DonationDetailIcon.LINK -> {
                rotate(-42f, pivot = Offset(size.width / 2f, size.height / 2f)) {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(size.width * 0.08f, size.height * 0.35f),
                        size = Size(size.width * 0.48f, size.height * 0.30f),
                        cornerRadius = CornerRadius(size.height * 0.15f),
                        style = stroke,
                    )
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(size.width * 0.44f, size.height * 0.35f),
                        size = Size(size.width * 0.48f, size.height * 0.30f),
                        cornerRadius = CornerRadius(size.height * 0.15f),
                        style = stroke,
                    )
                    drawLine(
                        color = tint,
                        start = Offset(size.width * 0.39f, size.height * 0.50f),
                        end = Offset(size.width * 0.61f, size.height * 0.50f),
                        strokeWidth = width,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DonationHeadingFlourish(
    reverse: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accent,
) {
    Canvas(modifier) {
        val starX = if (reverse) size.width * 0.28f else size.width * 0.72f
        val lineStart = if (reverse) size.width else 0f
        val lineEnd = if (reverse) starX + size.height * 0.55f else starX - size.height * 0.55f
        val brush = Brush.horizontalGradient(
            colorStops = if (reverse) {
                arrayOf(0f to Color.Transparent, 0.56f to tint.copy(alpha = 0.58f), 1f to tint.copy(alpha = 0.12f))
            } else {
                arrayOf(0f to tint.copy(alpha = 0.12f), 0.44f to tint.copy(alpha = 0.58f), 1f to Color.Transparent)
            },
        )
        drawLine(
            brush = brush,
            start = Offset(lineStart, size.height / 2f),
            end = Offset(lineEnd, size.height / 2f),
            strokeWidth = size.height * 0.045f,
            cap = StrokeCap.Round,
        )
        drawFourPointStar(
            center = Offset(starX, size.height / 2f),
            radius = size.height * 0.34f,
            color = tint,
            strokeWidth = size.height * 0.045f,
        )
    }
}

@Composable
internal fun DonationFooterOrnament(
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accent,
) {
    Canvas(modifier) {
        val axisY = size.height / 2f
        drawLine(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.36f to tint.copy(alpha = 0.44f),
                0.5f to tint.copy(alpha = 0.78f),
                0.64f to tint.copy(alpha = 0.44f),
                1f to Color.Transparent,
            ),
            start = Offset(0f, axisY),
            end = Offset(size.width, axisY),
            strokeWidth = size.height * 0.045f,
            cap = StrokeCap.Round,
        )
        drawFourPointStar(
            center = Offset(size.width / 2f, axisY),
            radius = size.height * 0.42f,
            color = tint,
            strokeWidth = size.height * 0.055f,
        )
    }
}

@Composable
internal fun DonationArchLanternGlyph(
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    tint: Color = NamazTvTheme.colors.accent,
) {
    Canvas(modifier) {
        if (mirrored) {
            scale(scaleX = -1f, scaleY = 1f, pivot = center) {
                drawDonationArchLantern(tint)
            }
        } else {
            drawDonationArchLantern(tint)
        }
    }
}

private fun DrawScope.drawDonationArchLantern(tint: Color) {
    val strokeWidth = size.minDimension * 0.022f
    val stroke = Stroke(strokeWidth, cap = StrokeCap.Round)
    val arch = Path().apply {
        moveTo(size.width * 0.12f, size.height * 0.92f)
        lineTo(size.width * 0.12f, size.height * 0.48f)
        cubicTo(
            size.width * 0.12f,
            size.height * 0.35f,
            size.width * 0.34f,
            size.height * 0.33f,
            size.width * 0.50f,
            size.height * 0.10f,
        )
        cubicTo(
            size.width * 0.66f,
            size.height * 0.33f,
            size.width * 0.88f,
            size.height * 0.35f,
            size.width * 0.88f,
            size.height * 0.48f,
        )
        lineTo(size.width * 0.88f, size.height * 0.92f)
    }
    drawPath(
        path = arch,
        color = tint.copy(alpha = 0.18f),
        style = Stroke(strokeWidth * 3.6f, cap = StrokeCap.Round),
    )
    drawPath(path = arch, color = tint.copy(alpha = 0.9f), style = stroke)

    drawLine(
        color = tint.copy(alpha = 0.9f),
        start = Offset(size.width * 0.50f, size.height * 0.12f),
        end = Offset(size.width * 0.50f, size.height * 0.42f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = tint.copy(alpha = 0.9f),
        radius = strokeWidth * 1.1f,
        center = Offset(size.width * 0.50f, size.height * 0.37f),
    )
    val lantern = Path().apply {
        moveTo(size.width * 0.50f, size.height * 0.40f)
        lineTo(size.width * 0.38f, size.height * 0.50f)
        lineTo(size.width * 0.34f, size.height * 0.76f)
        lineTo(size.width * 0.42f, size.height * 0.84f)
        lineTo(size.width * 0.50f, size.height * 0.89f)
        lineTo(size.width * 0.58f, size.height * 0.84f)
        lineTo(size.width * 0.66f, size.height * 0.76f)
        lineTo(size.width * 0.62f, size.height * 0.50f)
        close()
    }
    drawPath(
        path = lantern,
        color = tint.copy(alpha = 0.14f),
    )
    drawPath(path = lantern, color = tint.copy(alpha = 0.95f), style = stroke)
    listOf(0.43f, 0.57f).forEach { paneX ->
        drawLine(
            color = tint.copy(alpha = 0.72f),
            start = Offset(size.width * paneX, size.height * 0.53f),
            end = Offset(size.width * paneX, size.height * 0.77f),
            strokeWidth = strokeWidth * 0.72f,
            cap = StrokeCap.Round,
        )
    }
    drawLine(
        color = tint.copy(alpha = 0.88f),
        start = Offset(size.width * 0.37f, size.height * 0.58f),
        end = Offset(size.width * 0.63f, size.height * 0.58f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.28f to tint.copy(alpha = 0.56f),
            1f to tint.copy(alpha = 0.08f),
        ),
        start = Offset(size.width * 0.02f, size.height * 0.94f),
        end = Offset(size.width * 0.46f, size.height * 0.94f),
        strokeWidth = strokeWidth * 0.75f,
        cap = StrokeCap.Round,
    )
}

@Composable
internal fun DonationMosqueGlyph(
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accent,
) {
    Canvas(modifier) {
        val width = size.minDimension * 0.045f
        val stroke = Stroke(width, cap = StrokeCap.Round)
        val dome = Path().apply {
            moveTo(size.width * 0.23f, size.height * 0.48f)
            cubicTo(size.width * 0.25f, size.height * 0.27f, size.width * 0.39f, size.height * 0.20f, size.width * 0.50f, size.height * 0.20f)
            cubicTo(size.width * 0.61f, size.height * 0.20f, size.width * 0.75f, size.height * 0.27f, size.width * 0.77f, size.height * 0.48f)
        }
        drawPath(dome, tint, style = stroke)
        drawLine(tint, Offset(size.width * 0.19f, size.height * 0.50f), Offset(size.width * 0.81f, size.height * 0.50f), width, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.23f, size.height * 0.52f), Offset(size.width * 0.23f, size.height * 0.88f), width, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.77f, size.height * 0.52f), Offset(size.width * 0.77f, size.height * 0.88f), width, StrokeCap.Round)
        listOf(0.33f, 0.50f, 0.67f).forEach { x ->
            drawArc(
                color = tint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(size.width * (x - 0.075f), size.height * 0.61f),
                size = Size(size.width * 0.15f, size.height * 0.22f),
                style = stroke,
            )
            drawLine(tint, Offset(size.width * (x - 0.075f), size.height * 0.72f), Offset(size.width * (x - 0.075f), size.height * 0.88f), width)
            drawLine(tint, Offset(size.width * (x + 0.075f), size.height * 0.72f), Offset(size.width * (x + 0.075f), size.height * 0.88f), width)
        }
        drawLine(tint, Offset(size.width * 0.13f, size.height * 0.90f), Offset(size.width * 0.87f, size.height * 0.90f), width, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.50f, size.height * 0.20f), Offset(size.width * 0.50f, size.height * 0.08f), width, StrokeCap.Round)
        drawArc(
            color = tint,
            startAngle = 60f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(size.width * 0.45f, size.height * 0.015f),
            size = Size(size.width * 0.10f, size.height * 0.10f),
            style = stroke,
        )
    }
}

private fun DrawScope.drawFourPointStar(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float = radius * 0.14f,
) {
    val star = Path().apply {
        moveTo(center.x, center.y - radius)
        cubicTo(center.x + radius * 0.14f, center.y - radius * 0.20f, center.x + radius * 0.20f, center.y - radius * 0.14f, center.x + radius, center.y)
        cubicTo(center.x + radius * 0.20f, center.y + radius * 0.14f, center.x + radius * 0.14f, center.y + radius * 0.20f, center.x, center.y + radius)
        cubicTo(center.x - radius * 0.14f, center.y + radius * 0.20f, center.x - radius * 0.20f, center.y + radius * 0.14f, center.x - radius, center.y)
        cubicTo(center.x - radius * 0.20f, center.y - radius * 0.14f, center.x - radius * 0.14f, center.y - radius * 0.20f, center.x, center.y - radius)
        close()
    }
    drawPath(star, color.copy(alpha = 0.12f))
    drawPath(star, color, style = Stroke(strokeWidth, cap = StrokeCap.Round))
}
