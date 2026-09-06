package ru.namaztime.tv.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ru.namaztime.tv.R

const val COMPACT_RIGHT_RAIL_TAG = "compact-right-rail"

/** Geometry only: consumes the very same immutable projection as STANDARD. */
@Composable
internal fun CompactPrayerDisplay(
    state: PrayerDisplayUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    retentionOffset: DpOffset = DpOffset.Zero,
    requestInitialFocus: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    BoxWithConstraints(modifier.fillMaxSize().testTag(MAIN_PRAYER_DISPLAY_TAG)) {
        val scale = maxHeight.value / 540f
        val safe = TvSafeFrameInsets.forSize(maxWidth, maxHeight)
        // A full shift budget plus 2 dp protects the midpoint even at the leftmost phase.
        val left = maxWidth / 2 + SCREEN_RETENTION_SHIFT_BUDGET + 2.dp
        val top = safe.vertical + SCREEN_RETENTION_SHIFT_BUDGET
        val railWidth = maxWidth - left - safe.horizontal - SCREEN_RETENTION_SHIFT_BUDGET
        val railHeight = maxHeight - top * 2
        val metrics = MainDisplayMetrics.forHeight(maxHeight).copy(
            gridHorizontalPadding = (10 * scale).dp,
            gridVerticalPadding = (6 * scale).dp,
            prayerRowSize = (20 * scale).sp,
            prayerIconSize = (22 * scale).dp,
            gridHeaderSize = (12 * scale).sp,
        )
        Column(
            Modifier.offset(left + retentionOffset.x, top + retentionOffset.y)
                .width(railWidth).height(railHeight).testTag(COMPACT_RIGHT_RAIL_TAG),
            verticalArrangement = Arrangement.spacedBy((8 * scale).dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height((50 * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
            ) {
                Column(Modifier.weight(1f)) {
                    CompactText(state.mosqueName, MOSQUE_NAME_TEST_TAG, 21 * scale)
                    state.location?.let { CompactText(it, MOSQUE_LOCATION_ORNAMENT_TAG, 14 * scale) }
                }
                SettingsButton(metrics, focusRequester, requestInitialFocus, onOpenSettings)
            }
            Row(
                Modifier.fillMaxWidth().height((192 * scale).dp),
                horizontalArrangement = Arrangement.spacedBy((10 * scale).dp),
            ) {
                state.campaign?.let { campaign ->
                    Column(
                        Modifier.width((168 * scale).dp).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ReferenceQrCode(campaign, (152 * scale).dp, framePadding = (8 * scale).dp)
                        CompactText(campaign.title, QR_CAMPAIGN_TITLE_TAG, 12 * scale)
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                    TvGlassPanel(Modifier.fillMaxWidth().weight(1.15f).testTag(NEXT_EVENT_CARD_TAG), radius = (16 * scale).dp) {
                        Column(Modifier.fillMaxSize().padding(horizontal = (10 * scale).dp, vertical = (6 * scale).dp), verticalArrangement = Arrangement.Center) {
                            CompactText(appString(R.string.next_prayer), NEXT_EVENT_LABEL_TAG, 12 * scale)
                            CompactText(state.nextPrayerLabel, NEXT_EVENT_NAME_TAG, 23 * scale)
                            CompactText(state.countdown, COUNTDOWN_TEST_TAG, 32 * scale)
                        }
                    }
                    TvGlassPanel(Modifier.fillMaxWidth().weight(1f).testTag(LOCAL_CLOCK_CARD_TAG), radius = (16 * scale).dp) {
                        Column(Modifier.fillMaxSize().padding(horizontal = (10 * scale).dp), verticalArrangement = Arrangement.Center) {
                            CompactText(state.dateLabel, DATE_LABEL_TAG, 14 * scale)
                            CompactText(state.weekdayLabel, WEEKDAY_LABEL_TAG, 12 * scale)
                            CompactText(state.mosqueLocalTime, LOCAL_CLOCK_VALUE_TAG, 29 * scale)
                        }
                    }
                }
            }
            PrayerListCard(state, metrics, Modifier.fillMaxWidth().weight(1f))
            if (state.sourceRequiresAttention || state.supportCode != null) {
                CompactText(
                    state.supportCode?.let { appString(R.string.support_code, it) } ?: state.sourceLabel,
                    "compact-source-status", 10 * scale,
                )
            }
        }
    }
}

@Composable
private fun CompactText(value: String, tag: String, size: Float) {
    Text(
        value,
        modifier = Modifier.testTag(tag),
        color = NamazTvTheme.colors.textPrimary,
        fontSize = size.sp,
        lineHeight = (size * 1.15f).sp,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
