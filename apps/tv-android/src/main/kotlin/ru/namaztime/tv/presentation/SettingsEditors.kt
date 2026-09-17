package ru.namaztime.tv.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import ru.namaztime.tv.R
import ru.namaztime.tv.repository.MAX_MOSQUE_DISPLAY_ADDRESS_LENGTH
import ru.namaztime.tv.repository.MAX_MOSQUE_DISPLAY_NAME_LENGTH
import ru.namaztime.tv.repository.MAX_DONATION_GRATITUDE_LENGTH
import ru.namaztime.tv.repository.OPERATOR_DHUHR_FIXED_TIME_RANGE
import ru.namaztime.tv.repository.OPERATOR_IQAMAH_PRAYER_IDS
import ru.namaztime.tv.repository.OPERATOR_IQAMAH_OFFSET_RANGE
import ru.namaztime.tv.repository.OperatorIqamahConfiguration
import ru.namaztime.tv.repository.CUSTOM_DONATION_IMAGE_STYLE_ID
import ru.namaztime.tv.repository.OperatorDonationConfiguration
import ru.namaztime.tv.repository.OperatorImageSlot
import ru.namaztime.tv.repository.OperatorMosquePresentationIdentity
import ru.namaztime.tv.repository.OperatorQrConfiguration
import ru.namaztime.tv.repository.OperatorQrTextFitPolicy

const val SETTINGS_QR_URL_FIELD_TAG = "settings-qr-url"
const val SETTINGS_MOSQUE_NAME_FIELD_TAG = "settings-mosque-display-name"
const val SETTINGS_MOSQUE_ADDRESS_FIELD_TAG = "settings-mosque-display-address"
const val SETTINGS_MOSQUE_IDENTITY_SAVE_TAG = "settings-mosque-identity-save"
const val SETTINGS_QR_TITLE_FIELD_TAG = "settings-qr-title"
const val SETTINGS_QR_MESSAGE_FIELD_TAG = "settings-qr-message"
const val SETTINGS_QR_SAVE_TAG = "settings-qr-save"
const val SETTINGS_IQAMAH_FIELD_TAG_PREFIX = "settings-iqamah-"
const val SETTINGS_IQAMAH_SAVE_TAG = "settings-iqamah-save"
const val SETTINGS_DONATION_URL_FIELD_TAG = "settings-donation-url"
const val SETTINGS_DONATION_RECIPIENT_FIELD_TAG = "settings-donation-recipient"
const val SETTINGS_DONATION_BANK_FIELD_TAG = "settings-donation-bank"
const val SETTINGS_DONATION_CARD_NUMBER_FIELD_TAG = "settings-donation-card-number"
const val SETTINGS_DONATION_PHONE_FIELD_TAG = "settings-donation-phone"
const val SETTINGS_DONATION_GRATITUDE_FIELD_TAG = "settings-donation-gratitude"
const val SETTINGS_DONATION_SELECTED_PREVIEW_TAG = "settings-donation-selected-preview"
const val SETTINGS_DONATION_FILMSTRIP_TAG = "settings-donation-filmstrip"
const val SETTINGS_DONATION_SAVE_TAG = "settings-donation-save"
const val SETTINGS_DONATION_PICKER_TAG = "settings-donation-picker"
const val SETTINGS_DONATION_MODE_TAG = "settings-donation-mode"
val IqamahScheduleLabelFullyVisibleKey =
    SemanticsPropertyKey<Boolean>("IqamahScheduleLabelFullyVisible")
private var SemanticsPropertyReceiver.iqamahScheduleLabelFullyVisible by
    IqamahScheduleLabelFullyVisibleKey

