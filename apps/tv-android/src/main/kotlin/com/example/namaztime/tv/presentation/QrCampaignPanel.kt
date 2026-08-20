package com.example.namaztime.tv.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.namaztime.tv.domain.QrCodeMatrix
import com.example.namaztime.tv.domain.ResolvedCampaign

const val QR_CAMPAIGN_PANEL_TAG = "qr-campaign-panel"
const val QR_CAMPAIGN_PREVIEW_TAG = "qr-campaign-preview"
const val QR_CODE_IMAGE_TAG = "qr-code-image"
const val QR_CAMPAIGN_TITLE_TAG = "qr-campaign-title"
const val QR_CAMPAIGN_SUBTITLE_TAG = "qr-campaign-subtitle"

data class QrCampaignUiState(
    val id: String,
    val kindLabel: String,
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
    Column(
        modifier = modifier
            .fillMaxHeight()
            .testTag(if (state.preview) QR_CAMPAIGN_PREVIEW_TAG else QR_CAMPAIGN_PANEL_TAG)
            .background(Color(0xFF102326).copy(alpha = 0.94f), RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFF56706D), RoundedCornerShape(18.dp))
            .padding(if (compact) 8.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (state.preview) "ПРЕДПРОСМОТР · ${state.kindLabel}" else state.kindLabel,
            color = Color(0xFFFFE3A0),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Image(
            bitmap = rememberQrBitmap(
                matrix = state.qrCode,
                outputSize = with(LocalDensity.current) { qrSize.roundToPx() },
            ),
            contentDescription = "QR-код: ${state.title}",
            modifier = Modifier
                .padding(vertical = 8.dp)
                .size(qrSize)
                .testTag(QR_CODE_IMAGE_TAG)
                .background(Color.White),
            filterQuality = FilterQuality.None,
        )
        Text(
            text = state.title,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(QR_CAMPAIGN_TITLE_TAG),
            color = Color.White,
            fontSize = if (compact) 16.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        state.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(QR_CAMPAIGN_SUBTITLE_TAG)
                    .padding(top = 4.dp),
                color = Color(0xFFCFDCDA),
                fontSize = if (compact) 12.sp else 14.sp,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
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
    kindLabel = when (kind) {
        "donation" -> "ПОЖЕРТВОВАНИЕ"
        "telegram" -> "TELEGRAM"
        "schedule" -> "РАСПИСАНИЕ"
        "contacts" -> "КОНТАКТЫ"
        "website" -> "САЙТ"
        else -> "ИНФОРМАЦИЯ"
    },
    title = title,
    subtitle = subtitle,
    qrCode = qrCode,
    preview = preview,
)

private const val BLACK: Int = -0x1000000
private const val WHITE: Int = -0x1
