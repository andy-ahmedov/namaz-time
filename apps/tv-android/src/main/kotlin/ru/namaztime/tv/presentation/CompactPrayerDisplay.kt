package ru.namaztime.tv.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ru.namaztime.tv.R
import androidx.compose.ui.unit.Dp

const val COMPACT_RIGHT_RAIL_TAG = "compact-right-rail"
internal const val COMPACT_HEADER_TAG = "compact-header"
internal const val COMPACT_SCHEDULE_HEADING_TAG = "compact-schedule-heading"
internal const val COMPACT_SOURCE_STATUS_TAG = "compact-source-status"

/** Compact-only rendering of the same immutable projection consumed by STANDARD. */
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
        val left = maxWidth / 2 + SCREEN_RETENTION_SHIFT_BUDGET + 2.dp
        val top = safe.vertical + SCREEN_RETENTION_SHIFT_BUDGET
        val railWidth = maxWidth - left - safe.horizontal - SCREEN_RETENTION_SHIFT_BUDGET
        val railHeight = maxHeight - top - (36 * scale).dp
        val metrics = MainDisplayMetrics.forHeight(maxHeight)
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val campaignMetrics = state.campaign?.let { campaign ->
            val qrSize = maxOf((112 * scale).dp, with(density) { (campaign.qrCode.moduleCount * 2).toDp() })
            val textWidth = with(density) { (railWidth - qrSize - (52 * scale).dp).roundToPx() }
            val titleSize = (if (campaign.title.length > 55) 12 else 21) * scale
            val subtitleSize = when {
                campaign.subtitle.orEmpty().count { it == '\n' } >= 3 -> 12
                campaign.subtitle.orEmpty().length > 100 -> 13
                else -> 16
            } * scale
            fun height(text: String, fontSize: Float): Dp = with(density) {
                measurer.measure(text, TextStyle(fontSize = fontSize.sp, lineHeight = (fontSize * 1.05f).sp),
                    constraints = Constraints(maxWidth = textWidth)).size.height.toDp()
            }
            val titleHeight = maxOf((29 * scale).dp, height(campaign.title, titleSize))
            val subtitleHeight = campaign.subtitle?.let { height(it, subtitleSize) } ?: 0.dp
            CompactCampaignMetrics(qrSize, titleSize, titleHeight, subtitleSize,
                maxOf((137 * scale).dp, qrSize + (24 * scale).dp,
                    titleHeight + subtitleHeight + (54 * scale).dp))
        }
        Column(
            Modifier.offset(left + retentionOffset.x, top + retentionOffset.y)
                .width(railWidth).height(railHeight).testTag(COMPACT_RIGHT_RAIL_TAG),
            verticalArrangement = Arrangement.spacedBy((8 * scale).dp),
        ) {
            Box(Modifier.fillMaxWidth().height((116 * scale).dp - top - (8 * scale).dp)
                .testTag(COMPACT_HEADER_TAG)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier.testTag(BRAND_PILL_TEST_TAG)
                            .background(CompactVisualStyle.surfaceStrong.copy(alpha = .76f).scheduleBlockColor(), RoundedCornerShape(50))
                            .border(.5.dp, CompactVisualStyle.surfaceOutline, RoundedCornerShape(50))
                            .padding(horizontal = (12 * scale).dp, vertical = (3 * scale).dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((5 * scale).dp),
                    ) {
                        BrandMark(Modifier.size((18 * scale).dp), tint = CompactVisualStyle.accent)
                        Text("NamazTime", color = CompactVisualStyle.textPrimary,
                            fontSize = (13 * scale).sp, lineHeight = (15 * scale).sp)
                    }
                    CompactFitText(compactMosqueIdentityPresentation(state.mosqueName), MOSQUE_NAME_TEST_TAG, 34 * scale,
                        Modifier.fillMaxWidth().weight(1f).padding(horizontal = (42 * scale).dp).semantics { heading() },
                        weight = FontWeight.SemiBold, maxLines = 2, minSize = 18 * scale,
                        accessibilityDescription = state.mosqueName, lineHeightMultiplier = 1.08f)
                    state.location?.let { location ->
                        Row(Modifier.fillMaxWidth().height((16 * scale).dp).testTag(MOSQUE_LOCATION_ORNAMENT_TAG),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            CompactDivider(Modifier.width((44 * scale).dp).height((6 * scale).dp),
                                diamondPosition = OrnamentDiamondPosition.END)
                            CompactFitText(location, "compact-locality", 14 * scale,
                                Modifier.widthIn(max = (280 * scale).dp).padding(horizontal = (8 * scale).dp))
                            CompactDivider(Modifier.width((44 * scale).dp).height((6 * scale).dp),
                                diamondPosition = OrnamentDiamondPosition.START)
                        }
                    }
                }
                Box(Modifier.align(Alignment.TopEnd)) {
                    SettingsButton(metrics, focusRequester, requestInitialFocus, onOpenSettings)
                }
                if (state.sourceRequiresAttention || state.supportCode != null) {
                    CompactStatusChip(
                        label = state.supportCode?.let { appString(R.string.support_code, it) } ?: state.sourceLabel,
                        scale = scale,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                CompactScheduleCard(state, scale, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy((6 * scale).dp)) {
                    CompactNextCard(state, scale, Modifier.fillMaxWidth().weight(1.3f))
                    CompactClockCard(state, scale, Modifier.fillMaxWidth().weight(1f))
                }
            }
            state.campaign?.let {
                CompactCampaignCard(it, scale, requireNotNull(campaignMetrics), Modifier.fillMaxWidth().height(campaignMetrics.height))
            }
        }
    }
}

