package ru.namaztime.tv.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ru.namaztime.tv.R
import ru.namaztime.tv.domain.QrCodeMatrix
import ru.namaztime.tv.domain.ResolvedCampaign
import ru.namaztime.tv.repository.OperatorQrTextFitPolicy

const val QR_CAMPAIGN_PANEL_TAG = "qr-campaign-panel"
const val QR_CAMPAIGN_PREVIEW_TAG = "qr-campaign-preview"
const val QR_CODE_IMAGE_TAG = "qr-code-image"
const val QR_CAMPAIGN_TITLE_TAG = "qr-campaign-title"
const val QR_CAMPAIGN_SUBTITLE_TAG = "qr-campaign-subtitle"
const val QR_ORNAMENT_DIVIDER_TAG = "qr-ornament-divider"
const val QR_ELEGANT_FRAME_TAG = "qr-elegant-frame"
const val QR_SUPPORT_ICON_TAG = "qr-support-icon"
const val QR_BOTTOM_ORNAMENT_TAG = "qr-bottom-ornament"
internal const val REFERENCE_QR_CORNER_ARM_FRACTION = 0.13f
val QrSubtitleFullyVisibleKey = SemanticsPropertyKey<Boolean>("QrSubtitleFullyVisible")
private var SemanticsPropertyReceiver.qrSubtitleFullyVisible by QrSubtitleFullyVisibleKey

internal data class ReferenceQrCorner(
    val horizontalDirection: Int,
    val verticalDirection: Int,
)