@Composable
internal fun MosqueIdentitySettingsEditor(
    identity: OperatorMosquePresentationIdentity,
    onIdentityChange: (OperatorMosquePresentationIdentity) -> Unit,
    fallbackIdentity: MosqueDisplayIdentity,
    canonicalLocality: String?,
    timezoneId: String,
    entryRequester: FocusRequester,
    saveRequester: FocusRequester,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val addressRequester = remember { FocusRequester() }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
    ) {
        TvSettingsTextField(
            value = identity.displayName,
            onValueChange = {
                onIdentityChange(
                    identity.copy(
                        displayName = it.takeCodePoints(MAX_MOSQUE_DISPLAY_NAME_LENGTH),
                    ),
                )
            },
            label = appString(R.string.mosque_display_name_label),
            placeholder = fallbackIdentity.name,
            requester = entryRequester,
            previousRequester = null,
            nextRequester = addressRequester,
            modifier = Modifier.testTag(SETTINGS_MOSQUE_NAME_FIELD_TAG),
        )
        TvSettingsTextField(
            value = identity.displayAddress,
            onValueChange = {
                onIdentityChange(
                    identity.copy(
                        displayAddress = it.takeCodePoints(MAX_MOSQUE_DISPLAY_ADDRESS_LENGTH),
                    ),
                )
            },
            label = appString(R.string.mosque_display_address_label),
            placeholder = fallbackIdentity.locality.orEmpty(),
            requester = addressRequester,
            previousRequester = entryRequester,
            nextRequester = saveRequester,
            modifier = Modifier.testTag(SETTINGS_MOSQUE_ADDRESS_FIELD_TAG),
        )
        Text(
            text = appString(R.string.mosque_identity_local_only_note),
            color = NamazTvTheme.colors.textSecondary,
            fontSize = if (compact) 14.sp else 16.sp,
            lineHeight = if (compact) 18.sp else 22.sp,
        )
        Text(
            text = appString(
                R.string.mosque_canonical_location_and_timezone,
                canonicalLocality ?: appString(R.string.value_not_available),
                timezoneId,
            ),
            color = NamazTvTheme.colors.textSecondary,
            fontSize = if (compact) 14.sp else 16.sp,
            lineHeight = if (compact) 18.sp else 22.sp,
            maxLines = 2,
        )
    }
}

