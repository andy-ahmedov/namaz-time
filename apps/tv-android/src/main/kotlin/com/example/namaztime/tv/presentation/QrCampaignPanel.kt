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
import androidx.compose.ui.draw.clip
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
                    .height(if (compact) 72.dp else 104.dp)
                    .testTag(QR_BOTTOM_ORNAMENT_TAG),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 11.dp),
            ) {
                Text(
                    text = kindLabel,
                    color = NamazTvTheme.colors.accent,
                    fontSize = if (compact) 15.sp else 19.sp,
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                    ) {
                        SadaqahSupportGlyph(
                            modifier = Modifier
                                .size(if (compact) 26.dp else 34.dp)
                                .testTag(QR_SUPPORT_ICON_TAG),
                        )
                        Text(
                            text = subtitle,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(QR_CAMPAIGN_SUBTITLE_TAG),
                            color = NamazTvTheme.colors.textSecondary,
                            fontSize = if (compact) 11.sp else 14.sp,
                            lineHeight = if (compact) 14.sp else 18.sp,
                            textAlign = TextAlign.Start,
                            maxLines = if (compact) 3 else 4,
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
    val frameSize = qrSize + 22.dp
    val tint = NamazTvTheme.colors.accent
    Box(modifier = modifier.size(frameSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 1.dp.toPx()
            val arm = size.minDimension * 0.15f
            val radius = size.minDimension * 0.06f
            val strokeWidth = 1.15.dp.toPx()
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            val framePaths = listOf(
                Path().apply {
                    moveTo(left, top + arm * 1.08f)
                    lineTo(left, top + radius)
                    quadraticTo(left, top, left + radius, top)
                    lineTo(size.width * 0.76f, top)
                },
                Path().apply {
                    moveTo(right, bottom - arm * 1.42f)
                    lineTo(right, bottom - radius)
                    quadraticTo(right, bottom, right - radius, bottom)
                    lineTo(size.width * 0.18f, bottom)
                    quadraticTo(left, bottom, left, bottom - radius)
                    lineTo(left, bottom - arm * 0.48f)
                },
            )
            framePaths.forEach {
                drawPath(
                    it,
                    tint.copy(alpha = 0.22f),
                    style = Stroke(3.2.dp.toPx(), cap = StrokeCap.Round),
                )
                drawPath(it, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
        }
        Box(
            modifier = Modifier
                .size(qrSize)
                .clip(RoundedCornerShape(qrSize * 0.055f))
                .background(QR_LIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = rememberQrBitmap(
                    matrix = state.qrCode,
                    outputSize = with(LocalDensity.current) { qrSize.roundToPx() },
                ),
                contentDescription = appString(R.string.qr_content_description, state.title),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(QR_CODE_IMAGE_TAG)
                    .background(QR_LIGHT),
                filterQuality = FilterQuality.None,
            )
            DecorativeQrBadge(
                modifier = Modifier
                    .size(qrSize * 0.23f)
                    .testTag(QR_CENTER_BRAND_BADGE_TAG),
            )
        }
    }
}

@Composable
private fun DecorativeQrBadge(modifier: Modifier = Modifier) {
    val colors = NamazTvTheme.colors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.47f
            val star = Path()
            repeat(16) { point ->
                val angle = Math.PI * point / 8.0 - Math.PI / 2.0
                val pointRadius = radius * if (point % 2 == 0) 1f else 0.78f
                val x = center.x + kotlin.math.cos(angle).toFloat() * pointRadius
                val y = center.y + kotlin.math.sin(angle).toFloat() * pointRadius
                if (point == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            drawPath(star, colors.backgroundTop)
            drawPath(
                star,
                colors.accent.copy(alpha = 0.92f),
                style = Stroke(0.8.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize(0.70f)
                .background(colors.backgroundTop, RoundedCornerShape(22))
                .border(0.55.dp, colors.accentOutline, RoundedCornerShape(22)),
            contentAlignment = Alignment.Center,
        ) {
            BrandMark(Modifier.fillMaxSize().padding(4.dp))
        }
    }
}

@Composable
private fun SadaqahDivider(modifier: Modifier = Modifier) {
    TvFadingDiamondDivider(modifier = modifier)
}

@Composable
private fun SadaqahSupportGlyph(modifier: Modifier = Modifier) {
    val tint = NamazTvTheme.colors.accent
    Canvas(modifier) {
        val stroke = Stroke(size.minDimension * TV_ICON_STROKE_FRACTION, cap = StrokeCap.Round)
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
    TvIslamicGeometricPattern(modifier = modifier, intensity = 0.12f)
}

@Composable
internal fun rememberQrBitmap(matrix: QrCodeMatrix, outputSize: Int) =
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
        if (matrix.darkPixels[sourceY * matrix.size + sourceX]) QR_DARK else QR_LIGHT_ARGB
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

private val QR_LIGHT = Color(0xFFFFFDF8)
private const val QR_DARK: Int = 0xFF172331.toInt()
private const val QR_LIGHT_ARGB: Int = 0xFFFFFDF8.toInt()
