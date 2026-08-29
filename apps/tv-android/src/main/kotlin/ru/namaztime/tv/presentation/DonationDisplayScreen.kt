package ru.namaztime.tv.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import ru.namaztime.tv.R
import ru.namaztime.tv.repository.CUSTOM_DONATION_IMAGE_STYLE_ID
import ru.namaztime.tv.repository.DEFAULT_DONATION_IMAGE_STYLE_ID
import ru.namaztime.tv.repository.DONATION_IMAGE_COMMUNITY_STYLE_ID
import ru.namaztime.tv.repository.DONATION_IMAGE_COURTYARD_STYLE_ID
import ru.namaztime.tv.repository.DONATION_IMAGE_CRESCENT_STYLE_ID
import ru.namaztime.tv.repository.DONATION_IMAGE_LANTERN_STYLE_ID
import ru.namaztime.tv.repository.OperatorDonationConfiguration
import ru.namaztime.tv.repository.OperatorImageSlot

const val DONATION_DISPLAY_TAG = "donation-display"
const val DONATION_DISPLAY_SAFE_CONTENT_TAG = "donation-display-safe-content"
const val DONATION_DISPLAY_IMAGE_TAG = "donation-display-image"
const val DONATION_DISPLAY_STATUS_TAG = "donation-display-status"
const val DONATION_DISPLAY_DETAILS_TAG = "donation-display-details"
const val DONATION_DISPLAY_SETTINGS_TAG = "donation-display-settings"
const val DONATION_DISPLAY_SETTINGS_VISUAL_TAG = "donation-display-settings-visual"
const val DONATION_DISPLAY_BRAND_TAG = "donation-display-brand"
const val DONATION_DISPLAY_QR_TAG = "donation-display-qr"
const val DONATION_DISPLAY_ROWS_TAG = "donation-display-rows"
const val DONATION_DISPLAY_FOOTER_TAG = "donation-display-footer"
const val DONATION_DISPLAY_TITLE_TAG = "donation-display-title"
const val SETTINGS_DONATION_IMAGE_TAG_PREFIX = "settings-donation-image-"

internal enum class DonationImageStyle(
    val id: String,
    @param:DrawableRes val drawableRes: Int,
    @param:StringRes val labelRes: Int,
) {
    MOSQUE(
        DEFAULT_DONATION_IMAGE_STYLE_ID,
        R.drawable.tv_background_golden_dusk,
        R.string.value_donation_image_mosque,
    ),
    COURTYARD(
        DONATION_IMAGE_COURTYARD_STYLE_ID,
        R.drawable.tv_background_autumn_courtyard,
        R.string.value_donation_image_courtyard,
    ),
    LANTERN(
        DONATION_IMAGE_LANTERN_STYLE_ID,
        R.drawable.tv_background_desert_dawn,
        R.string.value_donation_image_lantern,
    ),
    CRESCENT(
        DONATION_IMAGE_CRESCENT_STYLE_ID,
        R.drawable.tv_background_celestial_navy,
        R.string.value_donation_image_crescent,
    ),
    COMMUNITY(
        DONATION_IMAGE_COMMUNITY_STYLE_ID,
        R.drawable.tv_background_emerald_mosque,
        R.string.value_donation_image_community,
    ),
    ;

    companion object {
        fun fromId(id: String): DonationImageStyle = entries.firstOrNull { it.id == id } ?: MOSQUE
    }
}

private data class DonationDisplayMetrics(val scale: Float) {
    fun dp(value: Float): Dp = (value * scale).dp

    val safeLeft = dp(31f)
    val safeTop = dp(18f)
    val safeWidth = dp(898f)
    val safeHeight = dp(504f)
    val statusLeft = dp(656f)
    val statusTop = dp(28f)
    val statusWidth = dp(206f)
    val topBlockHeight = dp(50f)
    val settingsLeft = dp(870f)
    val cardLeft = dp(656f)
    val cardTop = dp(88f)
    val cardWidth = dp(264f)
    val cardHeight = dp(348f)
    val cardRadius = dp(18f)
    val footerLeft = dp(656f)
    val footerTop = dp(446f)
    val footerWidth = dp(264f)
    val footerHeight = dp(66f)
    val settingsVisualSize = topBlockHeight
    val settingsHitSize = topBlockHeight
}

