package ru.namaztime.tv.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import ru.namaztime.tv.R
import ru.namaztime.tv.domain.PrayerEventKind
import ru.namaztime.tv.domain.PrayerTimeResolution
import ru.namaztime.tv.repository.LocalPrayerDay
import ru.namaztime.tv.repository.LocalPrayerSchedule
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
const val MAIN_DISPLAY_COMPOSITION_TAG = "reference-display-composition"
const val MOSQUE_LOCATION_ORNAMENT_TAG = "mosque-location-ornament"
const val NEXT_EVENT_DIVIDER_TAG = "next-event-ornament-divider"
const val CLOCK_DIVIDER_TAG = "clock-ornament-divider"
const val CALENDAR_ICON_TAG = "calendar-icon"
const val LOCAL_CLOCK_VALUE_TAG = "mosque-local-clock"
const val NEXT_EVENT_WATERMARK_TAG = "next-event-watermark"
const val BOTTOM_STRIP_ORNAMENT_TAG = "bottom-strip-ornament"
const val NEXT_EVENT_LABEL_TAG = "next-event-label"
const val NEXT_EVENT_NAME_TAG = "next-event-name"
const val DATE_LABEL_TAG = "mosque-local-date"
const val WEEKDAY_LABEL_TAG = "mosque-local-weekday"

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
    val currentPrayerLabel: String,
    val nextPrayerLabel: String,
    val nextEventKindLabel: String,
    val nextEventTime: String,
    val countdown: String,
    val iqamahSummary: IqamahSummaryUiState?,
    val showIqamahOnSchedule: Boolean = true,
    val sourceLabel: String,
    val sourceDescription: String,
    val sourceRequiresAttention: Boolean,
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
            Box(Modifier.fillMaxSize()) {
                val compositionWidth = if (state.campaign == null) 0.79f else 0.872f
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(x = if (state.campaign == null) 0.dp else 4.dp)
                        .fillMaxWidth(compositionWidth)
                        .fillMaxHeight()
                        .testTag(MAIN_DISPLAY_COMPOSITION_TAG),
                ) {
                    DisplayHeader(state, metrics)
                    Spacer(Modifier.height(metrics.bodyTopGap))
                    if (state.campaign == null) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                        ) {
                            MainLeftColumn(state, metrics, Modifier.weight(1f).fillMaxHeight())
                            PrayerListCard(
                                state,
                                metrics,
                                Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        Spacer(Modifier.height(metrics.stripGap))
                        IqamahStatusStrip(
                            state,
                            metrics,
                            Modifier.fillMaxWidth().height(metrics.iqamahStripHeight),
                        )
                    } else {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                        ) {
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Row(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                                ) {
                                    MainLeftColumn(state, metrics, Modifier.weight(1f).fillMaxHeight())
                                    PrayerListCard(
                                        state,
                                        metrics,
                                        Modifier.weight(1f).fillMaxHeight(),
                                    )
                                }
                                Spacer(Modifier.height(metrics.stripGap))
                                IqamahStatusStrip(
                                    state,
                                    metrics,
                                    Modifier.fillMaxWidth().height(metrics.iqamahStripHeight),
                                )
                            }
                            QrCampaignPanel(
                                state = state.campaign,
                                qrSize = metrics.qrSize,
                                compact = true,
                                modifier = Modifier.width(metrics.campaignPanelWidth).fillMaxHeight(),
                            )
                        }
                    }
                    Spacer(Modifier.height(metrics.bottomBreathingRoom))
                }
            }
        }
        SettingsButton(
            metrics = metrics,
            settingsFocusRequester = settingsFocusRequester,
            requestInitialFocus = requestInitialFocus,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = metrics.settingsTopMargin, end = metrics.settingsEndMargin),
        )
    }
}

@Composable
private fun MainLeftColumn(
    state: PrayerDisplayUiState,
    metrics: MainDisplayMetrics,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        NextEventCard(state, metrics, Modifier.weight(1f).fillMaxWidth())
        Spacer(Modifier.height(metrics.sectionGap))
        LocalClockCard(
            state,
            metrics,
            Modifier.fillMaxWidth().height(metrics.clockCardHeight),
        )
    }
}

