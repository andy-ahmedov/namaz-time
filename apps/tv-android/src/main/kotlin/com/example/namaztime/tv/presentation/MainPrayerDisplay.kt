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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.namaztime.tv.R
import com.example.namaztime.tv.domain.PrayerTimeResolution
import com.example.namaztime.tv.repository.LocalPrayerDay
import com.example.namaztime.tv.repository.LocalPrayerSchedule
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

const val MAIN_PRAYER_DISPLAY_TAG = "main-prayer-display"
const val MAIN_DISPLAY_SETTINGS_TAG = "main-display-settings"
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

internal data class PrayerDisplayUiState(
    val mosqueName: String,
    val location: String?,
    val dateLabel: String,
    val mosqueLocalTime: String,
    val nextPrayerLabel: String,
    val countdown: String,
    val sourceLabel: String,
    val sourceDescription: String,
    val supportCode: String? = null,
    val jumuahSessions: List<JumuahDisplaySession> = emptyList(),
    val rows: List<PrayerDisplayRow>,
)

internal data class JumuahDisplaySession(
    val id: String,
    val text: String,
)

@Composable
internal fun MainPrayerDisplay(
    state: PrayerDisplayUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = true,
) {
    val settingsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) settingsFocusRequester.requestFocus()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(MAIN_PRAYER_DISPLAY_TAG)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF173C3A),
                        Color(0xFF10272B),
                        Color(0xFF09171C),
                    ),
                ),
            ),
    ) {
        val metrics = MainDisplayMetrics.forHeight(maxHeight)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.24f)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(metrics.outerPadding),
        ) {
            DisplayHeader(
                state = state,
                metrics = metrics,
                settingsFocusRequester = settingsFocusRequester,
                onOpenSettings = onOpenSettings,
            )
            Spacer(Modifier.height(metrics.sectionGap))
            DisplayStatusRow(state = state, metrics = metrics)
            if (state.jumuahSessions.isNotEmpty()) {
                Spacer(Modifier.height(metrics.sectionGap / 2))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap / 2),
                ) {
                    state.jumuahSessions.forEach { session ->
                        Text(
                            text = session.text,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("$JUMUAH_SESSION_TEST_TAG_PREFIX${session.id}")
                                .background(
                                    Color(0xFFDBB96A).copy(alpha = 0.14f),
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = metrics.cellPadding, vertical = 4.dp),
                            color = Color(0xFFFFE3A0),
                            fontSize = metrics.secondarySize,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(metrics.sectionGap))
            PrayerGrid(
                rows = state.rows,
                metrics = metrics,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DisplayHeader(
    state: PrayerDisplayUiState,
    metrics: MainDisplayMetrics,
    settingsFocusRequester: FocusRequester,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.mosqueName,
                modifier = Modifier
                    .testTag(MOSQUE_NAME_TEST_TAG)
                    .semantics { heading() },
                color = Color.White,
                fontSize = metrics.mosqueNameSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            state.location?.let { location ->
                Text(
                    text = location,
                    color = Color(0xFFCFDCDA),
                    fontSize = metrics.secondarySize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = state.mosqueLocalTime,
                color = Color.White,
                fontSize = metrics.clockSize,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.dateLabel,
                color = Color(0xFFCFDCDA),
                fontSize = metrics.secondarySize,
            )
        }
        var focused by remember { mutableStateOf(false) }
        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .testTag(MAIN_DISPLAY_SETTINGS_TAG)
                .focusRequester(settingsFocusRequester)
                .onFocusChanged { focused = it.isFocused }
                .border(
                    width = if (focused) 4.dp else 1.dp,
                    color = if (focused) Color.White else Color(0xFF77908D),
                    shape = MaterialTheme.shapes.medium,
                ),
        ) {
            Text(stringResource(R.string.open_settings), fontSize = metrics.actionSize)
        }
    }
}

@Composable
private fun DisplayStatusRow(
    state: PrayerDisplayUiState,
    metrics: MainDisplayMetrics,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.statusHeight),
        horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap),
    ) {
        Row(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .background(Color(0xFFDBB96A).copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFDBB96A), RoundedCornerShape(16.dp))
                .padding(horizontal = metrics.cardPadding, vertical = metrics.cardVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Следующий намаз",
                    color = Color(0xFFFFE3A0),
                    fontSize = metrics.labelSize,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.nextPrayerLabel,
                    color = Color.White,
                    fontSize = metrics.statusValueSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = state.countdown,
                modifier = Modifier
                    .width(metrics.countdownWidth)
                    .testTag(COUNTDOWN_TEST_TAG),
                color = Color.White,
                fontSize = metrics.countdownSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
            )
        }
        Column(
            modifier = Modifier
                .weight(0.8f)
                .fillMaxHeight()
                .background(Color(0xFF102326).copy(alpha = 0.88f), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF56706D), RoundedCornerShape(16.dp))
                .padding(horizontal = metrics.cardPadding, vertical = metrics.cardVerticalPadding),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = state.sourceLabel,
                color = Color(0xFFFFE3A0),
                fontSize = metrics.labelSize,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.sourceDescription,
                color = Color(0xFFCFDCDA),
                fontSize = metrics.sourceDescriptionSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            state.supportCode?.let { supportCode ->
                Text(
                    text = "Support code: $supportCode",
                    color = Color(0xFFFFE3A0),
                    fontSize = metrics.sourceDescriptionSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PrayerGrid(
    rows: List<PrayerDisplayRow>,
    metrics: MainDisplayMetrics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0C1C20).copy(alpha = 0.88f), RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFF46625F), RoundedCornerShape(18.dp))
            .padding(horizontal = metrics.gridHorizontalPadding, vertical = metrics.gridVerticalPadding),
    ) {
        GridColumns(
            prayer = "Намаз",
            adhan = "Азан",
            iqamah = "Икамат",
            metrics = metrics,
            header = true,
        )
        rows.forEachIndexed { index, row ->
            val iqamahText = row.iqamah ?: "—"
            val spokenIqamah = row.iqamah ?: row.iqamahPresentation.spokenValue
            GridColumns(
                prayer = row.label,
                adhan = row.adhan,
                iqamah = iqamahText,
                metrics = metrics,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("$PRAYER_ROW_TEST_TAG_PREFIX${row.id}")
                    .background(
                        when {
                            row.isNextEvent -> Color(0xFFDBB96A).copy(alpha = 0.20f)
                            index % 2 == 0 -> Color.White.copy(alpha = 0.045f)
                            else -> Color.Transparent
                        },
                        RoundedCornerShape(10.dp),
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription =
                            "${row.label}, азан ${row.adhan}, икамат $spokenIqamah" +
                                if (row.isNextEvent) ", следующее событие" else ""
                    },
            )
        }
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (header) Modifier.height(metrics.gridHeaderHeight) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GridText(prayer, 1.4f, metrics, header)
        GridText(adhan, 1f, metrics, header)
        GridText(iqamah, 1f, metrics, header)
    }
}

