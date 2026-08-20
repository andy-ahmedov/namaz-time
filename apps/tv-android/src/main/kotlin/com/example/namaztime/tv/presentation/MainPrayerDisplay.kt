package com.example.namaztime.tv.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.example.namaztime.tv.R
import com.example.namaztime.tv.domain.PrayerEventKind
import com.example.namaztime.tv.domain.PrayerTimeResolution
import com.example.namaztime.tv.repository.LocalPrayerDay
import com.example.namaztime.tv.repository.LocalPrayerSchedule
import java.time.format.DateTimeFormatter
import java.util.Locale

const val MAIN_PRAYER_DISPLAY_TAG = "main-prayer-display"
const val MAIN_DISPLAY_SAFE_CONTENT_TAG = "main-display-safe-content"
const val MAIN_DISPLAY_SETTINGS_TAG = "main-display-settings"
const val NEXT_EVENT_CARD_TAG = "next-event-card"
const val LOCAL_CLOCK_CARD_TAG = "local-clock-card"
const val PRAYER_LIST_CARD_TAG = "prayer-list-card"
const val IQAMAH_STRIP_TAG = "iqamah-strip"
const val PRAYER_ROW_TEST_TAG_PREFIX = "prayer-row-"
const val MOSQUE_NAME_TEST_TAG = "mosque-name"
const val COUNTDOWN_TEST_TAG = "next-prayer-countdown"
const val JUMUAH_SESSION_TEST_TAG_PREFIX = "jumuah-session-"
const val BRAND_PILL_TEST_TAG = "brand-pill"
const val PRAYER_TIME_AREA_TEST_TAG_PREFIX = "prayer-time-area-"
const val SUNRISE_CENTERED_TIME_TEST_TAG = "sunrise-centered-time"

internal enum class IqamahPresentation {
    MISSING,
    NOT_APPLICABLE,
}

internal data class PrayerDisplayRow(
    val id: String,
    val label: String,
    val adhan: String,
    val iqamah: String? = null,
    val iqamahPresentation: IqamahPresentation = IqamahPresentation.MISSING,
    val isNextEvent: Boolean = false,
)

internal data class IqamahSummaryUiState(
    val label: String,
    val time: String,
    val countdownLabel: String?,
)

internal data class PrayerDisplayUiState(
    val mosqueName: String,
    val location: String?,
    val dateLabel: String,
    val weekdayLabel: String,
    val mosqueLocalTime: String,
    val nextPrayerLabel: String,
    val nextEventKindLabel: String,
    val nextEventTime: String,
    val countdown: String,
    val iqamahSummary: IqamahSummaryUiState?,
    val sourceLabel: String,
    val sourceDescription: String,
    val supportCode: String? = null,
    val jumuahSessions: List<JumuahDisplaySession> = emptyList(),
    val campaign: QrCampaignUiState? = null,
    val rows: List<PrayerDisplayRow>,
)

internal data class JumuahDisplaySession(val id: String, val text: String)

@Composable
internal fun MainPrayerDisplay(
    state: PrayerDisplayUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = true,
    retentionOffset: DpOffset = DpOffset.Zero,
) {
    val settingsFocusRequester = remember { FocusRequester() }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().testTag(MAIN_PRAYER_DISPLAY_TAG),
    ) {
        val metrics = MainDisplayMetrics.forHeight(maxHeight)
        TvSafeFrame(
            testTag = MAIN_DISPLAY_SAFE_CONTENT_TAG,
            contentOffset = retentionOffset,
            contentShiftBudget = SCREEN_RETENTION_SHIFT_BUDGET,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DisplayHeader(
                    state,
                    metrics,
                    settingsFocusRequester,
                    requestInitialFocus,
                    onOpenSettings,
                )
                Spacer(Modifier.height(metrics.sectionGap))
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                ) {
                    Column(
                        modifier = Modifier.weight(metrics.leftColumnWeight).fillMaxHeight(),
                    ) {
                        NextEventCard(
                            state,
                            metrics,
                            Modifier.weight(1f).fillMaxWidth(),
                        )
                        Spacer(Modifier.height(metrics.sectionGap))
                        LocalClockCard(
                            state,
                            metrics,
                            Modifier.fillMaxWidth().height(metrics.clockCardHeight),
                        )
                    }
                    PrayerListCard(
                        state,
                        metrics,
                        Modifier.weight(1f).fillMaxHeight(),
                    )
                    state.campaign?.let { campaign ->
                        QrCampaignPanel(
                            state = campaign,
                            qrSize = metrics.qrSize,
                            compact = true,
                            modifier = Modifier.width(metrics.campaignPanelWidth),
                        )
                    }
                }
                Spacer(Modifier.height(metrics.sectionGap))
                IqamahStatusStrip(
                    state,
                    metrics,
                    Modifier.fillMaxWidth().height(metrics.iqamahStripHeight),
                )
            }
        }
    }
}