@Composable
private fun DisplayHeader(
    state: PrayerDisplayUiState,
    metrics: MainDisplayMetrics,
) {
    val colors = NamazTvTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().height(metrics.headerHeight).offset(y = (-7).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .offset(y = (-8).dp)
                .testTag(BRAND_PILL_TEST_TAG)
                .background(colors.surfaceStrong.copy(alpha = 0.76f), RoundedCornerShape(50))
                .border(0.5.dp, colors.surfaceOutline, RoundedCornerShape(50))
                .padding(horizontal = metrics.inlineGap * 1.65f, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.inlineGap / 2),
        ) {
            BrandMark(Modifier.size(metrics.brandIconSize))
            Text(
                text = "NamazTime",
                color = colors.textPrimary,
                fontSize = metrics.brandSize,
                fontWeight = FontWeight.Normal,
            )
        }
        Text(
            text = state.mosqueName,
            modifier = Modifier.testTag(MOSQUE_NAME_TEST_TAG).semantics { heading() },
            color = colors.textPrimary,
            fontSize = metrics.mosqueNameSize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state.location?.let { location ->
            OrnamentedLocation(location, metrics)
        }
    }
}

@Composable
internal fun SettingsButton(
    metrics: MainDisplayMetrics,
    settingsFocusRequester: FocusRequester,
    requestInitialFocus: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NamazTvTheme.colors
    val settingsLabel = appString(R.string.open_settings)
    var focused by remember { mutableStateOf(false) }
    val settingsShape = RoundedCornerShape(metrics.settingsCornerRadius)
    LaunchedEffect(requestInitialFocus, settingsFocusRequester) {
        if (requestInitialFocus) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { settingsFocusRequester.requestFocus() }
        }
    }
    Button(
        onClick = onOpenSettings,
        colors = ButtonDefaults.colors(
            containerColor = colors.surfaceStrong.copy(alpha = 0.46f),
            contentColor = colors.textPrimary,
            focusedContainerColor = colors.surfaceStrong.copy(alpha = 0.76f),
            focusedContentColor = colors.textPrimary,
        ),
        shape = ButtonDefaults.shape(
            shape = settingsShape,
            focusedShape = settingsShape,
            pressedShape = settingsShape,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .size(metrics.settingsHeight)
            .testTag(MAIN_DISPLAY_SETTINGS_TAG)
            .focusRequester(settingsFocusRequester)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = settingsLabel }
            .border(
                width = if (focused) 2.dp else 0.75.dp,
                color = if (focused) colors.focus else colors.surfaceOutline,
                shape = settingsShape,
            ),
    ) {
        SettingsGlyph(
            tint = colors.textPrimary,
            modifier = Modifier.size(metrics.settingsIconSize),
        )
    }
}

@Composable
private fun OrnamentedLocation(
    location: String,
    metrics: MainDisplayMetrics,
    modifier: Modifier = Modifier,
) {
    val colors = NamazTvTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MOSQUE_LOCATION_ORNAMENT_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        OrnamentDivider(
            tint = colors.accentOutline,
            modifier = Modifier.width(metrics.locationOrnamentWidth).height(8.dp),
            diamondAtEnd = true,
        )
        Text(
            text = location,
            modifier = Modifier.padding(horizontal = metrics.inlineGap),
            color = colors.textSecondary,
            fontSize = metrics.secondarySize,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OrnamentDivider(
            tint = colors.accentOutline,
            modifier = Modifier.width(metrics.locationOrnamentWidth).height(8.dp),
            diamondAtStart = true,
        )
    }
}

@Composable
private fun OrnamentDivider(
    tint: Color,
    modifier: Modifier = Modifier,
    diamondAtStart: Boolean = false,
    diamondAtEnd: Boolean = false,
) {
    TvFadingDiamondDivider(
        modifier = modifier,
        tint = tint,
        diamondPosition = when {
            diamondAtStart -> OrnamentDiamondPosition.START
            diamondAtEnd -> OrnamentDiamondPosition.END
            else -> OrnamentDiamondPosition.CENTER
        },
    )
}

