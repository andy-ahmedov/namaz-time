package com.example.namaztime.tv.presentation

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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.example.namaztime.tv.R
import com.example.namaztime.tv.repository.CUSTOM_DONATION_IMAGE_STYLE_ID
import com.example.namaztime.tv.repository.DEFAULT_DONATION_IMAGE_STYLE_ID
import com.example.namaztime.tv.repository.DONATION_IMAGE_COMMUNITY_STYLE_ID
import com.example.namaztime.tv.repository.DONATION_IMAGE_COURTYARD_STYLE_ID
import com.example.namaztime.tv.repository.DONATION_IMAGE_CRESCENT_STYLE_ID
import com.example.namaztime.tv.repository.DONATION_IMAGE_LANTERN_STYLE_ID
import com.example.namaztime.tv.repository.OperatorDonationConfiguration
import com.example.namaztime.tv.repository.OperatorImageSlot

const val DONATION_DISPLAY_TAG = "donation-display"
const val DONATION_DISPLAY_SAFE_CONTENT_TAG = "donation-display-safe-content"
const val DONATION_DISPLAY_IMAGE_TAG = "donation-display-image"
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
    @DrawableRes val drawableRes: Int,
    @StringRes val labelRes: Int,
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

    val safeLeft = dp(9.5f)
    val safeTop = dp(9.5f)
    val safeWidth = dp(941f)
    val safeHeight = dp(521f)
    val brandTop = dp(16.5f)
    val brandWidth = dp(121f)
    val brandHeight = dp(35f)
    val cardLeft = dp(596.5f)
    val cardTop = dp(59.5f)
    val cardWidth = dp(295f)
    val cardHeight = dp(413.5f)
    val cardRadius = dp(20f)
    val footerLeft = dp(97f)
    val footerTop = dp(485f)
    val footerWidth = dp(765f)
    val footerHeight = dp(40.5f)
    val settingsVisualSize = dp(38f)
    val settingsHitSize = dp(48f)
    val settingsTop = dp(14.5f)
    val settingsEnd = dp(13.5f)
}

@Composable
internal fun DonationDisplayScreen(
    configuration: OperatorDonationConfiguration,
    qrState: QrCampaignUiState,
    customAssetVersion: Long,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = true,
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
        DonationBrandPill(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = metrics.brandTop)
                .size(metrics.brandWidth, metrics.brandHeight)
                .testTag(DONATION_DISPLAY_BRAND_TAG),
            scale = scale,
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
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = metrics.settingsTop, end = metrics.settingsEnd),
        )
    }
}

@Composable
private fun DonationBrandPill(
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val colors = NamazTvTheme.colors
    val shape = RoundedCornerShape((13f * scale).dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceStrong.copy(alpha = 0.64f))
            .border((0.55f * scale).dp, colors.accentOutline.copy(alpha = 0.58f), shape)
            .padding(horizontal = (8f * scale).dp),
        horizontalArrangement = Arrangement.spacedBy((5f * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DonationBrandMark(Modifier.size((18f * scale).dp))
        Text(
            text = appString(R.string.app_name),
            color = colors.textPrimary,
            fontSize = (13.5f * scale).sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
        )
    }
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
            .border(metrics.dp(0.65f), colors.accentOutline.copy(alpha = 0.78f), shape),
    ) {
        DonationSectionHeading(
            text = appString(R.string.donation_display_title),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = metrics.dp(17f))
                .width(metrics.dp(238f))
                .height(metrics.dp(24f))
                .testTag(DONATION_DISPLAY_TITLE_TAG),
            scale = metrics.scale,
            title = true,
        )
        Text(
            text = appString(R.string.donation_display_subtitle),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = metrics.dp(45f))
                .width(metrics.dp(260f)),
            color = colors.textPrimary.copy(alpha = 0.92f),
            fontSize = (11f * metrics.scale).sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DonationQrSurface(
            state = qrState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = metrics.dp(-3f), y = metrics.dp(65.5f))
                .size(metrics.dp(154f))
                .testTag(DONATION_DISPLAY_QR_TAG),
            scale = metrics.scale,
        )
        TvFadingDiamondDivider(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = metrics.dp(224f))
                .width(metrics.dp(260f))
                .height(metrics.dp(7f)),
            tint = colors.accentOutline,
        )
        DonationSectionHeading(
            text = appString(R.string.donation_transfer_details_title),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = metrics.dp(236f))
                .width(metrics.dp(170f))
                .height(metrics.dp(22f)),
            scale = metrics.scale,
            title = false,
        )
        DonationDetailsRows(
            configuration = configuration,
            modifier = Modifier
                .offset(x = metrics.dp(20f), y = metrics.dp(256f))
                .width(metrics.dp(255f))
                .height(metrics.dp(145f))
                .testTag(DONATION_DISPLAY_ROWS_TAG),
            scale = metrics.scale,
        )
    }
}

@Composable
private fun DonationSectionHeading(
    text: String,
    scale: Float,
    title: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        DonationHeadingFlourish(
            reverse = false,
            modifier = Modifier.width((35f * scale).dp).height((13f * scale).dp),
        )
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = (6f * scale).dp),
            color = NamazTvTheme.colors.accent,
            fontSize = ((if (title) 21.3f else 17.2f) * scale).sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
        DonationHeadingFlourish(
            reverse = true,
            modifier = Modifier.width((35f * scale).dp).height((13f * scale).dp),
        )
    }
}