@Composable
private fun CompactStatusChip(
    label: String,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape((8 * scale).dp)
    val warning = NamazTvTheme.colors.warning
    Row(
        modifier = modifier
            .widthIn(max = (128 * scale).dp)
            .height((22 * scale).dp)
            .background(CompactVisualStyle.warningSurface, shape)
            .border(.6.dp, CompactVisualStyle.warningOutline, shape)
            .padding(horizontal = (7 * scale).dp, vertical = (2 * scale).dp)
            .testTag(COMPACT_SOURCE_STATUS_TAG)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((5 * scale).dp),
    ) {
        Canvas(Modifier.size((7 * scale).dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * .46f
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y - radius)
                    lineTo(center.x + radius, center.y)
                    lineTo(center.x, center.y + radius)
                    lineTo(center.x - radius, center.y)
                    close()
                },
                warning,
            )
        }
        CompactFitText(
            value = label,
            tag = "compact-source-status-label",
            size = 10 * scale,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            color = warning,
            align = TextAlign.Start,
            minSize = 8 * scale,
        )
    }
}

@Composable
private fun CompactScheduleCard(state: PrayerDisplayUiState, scale: Float, modifier: Modifier) {
    val colors = CompactVisualStyle
    CompactGlassPanel(modifier.testTag(PRAYER_LIST_CARD_TAG), radius = (14 * scale).dp) {
        Column(Modifier.fillMaxSize().padding((6 * scale).dp)) {
            CompactFitText(appString(R.string.compact_prayer_schedule), COMPACT_SCHEDULE_HEADING_TAG,
                18 * scale, Modifier.fillMaxWidth().height((29 * scale).dp).semantics { heading() }, color = colors.accent)
            CompactDivider(Modifier.fillMaxWidth().height((6 * scale).dp))
            if (state.showIqamahOnSchedule) {
                Row(Modifier.fillMaxWidth().height((17 * scale).dp), horizontalArrangement = Arrangement.End) {
                    CompactFitText(appString(R.string.adhan_column), "compact-adhan-heading", 10 * scale, Modifier.width((43 * scale).dp))
                    CompactFitText(appString(R.string.iqamah_column), "compact-iqamah-heading", 10 * scale, Modifier.width((43 * scale).dp))
                }
            }
            state.rows.forEachIndexed { index, row ->
                val suffix = if (row.isNextEvent) appString(R.string.next_event_accessibility_suffix) else ""
                val spokenIqamah = row.iqamah ?: appString(if (row.iqamahPresentation == IqamahPresentation.NOT_APPLICABLE)
                    R.string.iqamah_not_applicable_spoken else R.string.iqamah_missing_spoken)
                val description = if (state.showIqamahOnSchedule) appString(R.string.prayer_row_accessibility,
                    row.label, row.adhan, spokenIqamah, suffix)
                else "${row.label}, ${appString(R.string.adhan_column)} ${row.adhan}$suffix"
                Row(
                    Modifier.fillMaxWidth().weight(1f).testTag(PRAYER_ROW_TEST_TAG_PREFIX + row.id)
                        .then(if (row.isNextEvent) Modifier
                            .drawBehind {
                                val radius = (9 * scale).dp.toPx()
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(colors.activeLeading, colors.activeMiddle, colors.activeTrailing),
                                        endX = size.width,
                                    ),
                                    cornerRadius = CornerRadius(radius),
                                )
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(colors.activeGlow, Color.Transparent),
                                        endX = size.width * .56f,
                                    ),
                                    cornerRadius = CornerRadius(radius),
                                )
                                drawRoundRect(
                                    color = colors.activeOutline,
                                    cornerRadius = CornerRadius(radius),
                                    style = Stroke(.8.dp.toPx()),
                                )
                                drawRoundRect(
                                    color = colors.accent,
                                    topLeft = Offset((1.7 * scale).dp.toPx(), size.height * .18f),
                                    size = androidx.compose.ui.geometry.Size((2.8 * scale).dp.toPx(), size.height * .64f),
                                    cornerRadius = CornerRadius((1.4 * scale).dp.toPx()),
                                )
                            } else Modifier)
                        .semantics(mergeDescendants = true) { contentDescription = description }
                        .padding(horizontal = (7 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((6 * scale).dp),
                ) {
                    Box(
                        Modifier
                            .size(((if (state.showIqamahOnSchedule) 21 else 28) * scale).dp)
                            .drawBehind {
                                if (row.isNextEvent) drawCircle(colors.activeGlow, radius = size.minDimension * .62f)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        PrayerIcon(row.id, Modifier.fillMaxSize(), tint = colors.accent, strokeFraction = .05f)
                    }
                    CompactFitText(row.label, "compact-prayer-name-${row.id}", (if (state.showIqamahOnSchedule) 14 else 18) * scale,
                        Modifier.weight(1f).fillMaxHeight(), align = TextAlign.Start, minSize = 11 * scale)
                    CompactFitText(row.adhan, "compact-adhan-${row.id}", (if (state.showIqamahOnSchedule) 18 else 24) * scale,
                        Modifier.width(((if (state.showIqamahOnSchedule) 43 else 64) * scale).dp).fillMaxHeight(),
                        weight = FontWeight.SemiBold, align = TextAlign.End,
                        minSize = (if (state.showIqamahOnSchedule) 12 else 15) * scale,
                        lineHeightMultiplier = 1.0f)
                    if (state.showIqamahOnSchedule) CompactFitText(row.iqamah ?: "—", "compact-iqamah-${row.id}", 15 * scale,
                        Modifier.width((38 * scale).dp).fillMaxHeight(), color = colors.textSecondary, align = TextAlign.End)
                }
                if (index < state.rows.lastIndex) TvFadingHairline(Modifier.fillMaxWidth().height(.5.dp), colors.separator)
            }
            state.jumuahSessions.forEach { session ->
                CompactFitText(session.text, JUMUAH_SESSION_TEST_TAG_PREFIX + session.id, 11 * scale,
                    Modifier.fillMaxWidth().height((17 * scale).dp), color = colors.accent)
            }
        }
    }
}