internal data class DonationStatusUiState(
    val dateLabel: String,
    val weekdayLabel: String,
    val mosqueLocalTime: String,
    val currentPrayerLabel: String,
)

@Composable
internal fun DonationDisplayScreen(
    configuration: OperatorDonationConfiguration,
    qrState: QrCampaignUiState,
    status: DonationStatusUiState,
    customAssetVersion: Long,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = true,
    retentionOffset: DpOffset = DpOffset.Zero,
) {
    val settingsRequester = remember { FocusRequester() }
    val style = DonationImageStyle.fromId(configuration.imageStyleId)
    val customImage = rememberOperatorImageBitmap(
        slot = OperatorImageSlot.DONATION,
        assetVersion = customAssetVersion,
        enabled = configuration.imageStyleId == CUSTOM_DONATION_IMAGE_STYLE_ID,
    )
    val painter = customImage?.let(::BitmapPainter) ?: painterResource(style.drawableRes)

    BoxWithConstraints(modifier = modifier.fillMaxSize().testTag(DONATION_DISPLAY_TAG)) {
        val scale = minOf(maxWidth.value / REFERENCE_WIDTH_DP, maxHeight.value / REFERENCE_HEIGHT_DP)
        val metrics = DonationDisplayMetrics(scale)
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().testTag(DONATION_DISPLAY_IMAGE_TAG),
        )
        Box(Modifier.fillMaxSize().background(DONATION_BACKGROUND_SCRIM))
        Box(
            Modifier
                .offset(metrics.safeLeft, metrics.safeTop)
                .size(metrics.safeWidth, metrics.safeHeight)
                .testTag(DONATION_DISPLAY_SAFE_CONTENT_TAG),
        )
        Box(Modifier.fillMaxSize().offset(retentionOffset.x, retentionOffset.y)) {
            DonationStatusBlock(
                status = status,
                metrics = metrics,
                modifier = Modifier
                    .offset(metrics.statusLeft, metrics.statusTop)
                    .size(metrics.statusWidth, metrics.topBlockHeight)
                    .testTag(DONATION_DISPLAY_STATUS_TAG),
            )
            DonationCard(
                configuration = configuration,
                qrState = qrState,
                metrics = metrics,
                modifier = Modifier
                    .offset(metrics.cardLeft, metrics.cardTop)
                    .size(metrics.cardWidth, metrics.cardHeight)
                    .testTag(DONATION_DISPLAY_DETAILS_TAG),
            )
            DonationFooter(
                text = configuration.gratitudeMessage.trim().ifEmpty {
                    appString(R.string.donation_footer_thanks)
                },
                metrics = metrics,
                modifier = Modifier
                    .offset(metrics.footerLeft, metrics.footerTop)
                    .size(metrics.footerWidth, metrics.footerHeight)
                    .testTag(DONATION_DISPLAY_FOOTER_TAG),
            )
            DonationSettingsButton(
                requester = settingsRequester,
                requestInitialFocus = requestInitialFocus,
                onOpenSettings = onOpenSettings,
                metrics = metrics,
                modifier = Modifier.offset(metrics.settingsLeft, metrics.statusTop),
            )
        }
    }
}

