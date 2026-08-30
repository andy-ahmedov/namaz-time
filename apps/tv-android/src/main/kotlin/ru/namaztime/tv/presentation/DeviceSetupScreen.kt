package ru.namaztime.tv.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import ru.namaztime.tv.R
import ru.namaztime.tv.sync.CanonicalCityCandidate

const val DEVICE_SETUP_SCREEN_TAG = "device-setup-screen"
const val DEVICE_SETUP_SEARCH_FIELD_TAG = "device-setup-search-field"
const val DEVICE_SETUP_CITY_LIST_TAG = "device-setup-city-list"
const val DEVICE_SETUP_CITY_RESULT_TAG_PREFIX = "device-setup-city-result-"
const val DEVICE_SETUP_RETRY_TAG = "device-setup-retry"

data class ActiveScheduleSummaryUi(
    val cityName: String,
    val authorityName: String,
    val sourceName: String,
    val timezone: String,
)

/** Renders the device-scoped canonical-city and schedule-choice setup flow. */
@Composable
fun DeviceScheduleSetupScreen(
    state: DeviceSetupUiState,
    activeSchedule: ActiveScheduleSummaryUi?,
    onQueryChanged: (String) -> Unit,
    onRetrySearch: () -> Unit,
    onCitySelected: (CanonicalCityCandidate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    TvSafeFrame(
        testTag = DEVICE_SETUP_SCREEN_TAG,
        modifier = modifier,
    ) {
        TvGlassPanel(
            modifier = Modifier.fillMaxSize(),
            radius = 24.dp,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxHeight < 600.dp
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (compact) 24.dp else 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 28.dp else 44.dp),
                ) {
                    SetupContextPanel(
                        activeSchedule = activeSchedule,
                        onBack = onBack,
                        compact = compact,
                        modifier = Modifier
                            .weight(0.34f)
                            .fillMaxHeight(),
                    )
                    when (state.step) {
                        DeviceSetupStep.SEARCH -> CitySearchPanel(
                            state = state,
                            onQueryChanged = onQueryChanged,
                            onRetrySearch = onRetrySearch,
                            onCitySelected = onCitySelected,
                            compact = compact,
                            modifier = Modifier
                                .weight(0.66f)
                                .fillMaxHeight(),
                        )
                        DeviceSetupStep.CHOICES -> SetupStatusPanel(
                            title = state.selectedCity?.canonicalName.orEmpty(),
                            message = appString(R.string.device_setup_choices_loading),
                            compact = compact,
                            modifier = Modifier
                                .weight(0.66f)
                                .fillMaxHeight(),
                        )
                        DeviceSetupStep.PENDING -> SetupStatusPanel(
                            title = state.selectedCity?.canonicalName.orEmpty(),
                            message = appString(R.string.device_setup_pending_review),
                            compact = compact,
                            modifier = Modifier
                                .weight(0.66f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupContextPanel(
    activeSchedule: ActiveScheduleSummaryUi?,
    onBack: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp),
    ) {
        Text(
            text = appString(R.string.device_setup_title),
            modifier = Modifier.semantics { heading() },
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 30.sp else 40.sp,
            lineHeight = if (compact) 36.sp else 48.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = appString(R.string.device_setup_current_schedule),
            color = NamazTvTheme.colors.accent,
            fontSize = if (compact) 16.sp else 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (activeSchedule == null) {
            Text(
                text = appString(R.string.device_setup_no_active_schedule),
                color = NamazTvTheme.colors.warning,
                fontSize = if (compact) 16.sp else 20.sp,
            )
        } else {
            SummaryLine(activeSchedule.cityName, compact)
            SummaryLine(activeSchedule.authorityName, compact)
            SummaryLine(activeSchedule.sourceName, compact)
            SummaryLine(activeSchedule.timezone, compact)
        }
        Text(
            text = appString(R.string.device_setup_last_known_good_note),
            color = NamazTvTheme.colors.textSecondary,
            fontSize = if (compact) 14.sp else 17.sp,
            lineHeight = if (compact) 19.sp else 23.sp,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onBack,
            colors = setupButtonColors(),
        ) {
            Text(appString(R.string.device_setup_back))
        }
    }
}

@Composable
private fun SummaryLine(value: String, compact: Boolean) {
    Text(
        text = value,
        color = NamazTvTheme.colors.textPrimary,
        fontSize = if (compact) 15.sp else 19.sp,
        lineHeight = if (compact) 19.sp else 24.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CitySearchPanel(
    state: DeviceSetupUiState,
    onQueryChanged: (String) -> Unit,
    onRetrySearch: () -> Unit,
    onCitySelected: (CanonicalCityCandidate) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val searchRequester = remember { FocusRequester() }
    val retryRequester = remember { FocusRequester() }
    val candidates = (state.search as? CitySearchUiState.Results)?.candidates.orEmpty()
    val resultRequesters = remember(candidates.map { it.id }) {
        candidates.associate { it.id to FocusRequester() }
    }
    LaunchedEffect(searchRequester) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { searchRequester.requestFocus() }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
    ) {
        Text(
            text = appString(R.string.device_setup_search_title),
            modifier = Modifier.semantics { heading() },
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 26.sp else 34.sp,
            fontWeight = FontWeight.SemiBold,
        )
        SetupSearchField(
            value = state.query,
            onValueChange = onQueryChanged,
            requester = searchRequester,
            firstResultRequester = candidates.firstOrNull()?.let { resultRequesters[it.id] }
                ?: retryRequester.takeIf {
                    (state.search as? CitySearchUiState.Error)?.retryable == true
                },
            compact = compact,
            modifier = Modifier.testTag(DEVICE_SETUP_SEARCH_FIELD_TAG),
        )
        when (val search = state.search) {
            CitySearchUiState.EmptyInput -> SetupMessage(
                appString(R.string.device_setup_search_empty),
                compact,
            )
            CitySearchUiState.Waiting -> SetupMessage(
                appString(R.string.device_setup_search_waiting),
                compact,
            )
            CitySearchUiState.Loading -> SetupMessage(
                appString(R.string.device_setup_search_loading),
                compact,
            )
            is CitySearchUiState.Results -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(DEVICE_SETUP_CITY_LIST_TAG),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            ) {
                itemsIndexed(
                    items = search.candidates,
                    key = { _, city -> city.id },
                ) { index, city ->
                    CityCandidateButton(
                        city = city,
                        onClick = { onCitySelected(city) },
                        requester = resultRequesters.getValue(city.id),
                        previousRequester = search.candidates.getOrNull(index - 1)
                            ?.let { resultRequesters[it.id] }
                            ?: searchRequester,
                        nextRequester = search.candidates.getOrNull(index + 1)
                            ?.let { resultRequesters[it.id] },
                        compact = compact,
                    )
                }
            }
            CitySearchUiState.NoResults -> SetupMessage(
                appString(R.string.device_setup_search_no_results),
                compact,
            )
            CitySearchUiState.NotProvisioned -> SetupMessage(
                appString(R.string.device_setup_not_provisioned),
                compact,
                warning = true,
            )
            CitySearchUiState.Unauthorized -> SetupMessage(
                appString(R.string.device_setup_unauthorized),
                compact,
                warning = true,
            )
            is CitySearchUiState.Error -> {
                SetupMessage(
                    appString(R.string.device_setup_search_error),
                    compact,
                    warning = true,
                )
                if (search.retryable) {
                    Button(
                        onClick = onRetrySearch,
                        modifier = Modifier
                            .testTag(DEVICE_SETUP_RETRY_TAG)
                            .focusRequester(retryRequester)
                            .focusProperties { up = searchRequester },
                        colors = setupButtonColors(),
                    ) {
                        Text(appString(R.string.device_setup_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    requester: FocusRequester,
    firstResultRequester: FocusRequester?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = NamazTvTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 52.dp else 64.dp)
            .focusRequester(requester)
            .focusProperties {
                firstResultRequester?.let { down = it }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    firstResultRequester?.requestFocus()
                    firstResultRequester != null
                } else {
                    false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .background(colors.surfaceStrong.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.focus else colors.surfaceOutline,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 18.dp, vertical = if (compact) 7.dp else 10.dp),
        textStyle = TextStyle(
            color = colors.textPrimary,
            fontSize = if (compact) 19.sp else 23.sp,
        ),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { firstResultRequester?.requestFocus() },
        ),
        singleLine = true,
        decorationBox = { innerTextField ->
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = appString(R.string.device_setup_search_label),
                    color = if (focused) colors.accent else colors.textSecondary,
                    fontSize = if (compact) 11.sp else 13.sp,
                )
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = appString(R.string.device_setup_search_hint),
                            color = colors.textSecondary.copy(alpha = 0.72f),
                            fontSize = if (compact) 18.sp else 21.sp,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun CityCandidateButton(
    city: CanonicalCityCandidate,
    onClick: () -> Unit,
    requester: FocusRequester,
    previousRequester: FocusRequester,
    nextRequester: FocusRequester?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = setupButtonColors(),
        modifier = modifier
            .fillMaxWidth()
            .testTag("$DEVICE_SETUP_CITY_RESULT_TAG_PREFIX${city.id}")
            .focusRequester(requester)
            .focusProperties {
                up = previousRequester
                nextRequester?.let { down = it }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (compact) 2.dp else 5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = city.canonicalName,
                fontSize = if (compact) 19.sp else 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = city.federalSubjectName,
                fontSize = if (compact) 15.sp else 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = appString(
                    R.string.device_setup_city_details,
                    settlementTypeLabel(city.settlementType),
                    city.timezone,
                ),
                fontSize = if (compact) 13.sp else 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun settlementTypeLabel(type: String): String = appString(
    when (type) {
        "city", "town" -> R.string.device_setup_settlement_city
        "village", "hamlet" -> R.string.device_setup_settlement_village
        "urban_settlement" -> R.string.device_setup_settlement_urban
        else -> R.string.device_setup_settlement_other
    },
)

@Composable
private fun SetupMessage(
    message: String,
    compact: Boolean,
    warning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        modifier = modifier.padding(top = if (compact) 8.dp else 12.dp),
        color = if (warning) NamazTvTheme.colors.warning else NamazTvTheme.colors.textSecondary,
        fontSize = if (compact) 18.sp else 22.sp,
        lineHeight = if (compact) 23.sp else 28.sp,
    )
}

@Composable
private fun SetupStatusPanel(
    title: String,
    message: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 30.sp else 40.sp,
            fontWeight = FontWeight.Bold,
        )
        SetupMessage(message, compact)
    }
}

@Composable
private fun setupButtonColors() = ButtonDefaults.colors(
    containerColor = NamazTvTheme.colors.surfaceStrong.copy(alpha = 0.74f),
    contentColor = NamazTvTheme.colors.textPrimary,
    focusedContainerColor = NamazTvTheme.colors.accent,
    focusedContentColor = NamazTvTheme.colors.backgroundBottom,
)