@Composable
private fun CompactNextCard(state: PrayerDisplayUiState, scale: Float, modifier: Modifier) {
    CompactGlassPanel(modifier.testTag(NEXT_EVENT_CARD_TAG), radius = (14 * scale).dp, role = CompactGlassRole.HERO) {
        Box(Modifier.fillMaxSize()) {
            NextPrayerWatermark(Modifier.fillMaxSize().testTag(NEXT_EVENT_WATERMARK_TAG))
            Column(Modifier.fillMaxSize().padding(horizontal = (10 * scale).dp, vertical = (8 * scale).dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                CompactFitText(appString(R.string.next_prayer), NEXT_EVENT_LABEL_TAG, 15 * scale,
                    Modifier.fillMaxWidth().weight(.20f), color = CompactVisualStyle.accent,
                    minSize = 10 * scale, lineHeightMultiplier = 1.05f)
                CompactFitText(state.nextPrayerLabel, NEXT_EVENT_NAME_TAG, 32 * scale,
                    Modifier.fillMaxWidth().weight(.31f), weight = FontWeight.Medium, minSize = 17 * scale)
                CompactDivider(Modifier.fillMaxWidth(.78f).height((9 * scale).dp).testTag(NEXT_EVENT_DIVIDER_TAG))
                val description = appString(R.string.countdown_accessibility, state.countdown)
                CompactFitText(state.countdown, COUNTDOWN_TEST_TAG, 50 * scale,
                    Modifier.fillMaxWidth().weight(.49f),
                    color = CompactVisualStyle.accent, weight = FontWeight.SemiBold, minSize = 24 * scale,
                    displayNumeral = true, accessibilityDescription = description)
            }
        }
    }
}

@Composable
private fun CompactClockCard(state: PrayerDisplayUiState, scale: Float, modifier: Modifier) {
    CompactGlassPanel(modifier.testTag(LOCAL_CLOCK_CARD_TAG), radius = (14 * scale).dp) {
        Column(Modifier.fillMaxSize().padding((8 * scale).dp)) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                CalendarGlyph(Modifier.size((34 * scale).dp).testTag(CALENDAR_ICON_TAG), tint = CompactVisualStyle.accent)
                Column(Modifier.weight(1f).heightIn(max = (40 * scale).dp).fillMaxHeight()) {
                    CompactFitText(state.dateLabel, DATE_LABEL_TAG, 15 * scale, Modifier.fillMaxWidth().weight(1.2f))
                    CompactFitText(state.weekdayLabel, WEEKDAY_LABEL_TAG, 13 * scale, Modifier.fillMaxWidth().weight(1f),
                        color = CompactVisualStyle.textSecondary)
                }
            }
            CompactFitText(state.mosqueLocalTime, LOCAL_CLOCK_VALUE_TAG, 48 * scale,
                Modifier.fillMaxWidth().weight(1.3f), weight = FontWeight.SemiBold, minSize = 26 * scale,
                displayNumeral = true, accessibilityDescription = state.mosqueLocalTime)
        }
    }
}