@Composable
internal fun CalendarGlyph(
    modifier: Modifier = Modifier,
    tint: Color = NamazTvTheme.colors.accent,
) {
    Canvas(modifier) {
        val strokeWidth = size.minDimension * 0.045f
        val stroke = Stroke(strokeWidth, cap = StrokeCap.Round)
        val left = size.width * 0.14f
        val top = size.height * 0.22f
        val right = size.width * 0.86f
        val bottom = size.height * 0.88f
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.09f),
            style = stroke,
        )
        drawLine(tint, Offset(left, size.height * 0.38f), Offset(right, size.height * 0.38f), strokeWidth)
        listOf(0.34f, 0.52f, 0.70f).forEach { x ->
            listOf(0.55f, 0.72f).forEach { y ->
                drawCircle(tint, size.minDimension * 0.025f, Offset(size.width * x, size.height * y))
            }
        }
        drawLine(tint, Offset(size.width * 0.32f, size.height * 0.12f), Offset(size.width * 0.32f, size.height * 0.30f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.68f, size.height * 0.12f), Offset(size.width * 0.68f, size.height * 0.30f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
internal fun SettingsGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension * 0.31f
        val strokeWidth = size.minDimension * 0.075f
        drawCircle(tint, outerRadius, center, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        drawCircle(tint, outerRadius * 0.52f, center, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        repeat(8) { index ->
            val angle = Math.toRadians(index * 45.0)
            val start = Offset(
                center.x + kotlin.math.cos(angle).toFloat() * outerRadius * 0.78f,
                center.y + kotlin.math.sin(angle).toFloat() * outerRadius * 0.78f,
            )
            val end = Offset(
                center.x + kotlin.math.cos(angle).toFloat() * outerRadius * 1.25f,
                center.y + kotlin.math.sin(angle).toFloat() * outerRadius * 1.25f,
            )
            drawLine(tint, start, end, strokeWidth, StrokeCap.Round)
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
        Box(Modifier.fillMaxSize()) {
            NextPrayerWatermark(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(NEXT_EVENT_WATERMARK_TAG),
            )
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    appString(R.string.next_prayer),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = metrics.nextTitleTop)
                        .testTag(NEXT_EVENT_LABEL_TAG),
                    color = colors.accent,
                    fontSize = metrics.labelSize,
                    fontWeight = FontWeight.Normal,
                )
                Text(
                    state.nextPrayerLabel,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = metrics.nextPrayerTop)
                        .testTag(NEXT_EVENT_NAME_TAG),
                    color = colors.textPrimary,
                    fontSize = metrics.nextPrayerSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OrnamentDivider(
                    tint = colors.accentOutline,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = metrics.nextDividerTop)
                        .width(metrics.ornamentDividerWidth)
                        .height(8.dp)
                        .testTag(NEXT_EVENT_DIVIDER_TAG),
                )
                Text(
                    state.countdown,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = metrics.nextCountdownTop)
                        .width(metrics.countdownWidth)
                        .testTag(COUNTDOWN_TEST_TAG)
                        .semantics {
                            contentDescription = countdownDescription
                        },
                    color = colors.accent,
                    fontSize = metrics.countdownSize,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

internal data class NextPrayerWatermarkGeometry(
    val archLeft: Float,
    val archRight: Float,
    val apexX: Float,
    val apexY: Float,
    val shoulderY: Float,
    val verticalSideTopY: Float,
    val bottomY: Float,
) {
    init {
        listOf(
            archLeft,
            archRight,
            apexX,
            apexY,
            shoulderY,
            verticalSideTopY,
            bottomY,
        ).forEach { value ->
            require(value in 0f..1f) { "watermark geometry must be normalized" }
        }
        require(archLeft < apexX && apexX < archRight) { "apex must be inside the arch" }
        require(apexY < shoulderY && shoulderY < verticalSideTopY) {
            "arch vertical landmarks must be ordered"
        }
        require(verticalSideTopY < bottomY) { "arch sides must reach the bottom" }
    }

    val archWidth: Float
        get() = archRight - archLeft
}

internal val NEXT_PRAYER_WATERMARK_GEOMETRY = NextPrayerWatermarkGeometry(
    archLeft = 0.04f,
    archRight = 0.27f,
    apexX = 0.155f,
    apexY = 0.205f,
    shoulderY = 0.40f,
    verticalSideTopY = 0.49f,
    bottomY = 1f,
)

@Composable
internal fun NextPrayerWatermark(modifier: Modifier = Modifier) {
    val colors = NamazTvTheme.colors
    val tint = colors.accentOutline
    Canvas(modifier) {
        val geometry = NEXT_PRAYER_WATERMARK_GEOMETRY
        val shoulderInset = geometry.archWidth * 0.15f
        val lowerCurveInset = geometry.archWidth * 0.035f
        val apexApproach = geometry.archWidth * 0.08f
        val stroke = Stroke(0.8.dp.toPx(), cap = StrokeCap.Round)
        val glowStroke = Stroke(3.2.dp.toPx(), cap = StrokeCap.Round)
        val arch = Path().apply {
            moveTo(size.width * geometry.archLeft, size.height * geometry.bottomY)
            lineTo(size.width * geometry.archLeft, size.height * geometry.verticalSideTopY)
            cubicTo(
                size.width * (geometry.archLeft - lowerCurveInset),
                size.height * 0.445f,
                size.width * (geometry.archLeft + lowerCurveInset * 2f),
                size.height * 0.415f,
                size.width * (geometry.archLeft + shoulderInset),
                size.height * geometry.shoulderY,
            )
            cubicTo(
                size.width * (geometry.archLeft + geometry.archWidth * 0.32f),
                size.height * 0.35f,
                size.width * (geometry.apexX - apexApproach),
                size.height * 0.245f,
                size.width * geometry.apexX,
                size.height * geometry.apexY,
            )
            cubicTo(
                size.width * (geometry.apexX + apexApproach),
                size.height * 0.245f,
                size.width * (geometry.archRight - geometry.archWidth * 0.32f),
                size.height * 0.35f,
                size.width * (geometry.archRight - shoulderInset),
                size.height * geometry.shoulderY,
            )
            cubicTo(
                size.width * (geometry.archRight - lowerCurveInset * 2f),
                size.height * 0.415f,
                size.width * (geometry.archRight + lowerCurveInset),
                size.height * 0.445f,
                size.width * geometry.archRight,
                size.height * geometry.verticalSideTopY,
            )
            lineTo(size.width * geometry.archRight, size.height * geometry.bottomY)
        }
        drawPath(arch, tint.copy(alpha = 0.045f), style = glowStroke)
        drawPath(arch, tint.copy(alpha = 0.19f), style = stroke)

        val lanternCenterX = size.width * 0.12f
        val lanternHalfWidth = size.width * 0.0425f
        val lanternTop = size.height * 0.42f
        val bodyTop = size.height * 0.485f
        val bodyBottom = size.height * 0.665f
        val lanternOutline = Stroke(0.7.dp.toPx(), cap = StrokeCap.Round)
        val lanternGlow = Stroke(2.8.dp.toPx(), cap = StrokeCap.Round)

        drawCircle(
            color = colors.accent.copy(alpha = 0.075f),
            radius = size.width * 0.054f,
            center = Offset(lanternCenterX, size.height * 0.56f),
        )
        drawLine(
            color = tint.copy(alpha = 0.25f),
            start = Offset(lanternCenterX, size.height * 0.235f),
            end = Offset(lanternCenterX, lanternTop),
            strokeWidth = 0.65.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = tint.copy(alpha = 0.28f),
            radius = 1.1.dp.toPx(),
            center = Offset(lanternCenterX, lanternTop),
            style = lanternOutline,
        )

        val canopy = Path().apply {
            moveTo(lanternCenterX, lanternTop)
            cubicTo(
                lanternCenterX - lanternHalfWidth * 0.18f,
                size.height * 0.435f,
                lanternCenterX - lanternHalfWidth * 0.58f,
                size.height * 0.445f,
                lanternCenterX - lanternHalfWidth * 0.72f,
                size.height * 0.46f,
            )
            lineTo(lanternCenterX - lanternHalfWidth, bodyTop)
            lineTo(lanternCenterX + lanternHalfWidth, bodyTop)
            lineTo(lanternCenterX + lanternHalfWidth * 0.72f, size.height * 0.46f)
            cubicTo(
                lanternCenterX + lanternHalfWidth * 0.58f,
                size.height * 0.445f,
                lanternCenterX + lanternHalfWidth * 0.18f,
                size.height * 0.435f,
                lanternCenterX,
                lanternTop,
            )
            close()
        }
        drawPath(canopy, colors.surfaceStrong.copy(alpha = 0.78f))
        drawPath(canopy, tint.copy(alpha = 0.30f), style = lanternGlow)
        drawPath(canopy, tint.copy(alpha = 0.42f), style = lanternOutline)

        val body = Path().apply {
            moveTo(lanternCenterX - lanternHalfWidth, bodyTop)
            lineTo(lanternCenterX - lanternHalfWidth * 0.80f, bodyBottom)
            quadraticTo(
                lanternCenterX,
                size.height * 0.685f,
                lanternCenterX + lanternHalfWidth * 0.80f,
                bodyBottom,
            )
            lineTo(lanternCenterX + lanternHalfWidth, bodyTop)
            close()
        }
        drawPath(body, colors.surfaceStrong.copy(alpha = 0.72f))
        drawPath(body, tint.copy(alpha = 0.055f), style = lanternGlow)
        drawPath(body, tint.copy(alpha = 0.40f), style = lanternOutline)

        val paneGap = size.width * 0.0035f
        val paneWidth = (lanternHalfWidth * 1.52f - paneGap * 2f) / 3f
        val paneTop = size.height * 0.515f
        val paneBottom = size.height * 0.635f
        repeat(3) { index ->
            val paneLeft = lanternCenterX - lanternHalfWidth * 0.76f +
                index * (paneWidth + paneGap)
            val paneRight = paneLeft + paneWidth
            val lean = (index - 1) * size.width * 0.002f
            val pane = Path().apply {
                moveTo(paneLeft + lean, paneTop)
                lineTo(paneRight + lean, paneTop)
                lineTo(paneRight - lean * 0.35f, paneBottom)
                lineTo(paneLeft - lean * 0.35f, paneBottom)
                close()
            }
            drawPath(pane, colors.accent.copy(alpha = 0.34f))
            drawPath(pane, tint.copy(alpha = 0.34f), style = lanternOutline)
        }

        drawLine(
            color = tint.copy(alpha = 0.40f),
            start = Offset(lanternCenterX - lanternHalfWidth * 0.88f, bodyTop),
            end = Offset(lanternCenterX + lanternHalfWidth * 0.88f, bodyTop),
            strokeWidth = lanternOutline.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint.copy(alpha = 0.40f),
            start = Offset(
                lanternCenterX - lanternHalfWidth * 0.70f,
                size.height * 0.655f,
            ),
            end = Offset(
                lanternCenterX + lanternHalfWidth * 0.70f,
                size.height * 0.655f,
            ),
            strokeWidth = lanternOutline.width,
            cap = StrokeCap.Round,
        )

        val finialTop = size.height * 0.68f
        val finial = Path().apply {
            moveTo(lanternCenterX - lanternHalfWidth * 0.28f, finialTop)
            quadraticTo(
                lanternCenterX,
                size.height * 0.705f,
                lanternCenterX + lanternHalfWidth * 0.28f,
                finialTop,
            )
            quadraticTo(
                lanternCenterX,
                size.height * 0.715f,
                lanternCenterX - lanternHalfWidth * 0.28f,
                finialTop,
            )
            close()
        }
        drawPath(finial, colors.surfaceStrong.copy(alpha = 0.76f))
        drawPath(finial, tint.copy(alpha = 0.38f), style = lanternOutline)
        drawLine(
            color = tint.copy(alpha = 0.33f),
            start = Offset(lanternCenterX, size.height * 0.705f),
            end = Offset(lanternCenterX, size.height * 0.735f),
            strokeWidth = lanternOutline.width,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = tint.copy(alpha = 0.34f),
            radius = 1.15.dp.toPx(),
            center = Offset(lanternCenterX, size.height * 0.742f),
        )
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
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = metrics.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(metrics.calendarAreaWidth),
                contentAlignment = Alignment.Center,
            ) {
                CalendarGlyph(
                    modifier = Modifier
                        .size(metrics.calendarIconSize)
                        .testTag(CALENDAR_ICON_TAG),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = metrics.clockContentTop),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    state.dateLabel,
                    modifier = Modifier.testTag(DATE_LABEL_TAG),
                    color = colors.textPrimary,
                    fontSize = metrics.dateSize,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                )
                Spacer(Modifier.height(metrics.dateWeekdayGap))
                Text(
                    state.weekdayLabel,
                    modifier = Modifier.testTag(WEEKDAY_LABEL_TAG),
                    color = colors.textSecondary,
                    fontSize = metrics.captionSize,
                )
                OrnamentDivider(
                    tint = colors.accentOutline,
                    modifier = Modifier
                        .padding(vertical = 1.dp)
                        .width(metrics.clockDividerWidth)
                        .height(7.dp)
                        .testTag(CLOCK_DIVIDER_TAG),
                )
                Spacer(Modifier.height(metrics.clockPreTimeGap))
                Text(
                    state.mosqueLocalTime,
                    modifier = Modifier
                        .widthIn(min = metrics.clockWidth)
                        .testTag(LOCAL_CLOCK_VALUE_TAG),
                    color = colors.textPrimary,
                    fontSize = metrics.clockSize,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(metrics.calendarAreaWidth / 2))
        }
    }
}

@Composable
internal fun PrayerListCard(
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
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = metrics.rowHighlightHorizontalInset,
                    vertical = metrics.gridVerticalPadding,
                ),
        ) {
            PrayerGridHeader(
                metrics,
                Modifier.padding(horizontal = metrics.gridHorizontalPadding),
                showIqamah = state.showIqamahOnSchedule,
            )
            state.rows.forEachIndexed { index, row ->
                PrayerGridRow(row, metrics, Modifier.weight(1f), showIqamah = state.showIqamahOnSchedule)
                if (index < state.rows.lastIndex) {
                    TvFadingHairline(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = metrics.gridHorizontalPadding)
                            .height(1.dp),
                        color = colors.separator,
                    )
                }
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
                                .background(
                                    colors.accentSoft.copy(alpha = 0.16f),
                                    RoundedCornerShape(metrics.rowRadius),
                                )
                                .padding(horizontal = metrics.cellPadding, vertical = 3.dp),
                            color = colors.accent,
                            fontSize = metrics.captionSize,
                            fontWeight = FontWeight.Medium,
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
    showIqamah: Boolean,
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
    val rowDescription = if (!showIqamah) "${row.label}, ${appString(R.string.adhan_column)} ${row.adhan}$accessibilitySuffix" else appString(
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
            .then(
                if (row.isNextEvent) {
                    Modifier
                        .drawBehind {
                            val radius = metrics.rowRadius.toPx()
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        colors.accentSoft.copy(alpha = 0.57f),
                                        colors.accentSoft.copy(alpha = 0.31f),
                                        colors.accentSoft.copy(alpha = 0.16f),
                                    ),
                                ),
                                cornerRadius = CornerRadius(radius),
                            )
                            drawRoundRect(
                                color = colors.accentOutline.copy(alpha = 0.66f),
                                cornerRadius = CornerRadius(radius),
                                style = Stroke(0.65.dp.toPx()),
                            )
                            drawLine(
                                color = colors.accent,
                                start = Offset(1.8.dp.toPx(), radius * 0.72f),
                                end = Offset(1.8.dp.toPx(), size.height - radius * 0.72f),
                                strokeWidth = 3.6.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            },
    ) {
        PrayerGridColumns(
            row = row,
            iqamahText = iqamahText,
            showIqamah = showIqamah,
            metrics = metrics,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = metrics.gridHorizontalPadding),
        )
    }
}

