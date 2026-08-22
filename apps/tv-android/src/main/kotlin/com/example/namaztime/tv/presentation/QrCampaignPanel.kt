package com.example.namaztime.tv.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.namaztime.tv.R
import com.example.namaztime.tv.domain.QrCodeMatrix
import com.example.namaztime.tv.domain.ResolvedCampaign

const val QR_CAMPAIGN_PANEL_TAG = "qr-campaign-panel"
const val QR_CAMPAIGN_PREVIEW_TAG = "qr-campaign-preview"
const val QR_CODE_IMAGE_TAG = "qr-code-image"
const val QR_CAMPAIGN_TITLE_TAG = "qr-campaign-title"
const val QR_CAMPAIGN_SUBTITLE_TAG = "qr-campaign-subtitle"
const val QR_ORNAMENT_DIVIDER_TAG = "qr-ornament-divider"
const val QR_ELEGANT_FRAME_TAG = "qr-elegant-frame"
const val QR_SUPPORT_ICON_TAG = "qr-support-icon"
const val QR_BOTTOM_ORNAMENT_TAG = "qr-bottom-ornament"
const val QR_CENTER_BRAND_BADGE_TAG = "qr-center-brand-badge"

data class QrCampaignUiState(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String?,
    val qrCode: QrCodeMatrix,
    val preview: Boolean,
)