internal val REFERENCE_QR_CORNERS = listOf(
    ReferenceQrCorner(horizontalDirection = 1, verticalDirection = 1),
    ReferenceQrCorner(horizontalDirection = -1, verticalDirection = 1),
    ReferenceQrCorner(horizontalDirection = 1, verticalDirection = -1),
    ReferenceQrCorner(horizontalDirection = -1, verticalDirection = -1),
)

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
                    .padding(
                        horizontal = if (compact) 14.dp else 20.dp,
                        vertical = if (compact) 21.dp else 14.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 11.dp),
            ) {
                Text(
                    text = kindLabel,
                    color = NamazTvTheme.colors.accent,
                    fontSize = if (compact) 16.sp else 19.sp,
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
                    fontSize = if (compact) 14.sp else 19.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ReferenceQrCode(
                    state = state,
                    qrSize = qrSize,
                    modifier = Modifier.testTag(QR_ELEGANT_FRAME_TAG),
                )
                state.subtitle?.let { subtitle ->
                    val fit = remember(subtitle) { OperatorQrTextFitPolicy.fit(subtitle) }
                    var fullyVisible by remember(subtitle, fit) { mutableStateOf(fit != null) }
                    if (fit == null) return@let
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
                                .semantics {
                                    qrSubtitleFullyVisible = fullyVisible
                                }
                                .testTag(QR_CAMPAIGN_SUBTITLE_TAG),
                            color = NamazTvTheme.colors.textSecondary,
                            fontSize = if (compact) fit.fontSizeSp.sp else 14.sp,
                            lineHeight = if (compact) fit.lineHeightSp.sp else 18.sp,
                            textAlign = TextAlign.Start,
                            onTextLayout = { result ->
                                fullyVisible = !result.hasVisualOverflow &&
                                    result.lineCount <= OperatorQrTextFitPolicy.MAX_LINES
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReferenceQrCode(
    state: QrCampaignUiState,
    qrSize: Dp,
    modifier: Modifier = Modifier,
    framePadding: Dp = 8.dp,
) {
    val frameSize = qrSize + framePadding * 2f
    val tint = NamazTvTheme.colors.accent
    Box(modifier = modifier.size(frameSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 1.dp.toPx()
            val arm = size.minDimension * REFERENCE_QR_CORNER_ARM_FRACTION
            val radius = size.minDimension * 0.035f
            val strokeWidth = 1.15.dp.toPx()
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            val framePaths = REFERENCE_QR_CORNERS.map { corner ->
                val cornerX = if (corner.horizontalDirection > 0) left else right
                val cornerY = if (corner.verticalDirection > 0) top else bottom
                val horizontalEnd = cornerX + corner.horizontalDirection * arm
                val verticalEnd = cornerY + corner.verticalDirection * arm
                val horizontalTurn = cornerX + corner.horizontalDirection * radius
                val verticalTurn = cornerY + corner.verticalDirection * radius
                Path().apply {
                    moveTo(horizontalEnd, cornerY)
                    lineTo(horizontalTurn, cornerY)
                    quadraticTo(cornerX, cornerY, cornerX, verticalTurn)
                    lineTo(cornerX, verticalEnd)
                }
            }
            framePaths.forEach {
                drawPath(
                    it,
                    tint.copy(alpha = 0.20f),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
                drawPath(it, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .size(qrSize),
            contentAlignment = Alignment.Center,
        ) {
            val outputSize = with(LocalDensity.current) { minOf(maxWidth, maxHeight).roundToPx() }
            if (outputSize < state.qrCode.moduleCount) {
                Text(
                    appString(R.string.qr_insufficient_space),
                    color = Color(QR_DARK),
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("qr-insufficient-space"),
                )
                return@BoxWithConstraints
            }
            Image(
                bitmap = rememberQrBitmap(
                    matrix = state.qrCode,
                    outputSize = outputSize,
                ),
                contentDescription = appString(R.string.qr_content_description, state.title),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(QR_CODE_IMAGE_TAG)
                    .clip(RoundedCornerShape(with(LocalDensity.current) {
                        qrCornerRadiusPixels(state.qrCode, outputSize).toDp()
                    }))
                    .background(QR_LIGHT),
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.None,
            )

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
            topLeft = Offset(size.width * 0.12f, size.height * 0.05f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.54f, size.height * 0.86f),
            cornerRadius = CornerRadius(size.minDimension * 0.08f),
            style = stroke,
        )
        drawLine(
            tint,
            Offset(size.width * 0.28f, size.height * 0.15f),
            Offset(size.width * 0.49f, size.height * 0.15f),
            stroke.width,
            StrokeCap.Round,
        )
        drawCircle(
            tint,
            size.minDimension * 0.021f,
            Offset(size.width * 0.39f, size.height * 0.82f),
        )
        val hand = Path().apply {
            moveTo(size.width * 0.61f, size.height * 0.50f)
            cubicTo(
                size.width * 0.70f,
                size.height * 0.45f,
                size.width * 0.77f,
                size.height * 0.51f,
                size.width * 0.74f,
                size.height * 0.61f,
            )
            cubicTo(
                size.width * 0.82f,
                size.height * 0.56f,
                size.width * 0.89f,
                size.height * 0.62f,
                size.width * 0.84f,
                size.height * 0.71f,
            )
            lineTo(size.width * 0.68f, size.height * 0.88f)
            cubicTo(
                size.width * 0.56f,
                size.height * 0.98f,
                size.width * 0.38f,
                size.height * 0.88f,
                size.width * 0.39f,
                size.height * 0.74f,
            )
            lineTo(size.width * 0.40f, size.height * 0.58f)
            cubicTo(
                size.width * 0.41f,
                size.height * 0.51f,
                size.width * 0.49f,
                size.height * 0.50f,
                size.width * 0.52f,
                size.height * 0.57f,
            )
            lineTo(size.width * 0.55f, size.height * 0.66f)
        }
        drawPath(hand, tint, style = stroke)
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
    require(outputSize >= matrix.moduleCount) { "QR raster cannot fit its modules" }
    val pitch = outputSize / matrix.moduleCount
    // Below three pixels per module, fractional widths can break dense-code
    // decoding. At three pixels, use the whole square instead of adding a
    // second white border around the four-module quiet zone.
    if (pitch < 3) {
        val inset = (outputSize - matrix.moduleCount * pitch) / 2
        return IntArray(outputSize * outputSize) { index ->
            val x = index % outputSize - inset
            val y = index / outputSize - inset
            if (x >= 0 && y >= 0 && x < matrix.moduleCount * pitch &&
                y < matrix.moduleCount * pitch &&
                matrix.darkModules[(y / pitch) * matrix.moduleCount + x / pitch]
            ) QR_DARK else QR_LIGHT_ARGB
        }
    }
    return IntArray(outputSize * outputSize) { index ->
        // Spread fractional pitch across the symbol instead of turning the remainder
        // into extra white padding. Modules stay sharp, with widths differing by at most 1 px.
        val moduleX = (index % outputSize).toLong() * matrix.moduleCount / outputSize
        val moduleY = (index / outputSize).toLong() * matrix.moduleCount / outputSize
        if (matrix.darkModules[(moduleY * matrix.moduleCount + moduleX).toInt()])
            QR_DARK else QR_LIGHT_ARGB
    }
}

/** Round only the outer white corners; never enter the symbol or its side clearances. */
internal fun qrCornerRadiusPixels(matrix: QrCodeMatrix, outputSize: Int): Float =
    minOf(outputSize * 0.20f, (outputSize * 4f / matrix.moduleCount).toInt().toFloat())

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

internal fun campaignKindResource(kind: String): Int = when (kind) {
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