@Composable
private fun DisplayHeader(
    state: PrayerDisplayUiState,
    metrics: MainDisplayMetrics,
    settingsFocusRequester: FocusRequester,
    requestInitialFocus: Boolean,
    onOpenSettings: () -> Unit,
) {
    val colors = NamazTvTheme.colors
    LaunchedEffect(requestInitialFocus, settingsFocusRequester) {
        if (requestInitialFocus) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { settingsFocusRequester.requestFocus() }
        }
    }
    Box(Modifier.fillMaxWidth().height(metrics.headerHeight)) {
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.68f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .testTag(BRAND_PILL_TEST_TAG)
                    .background(colors.surfaceStrong.copy(alpha = 0.72f), RoundedCornerShape(50))
                    .border(1.dp, colors.accentOutline, RoundedCornerShape(50))
                    .padding(horizontal = metrics.inlineGap * 1.5f, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.inlineGap / 2),
            ) {
                BrandMark(Modifier.width(metrics.brandIconSize).height(metrics.brandIconSize))
                Text(
                    text = "NamazTime",
                    color = colors.accent,
                    fontSize = metrics.brandSize,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = state.mosqueName,
                modifier = Modifier.testTag(MOSQUE_NAME_TEST_TAG).semantics { heading() },
                color = colors.textPrimary,
                fontSize = metrics.mosqueNameSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            state.location?.let { location ->
                Text(
                    text = location,
                    color = colors.textSecondary,
                    fontSize = metrics.secondarySize,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        var focused by remember { mutableStateOf(false) }
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.colors(
                containerColor = colors.surfaceStrong.copy(alpha = 0.72f),
                contentColor = colors.textPrimary,
                focusedContainerColor = colors.accent,
                focusedContentColor = colors.backgroundBottom,
            ),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(metrics.settingsHeight)
                .testTag(MAIN_DISPLAY_SETTINGS_TAG)
                .focusRequester(settingsFocusRequester)
                .onFocusChanged { focused = it.isFocused }
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) colors.focus else colors.surfaceOutline,
                    shape = RoundedCornerShape(metrics.controlRadius),
                ),
        ) {
            Text(appString(R.string.open_settings), fontSize = metrics.actionSize)
        }
    }
}