@Composable
internal fun QrCampaignPanel(
    state: QrCampaignUiState,
    qrSize: Dp,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val kindLabel = appString(campaignKindResource(state.kind))
    TvGlassPanel(
        modifier = modifier
            .fillMaxHeight()
            .testTag(if (state.preview) QR_CAMPAIGN_PREVIEW_TAG else QR_CAMPAIGN_PANEL_TAG),
        radius = 20.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SadaqahBottomOrnament(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(if (compact) 60.dp else 94.dp)
                    .testTag(QR_BOTTOM_ORNAMENT_TAG),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            ) {
                Text(
                    text = kindLabel,
                    color = NamazTvTheme.colors.accent,
                    fontSize = if (compact) 16.sp else 20.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SadaqahDivider(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(8.dp)
                        .testTag(QR_ORNAMENT_DIVIDER_TAG),
                )
                Text(
                    text = state.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(QR_CAMPAIGN_TITLE_TAG),
                    color = NamazTvTheme.colors.textPrimary,
                    fontSize = if (compact) 16.sp else 19.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ElegantQrFrame(
                    state = state,
                    qrSize = qrSize,
                    modifier = Modifier.testTag(QR_ELEGANT_FRAME_TAG),
                )
                state.subtitle?.let { subtitle ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                    ) {
                        SadaqahSupportGlyph(
                            modifier = Modifier
                                .size(if (compact) 30.dp else 38.dp)
                                .testTag(QR_SUPPORT_ICON_TAG),
                        )
                        Text(
                            text = subtitle,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(QR_CAMPAIGN_SUBTITLE_TAG),
                            color = NamazTvTheme.colors.textSecondary,
                            fontSize = if (compact) 12.sp else 15.sp,
                            textAlign = TextAlign.Start,
                            maxLines = if (compact) 4 else 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ElegantQrFrame(
    state: QrCampaignUiState,
    qrSize: Dp,
    modifier: Modifier = Modifier,
) {
    val frameSize = qrSize + 18.dp
    val tint = NamazTvTheme.colors.accent
    Box(modifier = modifier.size(frameSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 1.dp.toPx()
            val arm = size.minDimension * 0.14f
            val radius = size.minDimension * 0.06f
            val strokeWidth = 1.5.dp.toPx()
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            val corners = listOf(
                Path().apply {
                    moveTo(left, top + arm)
                    lineTo(left, top + radius)
                    quadraticTo(left, top, left + radius, top)
                    lineTo(left + arm, top)
                },
                Path().apply {
                    moveTo(right - arm, top)
                    lineTo(right - radius, top)
                    quadraticTo(right, top, right, top + radius)
                    lineTo(right, top + arm)
                },
                Path().apply {
                    moveTo(right, bottom - arm)
                    lineTo(right, bottom - radius)
                    quadraticTo(right, bottom, right - radius, bottom)
                    lineTo(right - arm, bottom)
                },
                Path().apply {
                    moveTo(left + arm, bottom)
                    lineTo(left + radius, bottom)
                    quadraticTo(left, bottom, left, bottom - radius)
                    lineTo(left, bottom - arm)
                },
            )
            corners.forEach { drawPath(it, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round)) }
        }
        Box(modifier = Modifier.size(qrSize), contentAlignment = Alignment.Center) {
            Image(
                bitmap = rememberQrBitmap(
                    matrix = state.qrCode,
                    outputSize = with(LocalDensity.current) { qrSize.roundToPx() },
                ),
                contentDescription = appString(R.string.qr_content_description, state.title),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(QR_CODE_IMAGE_TAG)
                    .background(Color.White),
                filterQuality = FilterQuality.None,
            )
            Box(
                modifier = Modifier
                    .size(qrSize * 0.23f)
                    .background(
                        NamazTvTheme.colors.backgroundTop,
                        RoundedCornerShape(qrSize * 0.035f),
                    )
                    .border(
                        1.dp,
                        NamazTvTheme.colors.accent,
                        RoundedCornerShape(qrSize * 0.035f),
                    )
                    .testTag(QR_CENTER_BRAND_BADGE_TAG),
                contentAlignment = Alignment.Center,
            ) {
                BrandMark(Modifier.fillMaxSize().padding(qrSize * 0.045f))
            }
        }
    }
}

@Composable
private fun SadaqahDivider(modifier: Modifier = Modifier) {
    val tint = NamazTvTheme.colors.accentOutline
    Canvas(modifier) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val radius = size.height * 0.31f
        drawLine(tint.copy(alpha = 0.58f), androidx.compose.ui.geometry.Offset(0f, center.y), androidx.compose.ui.geometry.Offset(center.x - radius * 2f, center.y), 0.75.dp.toPx())
        drawLine(tint.copy(alpha = 0.58f), androidx.compose.ui.geometry.Offset(center.x + radius * 2f, center.y), androidx.compose.ui.geometry.Offset(size.width, center.y), 0.75.dp.toPx())
        val diamond = Path().apply {
            moveTo(center.x, center.y - radius)
            lineTo(center.x + radius, center.y)
            lineTo(center.x, center.y + radius)
            lineTo(center.x - radius, center.y)
            close()
        }
        drawPath(diamond, tint, style = Stroke(0.9.dp.toPx()))
    }
}

@Composable
private fun SadaqahSupportGlyph(modifier: Modifier = Modifier) {
    val tint = NamazTvTheme.colors.accent
    Canvas(modifier) {
        val stroke = Stroke(size.minDimension * 0.055f, cap = StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.06f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.86f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.09f),
            style = stroke,
        )
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.17f),
            androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.17f),
            stroke.width,
            StrokeCap.Round,
        )
        val supportMark = Path().apply {
            moveTo(size.width * 0.34f, size.height * 0.39f)
            cubicTo(
                size.width * 0.28f,
                size.height * 0.56f,
                size.width * 0.36f,
                size.height * 0.69f,
                size.width * 0.53f,
                size.height * 0.70f,
            )
            cubicTo(
                size.width * 0.64f,
                size.height * 0.70f,
                size.width * 0.69f,
                size.height * 0.64f,
                size.width * 0.72f,
                size.height * 0.55f,
            )
        }
        drawPath(supportMark, tint, style = stroke)
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.055f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height * 0.38f),
            style = stroke,
        )
        drawCircle(tint, size.minDimension * 0.025f, androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.82f))
    }
}

