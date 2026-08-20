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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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

internal enum class IqamahPresentation(val spokenValue: String) {
    MISSING("не указана"),
    NOT_APPLICABLE("не предусмотрена"),
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
    var initialFocusRequested by remember(requestInitialFocus) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(metrics.headerHeight)) {
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.68f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(metrics.settingsHeight)
                .testTag(MAIN_DISPLAY_SETTINGS_TAG)
                .focusRequester(settingsFocusRequester)
                .onGloballyPositioned {
                    if (requestInitialFocus && !initialFocusRequested) {
                        initialFocusRequested = true
                        settingsFocusRequester.requestFocus()
                    }
                }
                .onFocusChanged { focused = it.isFocused }
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) colors.focus else colors.surfaceOutline,
                    shape = RoundedCornerShape(metrics.controlRadius),
                ),
        ) {
            Text(stringResource(R.string.open_settings), fontSize = metrics.actionSize)
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
            Text(
                "Следующий намаз",
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
                "До следующего события",
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
                        contentDescription = "До следующего события ${state.countdown}"
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
            GridColumns("Намаз", "Азан", "Икамат", metrics, header = true)
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
    val spokenIqamah = row.iqamah ?: row.iqamahPresentation.spokenValue
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
                contentDescription =
                    "${row.label}, азан ${row.adhan}, икамат $spokenIqamah" +
                        if (row.isNextEvent) ", следующее событие" else ""
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
        GridColumns(
            row.label,
            row.adhan,
            iqamahText,
            metrics,
            modifier = Modifier.fillMaxSize().padding(
                start = if (row.isNextEvent) metrics.inlineGap else 0.dp,
            ),
        )
    }
}