@Composable
private fun NextEventCard(
    state: PrayerDisplayUiState,
    metrics: MainDisplayMetrics,
    modifier: Modifier,
) {
    val colors = NamazTvTheme.colors
    val countdownDescription = appString(R.string.countdown_accessibility, state.countdown)
    TvGlassPanel(
        modifier = modifier.testTag(NEXT_EVENT_CARD_TAG),
        radius = metrics.cardRadius,
        accented = true,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(metrics.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            state.rows.firstOrNull { it.isNextEvent }?.let { next ->
                PrayerIcon(
                    prayerId = next.id,
                    modifier = Modifier.width(metrics.nextIconSize).height(metrics.nextIconSize),
                    exposeTestTag = false,
                )
            }
            Text(
                appString(R.string.next_prayer),
                color = colors.accent,
                fontSize = metrics.labelSize,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                state.nextPrayerLabel,
                color = colors.textPrimary,
                fontSize = metrics.nextPrayerSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = metrics.inlineGap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.inlineGap),
            ) {
                Text(
                    state.nextEventKindLabel,
                    color = colors.textSecondary,
                    fontSize = metrics.secondarySize,
                )
                Text(
                    state.nextEventTime,
                    color = colors.accent,
                    fontSize = metrics.secondarySize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                appString(R.string.until_next_event),
                modifier = Modifier.padding(top = metrics.inlineGap),
                color = colors.textSecondary,
                fontSize = metrics.captionSize,
            )
            Text(
                state.countdown,
                modifier = Modifier
                    .width(metrics.countdownWidth)
                    .testTag(COUNTDOWN_TEST_TAG)
                    .semantics {
                        contentDescription = countdownDescription
                    },
                color = colors.accent,
                fontSize = metrics.countdownSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LocalClockCard(
    state: PrayerDisplayUiState,
    metrics: MainDisplayMetrics,
    modifier: Modifier,
) {
    val colors = NamazTvTheme.colors
    TvGlassPanel(
        modifier = modifier.fillMaxHeight().testTag(LOCAL_CLOCK_CARD_TAG),
        radius = metrics.cardRadius,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = metrics.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                state.dateLabel,
                color = colors.textPrimary,
                fontSize = metrics.dateSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                state.weekdayLabel,
                color = colors.textSecondary,
                fontSize = metrics.captionSize,
            )
            Text(
                state.mosqueLocalTime,
                modifier = Modifier.widthIn(min = metrics.clockWidth),
                color = colors.textPrimary,
                fontSize = metrics.clockSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PrayerListCard(
    state: PrayerDisplayUiState,
    metrics: MainDisplayMetrics,
    modifier: Modifier,
) {
    val colors = NamazTvTheme.colors
    TvGlassPanel(
        modifier = modifier.testTag(PRAYER_LIST_CARD_TAG),
        radius = metrics.cardRadius,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(
                horizontal = metrics.gridHorizontalPadding,
                vertical = metrics.gridVerticalPadding,
            ),
        ) {
            PrayerGridHeader(metrics)
            state.rows.forEach { row ->
                PrayerGridRow(row, metrics, Modifier.weight(1f))
            }
            if (state.jumuahSessions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(metrics.jumuahHeight),
                    horizontalArrangement = Arrangement.spacedBy(metrics.inlineGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.jumuahSessions.forEach { session ->
                        Text(
                            session.text,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("$JUMUAH_SESSION_TEST_TAG_PREFIX${session.id}")
                                .background(colors.accentSoft, RoundedCornerShape(metrics.rowRadius))
                                .padding(horizontal = metrics.cellPadding, vertical = 3.dp),
                            color = colors.accent,
                            fontSize = metrics.captionSize,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerGridRow(
    row: PrayerDisplayRow,
    metrics: MainDisplayMetrics,
    modifier: Modifier,
) {
    val colors = NamazTvTheme.colors
    val iqamahText = row.iqamah ?: "—"
    val spokenIqamah = row.iqamah ?: appString(
        when (row.iqamahPresentation) {
            IqamahPresentation.MISSING -> R.string.iqamah_missing_spoken
            IqamahPresentation.NOT_APPLICABLE -> R.string.iqamah_not_applicable_spoken
        },
    )
    val accessibilitySuffix = if (row.isNextEvent) {
        appString(R.string.next_event_accessibility_suffix)
    } else {
        ""
    }
    val rowDescription = appString(
        R.string.prayer_row_accessibility,
        row.label,
        row.adhan,
        spokenIqamah,
        accessibilitySuffix,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("$PRAYER_ROW_TEST_TAG_PREFIX${row.id}")
            .background(
                if (row.isNextEvent) colors.accentSoft else Color.Transparent,
                RoundedCornerShape(metrics.rowRadius),
            )
            .then(
                if (row.isNextEvent) {
                    Modifier.border(
                        1.dp,
                        colors.accentOutline,
                        RoundedCornerShape(metrics.rowRadius),
                    )
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            },
    ) {
        if (row.isNextEvent) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .fillMaxHeight(0.66f)
                    .background(colors.accent, RoundedCornerShape(4.dp)),
            )
        }
        PrayerGridColumns(
            row = row,
            iqamahText = iqamahText,
            metrics = metrics,
            modifier = Modifier.fillMaxSize().padding(
                start = if (row.isNextEvent) metrics.inlineGap else 0.dp,
            ),
        )
    }
}

@Composable
private fun PrayerGridHeader(
    metrics: MainDisplayMetrics,
) {
    val colors = NamazTvTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(metrics.gridHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(metrics.prayerIconSize + metrics.inlineGap))
        GridText(appString(R.string.prayer_column), 1.25f, metrics, true, TextAlign.Start)
        Row(
            modifier = Modifier.weight(1.55f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GridText(appString(R.string.adhan_column), 1f, metrics, true, TextAlign.Center)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IqamahIcon(Modifier.width(metrics.headerIconSize).height(metrics.headerIconSize))
                Spacer(Modifier.width(3.dp))
                Text(
                    appString(R.string.iqamah_column),
                    color = colors.textSecondary,
                    fontSize = metrics.gridHeaderSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
}

@Composable
private fun PrayerGridColumns(
    row: PrayerDisplayRow,
    iqamahText: String,
    metrics: MainDisplayMetrics,
    modifier: Modifier = Modifier,
) {
    val colors = NamazTvTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PrayerIcon(
            prayerId = row.id,
            modifier = Modifier.width(metrics.prayerIconSize).height(metrics.prayerIconSize),
        )
        Spacer(Modifier.width(metrics.inlineGap))
        GridText(row.label, 1.25f, metrics, false, TextAlign.Start)
        Row(
            modifier = Modifier
                .weight(1.55f)
                .fillMaxHeight()
                .testTag("$PRAYER_TIME_AREA_TEST_TAG_PREFIX${row.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.id == "sunrise") {
                Text(
                    row.adhan,
                    modifier = Modifier.fillMaxWidth().testTag(SUNRISE_CENTERED_TIME_TEST_TAG),
                    color = if (row.isNextEvent) colors.accent else colors.textPrimary,
                    fontSize = metrics.prayerRowSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            } else {
                GridText(row.adhan, 1f, metrics, false, TextAlign.Center)
                GridText(iqamahText, 1f, metrics, false, TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RowScope.GridText(
    value: String,
    weight: Float,
    metrics: MainDisplayMetrics,
    header: Boolean,
    alignment: TextAlign,
) {
    val colors = NamazTvTheme.colors
    Text(
        value,
        modifier = Modifier.weight(weight).padding(horizontal = metrics.cellPadding),
        color = if (header) colors.textSecondary else colors.textPrimary,
        fontSize = if (header) metrics.gridHeaderSize else metrics.prayerRowSize,
        fontWeight = if (header) FontWeight.Medium else FontWeight.SemiBold,
        fontFamily = if (!header && alignment == TextAlign.End) FontFamily.Monospace else null,
        textAlign = alignment,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun IqamahStatusStrip(
    state: PrayerDisplayUiState,
    metrics: MainDisplayMetrics,
    modifier: Modifier,
) {
    val colors = NamazTvTheme.colors
    TvGlassPanel(
        modifier = modifier.testTag(IQAMAH_STRIP_TAG),
        radius = metrics.cardRadius,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = metrics.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap),
        ) {
            IqamahIcon(
                Modifier.width(metrics.iqamahIconSize).height(metrics.iqamahIconSize),
                exposeTestTag = true,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    state.iqamahSummary?.label ?: appString(R.string.iqamah_nearest),
                    color = colors.textSecondary,
                    fontSize = metrics.captionSize,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.inlineGap),
                ) {
                    Text(
                        state.iqamahSummary?.time ?: appString(R.string.iqamah_not_specified),
                        color = if (state.iqamahSummary == null) {
                            colors.textPrimary
                        } else {
                            colors.accent
                        },
                        fontSize = metrics.iqamahSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (state.iqamahSummary == null) null else FontFamily.Monospace,
                    )
                    state.iqamahSummary?.countdownLabel?.let { countdown ->
                        Text(
                            countdown,
                            color = colors.textSecondary,
                            fontSize = metrics.secondarySize,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight(0.56f).background(colors.separator))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    state.sourceLabel,
                    color = colors.warning,
                    fontSize = metrics.captionSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.supportCode?.let { appString(R.string.support_code, it) }
                        ?: state.sourceDescription,
                    color = colors.textSecondary,
                    fontSize = metrics.sourceDescriptionSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun LocalPrayerSchedule.toPrayerDisplayUiState(
    resolution: PrayerTimeResolution.Available,
    strings: AppStrings,
): PrayerDisplayUiState {
    val day = requireNotNull(days.firstOrNull { it.localDate == resolution.localDate.toString() }) {
        "resolved date is outside the local snapshot"
    }
    val dateLabel = resolution.localDate.format(
        DateTimeFormatter.ofPattern("d MMMM yyyy", strings.locale),
    )
    val weekdayLabel = resolution.localDate
        .format(DateTimeFormatter.ofPattern("EEEE", strings.locale))
        .replaceFirstChar { character -> character.titlecase(strings.locale) }
    val sourceLabel = when {
        diagnostics == null -> strings.get(R.string.source_unverified)
        diagnostics.approvalStatus != "approved" -> strings.get(R.string.source_not_approved)
        diagnostics.dataClassification == "synthetic" -> strings.get(R.string.source_test_data)
        sourceKind == "calculation_profile" -> strings.get(R.string.source_calculated_unofficial)
        diagnostics.dataClassification == "production" -> strings.get(R.string.source_approved)
        else -> strings.get(R.string.source_unverified)
    }
    val nextEvent = resolution.nextEvent
    return PrayerDisplayUiState(
        mosqueName = mosqueName,
        location = locality,
        dateLabel = dateLabel,
        weekdayLabel = weekdayLabel,
        mosqueLocalTime = resolution.localTime.format(CLOCK_FORMAT),
        nextPrayerLabel = nextEvent?.let { event ->
            val label = resolution.eventLabel(event, strings)
            if (event.localDate != resolution.localDate) {
                strings.get(R.string.event_tomorrow_label, label)
            } else {
                label
            }
        } ?: strings.get(R.string.no_future_event),
        nextEventKindLabel = when (nextEvent?.kind) {
            PrayerEventKind.ADHAN -> if (nextEvent.localDate == resolution.localDate) {
                strings.get(R.string.until_adhan)
            } else {
                strings.get(R.string.until_adhan_tomorrow)
            }
            PrayerEventKind.IQAMAH -> strings.get(R.string.until_iqamah)
            PrayerEventKind.JUMUAH -> strings.get(R.string.until_jumuah)
            null -> strings.get(R.string.events_completed)
        },
        nextEventTime = nextEvent?.localTime?.format(PRAYER_TIME_FORMAT) ?: "—:——",
        countdown = resolution.countdownSeconds?.let(::formatCountdown) ?: "—:——:——",
        iqamahSummary = resolution.nextIqamahSummary(strings),
        sourceLabel = sourceLabel,
        sourceDescription = attribution ?: authorityName,
        supportCode = null,
        jumuahSessions = resolution.jumuahSessions.map { session ->
            JumuahDisplaySession(
                id = session.id,
                text = "${session.label} ${session.salahTime.format(PRAYER_TIME_FORMAT)}",
            )
        },
        rows = day.toDisplayRows(resolution, strings),
    )
}

private fun PrayerTimeResolution.Available.nextIqamahSummary(strings: AppStrings): IqamahSummaryUiState? {
    if (nextEvent?.localDate != localDate) return null
    val preferred = nextEvent.prayer
        ?.let(prayers::get)
        ?.takeIf { prayer -> prayer.iqamah?.isBefore(localTime) == false }
        ?: prayers.values.asSequence()
            .filter { prayer -> prayer.iqamah?.isBefore(localTime) == false }
            .sortedBy { prayer -> prayer.iqamah }
            .firstOrNull()
        ?: return null
    val time = preferred.iqamah ?: return null
    val isCountdownTarget = nextEvent.kind == PrayerEventKind.IQAMAH &&
        nextEvent.prayer == preferred.id && nextEvent.localTime == time
    return IqamahSummaryUiState(
        label = strings.get(R.string.iqamah_for_prayer, prayerLabel(preferred.id, strings)),
        time = time.format(PRAYER_TIME_FORMAT),
        countdownLabel = if (isCountdownTarget) {
            countdownSeconds?.let { strings.get(R.string.iqamah_countdown, formatCountdown(it)) }
        } else {
            null
        },
    )
}

private fun LocalPrayerDay.toDisplayRows(
    resolution: PrayerTimeResolution.Available,
    strings: AppStrings,
): List<PrayerDisplayRow> = listOf(
    prayerRow("fajr", prayerLabel("fajr", strings), fajr, resolution),
    PrayerDisplayRow(
        "sunrise",
        prayerLabel("sunrise", strings),
        sunrise,
        iqamahPresentation = IqamahPresentation.NOT_APPLICABLE,
        isNextEvent = resolution.isCurrentDateNextPrayer("sunrise"),
    ),
    prayerRow("dhuhr", prayerLabel("dhuhr", strings), dhuhr, resolution),
    prayerRow("asr", prayerLabel("asr", strings), asr, resolution),
    prayerRow("maghrib", prayerLabel("maghrib", strings), maghrib, resolution),
    prayerRow("isha", prayerLabel("isha", strings), isha, resolution),
)

private fun prayerRow(
    id: String,
    label: String,
    adhan: String,
    resolution: PrayerTimeResolution.Available,
): PrayerDisplayRow = PrayerDisplayRow(
    id = id,
    label = label,
    adhan = adhan,
    iqamah = resolution.prayers[id]?.iqamah?.format(PRAYER_TIME_FORMAT),
    isNextEvent = resolution.isCurrentDateNextPrayer(id),
)

private fun PrayerTimeResolution.Available.isCurrentDateNextPrayer(prayer: String): Boolean =
    nextEvent?.prayer == prayer && nextEvent.localDate == localDate

private fun prayerLabel(id: String, strings: AppStrings): String {
    val resource = when (id) {
        "fajr" -> R.string.prayer_fajr
        "sunrise" -> R.string.prayer_sunrise
        "dhuhr" -> R.string.prayer_dhuhr
        "asr" -> R.string.prayer_asr
        "maghrib" -> R.string.prayer_maghrib
        "isha" -> R.string.prayer_isha
        else -> return id
    }
    return strings.get(resource)
}

private fun PrayerTimeResolution.Available.eventLabel(
    event: com.example.namaztime.tv.domain.PrayerEvent,
    strings: AppStrings,
): String = when (event.kind) {
    PrayerEventKind.ADHAN -> prayerLabel(requireNotNull(event.prayer), strings)
    PrayerEventKind.IQAMAH -> strings.get(
        R.string.event_iqamah_label,
        prayerLabel(requireNotNull(event.prayer), strings),
    )
    PrayerEventKind.JUMUAH -> strings.get(
        R.string.event_jumuah_label,
        jumuahSessions.firstOrNull { it.id == event.jumuahSessionId }?.label.orEmpty(),
    )
}

private fun formatCountdown(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, remainingSeconds)
}

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
private val PRAYER_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

private data class MainDisplayMetrics(
    val sectionGap: Dp,
    val inlineGap: Dp,
    val cardPadding: Dp,
    val gridHorizontalPadding: Dp,
    val gridVerticalPadding: Dp,
    val cellPadding: Dp,
    val headerHeight: Dp,
    val settingsHeight: Dp,
    val clockCardHeight: Dp,
    val iqamahStripHeight: Dp,
    val countdownWidth: Dp,
    val clockWidth: Dp,
    val campaignPanelWidth: Dp,
    val qrSize: Dp,
    val gridHeaderHeight: Dp,
    val jumuahHeight: Dp,
    val cardRadius: Dp,
    val controlRadius: Dp,
    val rowRadius: Dp,
    val leftColumnWeight: Float,
    val mosqueNameSize: TextUnit,
    val nextPrayerSize: TextUnit,
    val clockSize: TextUnit,
    val countdownSize: TextUnit,
    val iqamahSize: TextUnit,
    val dateSize: TextUnit,
    val prayerRowSize: TextUnit,
    val gridHeaderSize: TextUnit,
    val labelSize: TextUnit,
    val secondarySize: TextUnit,
    val captionSize: TextUnit,
    val sourceDescriptionSize: TextUnit,
    val actionSize: TextUnit,
    val brandIconSize: Dp,
    val nextIconSize: Dp,
    val prayerIconSize: Dp,
    val headerIconSize: Dp,
    val iqamahIconSize: Dp,
    val brandSize: TextUnit,
) {
    companion object {
        fun forHeight(height: Dp): MainDisplayMetrics = if (height < 600.dp) {
            MainDisplayMetrics(
                10.dp, 8.dp, 16.dp, 12.dp, 8.dp, 8.dp,
                76.dp, 48.dp, 106.dp, 62.dp, 250.dp, 190.dp, 132.dp, 74.dp,
                28.dp, 28.dp, 20.dp, 12.dp, 10.dp, 0.92f,
                28.sp, 34.sp, 36.sp, 42.sp, 24.sp, 18.sp, 20.sp, 14.sp,
                17.sp, 15.sp, 13.sp, 12.sp, 15.sp,
                18.dp, 30.dp, 27.dp, 14.dp, 32.dp, 14.sp,
            )
        } else {
            MainDisplayMetrics(
                16.dp, 10.dp, 24.dp, 18.dp, 12.dp, 12.dp,
                94.dp, 56.dp, 140.dp, 76.dp, 330.dp, 240.dp, 170.dp, 96.dp,
                36.dp, 34.dp, 26.dp, 14.dp, 12.dp, 0.94f,
                38.sp, 46.sp, 50.sp, 58.sp, 30.sp, 23.sp, 27.sp, 18.sp,
                21.sp, 19.sp, 16.sp, 14.sp, 18.sp,
                22.dp, 40.dp, 36.dp, 18.dp, 42.dp, 18.sp,
            )
        }
    }
}