@Composable
private fun RowScope.GridText(
    value: String,
    weight: Float,
    metrics: MainDisplayMetrics,
    header: Boolean,
) {
    Text(
        text = value,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = metrics.cellPadding),
        color = if (header) Color(0xFFFFE3A0) else Color.White,
        fontSize = if (header) metrics.gridHeaderSize else metrics.prayerRowSize,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
    )
}

internal fun LocalPrayerSchedule.toPrayerDisplayUiState(
    resolution: PrayerTimeResolution.Available,
): PrayerDisplayUiState {
    val day = requireNotNull(days.firstOrNull { it.localDate == resolution.localDate.toString() }) {
        "resolved date is outside the local snapshot"
    }
    val dateLabel = resolution.localDate.format(
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")),
    )
    val sourceLabel = when {
        diagnostics == null -> "ИСТОЧНИК НЕ ПРОВЕРЕН"
        diagnostics.approvalStatus != "approved" -> "НЕ ОДОБРЕНО"
        diagnostics.dataClassification == "synthetic" -> "ТЕСТОВЫЕ ДАННЫЕ"
        sourceKind == "calculation_profile" -> "РАСЧЁТНОЕ — НЕ ОФИЦИАЛЬНО"
        else -> "ИСТОЧНИК НЕ ПРОВЕРЕН"
    }
    return PrayerDisplayUiState(
        mosqueName = mosqueName,
        location = locality,
        dateLabel = dateLabel,
        mosqueLocalTime = resolution.localTime.format(CLOCK_FORMAT),
        nextPrayerLabel = resolution.nextEvent?.let { event ->
            event.label + if (event.localDate != resolution.localDate) " · завтра" else ""
        } ?: "Нет будущего события",
        countdown = resolution.countdownSeconds?.let(::formatCountdown) ?: "—:——:——",
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

private fun formatCountdown(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, remainingSeconds)
}

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
private val PRAYER_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

private data class MainDisplayMetrics(
    val outerPadding: Dp,
    val sectionGap: Dp,
    val cardPadding: Dp,
    val cardVerticalPadding: Dp,
    val gridHorizontalPadding: Dp,
    val gridVerticalPadding: Dp,
    val cellPadding: Dp,
    val statusHeight: Dp,
    val countdownWidth: Dp,
    val gridHeaderHeight: Dp,
    val mosqueNameSize: TextUnit,
    val clockSize: TextUnit,
    val statusValueSize: TextUnit,
    val countdownSize: TextUnit,
    val prayerRowSize: TextUnit,
    val gridHeaderSize: TextUnit,
    val labelSize: TextUnit,
    val secondarySize: TextUnit,
    val sourceDescriptionSize: TextUnit,
    val actionSize: TextUnit,
) {
    companion object {
        fun forHeight(height: Dp): MainDisplayMetrics = if (height < 600.dp) {
            MainDisplayMetrics(
                outerPadding = 24.dp,
                sectionGap = 12.dp,
                cardPadding = 18.dp,
                cardVerticalPadding = 10.dp,
                gridHorizontalPadding = 12.dp,
                gridVerticalPadding = 8.dp,
                cellPadding = 10.dp,
                statusHeight = 88.dp,
                countdownWidth = 142.dp,
                gridHeaderHeight = 32.dp,
                mosqueNameSize = 28.sp,
                clockSize = 30.sp,
                statusValueSize = 24.sp,
                countdownSize = 26.sp,
                prayerRowSize = 24.sp,
                gridHeaderSize = 18.sp,
                labelSize = 16.sp,
                secondarySize = 16.sp,
                sourceDescriptionSize = 14.sp,
                actionSize = 16.sp,
            )
        } else {
            MainDisplayMetrics(
                outerPadding = 40.dp,
                sectionGap = 18.dp,
                cardPadding = 24.dp,
                cardVerticalPadding = 14.dp,
                gridHorizontalPadding = 18.dp,
                gridVerticalPadding = 12.dp,
                cellPadding = 14.dp,
                statusHeight = 112.dp,
                countdownWidth = 190.dp,
                gridHeaderHeight = 40.dp,
                mosqueNameSize = 38.sp,
                clockSize = 42.sp,
                statusValueSize = 30.sp,
                countdownSize = 34.sp,
                prayerRowSize = 30.sp,
                gridHeaderSize = 22.sp,
                labelSize = 18.sp,
                secondarySize = 20.sp,
                sourceDescriptionSize = 17.sp,
                actionSize = 18.sp,
            )
        }
    }
}
