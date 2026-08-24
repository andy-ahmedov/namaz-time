package com.example.namaztime.tv.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.example.namaztime.tv.R
import com.example.namaztime.tv.repository.OPERATOR_IQAMAH_PRAYER_IDS
import com.example.namaztime.tv.repository.OPERATOR_IQAMAH_OFFSET_RANGE
import com.example.namaztime.tv.repository.OperatorIqamahOffsets
import com.example.namaztime.tv.repository.OperatorQrConfiguration

const val SETTINGS_QR_URL_FIELD_TAG = "settings-qr-url"
const val SETTINGS_QR_TITLE_FIELD_TAG = "settings-qr-title"
const val SETTINGS_QR_MESSAGE_FIELD_TAG = "settings-qr-message"
const val SETTINGS_QR_SAVE_TAG = "settings-qr-save"
const val SETTINGS_IQAMAH_FIELD_TAG_PREFIX = "settings-iqamah-"
const val SETTINGS_IQAMAH_SAVE_TAG = "settings-iqamah-save"

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
                placeholder = "https://",
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
                onValueChange = { onConfigurationChange(configuration.copy(message = it.take(500))) },
                label = appString(R.string.qr_message_label),
                placeholder = appString(R.string.qr_message_hint),
                requester = messageRequester,
                previousRequester = titleRequester,
                nextRequester = saveRequester,
                modifier = Modifier.testTag(SETTINGS_QR_MESSAGE_FIELD_TAG),
                singleLine = false,
            )
        }
        campaignPreview?.let { preview ->
            QrCampaignPanel(
                state = preview,
                qrSize = if (compact) 82.dp else 112.dp,
                modifier = Modifier.width(if (compact) 150.dp else 190.dp),
                compact = true,
            )
        }
    }
}

@Composable
internal fun IqamahSettingsEditor(
    offsets: OperatorIqamahOffsets,
    onOffsetsChange: (OperatorIqamahOffsets) -> Unit,
    entryRequester: FocusRequester,
    saveRequester: FocusRequester,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val decrementRequesters = remember {
        OPERATOR_IQAMAH_PRAYER_IDS.associateWith { FocusRequester() }
    }
    val incrementRequesters = remember(entryRequester) {
        OPERATOR_IQAMAH_PRAYER_IDS.associateWith { FocusRequester() }.toMutableMap().apply {
            this[OPERATOR_IQAMAH_PRAYER_IDS.first()] = entryRequester
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp),
    ) {
        OPERATOR_IQAMAH_PRAYER_IDS.forEachIndexed { index, prayerId ->
            IqamahOffsetRow(
                prayerId = prayerId,
                value = offsets.forPrayer(prayerId),
                onValueChange = { value ->
                    onOffsetsChange(offsets.withPrayer(prayerId, value))
                },
                decrementRequester = decrementRequesters.getValue(prayerId),
                incrementRequester = incrementRequesters.getValue(prayerId),
                previousDecrementRequester = decrementRequesters[
                    OPERATOR_IQAMAH_PRAYER_IDS.getOrNull(index - 1)
                ],
                previousIncrementRequester = incrementRequesters[
                    OPERATOR_IQAMAH_PRAYER_IDS.getOrNull(index - 1)
                ],
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
    onValueChange: (Int?) -> Unit,
    decrementRequester: FocusRequester,
    incrementRequester: FocusRequester,
    previousDecrementRequester: FocusRequester?,
    previousIncrementRequester: FocusRequester?,
    nextDecrementRequester: FocusRequester,
    nextIncrementRequester: FocusRequester,
    compact: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$SETTINGS_IQAMAH_FIELD_TAG_PREFIX$prayerId"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = appString(prayerNameResource(prayerId)),
            modifier = Modifier.weight(1f),
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 17.sp else 21.sp,
        )
        IqamahOffsetButton(
            label = "−",
            enabled = value != null,
            onClick = {
                onValueChange(value?.minus(IQAMAH_OFFSET_STEP)?.takeIf { it >= 0 })
            },
            requester = decrementRequester,
            leftRequester = null,
            rightRequester = incrementRequester,
            upRequester = previousDecrementRequester,
            downRequester = nextDecrementRequester,
            modifier = Modifier.testTag("$SETTINGS_IQAMAH_FIELD_TAG_PREFIX${prayerId}-decrement"),
        )
        Text(
            text = value?.let { appString(R.string.iqamah_offset_minutes_value, it) }
                ?: appString(R.string.iqamah_use_schedule_value),
            modifier = Modifier.width(if (compact) 116.dp else 160.dp),
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 16.sp else 19.sp,
            fontFamily = FontFamily.Monospace,
        )
        IqamahOffsetButton(
            label = "+",
            enabled = value != OPERATOR_IQAMAH_OFFSET_RANGE.last,
            onClick = {
                val next = if (value == null) IQAMAH_OFFSET_STEP else value + IQAMAH_OFFSET_STEP
                onValueChange(next.coerceAtMost(OPERATOR_IQAMAH_OFFSET_RANGE.last))
            },
            requester = incrementRequester,
            leftRequester = decrementRequester,
            rightRequester = null,
            upRequester = previousIncrementRequester,
            downRequester = nextIncrementRequester,
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
            .width(52.dp)
            .height(40.dp)
            .focusRequester(requester)
            .focusProperties {
                leftRequester?.let { left = it }
                rightRequester?.let { right = it }
                upRequester?.let { up = it }
                down = downRequester
            },
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

private const val IQAMAH_OFFSET_STEP = 5

@Composable
private fun TvSettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    requester: FocusRequester,
    previousRequester: FocusRequester?,
    nextRequester: FocusRequester,
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
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        nextRequester.requestFocus()
                        true
                    }
                    Key.DirectionUp -> previousRequester?.let {
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
