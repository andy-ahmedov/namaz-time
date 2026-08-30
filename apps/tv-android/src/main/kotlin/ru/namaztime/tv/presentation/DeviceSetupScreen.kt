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
import ru.namaztime.tv.sync.DeviceScheduleChoice

const val DEVICE_SETUP_SCREEN_TAG = "device-setup-screen"
const val DEVICE_SETUP_SEARCH_FIELD_TAG = "device-setup-search-field"
const val DEVICE_SETUP_CITY_LIST_TAG = "device-setup-city-list"
const val DEVICE_SETUP_CITY_RESULT_TAG_PREFIX = "device-setup-city-result-"
const val DEVICE_SETUP_RETRY_TAG = "device-setup-retry"
const val DEVICE_SETUP_CHOICE_LIST_TAG = "device-setup-choice-list"
const val DEVICE_SETUP_CHOICE_TAG_PREFIX = "device-setup-choice-"
const val DEVICE_SETUP_CHOICES_RETRY_TAG = "device-setup-choices-retry"
const val DEVICE_SETUP_REQUEST_RETRY_TAG = "device-setup-request-retry"
const val DEVICE_SETUP_PENDING_TAG = "device-setup-pending"

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
    onRetryScheduleChoices: () -> Unit = {},
    onScheduleChoiceSelected: (DeviceScheduleChoice) -> Unit = {},
    onRetryScheduleChoiceRequest: () -> Unit = {},
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
                        DeviceSetupStep.CHOICES -> ScheduleChoicesPanel(
                            state = state,
                            onRetryScheduleChoices = onRetryScheduleChoices,
                            onScheduleChoiceSelected = onScheduleChoiceSelected,
                            onRetryScheduleChoiceRequest = onRetryScheduleChoiceRequest,
                            compact = compact,
                            modifier = Modifier
                                .weight(0.66f)
                                .fillMaxHeight(),
                        )
                        DeviceSetupStep.PENDING -> PendingChoicePanel(
                            state = state,
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
private fun ScheduleChoicesPanel(
    state: DeviceSetupUiState,
    onRetryScheduleChoices: () -> Unit,
    onScheduleChoiceSelected: (DeviceScheduleChoice) -> Unit,
    onRetryScheduleChoiceRequest: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val city = state.selectedCity ?: return
    val available = state.choices as? ScheduleChoicesUiState.Available
    val firstChoiceRequester = remember(available?.set?.choices?.map { it.id }) { FocusRequester() }
    val choicesRetryRequester = remember { FocusRequester() }
    val requestRetryRequester = remember { FocusRequester() }
    LaunchedEffect(available?.set?.choices?.map { it.id }, state.submission) {
        if (available?.set?.choices?.isNotEmpty() == true &&
            state.submission !is ScheduleChoiceSubmissionUiState.Submitting
        ) {
            withFrameNanos { }
            runCatching { firstChoiceRequester.requestFocus() }
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        Text(
            text = city.canonicalName,
            modifier = Modifier.semantics { heading() },
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 27.sp else 36.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = appString(
                R.string.device_setup_selected_city_context,
                city.federalSubjectName,
                city.timezone,
            ),
            color = NamazTvTheme.colors.textSecondary,
            fontSize = if (compact) 14.sp else 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (val choices = state.choices) {
            ScheduleChoicesUiState.Idle,
            ScheduleChoicesUiState.Loading,
            -> SetupMessage(appString(R.string.device_setup_choices_loading), compact)
            is ScheduleChoicesUiState.Unavailable -> SetupMessage(
                appString(R.string.device_setup_choices_unavailable),
                compact,
                warning = true,
            )
            ScheduleChoicesUiState.NotProvisioned -> SetupMessage(
                appString(R.string.device_setup_not_provisioned),
                compact,
                warning = true,
            )
            ScheduleChoicesUiState.Unauthorized -> SetupMessage(
                appString(R.string.device_setup_unauthorized),
                compact,
                warning = true,
            )
            is ScheduleChoicesUiState.Error -> {
                SetupMessage(
                    appString(R.string.device_setup_choices_error),
                    compact,
                    warning = true,
                )
                if (choices.retryable) {
                    Button(
                        onClick = onRetryScheduleChoices,
                        modifier = Modifier
                            .testTag(DEVICE_SETUP_CHOICES_RETRY_TAG)
                            .focusRequester(choicesRetryRequester),
                        colors = setupButtonColors(),
                    ) {
                        Text(appString(R.string.device_setup_retry))
                    }
                }
            }
            is ScheduleChoicesUiState.Available -> {
                Text(
                    text = if (choices.set.selectionRequired) {
                        appString(R.string.device_setup_choices_multiple)
                    } else {
                        appString(R.string.device_setup_choices_single)
                    },
                    color = NamazTvTheme.colors.accent,
                    fontSize = if (compact) 14.sp else 18.sp,
                    lineHeight = if (compact) 18.sp else 23.sp,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag(DEVICE_SETUP_CHOICE_LIST_TAG),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
                ) {
                    itemsIndexed(
                        items = choices.set.choices,
                        key = { _, choice -> choice.id },
                    ) { index, choice ->
                        ScheduleChoiceButton(
                            choice = choice,
                            submitting = state.submission is ScheduleChoiceSubmissionUiState.Submitting,
                            onClick = { onScheduleChoiceSelected(choice) },
                            firstChoiceRequester = firstChoiceRequester.takeIf { index == 0 },
                            compact = compact,
                        )
                    }
                }
                when (val submission = state.submission) {
                    ScheduleChoiceSubmissionUiState.Idle -> Unit
                    is ScheduleChoiceSubmissionUiState.Submitting -> SetupMessage(
                        appString(
                            R.string.device_setup_choice_submitting,
                            submission.choice.authorityLabel,
                        ),
                        compact,
                    )
                    is ScheduleChoiceSubmissionUiState.Error -> {
                        SetupMessage(
                            appString(R.string.device_setup_choice_request_error),
                            compact,
                            warning = true,
                        )
                        if (submission.retryable) {
                            Button(
                                onClick = onRetryScheduleChoiceRequest,
                                modifier = Modifier
                                    .testTag(DEVICE_SETUP_REQUEST_RETRY_TAG)
                                    .focusRequester(requestRetryRequester),
                                colors = setupButtonColors(),
                            ) {
                                Text(appString(R.string.device_setup_retry))
                            }
                        }
                    }
                    is ScheduleChoiceSubmissionUiState.Pending -> Unit
                }
            }
        }
    }
}

@Composable
private fun ScheduleChoiceButton(
    choice: DeviceScheduleChoice,
    submitting: Boolean,
    onClick: () -> Unit,
    firstChoiceRequester: FocusRequester?,
    compact: Boolean,
) {
    Button(
        onClick = {
            if (choice.requestable && !choice.executable && !submitting) onClick()
        },
        enabled = choice.selectable && !submitting,
        colors = setupButtonColors(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$DEVICE_SETUP_CHOICE_TAG_PREFIX${choice.id}")
            .then(
                if (firstChoiceRequester == null) Modifier else Modifier.focusRequester(
                    firstChoiceRequester,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (compact) 1.dp else 3.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp),
        ) {
            Text(
                text = choice.authorityLabel,
                fontSize = if (compact) 17.sp else 21.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = choice.scopeDescription,
                fontSize = if (compact) 12.sp else 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = appString(
                    R.string.device_setup_choice_source,
                    choice.source.kind,
                    choice.source.id,
                ),
                fontSize = if (compact) 11.sp else 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = appString(
                    R.string.device_setup_choice_effective,
                    choice.effectiveFrom.toString(),
                    choice.effectiveTo.toString(),
                    scheduleKindLabel(choice.scheduleKind),
                ),
                fontSize = if (compact) 11.sp else 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = appString(
                    R.string.device_setup_choice_identity,
                    choice.policyId,
                    choice.source.id,
                ),
                fontSize = if (compact) 10.sp else 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (choice.executable) {
                Text(
                    text = appString(R.string.device_setup_choice_active),
                    color = NamazTvTheme.colors.backgroundBottom,
                    fontSize = if (compact) 11.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PendingChoicePanel(
    state: DeviceSetupUiState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val pending = state.submission as? ScheduleChoiceSubmissionUiState.Pending
    val city = state.selectedCity
    Column(
        modifier = modifier.testTag(DEVICE_SETUP_PENDING_TAG),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
    ) {
        Text(
            text = city?.canonicalName.orEmpty(),
            modifier = Modifier.semantics { heading() },
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 30.sp else 40.sp,
            fontWeight = FontWeight.Bold,
        )
        if (pending == null) {
            SetupMessage(appString(R.string.device_setup_choice_request_error), compact, warning = true)
        } else {
            Text(
                text = appString(
                    R.string.device_setup_choice_selected,
                    pending.choice.authorityLabel,
                ),
                color = NamazTvTheme.colors.textPrimary,
                fontSize = if (compact) 19.sp else 25.sp,
                lineHeight = if (compact) 25.sp else 32.sp,
            )
            Text(
                text = appString(R.string.device_setup_pending_review),
                color = NamazTvTheme.colors.accent,
                fontSize = if (compact) 22.sp else 30.sp,
                fontWeight = FontWeight.Bold,
            )
            SetupMessage(appString(R.string.device_setup_pending_explanation), compact)
        }
    }
}

@Composable
private fun scheduleKindLabel(kind: String): String = appString(
    if (kind == "timetable") {
        R.string.device_setup_schedule_type_timetable
    } else {
        R.string.device_setup_schedule_type_calculation
    },
)

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