@Composable
private fun GridColumns(
    prayer: String,
    adhan: String,
    iqamah: String,
    metrics: MainDisplayMetrics,
    modifier: Modifier = Modifier,
    header: Boolean = false,
) {
    val colors = NamazTvTheme.colors
    Row(
        modifier = modifier.fillMaxWidth()
            .then(if (header) Modifier.height(metrics.gridHeaderHeight) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GridText(prayer, 1.35f, metrics, header, TextAlign.Start)
        GridText(adhan, 0.9f, metrics, header, TextAlign.End)
        GridText(iqamah, 0.9f, metrics, header, TextAlign.End)
    }
    if (header) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
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
            Column(Modifier.weight(1f)) {
                Text(
                    state.iqamahSummary?.label ?: "Икамат · ближайший",
                    color = colors.textSecondary,
                    fontSize = metrics.captionSize,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.inlineGap),
                ) {
                    Text(
                        state.iqamahSummary?.time ?: "Не указан",
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
                    state.supportCode?.let { "Support code: $it" }
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
): PrayerDisplayUiState {
    val day = requireNotNull(days.firstOrNull { it.localDate == resolution.localDate.toString() }) {
        "resolved date is outside the local snapshot"
    }
    val dateLabel = resolution.localDate.format(
        DateTimeFormatter.ofPattern("d MMMM yyyy", RUSSIAN_LOCALE),
    )
    val weekdayLabel = resolution.localDate
        .format(DateTimeFormatter.ofPattern("EEEE", RUSSIAN_LOCALE))
        .replaceFirstChar { character -> character.titlecase(RUSSIAN_LOCALE) }
    val sourceLabel = when {
        diagnostics == null -> "ИСТОЧНИК НЕ ПРОВЕРЕН"
        diagnostics.approvalStatus != "approved" -> "НЕ ОДОБРЕНО"
        diagnostics.dataClassification == "synthetic" -> "ТЕСТОВЫЕ ДАННЫЕ"
        sourceKind == "calculation_profile" -> "РАСЧЁТНОЕ — НЕ ОФИЦИАЛЬНО"
        else -> "ИСТОЧНИК НЕ ПРОВЕРЕН"
    }
    val nextEvent = resolution.nextEvent
    return PrayerDisplayUiState(
        mosqueName = mosqueName,
        location = locality,
        dateLabel = dateLabel,
        weekdayLabel = weekdayLabel,
        mosqueLocalTime = resolution.localTime.format(CLOCK_FORMAT),
        nextPrayerLabel = nextEvent?.let { event ->
            event.label + if (event.localDate != resolution.localDate) " · завтра" else ""
        } ?: "Нет будущего события",
        nextEventKindLabel = when (nextEvent?.kind) {
            PrayerEventKind.ADHAN -> if (nextEvent.localDate == resolution.localDate) {
                "До азана"
            } else {
                "До азана завтра"
            }
            PrayerEventKind.IQAMAH -> "До икамата"
            PrayerEventKind.JUMUAH -> "До джума-намаза"
            null -> "События завершены"
        },
        nextEventTime = nextEvent?.localTime?.format(PRAYER_TIME_FORMAT) ?: "—:——",
        countdown = resolution.countdownSeconds?.let(::formatCountdown) ?: "—:——:——",
        iqamahSummary = resolution.nextIqamahSummary(),
        sourceLabel = sourceLabel,
        sourceDescription = authorityName,
        supportCode = null,
        jumuahSessions = resolution.jumuahSessions.map { session ->
            JumuahDisplaySession(
                id = session.id,
                text = "${session.label} ${session.salahTime.format(PRAYER_TIME_FORMAT)}",
            )
        },
        rows = day.toDisplayRows(resolution),
    )
}

private fun PrayerTimeResolution.Available.nextIqamahSummary(): IqamahSummaryUiState? {
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
        label = "Икамат · ${prayerLabel(preferred.id)}",
        time = time.format(PRAYER_TIME_FORMAT),
        countdownLabel = if (isCountdownTarget) {
            countdownSeconds?.let { "через ${formatCountdown(it)}" }
        } else {
            null
        },
    )
}

private fun LocalPrayerDay.toDisplayRows(
    resolution: PrayerTimeResolution.Available,
): List<PrayerDisplayRow> = listOf(
    prayerRow("fajr", "Фаджр", fajr, resolution),
    PrayerDisplayRow(
        "sunrise",
        "Восход",
        sunrise,
        iqamahPresentation = IqamahPresentation.NOT_APPLICABLE,
        isNextEvent = resolution.isCurrentDateNextPrayer("sunrise"),
    ),
    prayerRow("dhuhr", "Зухр", dhuhr, resolution),
    prayerRow("asr", "Аср", asr, resolution),
    prayerRow("maghrib", "Магриб", maghrib, resolution),
    prayerRow("isha", "Иша", isha, resolution),
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

private fun prayerLabel(id: String): String = when (id) {
    "fajr" -> "Фаджр"
    "dhuhr" -> "Зухр"
    "asr" -> "Аср"
    "maghrib" -> "Магриб"
    "isha" -> "Иша"
    else -> id
}

private fun formatCountdown(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, remainingSeconds)
}

private val RUSSIAN_LOCALE = Locale.forLanguageTag("ru")
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
) {
    companion object {
        fun forHeight(height: Dp): MainDisplayMetrics = if (height < 600.dp) {
            MainDisplayMetrics(
                10.dp, 8.dp, 16.dp, 12.dp, 8.dp, 8.dp,
                58.dp, 48.dp, 116.dp, 62.dp, 250.dp, 190.dp, 132.dp, 74.dp,
                28.dp, 28.dp, 20.dp, 12.dp, 10.dp, 0.92f,
                28.sp, 34.sp, 36.sp, 42.sp, 24.sp, 18.sp, 20.sp, 14.sp,
                17.sp, 15.sp, 13.sp, 12.sp, 15.sp,
            )
        } else {
            MainDisplayMetrics(
                16.dp, 10.dp, 24.dp, 18.dp, 12.dp, 12.dp,
                76.dp, 56.dp, 150.dp, 76.dp, 330.dp, 240.dp, 170.dp, 96.dp,
                36.dp, 34.dp, 26.dp, 14.dp, 12.dp, 0.94f,
                38.sp, 46.sp, 50.sp, 58.sp, 30.sp, 23.sp, 27.sp, 18.sp,
                21.sp, 19.sp, 16.sp, 14.sp, 18.sp,
            )
        }
    }
}