@Composable
private fun PrayerGridHeader(
    metrics: MainDisplayMetrics,
    modifier: Modifier = Modifier,
    showIqamah: Boolean = true,
) {
    val colors = NamazTvTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().height(metrics.gridHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(metrics.prayerIconSize + metrics.inlineGap))
        Spacer(Modifier.weight(1.25f))
        Row(
            modifier = Modifier.weight(1.55f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GridText(
                appString(R.string.adhan_column),
                1f,
                metrics,
                header = true,
                alignment = TextAlign.Center,
            )
            if (showIqamah) GridText(
                appString(R.string.iqamah_column),
                1f,
                metrics,
                header = true,
                alignment = TextAlign.Center,
            )
        }
    }
    TvFadingHairline(
        modifier = modifier.fillMaxWidth().height(1.dp),
        color = colors.separator,
    )
}

@Composable
private fun PrayerGridColumns(
    row: PrayerDisplayRow,
    iqamahText: String,
    showIqamah: Boolean,
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
        GridText(
            row.label,
            1.25f,
            metrics,
            header = false,
            alignment = TextAlign.Start,
            emphasized = row.isNextEvent,
        )
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
                    fontWeight = if (row.isNextEvent) FontWeight.Medium else FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            } else {
                GridText(
                    row.adhan,
                    1f,
                    metrics,
                    header = false,
                    alignment = TextAlign.Center,
                    emphasized = row.isNextEvent,
                    numeric = true,
                )
                if (showIqamah) GridText(
                    iqamahText,
                    1f,
                    metrics,
                    header = false,
                    alignment = TextAlign.Center,
                    emphasized = row.isNextEvent,
                    numeric = true,
                )
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
    emphasized: Boolean = false,
    numeric: Boolean = false,
) {
    val colors = NamazTvTheme.colors
    Text(
        value,
        modifier = Modifier.weight(weight).padding(horizontal = metrics.cellPadding),
        color = if (header) colors.textSecondary else colors.textPrimary,
        fontSize = if (header) metrics.gridHeaderSize else metrics.prayerRowSize,
        fontWeight = when {
            header -> FontWeight.Normal
            emphasized -> FontWeight.SemiBold
            numeric -> FontWeight.Normal
            else -> FontWeight.Normal
        },
        fontFamily = if (numeric) FontFamily.SansSerif else null,
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
    if (!state.showIqamahOnSchedule) {
        if (state.sourceRequiresAttention || state.supportCode != null) {
            Text(
                state.supportCode?.let { appString(R.string.support_code, it) } ?: state.sourceLabel,
                color = NamazTvTheme.colors.warning,
                fontSize = metrics.sourceDescriptionSize,
                modifier = modifier,
            )
        }
        return
    }
    val colors = NamazTvTheme.colors
    TvGlassPanel(
        modifier = modifier.testTag(IQAMAH_STRIP_TAG),
        radius = metrics.cardRadius,
    ) {
        Box(Modifier.fillMaxSize()) {
            TvIslamicGeometricPattern(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(metrics.stripOrnamentWidth)
                    .fillMaxHeight()
                    .testTag(BOTTOM_STRIP_ORNAMENT_TAG),
                intensity = 0.12f,
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = metrics.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap),
            ) {
                IqamahIcon(
                    Modifier.width(metrics.iqamahIconSize).height(metrics.iqamahIconSize),
                    exposeTestTag = true,
                )
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.inlineGap),
                ) {
                    Text(
                        state.iqamahSummary?.label ?: appString(R.string.iqamah_nearest),
                        color = colors.textSecondary,
                        fontSize = metrics.captionSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.iqamahSummary?.time ?: appString(R.string.iqamah_not_specified),
                        color = if (state.iqamahSummary == null) {
                            colors.textPrimary
                        } else {
                            colors.accent
                        },
                        fontSize = metrics.iqamahSize,
                        fontWeight = FontWeight.Medium,
                        fontFamily = if (state.iqamahSummary == null) null else FontFamily.SansSerif,
                        maxLines = 1,
                    )
                }
                Box(Modifier.width(0.5.dp).fillMaxHeight(0.44f).background(colors.separator))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.nextEventKindLabel,
                        color = colors.textSecondary,
                        fontSize = metrics.captionSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.countdown,
                        modifier = Modifier.padding(start = metrics.inlineGap),
                        color = colors.accent,
                        fontSize = metrics.iqamahSize,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                    )
                }
                if (state.sourceRequiresAttention || state.supportCode != null) {
                    Text(
                        state.supportCode?.let { appString(R.string.support_code, it) }
                            ?: state.sourceLabel,
                        color = colors.warning,
                        fontSize = metrics.sourceDescriptionSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal fun LocalPrayerSchedule.toPrayerDisplayUiState(
    resolution: PrayerTimeResolution.Available,
    strings: AppStrings,
    displayIdentity: MosqueDisplayIdentity = toMosqueDisplayIdentity(),
    showIqamahOnSchedule: Boolean = true,
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
    val sourceRequiresAttention = diagnostics == null ||
        diagnostics.approvalStatus != "approved" ||
        diagnostics.dataClassification != "production" ||
        sourceKind == "calculation_profile"
    val nextEvent = resolution.nextEvent
    return PrayerDisplayUiState(
        mosqueName = displayIdentity.name,
        location = displayIdentity.locality,
        dateLabel = dateLabel,
        weekdayLabel = weekdayLabel,
        mosqueLocalTime = resolution.localTime.format(CLOCK_FORMAT),
        currentPrayerLabel = prayerLabel(resolution.currentPrayer, strings),
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
        showIqamahOnSchedule = showIqamahOnSchedule,
        iqamahSummary = if (showIqamahOnSchedule) resolution.nextIqamahSummary(strings) else null,
        sourceLabel = sourceLabel,
        sourceDescription = attribution ?: authorityName,
        sourceRequiresAttention = sourceRequiresAttention,
        supportCode = null,
        jumuahSessions = resolution.jumuahSessions.map { session ->
            JumuahDisplaySession(
                id = session.id,
                text = "${session.label} ${session.salahTime.format(PRAYER_TIME_FORMAT)}",
            )
        },
        rows = day.toDisplayRows(resolution, strings).map { row ->
            if (showIqamahOnSchedule) row else row.copy(iqamah = null)
        },
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
        "jumuah" -> R.string.prayer_jumuah
        else -> return id
    }
    return strings.get(resource)
}

private fun PrayerTimeResolution.Available.eventLabel(
    event: ru.namaztime.tv.domain.PrayerEvent,
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
    ).let { formatted ->
        val sessionLabel = jumuahSessions
            .firstOrNull { it.id == event.jumuahSessionId }
            ?.label
            .orEmpty()
        if (sessionLabel.lowercase(strings.locale).let { it.contains("джум") || it.contains("jumu") }) {
            sessionLabel
        } else {
            formatted
        }
    }
}

private fun formatCountdown(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, remainingSeconds)
}

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
private val PRAYER_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