@Composable
private fun DonationStatusBlock(
    status: DonationStatusUiState,
    metrics: DonationDisplayMetrics,
    modifier: Modifier = Modifier,
) {
    val colors = NamazTvTheme.colors
    val shape = RoundedCornerShape(metrics.dp(16f))
    Row(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(DONATION_STATUS_TOP, DONATION_STATUS_BOTTOM)))
            .border(metrics.dp(0.8f), colors.accentOutline.copy(alpha = 0.82f), shape)
            .padding(horizontal = metrics.dp(6f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1.2f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = status.dateLabel,
                color = colors.textPrimary,
                fontSize = (10.5f * metrics.scale).sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status.weekdayLabel,
                color = colors.textPrimary,
                fontSize = (9.5f * metrics.scale).sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DonationStatusDivider(metrics)
        Text(
            text = status.mosqueLocalTime,
            modifier = Modifier.weight(0.8f),
            color = colors.textPrimary,
            fontSize = (18f * metrics.scale).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        DonationStatusDivider(metrics)
        Text(
            text = status.currentPrayerLabel,
            modifier = Modifier.weight(0.72f),
            color = colors.textPrimary,
            fontSize = (15f * metrics.scale).sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DonationStatusDivider(metrics: DonationDisplayMetrics) {
    Box(
        Modifier
            .width(metrics.dp(0.8f))
            .height(metrics.dp(30f))
            .background(NamazTvTheme.colors.textSecondary.copy(alpha = 0.58f)),
    )
}

@Composable
private fun DonationCard(
    configuration: OperatorDonationConfiguration,
    qrState: QrCampaignUiState,
    metrics: DonationDisplayMetrics,
    modifier: Modifier = Modifier,
) {
    val colors = NamazTvTheme.colors
    val shape = RoundedCornerShape(metrics.cardRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(DONATION_CARD_TOP, DONATION_CARD_BOTTOM)))
            .border(metrics.dp(0.8f), colors.accentOutline.copy(alpha = 0.82f), shape),
    ) {
        Text(
            text = appString(R.string.donation_display_title),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = metrics.dp(8f))
                .width(metrics.dp(232f))
                .height(metrics.dp(28f))
                .testTag(DONATION_DISPLAY_TITLE_TAG),
            color = colors.accent,
            fontSize = (20f * metrics.scale).sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        TvFadingDiamondDivider(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = metrics.dp(38f))
                .width(metrics.dp(170f))
                .height(metrics.dp(8f)),
            tint = colors.accentOutline,
        )
        Text(
            text = appString(R.string.donation_display_subtitle),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = metrics.dp(48f))
                .width(metrics.dp(244f)),
            color = colors.textPrimary.copy(alpha = 0.92f),
            fontSize = (13f * metrics.scale).sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DonationQrSurface(
            state = qrState,
            modifier = Modifier
                .offset(x = metrics.dp(59f), y = metrics.dp(68f))
                .size(metrics.dp(146f))
                .testTag(DONATION_DISPLAY_QR_TAG),
            scale = metrics.scale,
        )
        DonationDetailsRows(
            configuration = configuration,
            modifier = Modifier
                .offset(x = metrics.dp(15f), y = metrics.dp(218f))
                .width(metrics.dp(234f))
                .height(metrics.dp(120f))
                .testTag(DONATION_DISPLAY_ROWS_TAG),
            scale = metrics.scale,
        )
    }
}

@Composable
private fun DonationQrSurface(
    state: QrCampaignUiState,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    ReferenceQrCode(
        state = state,
        qrSize = (130f * scale).dp,
        framePadding = (8f * scale).dp,
        modifier = modifier
            .testTag(QR_ELEGANT_FRAME_TAG),
    )
}

@Composable
private fun DonationDetailsRows(
    configuration: OperatorDonationConfiguration,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        DonationDetailRow(DonationDetailIcon.RECIPIENT, R.string.donation_recipient_label, configuration.recipient),
        DonationDetailRow(DonationDetailIcon.BANK, R.string.donation_bank_label, configuration.bank),
        DonationDetailRow(DonationDetailIcon.CARD, R.string.donation_card_number_label, configuration.cardNumber),
        DonationDetailRow(DonationDetailIcon.PHONE, R.string.donation_display_phone_label, configuration.phone),
        DonationDetailRow(
            DonationDetailIcon.LINK,
            R.string.donation_display_collection_url_label,
            configuration.collectionUrl,
            true,
        ),
    )
    Column(modifier) {
        rows.forEachIndexed { index, row ->
            DonationDetailsRow(
                row = row,
                modifier = Modifier.width((234f * scale).dp).height((24f * scale).dp),
                scale = scale,
                showSeparator = index != rows.lastIndex,
            )
        }
    }
}