@Composable
internal fun DonationSettingsEditor(
    configuration: OperatorDonationConfiguration,
    onConfigurationChange: (OperatorDonationConfiguration) -> Unit,
    entryRequester: FocusRequester,
    saveRequester: FocusRequester,
    customAssetVersion: Long,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val recipientRequester = remember { FocusRequester() }
    val bankRequester = remember { FocusRequester() }
    val cardNumberRequester = remember { FocusRequester() }
    val phoneRequester = remember { FocusRequester() }
    val gratitudeRequester = remember { FocusRequester() }
    val choices = remember { DonationImageStyle.entries.map { it.id } }
    val imageRequesters = remember { choices.associateWith { FocusRequester() } }
    val listState = rememberLazyListState()
    var focusedIndex by remember {
        mutableStateOf(choices.indexOf(configuration.imageStyleId).coerceAtLeast(0))
    }
    val customImage = rememberOperatorImageBitmap(
        slot = OperatorImageSlot.DONATION,
        assetVersion = customAssetVersion,
    )

    LaunchedEffect(focusedIndex) {
        listState.animateScrollToItem(focusedIndex)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1.15f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
        ) {
            TvSettingsTextField(
                value = configuration.httpsUrl,
                onValueChange = {
                    onConfigurationChange(configuration.copy(httpsUrl = it.take(2_048)))
                },
                label = appString(R.string.donation_qr_link_label),
                placeholder = ru.namaztime.tv.repository.DEFAULT_QR_HTTPS_URL,
                requester = entryRequester,
                previousRequester = null,
                nextRequester = recipientRequester,
                modifier = Modifier.testTag(SETTINGS_DONATION_URL_FIELD_TAG),
                keyboardType = KeyboardType.Uri,
                compact = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TvSettingsTextField(
                    value = configuration.recipient,
                    onValueChange = {
                        onConfigurationChange(configuration.copy(recipient = it.take(160)))
                    },
                    label = appString(R.string.donation_recipient_label),
                    placeholder = appString(R.string.donation_recipient_hint),
                    requester = recipientRequester,
                    previousRequester = entryRequester,
                    nextRequester = cardNumberRequester,
                    rightRequester = bankRequester,
                    modifier = Modifier.weight(1f).testTag(SETTINGS_DONATION_RECIPIENT_FIELD_TAG),
                    compact = true,
                )
                TvSettingsTextField(
                    value = configuration.bank,
                    onValueChange = {
                        onConfigurationChange(configuration.copy(bank = it.take(160)))
                    },
                    label = appString(R.string.donation_bank_label),
                    placeholder = appString(R.string.donation_bank_hint),
                    requester = bankRequester,
                    previousRequester = entryRequester,
                    nextRequester = phoneRequester,
                    leftRequester = recipientRequester,
                    modifier = Modifier.weight(1f).testTag(SETTINGS_DONATION_BANK_FIELD_TAG),
                    compact = true,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TvSettingsTextField(
                    value = configuration.cardNumber,
                    onValueChange = {
                        onConfigurationChange(configuration.copy(cardNumber = it.take(160)))
                    },
                    label = appString(R.string.donation_card_number_label),
                    placeholder = appString(R.string.donation_card_number_hint),
                    requester = cardNumberRequester,
                    previousRequester = recipientRequester,
                    nextRequester = gratitudeRequester,
                    rightRequester = phoneRequester,
                    modifier = Modifier.weight(1f).testTag(SETTINGS_DONATION_CARD_NUMBER_FIELD_TAG),
                    keyboardType = KeyboardType.Number,
                    compact = true,
                    monospaced = true,
                )
                TvSettingsTextField(
                    value = configuration.phone,
                    onValueChange = {
                        onConfigurationChange(configuration.copy(phone = it.take(160)))
                    },
                    label = appString(R.string.donation_phone_label),
                    placeholder = appString(R.string.donation_phone_hint),
                    requester = phoneRequester,
                    previousRequester = bankRequester,
                    nextRequester = gratitudeRequester,
                    leftRequester = cardNumberRequester,
                    modifier = Modifier.weight(1f).testTag(SETTINGS_DONATION_PHONE_FIELD_TAG),
                    keyboardType = KeyboardType.Phone,
                    compact = true,
                    monospaced = true,
                )
            }
            TvSettingsTextField(
                value = configuration.gratitudeMessage,
                onValueChange = {
                    onConfigurationChange(
                        configuration.copy(
                            gratitudeMessage = it.takeCodePoints(MAX_DONATION_GRATITUDE_LENGTH),
                        ),
                    )
                },
                label = appString(R.string.donation_gratitude_label),
                placeholder = appString(R.string.donation_gratitude_hint),
                requester = gratitudeRequester,
                previousRequester = cardNumberRequester,
                nextRequester = imageRequesters.getValue(choices.first()),
                modifier = Modifier.testTag(SETTINGS_DONATION_GRATITUDE_FIELD_TAG),
                compact = true,
            )
        }
        Column(
            modifier = Modifier.weight(0.85f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp),
        ) {
            Text(
                text = appString(R.string.donation_image_title),
                color = NamazTvTheme.colors.textSecondary,
                fontSize = if (compact) 14.sp else 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 95.dp else 124.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(NamazTvTheme.colors.backgroundBottom)
                    .border(
                        1.dp,
                        NamazTvTheme.colors.surfaceOutline,
                        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                    )
                    .testTag(SETTINGS_DONATION_SELECTED_PREVIEW_TAG),
            ) {
                val selectedStyle = DonationImageStyle.entries.firstOrNull {
                    it.id == configuration.imageStyleId
                }
                if (configuration.imageStyleId == CUSTOM_DONATION_IMAGE_STYLE_ID && customImage != null) {
                    Image(
                        painter = BitmapPainter(customImage),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painter = painterResource(
                            (selectedStyle ?: DonationImageStyle.MOSQUE).drawableRes,
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(NamazTvTheme.colors.backgroundTop.copy(alpha = 0.18f)),
                )
            }
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 52.dp else 64.dp)
                    .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                    .background(NamazTvTheme.colors.surfaceTop.copy(alpha = 0.72f))
                    .testTag(SETTINGS_DONATION_FILMSTRIP_TAG),
                contentPadding = PaddingValues(horizontal = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(count = choices.size, key = { choices[it] }) {
                    val styleId = choices[it]
                    val style = DonationImageStyle.entries.first { style -> style.id == styleId }
                    val selected = configuration.imageStyleId == styleId
                    val focused = focusedIndex == it
                    Button(
                        onClick = {
                            onConfigurationChange(configuration.copy(imageStyleId = styleId))
                        },
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            focusedContentColor = Color.White,
                        ),
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        modifier = Modifier
                            .width(if (compact) 76.dp else 96.dp)
                            .height(if (compact) 42.dp else 54.dp)
                            .testTag("$SETTINGS_DONATION_IMAGE_TAG_PREFIX$styleId")
                            .semantics { this.selected = selected }
                            .focusRequester(imageRequesters.getValue(styleId))
                            .focusProperties {
                                choices.getOrNull(it - 1)?.let { left = imageRequesters.getValue(it) }
                                choices.getOrNull(it + 1)?.let { right = imageRequesters.getValue(it) }
                                up = gratitudeRequester
                                down = saveRequester
                            }
                            .onFocusChanged { state ->
                                if (state.isFocused) focusedIndex = it
                            }
                            .border(
                                if (selected || focused) 3.dp else 1.dp,
                                if (selected || focused) {
                                    NamazTvTheme.colors.accent
                                } else {
                                    NamazTvTheme.colors.surfaceOutline
                                },
                                RoundedCornerShape(8.dp),
                            ),
                    ) {
                        Image(
                            painter = painterResource(style.drawableRes),
                            contentDescription = appString(style.labelRes),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun QrSettingsEditor(
    configuration: OperatorQrConfiguration,
    onConfigurationChange: (OperatorQrConfiguration) -> Unit,
    entryRequester: FocusRequester,
    saveRequester: FocusRequester,
    modifier: Modifier = Modifier,
    campaignPreview: QrCampaignUiState? = null,
    compact: Boolean = false,
) {
    val titleRequester = remember { FocusRequester() }
    val messageRequester = remember { FocusRequester() }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
        ) {
            TvSettingsTextField(
                value = configuration.httpsUrl,
                onValueChange = { onConfigurationChange(configuration.copy(httpsUrl = it.take(2_048))) },
                label = appString(R.string.qr_link_label),
                placeholder = ru.namaztime.tv.repository.DEFAULT_QR_HTTPS_URL,
                requester = entryRequester,
                previousRequester = null,
                nextRequester = titleRequester,
                modifier = Modifier.testTag(SETTINGS_QR_URL_FIELD_TAG),
                keyboardType = KeyboardType.Uri,
            )
            TvSettingsTextField(
                value = configuration.title,
                onValueChange = { onConfigurationChange(configuration.copy(title = it.take(160))) },
                label = appString(R.string.qr_purpose_label),
                placeholder = appString(R.string.qr_purpose_hint),
                requester = titleRequester,
                previousRequester = entryRequester,
                nextRequester = messageRequester,
                modifier = Modifier.testTag(SETTINGS_QR_TITLE_FIELD_TAG),
            )
            TvSettingsTextField(
                value = configuration.message,
                onValueChange = {
                    onConfigurationChange(
                        configuration.copy(
                            message = it.takeCodePoints(
                                OperatorQrTextFitPolicy.MAX_INPUT_CODE_POINTS,
                            ),
                        ),
                    )
                },
                label = appString(R.string.qr_message_label),
                placeholder = appString(R.string.qr_message_hint),
                requester = messageRequester,
                previousRequester = titleRequester,
                nextRequester = saveRequester,
                modifier = Modifier.testTag(SETTINGS_QR_MESSAGE_FIELD_TAG),
                singleLine = false,
            )
            if (configuration.message.isNotBlank() &&
                OperatorQrTextFitPolicy.fit(configuration.message) == null
            ) {
                Text(
                    text = appString(R.string.qr_message_too_long),
                    color = NamazTvTheme.colors.warning,
                    fontSize = if (compact) 13.sp else 15.sp,
                )
            }
        }
        campaignPreview?.let { preview ->
            QrCampaignPanel(
                state = preview,
                qrSize = if (compact) 132.dp else 174.dp,
                modifier = Modifier.width(if (compact) 180.dp else 218.dp),
                compact = true,
            )
        }
    }
}

@Composable
internal fun IqamahSettingsEditor(
    configuration: OperatorIqamahConfiguration,
    onConfigurationChange: (OperatorIqamahConfiguration) -> Unit,
    approvedDhuhrTimeMinutes: Int?,
    showIqamahOnSchedule: Boolean = true,
    onShowIqamahChanged: ((Boolean) -> Unit)? = null,
    entryRequester: FocusRequester,
    saveRequester: FocusRequester,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val visibilityRequester = remember { FocusRequester() }
    val resetRequesters = remember(entryRequester) {
        OPERATOR_IQAMAH_PRAYER_IDS.associateWith { FocusRequester() }.toMutableMap().apply {
            this[OPERATOR_IQAMAH_PRAYER_IDS.first()] = entryRequester
        }
    }
    val decrementRequesters = remember {
        OPERATOR_IQAMAH_PRAYER_IDS.associateWith { FocusRequester() }
    }
    val incrementRequesters = remember {
        OPERATOR_IQAMAH_PRAYER_IDS.associateWith { FocusRequester() }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp),
    ) {
        if (onShowIqamahChanged != null) {
            Button(
                onClick = { onShowIqamahChanged(!showIqamahOnSchedule) },
                scale = ButtonDefaults.scale(focusedScale = 1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.fillMaxWidth().height(if (compact) 36.dp else 42.dp)
                    .testTag("settings-show-iqamah")
                    .focusRequester(visibilityRequester)
                    .focusProperties { down = entryRequester }
                    .semantics {
                        role = androidx.compose.ui.semantics.Role.Switch
                        toggleableState = androidx.compose.ui.state.ToggleableState(showIqamahOnSchedule)
                    },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(appString(R.string.show_iqamah_on_schedule), Modifier.weight(1f), fontSize = if (compact) 14.sp else 18.sp)
                    val trackColor = if (showIqamahOnSchedule) NamazTvTheme.colors.accent
                        else NamazTvTheme.colors.textSecondary
                    androidx.compose.foundation.Canvas(Modifier.width(34.dp).height(20.dp)) {
                        drawRoundRect(trackColor.copy(alpha = 0.35f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                        drawCircle(trackColor, radius = size.height * 0.36f,
                            center = androidx.compose.ui.geometry.Offset(
                                if (showIqamahOnSchedule) size.width - size.height / 2 else size.height / 2,
                                size.height / 2,
                            ))
                    }
                }
            }
        }
        OPERATOR_IQAMAH_PRAYER_IDS.forEachIndexed { index, prayerId ->
            IqamahOffsetRow(
                prayerId = prayerId,
                value = configuration.editorValueForPrayer(prayerId),
                approvedDhuhrTimeMinutes = approvedDhuhrTimeMinutes,
                onValueChange = { value ->
                    onConfigurationChange(configuration.withEditorValue(prayerId, value))
                },
                resetRequester = resetRequesters.getValue(prayerId),
                decrementRequester = decrementRequesters.getValue(prayerId),
                incrementRequester = incrementRequesters.getValue(prayerId),
                previousResetRequester = resetRequesters[
                    OPERATOR_IQAMAH_PRAYER_IDS.getOrNull(index - 1)
                ] ?: visibilityRequester.takeIf { onShowIqamahChanged != null },
                previousDecrementRequester = decrementRequesters[
                    OPERATOR_IQAMAH_PRAYER_IDS.getOrNull(index - 1)
                ] ?: visibilityRequester.takeIf { onShowIqamahChanged != null },
                previousIncrementRequester = incrementRequesters[
                    OPERATOR_IQAMAH_PRAYER_IDS.getOrNull(index - 1)
                ] ?: visibilityRequester.takeIf { onShowIqamahChanged != null },
                nextResetRequester = resetRequesters[
                    OPERATOR_IQAMAH_PRAYER_IDS.getOrNull(index + 1)
                ] ?: saveRequester,
                nextDecrementRequester = decrementRequesters[
                    OPERATOR_IQAMAH_PRAYER_IDS.getOrNull(index + 1)
                ] ?: saveRequester,
                nextIncrementRequester = incrementRequesters[
                    OPERATOR_IQAMAH_PRAYER_IDS.getOrNull(index + 1)
                ] ?: saveRequester,
                compact = compact,
            )
        }
        if (!compact) {
            Text(
                text = appString(R.string.iqamah_local_override_note),
                color = NamazTvTheme.colors.textSecondary,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun IqamahOffsetRow(
    prayerId: String,
    value: Int?,
    approvedDhuhrTimeMinutes: Int?,
    onValueChange: (Int?) -> Unit,
    resetRequester: FocusRequester,
    decrementRequester: FocusRequester,
    incrementRequester: FocusRequester,
    previousResetRequester: FocusRequester?,
    previousDecrementRequester: FocusRequester?,
    previousIncrementRequester: FocusRequester?,
    nextResetRequester: FocusRequester,
    nextDecrementRequester: FocusRequester,
    nextIncrementRequester: FocusRequester,
    compact: Boolean,
) {
    val isDhuhr = prayerId == "dhuhr"
    val range = if (isDhuhr) OPERATOR_DHUHR_FIXED_TIME_RANGE else OPERATOR_IQAMAH_OFFSET_RANGE
    val baseValue = if (isDhuhr) approvedDhuhrTimeMinutes else 0
    val effectiveValue = value ?: baseValue
    val scheduleLabel = if (isDhuhr && value == null && effectiveValue != null) {
        appString(R.string.iqamah_use_schedule_time_value, effectiveValue.asClockText())
    } else {
        appString(R.string.iqamah_use_schedule_value)
    }
    var scheduleLabelFullyVisible by remember(scheduleLabel) { mutableStateOf(true) }
    fun updateBy(delta: Int) {
        val current = effectiveValue ?: return
        val updated = (current + delta).coerceIn(range)
        onValueChange(updated.takeUnless { isDhuhr && it == approvedDhuhrTimeMinutes })
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$SETTINGS_IQAMAH_FIELD_TAG_PREFIX$prayerId"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
    ) {
        Text(
            text = appString(prayerNameResource(prayerId)),
            modifier = Modifier.width(if (compact) 64.dp else 90.dp),
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 16.sp else 21.sp,
        )
        Button(
            onClick = { onValueChange(null) },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            colors = ButtonDefaults.colors(
                containerColor = if (value == null) {
                    NamazTvTheme.colors.accentSoft
                } else {
                    NamazTvTheme.colors.surfaceStrong.copy(alpha = 0.72f)
                },
                contentColor = if (value == null) {
                    NamazTvTheme.colors.accent
                } else {
                    NamazTvTheme.colors.textPrimary
                },
                focusedContainerColor = NamazTvTheme.colors.accent,
                focusedContentColor = NamazTvTheme.colors.backgroundBottom,
            ),
            modifier = Modifier
                .width(if (compact) 176.dp else 220.dp)
                .height(if (compact) 36.dp else 42.dp)
                .testTag("$SETTINGS_IQAMAH_FIELD_TAG_PREFIX${prayerId}-reset")
                .semantics { selected = value == null }
                .focusRequester(resetRequester)
                .focusProperties {
                    right = decrementRequester
                    previousResetRequester?.let { up = it }
                    down = nextResetRequester
                },
        ) {
            Text(
                text = scheduleLabel,
                modifier = Modifier
                    .testTag("$SETTINGS_IQAMAH_FIELD_TAG_PREFIX${prayerId}-schedule-label")
                    .semantics {
                        iqamahScheduleLabelFullyVisible = scheduleLabelFullyVisible
                    },
                fontSize = if (compact) 13.sp else 16.sp,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { result ->
                    scheduleLabelFullyVisible = !result.hasVisualOverflow && result.lineCount == 1
                },
            )
        }
        IqamahOffsetButton(
            label = "−",
            enabled = effectiveValue != null && effectiveValue > range.first,
            onClick = { updateBy(-IQAMAH_OFFSET_STEP) },
            requester = decrementRequester,
            leftRequester = resetRequester,
            rightRequester = incrementRequester,
            upRequester = previousDecrementRequester,
            downRequester = nextDecrementRequester,
            compact = compact,
            modifier = Modifier.testTag("$SETTINGS_IQAMAH_FIELD_TAG_PREFIX${prayerId}-decrement"),
        )
        Text(
            text = when {
                isDhuhr && effectiveValue != null -> effectiveValue.asClockText()
                value != null -> appString(R.string.iqamah_offset_minutes_value, value)
                else -> "—"
            },
            modifier = Modifier
                .width(if (compact) 60.dp else 90.dp)
                .testTag("$SETTINGS_IQAMAH_FIELD_TAG_PREFIX${prayerId}-value"),
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 14.sp else 19.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        IqamahOffsetButton(
            label = "+",
            enabled = effectiveValue == null || effectiveValue < range.last,
            onClick = {
                if (effectiveValue == null) {
                    onValueChange(IQAMAH_OFFSET_STEP)
                } else {
                    updateBy(IQAMAH_OFFSET_STEP)
                }
            },
            requester = incrementRequester,
            leftRequester = decrementRequester,
            rightRequester = null,
            upRequester = previousIncrementRequester,
            downRequester = nextIncrementRequester,
            compact = compact,
            modifier = Modifier.testTag("$SETTINGS_IQAMAH_FIELD_TAG_PREFIX${prayerId}-increment"),
        )
    }
}

@Composable
private fun IqamahOffsetButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    requester: FocusRequester,
    leftRequester: FocusRequester?,
    rightRequester: FocusRequester?,
    upRequester: FocusRequester?,
    downRequester: FocusRequester,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.colors(
            containerColor = NamazTvTheme.colors.surfaceStrong.copy(alpha = 0.72f),
            contentColor = NamazTvTheme.colors.textPrimary,
            focusedContainerColor = NamazTvTheme.colors.accent,
            focusedContentColor = NamazTvTheme.colors.backgroundBottom,
        ),
        modifier = modifier
            .width(if (compact) 38.dp else 52.dp)
            .height(if (compact) 36.dp else 40.dp)
            .focusRequester(requester)
            .focusProperties {
                leftRequester?.let { left = it }
                rightRequester?.let { right = it }
                upRequester?.let { up = it }
                down = downRequester
            },
    ) {
        Text(label, fontSize = if (compact) 19.sp else 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Int.asClockText(): String = "%02d:%02d".format(this / 60, this % 60)

private fun String.takeCodePoints(maximum: Int): String {
    val count = codePointCount(0, length)
    if (count <= maximum) return this
    return substring(0, offsetByCodePoints(0, maximum))
}

private const val IQAMAH_OFFSET_STEP = 1

@Composable
private fun TvSettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    requester: FocusRequester,
    previousRequester: FocusRequester?,
    nextRequester: FocusRequester,
    leftRequester: FocusRequester? = null,
    rightRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    compact: Boolean = false,
    monospaced: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = NamazTvTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(
                when {
                    compact -> 38.dp
                    singleLine -> 48.dp
                    else -> 76.dp
                },
            )
            .focusRequester(requester)
            .focusProperties {
                previousRequester?.let { up = it }
                down = nextRequester
                leftRequester?.let { left = it }
                rightRequester?.let { right = it }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown, Key.Tab -> {
                        nextRequester.requestFocus()
                        true
                    }
                    Key.DirectionUp -> previousRequester?.let {
                        it.requestFocus()
                        true
                    } ?: false
                    Key.DirectionLeft -> leftRequester?.let {
                        it.requestFocus()
                        true
                    } ?: false
                    Key.DirectionRight -> rightRequester?.let {
                        it.requestFocus()
                        true
                    } ?: false
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .background(colors.surfaceStrong.copy(alpha = 0.62f), RoundedCornerShape(12.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.focus else colors.surfaceOutline,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = if (compact) 2.dp else 7.dp),
        textStyle = TextStyle(
            color = colors.textPrimary,
            fontSize = if (compact) 16.sp else 19.sp,
            fontFamily = if (monospaced) {
                FontFamily.Monospace
            } else {
                FontFamily.SansSerif
            },
        ),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Done,
        ),
        singleLine = singleLine,
        decorationBox = { innerTextField ->
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = label,
                    color = if (focused) colors.accent else colors.textSecondary,
                    fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.textSecondary.copy(alpha = 0.64f),
                            fontSize = if (compact) 16.sp else 18.sp,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

private fun prayerNameResource(prayerId: String): Int = when (prayerId) {
    "fajr" -> R.string.prayer_fajr
    "dhuhr" -> R.string.prayer_dhuhr
    "asr" -> R.string.prayer_asr
    "maghrib" -> R.string.prayer_maghrib
    "isha" -> R.string.prayer_isha
    else -> error("unsupported prayer")
}