@Composable
private fun DonationQrSurface(
    state: QrCampaignUiState,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val colors = NamazTvTheme.colors
    val outerShape = RoundedCornerShape((14f * scale).dp)
    val qrSize = (140f * scale).dp
    Box(
        modifier = modifier
            .clip(outerShape)
            .background(colors.surfaceStrong.copy(alpha = 0.88f))
            .border((0.65f * scale).dp, colors.accentOutline.copy(alpha = 0.92f), outerShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(qrSize)
                .clip(RoundedCornerShape((9f * scale).dp))
                .background(QR_WARM_WHITE),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = rememberQrBitmap(
                    matrix = state.qrCode,
                    outputSize = with(LocalDensity.current) { qrSize.roundToPx() },
                ),
                contentDescription = appString(R.string.qr_content_description, state.title),
                modifier = Modifier.fillMaxSize().testTag(QR_CODE_IMAGE_TAG),
                filterQuality = FilterQuality.None,
            )
            Box(
                modifier = Modifier
                    .size((44f * scale).dp)
                    .clip(RoundedCornerShape((10f * scale).dp))
                    .background(colors.backgroundBottom.copy(alpha = 0.98f))
                    .border(
                        (0.65f * scale).dp,
                        colors.accentOutline,
                        RoundedCornerShape((10f * scale).dp),
                    )
                    .testTag(QR_CENTER_BRAND_BADGE_TAG),
                contentAlignment = Alignment.Center,
            ) {
                DonationBrandMark(Modifier.fillMaxSize().padding((7f * scale).dp))
            }
        }
    }
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
        DonationDetailRow(DonationDetailIcon.PHONE, R.string.donation_phone_label, configuration.phone),
        DonationDetailRow(DonationDetailIcon.LINK, R.string.donation_collection_url_label, configuration.collectionUrl, true),
    )
    Column(modifier) {
        rows.forEachIndexed { index, row ->
            DonationDetailsRow(
                row = row,
                modifier = Modifier.width((255f * scale).dp).height((29f * scale).dp),
                scale = scale,
                showSeparator = index != rows.lastIndex,
            )
        }
    }
}

private data class DonationDetailRow(
    val icon: DonationDetailIcon,
    @StringRes val labelRes: Int,
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
            modifier = Modifier.fillMaxSize().padding(start = (5f * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DonationDetailGlyph(icon = row.icon, modifier = Modifier.size((17f * scale).dp))
            Text(
                text = appString(row.labelRes),
                modifier = Modifier.padding(start = (14f * scale).dp).width((92.5f * scale).dp),
                color = colors.textSecondary.copy(alpha = 0.86f),
                fontSize = (11f * scale).sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.value,
                modifier = Modifier.weight(1f),
                color = if (row.accentValue) colors.accent else colors.textPrimary,
                fontSize = (12f * scale).sp,
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
    metrics: DonationDisplayMetrics,
    modifier: Modifier = Modifier,
) {
    val colors = NamazTvTheme.colors
    val shape = RoundedCornerShape(metrics.dp(20f))
    Row(
        modifier = modifier
            .clip(shape)
            .background(DONATION_FOOTER_SURFACE)
            .border(metrics.dp(0.55f), colors.accentOutline.copy(alpha = 0.75f), shape)
            .padding(horizontal = metrics.dp(82f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        DonationMosqueGlyph(Modifier.size(metrics.dp(27f)))
        DonationFooterOrnament(
            modifier = Modifier
                .padding(start = metrics.dp(40f), end = metrics.dp(26f))
                .width(metrics.dp(16f))
                .height(metrics.dp(12f)),
        )
        Text(
            text = appString(R.string.donation_footer_thanks),
            modifier = Modifier.weight(1f),
            color = colors.textPrimary,
            fontSize = (13.4f * metrics.scale).sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DonationFooterOrnament(
            modifier = Modifier
                .padding(start = metrics.dp(26f), end = metrics.dp(40f))
                .width(metrics.dp(16f))
                .height(metrics.dp(12f)),
        )
        DonationMosqueGlyph(Modifier.size(metrics.dp(27f)))
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
        scale = ButtonDefaults.scale(focusedScale = 1f),
        modifier = modifier
            .size(metrics.settingsHitSize)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label }
            .testTag(DONATION_DISPLAY_SETTINGS_TAG),
    ) {
        val shape = RoundedCornerShape(metrics.dp(11f))
        Box(
            modifier = Modifier
                .size(metrics.settingsVisualSize)
                .clip(shape)
                .background(colors.surfaceStrong.copy(alpha = 0.68f))
                .border(
                    width = if (focused) metrics.dp(0.75f) else metrics.dp(0.55f),
                    color = if (focused) colors.focus.copy(alpha = 0.85f) else colors.accentOutline.copy(alpha = 0.58f),
                    shape = shape,
                )
                .testTag(DONATION_DISPLAY_SETTINGS_VISUAL_TAG),
            contentAlignment = Alignment.Center,
        ) {
            DonationSettingsGlyph(
                tint = colors.textPrimary,
                modifier = Modifier.size(metrics.dp(21f)),
            )
        }
    }
}

private const val REFERENCE_WIDTH_DP = 960f
private const val REFERENCE_HEIGHT_DP = 540f
private val DONATION_BACKGROUND_SCRIM = Color(0x3D061322)
private val DONATION_CARD_TOP = Color(0xEE243143)
private val DONATION_CARD_BOTTOM = Color(0xF21A2533)
private val DONATION_FOOTER_SURFACE = Color(0xE6182638)
private val QR_WARM_WHITE = Color(0xFFFFFDF8)