private data class DonationDetailRow(
    val icon: DonationDetailIcon,
    @param:StringRes val labelRes: Int,
    val value: String,
    val accentValue: Boolean = false,
)

@Composable
private fun DonationDetailsRow(
    row: DonationDetailRow,
    scale: Float,
    showSeparator: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = NamazTvTheme.colors
    Box(modifier) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = (2f * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DonationDetailGlyph(icon = row.icon, modifier = Modifier.size((18f * scale).dp))
            Text(
                text = "${appString(row.labelRes)}:",
                modifier = Modifier.padding(start = (7f * scale).dp).width((72f * scale).dp),
                color = colors.textSecondary.copy(alpha = 0.86f),
                fontSize = (10.5f * scale).sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.value,
                modifier = Modifier.weight(1f),
                color = if (row.accentValue) colors.accent else colors.textPrimary,
                fontSize = (11.5f * scale).sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showSeparator) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .height((0.5f * scale).dp)
                    .background(colors.separator.copy(alpha = 0.72f)),
            )
        }
    }
}

@Composable
private fun DonationFooter(
    text: String,
    metrics: DonationDisplayMetrics,
    modifier: Modifier = Modifier,
) {
    val colors = NamazTvTheme.colors
    val shape = RoundedCornerShape(metrics.dp(18f))
    Row(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(DONATION_STATUS_TOP, DONATION_FOOTER_SURFACE)))
            .border(metrics.dp(0.8f), colors.accentOutline.copy(alpha = 0.82f), shape)
            .padding(horizontal = metrics.dp(7f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DonationArchLanternGlyph(
            modifier = Modifier.size(metrics.dp(42f)),
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f).padding(horizontal = metrics.dp(6f)),
            color = colors.textPrimary,
            fontSize = (12.5f * metrics.scale).sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            lineHeight = (16f * metrics.scale).sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        DonationArchLanternGlyph(
            mirrored = true,
            modifier = Modifier.size(metrics.dp(42f)),
        )
    }
}

@Composable
private fun DonationSettingsButton(
    requester: FocusRequester,
    requestInitialFocus: Boolean,
    onOpenSettings: () -> Unit,
    metrics: DonationDisplayMetrics,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = NamazTvTheme.colors
    val label = appString(R.string.open_settings)
    val shape = RoundedCornerShape(metrics.dp(16f))
    LaunchedEffect(requestInitialFocus, requester) {
        if (requestInitialFocus) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    Button(
        onClick = onOpenSettings,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = colors.textPrimary,
        ),
        shape = ButtonDefaults.shape(
            shape = shape,
            focusedShape = shape,
            pressedShape = shape,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        modifier = modifier
            .size(metrics.settingsHitSize)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label }
            .testTag(DONATION_DISPLAY_SETTINGS_TAG),
    ) {
        Box(
            modifier = Modifier
                .size(metrics.settingsVisualSize)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(DONATION_STATUS_TOP, DONATION_STATUS_BOTTOM)))
                .border(
                    width = if (focused) metrics.dp(1.25f) else metrics.dp(0.8f),
                    color = if (focused) colors.focus.copy(alpha = 0.85f) else colors.accentOutline.copy(alpha = 0.58f),
                    shape = shape,
                )
                .testTag(DONATION_DISPLAY_SETTINGS_VISUAL_TAG),
            contentAlignment = Alignment.Center,
        ) {
            DonationSettingsGlyph(
                tint = colors.textPrimary,
                modifier = Modifier.size(metrics.dp(24f)),
            )
        }
    }
}

private const val REFERENCE_WIDTH_DP = 960f
private const val REFERENCE_HEIGHT_DP = 540f
private val DONATION_BACKGROUND_SCRIM = Color(0x52061322)
private val DONATION_STATUS_TOP = Color(0xE5233042)
private val DONATION_STATUS_BOTTOM = Color(0xED192536)
private val DONATION_CARD_TOP = Color(0xED243143)
private val DONATION_CARD_BOTTOM = Color(0xF2192433)
private val DONATION_FOOTER_SURFACE = Color(0xF0182638)
