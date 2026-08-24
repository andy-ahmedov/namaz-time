package com.example.namaztime.tv.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    Box(modifier = modifier.fillMaxSize().testTag(DONATION_DISPLAY_TAG)) {
        TvSafeFrame(testTag = DONATION_DISPLAY_SAFE_CONTENT_TAG) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = appString(R.string.donation_display_title),
                            modifier = Modifier.semantics { heading() },
                            color = NamazTvTheme.colors.accent,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = configuration.message,
                            color = NamazTvTheme.colors.textPrimary,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DonationSettingsButton(
                        requester = settingsRequester,
                        requestInitialFocus = requestInitialFocus,
                        onOpenSettings = onOpenSettings,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .border(
                                1.dp,
                                NamazTvTheme.colors.surfaceOutline,
                                RoundedCornerShape(24.dp),
                            )
                            .testTag(DONATION_DISPLAY_IMAGE_TAG),
                    )
                    QrCampaignPanel(
                        state = qrState,
                        qrSize = 154.dp,
                        compact = true,
                        modifier = Modifier.width(270.dp).fillMaxHeight(),
                    )
                    TvGlassPanel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag(DONATION_DISPLAY_DETAILS_TAG),
                        radius = 24.dp,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = appString(R.string.donation_transfer_details_title),
                                color = NamazTvTheme.colors.accent,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = configuration.transferDetails,
                                color = NamazTvTheme.colors.textPrimary,
                                fontSize = 22.sp,
                                lineHeight = 29.sp,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonationSettingsButton(
    requester: FocusRequester,
    requestInitialFocus: Boolean,
    onOpenSettings: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
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
            containerColor = NamazTvTheme.colors.surfaceStrong.copy(alpha = 0.62f),
            contentColor = NamazTvTheme.colors.textPrimary,
            focusedContainerColor = NamazTvTheme.colors.accent,
            focusedContentColor = NamazTvTheme.colors.backgroundBottom,
        ),
        modifier = Modifier
            .size(48.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label }
            .border(
                if (focused) 2.dp else 0.75.dp,
                if (focused) NamazTvTheme.colors.accent else NamazTvTheme.colors.surfaceOutline,
                RoundedCornerShape(14.dp),
            )
            .testTag(DONATION_DISPLAY_SETTINGS_TAG),
    ) {
        SettingsGlyph(
            tint = if (focused) NamazTvTheme.colors.backgroundBottom else NamazTvTheme.colors.textPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}