@Composable
private fun SadaqahBottomOrnament(modifier: Modifier = Modifier) {
    val tint = NamazTvTheme.colors.accentOutline
    Canvas(modifier) {
        val cell = size.width / 3.25f
        val stroke = Stroke(0.5.dp.toPx())
        repeat(5) { column ->
            repeat(3) { row ->
                val centerX = (column - 0.42f) * cell + if (row % 2 == 0) 0f else cell / 2f
                val centerY = size.height * 0.27f + row * cell * 0.72f
                val radius = cell * 0.42f
                val alpha = (0.12f - row * 0.028f).coerceAtLeast(0.04f)
                listOf(1f to 0.42f, 0.68f to 0.30f).forEach { (outer, inner) ->
                    val star = Path()
                    repeat(16) { point ->
                        val angle = Math.PI * point / 8.0 - Math.PI / 2.0
                        val pointRadius = radius * if (point % 2 == 0) outer else inner
                        val x = centerX + kotlin.math.cos(angle).toFloat() * pointRadius
                        val y = centerY + kotlin.math.sin(angle).toFloat() * pointRadius
                        if (point == 0) star.moveTo(x, y) else star.lineTo(x, y)
                    }
                    star.close()
                    drawPath(star, tint.copy(alpha = alpha), style = stroke)
                }
                drawCircle(
                    tint.copy(alpha = alpha * 0.72f),
                    radius * 0.73f,
                    androidx.compose.ui.geometry.Offset(centerX, centerY),
                    style = stroke,
                )
                repeat(8) { point ->
                    val angle = Math.PI * point / 4.0
                    val start = androidx.compose.ui.geometry.Offset(
                        centerX + kotlin.math.cos(angle).toFloat() * radius * 0.30f,
                        centerY + kotlin.math.sin(angle).toFloat() * radius * 0.30f,
                    )
                    val end = androidx.compose.ui.geometry.Offset(
                        centerX + kotlin.math.cos(angle).toFloat() * radius * 0.73f,
                        centerY + kotlin.math.sin(angle).toFloat() * radius * 0.73f,
                    )
                    drawLine(tint.copy(alpha = alpha * 0.72f), start, end, stroke.width)
                }
            }
        }
    }
}

@Composable
private fun rememberQrBitmap(matrix: QrCodeMatrix, outputSize: Int) =
    remember(matrix, outputSize) {
        Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888).apply {
            setPixels(
                qrArgbPixels(matrix, outputSize),
                0,
                outputSize,
                0,
                0,
                outputSize,
                outputSize,
            )
        }.asImageBitmap()
    }

internal fun qrArgbPixels(matrix: QrCodeMatrix, outputSize: Int): IntArray {
    require(outputSize > 0) { "QR raster size must be positive" }
    return IntArray(outputSize * outputSize) { index ->
        val targetX = index % outputSize
        val targetY = index / outputSize
        val sourceX = targetX * matrix.size / outputSize
        val sourceY = targetY * matrix.size / outputSize
        if (matrix.darkPixels[sourceY * matrix.size + sourceX]) BLACK else WHITE
    }
}

internal fun ResolvedCampaign.toQrCampaignUiState(
    qrCode: QrCodeMatrix,
    preview: Boolean,
) = QrCampaignUiState(
    id = id,
    kind = kind,
    title = title,
    subtitle = subtitle,
    qrCode = qrCode,
    preview = preview,
)

private fun campaignKindResource(kind: String): Int = when (kind) {
    "donation" -> R.string.campaign_kind_donation
    "telegram" -> R.string.campaign_kind_telegram
    "schedule" -> R.string.campaign_kind_schedule
    "contacts" -> R.string.campaign_kind_contacts
    "website" -> R.string.campaign_kind_website
    else -> R.string.campaign_kind_information
}

private const val BLACK: Int = -0x1000000
private const val WHITE: Int = -0x1