internal data class MainDisplayMetrics(
    val sectionGap: Dp,
    val bodyTopGap: Dp,
    val stripGap: Dp,
    val inlineGap: Dp,
    val cardPadding: Dp,
    val gridHorizontalPadding: Dp,
    val rowHighlightHorizontalInset: Dp,
    val gridVerticalPadding: Dp,
    val cellPadding: Dp,
    val headerHeight: Dp,
    val settingsHeight: Dp,
    val settingsTopMargin: Dp,
    val settingsEndMargin: Dp,
    val settingsCornerRadius: Dp,
    val clockCardHeight: Dp,
    val clockContentTop: Dp,
    val dateWeekdayGap: Dp,
    val clockPreTimeGap: Dp,
    val iqamahStripHeight: Dp,
    val bottomBreathingRoom: Dp,
    val countdownWidth: Dp,
    val clockWidth: Dp,
    val campaignPanelWidth: Dp,
    val qrSize: Dp,
    val stripOrnamentWidth: Dp,
    val gridHeaderHeight: Dp,
    val jumuahHeight: Dp,
    val cardRadius: Dp,
    val rowRadius: Dp,
    val leftColumnWeight: Float,
    val ornamentDividerWidth: Dp,
    val nextTitleTop: Dp,
    val nextPrayerTop: Dp,
    val nextDividerTop: Dp,
    val nextCountdownTop: Dp,
    val clockDividerWidth: Dp,
    val locationOrnamentWidth: Dp,
    val calendarAreaWidth: Dp,
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
    val brandIconSize: Dp,
    val prayerIconSize: Dp,
    val iqamahIconSize: Dp,
    val settingsIconSize: Dp,
    val calendarIconSize: Dp,
    val brandSize: TextUnit,
) {
    companion object {
        fun forHeight(height: Dp): MainDisplayMetrics = if (height < 600.dp) {
            MainDisplayMetrics(
                sectionGap = 8.dp,
                bodyTopGap = 12.dp,
                stripGap = 13.dp,
                inlineGap = 8.dp,
                cardPadding = 16.dp,
                gridHorizontalPadding = 17.dp,
                rowHighlightHorizontalInset = 4.dp,
                gridVerticalPadding = 9.dp,
                cellPadding = 3.dp,
                headerHeight = 86.dp,
                settingsHeight = 40.dp,
                settingsTopMargin = 27.dp,
                settingsEndMargin = 20.dp,
                settingsCornerRadius = 11.dp,
                clockCardHeight = 127.dp,
                clockContentTop = 19.dp,
                dateWeekdayGap = 2.dp,
                clockPreTimeGap = 8.dp,
                iqamahStripHeight = 52.dp,
                bottomBreathingRoom = 0.dp,
                countdownWidth = 230.dp,
                clockWidth = 180.dp,
                campaignPanelWidth = 202.dp,
                qrSize = 132.dp,
                stripOrnamentWidth = 132.dp,
                gridHeaderHeight = 16.dp,
                jumuahHeight = 24.dp,
                cardRadius = 19.dp,
                rowRadius = 11.dp,
                leftColumnWeight = 1f,
                ornamentDividerWidth = 118.dp,
                nextTitleTop = 24.dp,
                nextPrayerTop = 53.dp,
                nextDividerTop = 98.dp,
                nextCountdownTop = 119.dp,
                clockDividerWidth = 92.dp,
                locationOrnamentWidth = 54.dp,
                calendarAreaWidth = 52.dp,
                mosqueNameSize = 28.sp,
                nextPrayerSize = 27.sp,
                clockSize = 36.sp,
                countdownSize = 46.sp,
                iqamahSize = 21.sp,
                dateSize = 17.sp,
                prayerRowSize = 17.sp,
                gridHeaderSize = 12.sp,
                labelSize = 14.sp,
                secondarySize = 14.sp,
                captionSize = 13.sp,
                sourceDescriptionSize = 10.sp,
                brandIconSize = 17.dp,
                prayerIconSize = 25.dp,
                iqamahIconSize = 24.dp,
                settingsIconSize = 22.dp,
                calendarIconSize = 34.dp,
                brandSize = 15.sp,
            )
        } else {
            MainDisplayMetrics(
                sectionGap = 14.dp,
                bodyTopGap = 17.dp,
                stripGap = 17.dp,
                inlineGap = 10.dp,
                cardPadding = 24.dp,
                gridHorizontalPadding = 23.dp,
                rowHighlightHorizontalInset = 5.dp,
                gridVerticalPadding = 12.dp,
                cellPadding = 5.dp,
                headerHeight = 108.dp,
                settingsHeight = 52.dp,
                settingsTopMargin = 36.dp,
                settingsEndMargin = 28.dp,
                settingsCornerRadius = 14.dp,
                clockCardHeight = 170.dp,
                clockContentTop = 25.dp,
                dateWeekdayGap = 3.dp,
                clockPreTimeGap = 11.dp,
                iqamahStripHeight = 69.dp,
                bottomBreathingRoom = 0.dp,
                countdownWidth = 310.dp,
                clockWidth = 230.dp,
                campaignPanelWidth = 260.dp,
                qrSize = 174.dp,
                stripOrnamentWidth = 176.dp,
                gridHeaderHeight = 22.dp,
                jumuahHeight = 30.dp,
                cardRadius = 24.dp,
                rowRadius = 15.dp,
                leftColumnWeight = 1f,
                ornamentDividerWidth = 156.dp,
                nextTitleTop = 32.dp,
                nextPrayerTop = 71.dp,
                nextDividerTop = 131.dp,
                nextCountdownTop = 159.dp,
                clockDividerWidth = 124.dp,
                locationOrnamentWidth = 72.dp,
                calendarAreaWidth = 70.dp,
                mosqueNameSize = 38.sp,
                nextPrayerSize = 36.sp,
                clockSize = 48.sp,
                countdownSize = 61.sp,
                iqamahSize = 28.sp,
                dateSize = 22.sp,
                prayerRowSize = 23.sp,
                gridHeaderSize = 15.sp,
                labelSize = 19.sp,
                secondarySize = 18.sp,
                captionSize = 16.sp,
                sourceDescriptionSize = 13.sp,
                brandIconSize = 21.dp,
                prayerIconSize = 34.dp,
                iqamahIconSize = 32.dp,
                settingsIconSize = 29.dp,
                calendarIconSize = 46.dp,
                brandSize = 18.sp,
            )
        }
    }
}