@Composable
private fun CompactCampaignCard(state: QrCampaignUiState, scale: Float, metrics: CompactCampaignMetrics, modifier: Modifier) {
    CompactGlassPanel(
        modifier.testTag(QR_CAMPAIGN_PANEL_TAG),
        radius = (14 * scale).dp,
        role = CompactGlassRole.DEEP,
        accented = true,
    ) {
        Box(Modifier.fillMaxSize()) {
            TvIslamicGeometricPattern(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width((136 * scale).dp).clipToBounds(),
                tint = CompactVisualStyle.accentOutline,
                intensity = .12f,
            )
            Row(Modifier.fillMaxSize().padding((8 * scale).dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
                ReferenceQrCode(state, metrics.qrSize, Modifier.testTag(QR_ELEGANT_FRAME_TAG), framePadding = (4 * scale).dp)
                CompactVerticalDivider(Modifier.width((8 * scale).dp).fillMaxHeight(.88f).testTag(QR_ORNAMENT_DIVIDER_TAG))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    CompactFitText(appString(campaignKindResource(state.kind)), "compact-campaign-kind", 24 * scale,
                        Modifier.fillMaxWidth().height((29 * scale).dp), color = CompactVisualStyle.accent, align = TextAlign.Start)
                    CompactDivider(Modifier.fillMaxWidth(.85f).height((7 * scale).dp))
                    CompactFitText(state.title, QR_CAMPAIGN_TITLE_TAG, metrics.titleSize,
                        Modifier.fillMaxWidth().height(metrics.titleHeight), align = TextAlign.Start,
                        maxLines = Int.MAX_VALUE, minSize = metrics.titleSize, lineHeightMultiplier = 1.05f)
                    state.subtitle?.let {
                        CompactFitText(it, QR_CAMPAIGN_SUBTITLE_TAG, metrics.subtitleSize,
                            Modifier.fillMaxWidth().weight(1f), color = CompactVisualStyle.textSecondary,
                            align = TextAlign.Start, maxLines = Int.MAX_VALUE, minSize = metrics.subtitleSize,
                            lineHeightMultiplier = 1.05f)
                    }
                }
            }
        }
    }
}

private data class CompactCampaignMetrics(
    val qrSize: Dp, val titleSize: Float, val titleHeight: Dp, val subtitleSize: Float, val height: Dp,
)

/** Fit actual font metrics to the allocated box; never silently ellipsize allowed copy. */
@Composable
private fun CompactFitText(
    value: String, tag: String, size: Float, modifier: Modifier = Modifier,
    color: Color = CompactVisualStyle.textPrimary, weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Center, maxLines: Int = 1, minSize: Float = size * .7f,
    displayNumeral: Boolean = false, accessibilityDescription: String? = null,
    lineHeightMultiplier: Float = 1.15f,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = when (align) {
        TextAlign.Start -> Alignment.CenterStart
        TextAlign.End -> Alignment.CenterEnd
        else -> Alignment.Center
    }) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        fun text(fontSize: Float): AnnotatedString = if (displayNumeral && value.count { it == ':' } == 2) {
            buildAnnotatedString {
                append(value.substringBeforeLast(':'))
                withStyle(SpanStyle(fontSize = (fontSize * .60f).sp)) { append(":" + value.substringAfterLast(':')) }
            }
        } else AnnotatedString(value)
        val style = remember(
            value,
            size,
            minSize,
            width,
            height,
            maxLines,
            weight,
            density,
            displayNumeral,
            lineHeightMultiplier,
        ) {
            val candidates = generateSequence(size) { (it - .5f).takeIf { next -> next >= minSize } }.toList() + minSize
            candidates.map {
                TextStyle(
                    fontSize = it.sp,
                    lineHeight = (it * lineHeightMultiplier).sp,
                    fontWeight = weight,
                    fontFeatureSettings = "tnum",
                )
            }
                .firstOrNull { candidate ->
                    val measured = measurer.measure(text(candidate.fontSize.value), candidate, maxLines = maxLines,
                        constraints = Constraints(maxWidth = width, maxHeight = height))
                    !measured.hasVisualOverflow
                } ?: TextStyle(
                    fontSize = minSize.sp,
                    lineHeight = (minSize * lineHeightMultiplier).sp,
                    fontWeight = weight,
                    fontFeatureSettings = "tnum",
                )
        }
        Text(text(style.fontSize.value), Modifier.fillMaxWidth().testTag(tag).then(
            if (accessibilityDescription == null) Modifier else Modifier.semantics {
                contentDescription = accessibilityDescription
            },
        ), color = color, style = style,
            textAlign = align, maxLines = maxLines)
    }
}

@Composable
private fun CompactVerticalDivider(modifier: Modifier) {
    val tint = CompactVisualStyle.accentOutline
    Canvas(modifier) {
        val x = size.width / 2
        val y = size.height / 2
        val radius = size.width * .4f
        val brush = Brush.verticalGradient(listOf(Color.Transparent, tint, Color.Transparent))
        drawLine(brush, Offset(x, 0f), Offset(x, y - radius * 1.8f), .7.dp.toPx())
        drawLine(brush, Offset(x, y + radius * 1.8f), Offset(x, size.height), .8.dp.toPx())
        drawPath(Path().apply {
            moveTo(x, y - radius); lineTo(x + radius, y); lineTo(x, y + radius)
            lineTo(x - radius, y); close()
        }, tint)
    }
}

internal fun compactMosqueIdentityPresentation(name: String): String {
    if (name.length <= 32 || '\n' in name) return name
    val breakIndex = name.indices
        .filter { name[it].isWhitespace() }
        .minByOrNull { index -> kotlin.math.abs(index - (name.length - index - 1)) }
        ?: return name
    return name.substring(0, breakIndex).trimEnd() + "\n" + name.substring(breakIndex + 1).trimStart()
}

@Composable
private fun CompactDivider(
    modifier: Modifier,
    diamondPosition: OrnamentDiamondPosition = OrnamentDiamondPosition.CENTER,
) = TvFadingDiamondDivider(modifier, tint = CompactVisualStyle.accentOutline,
    diamondPosition = diamondPosition, filledDiamond = true)
