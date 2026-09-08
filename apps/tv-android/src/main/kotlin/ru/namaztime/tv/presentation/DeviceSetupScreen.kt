package ru.namaztime.tv.presentation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import ru.namaztime.tv.R
import ru.namaztime.tv.sync.CanonicalCityCandidate
import ru.namaztime.tv.sync.DeviceScheduleChoice
import ru.namaztime.tv.sync.DeviceSchedulePreviewPrayer
import java.time.format.DateTimeFormatter

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
const val DEVICE_SETUP_PREVIEW_TAG = "device-setup-preview"
const val DEVICE_SETUP_PREVIEW_ROW_TAG_PREFIX = "device-setup-preview-row-"
const val DEVICE_SETUP_ACTIVATE_PREVIEW_TAG = "device-setup-activate-preview"
const val DEVICE_SETUP_SOURCE_LINK_TAG = "device-setup-source-link"
const val DEVICE_SETUP_PROVENANCE_TAG = "device-setup-provenance"
const val DEVICE_SETUP_PROVENANCE_CONTENT_TAG = "device-setup-provenance-content"

data class ActiveScheduleSummaryUi(
    val cityName: String,
    val authorityName: String,
    val sourceName: String,
    val timezone: String,
)

/** Renders the device-scoped canonical-city and schedule-choice setup flow. */
@OptIn(ExperimentalLayoutApi::class)
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
    onActivatePreview: () -> Unit = {},
    onOpenSource: ((String) -> Boolean)? = null,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val sourceLink = onOpenSource ?: remember(context) {
        { url: String -> launchSourceLink(url) { intent ->
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } }
    }
    val imeVisible = WindowInsets.isImeVisible
    BackHandler {
        if (imeVisible) {
            keyboardController?.hide()
        } else {
            onBack()
        }
    }
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
                val localPreviewFlow = state.step == DeviceSetupStep.PREVIEW ||
                    (state.choices as? ScheduleChoicesUiState.Available)
                        ?.set?.choices?.any { it.localPreview != null } == true
                val previewActivationAllowed =
                    (state.submission as? ScheduleChoiceSubmissionUiState.Preview)
                        ?.choice?.activationAllowed == true
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (compact) 24.dp else 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 28.dp else 44.dp),
                ) {
                    SetupContextPanel(
                        activeSchedule = activeSchedule,
                        previewVisible = localPreviewFlow,
                        requestBackFocus = state.step == DeviceSetupStep.PREVIEW &&
                            !previewActivationAllowed,
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
                        DeviceSetupStep.PREVIEW -> SchedulePreviewPanel(
                            state = state,
                            onActivatePreview = onActivatePreview,
                            onOpenSource = sourceLink,
                            compact = compact || (state.submission as? ScheduleChoiceSubmissionUiState.Preview)?.schedule?.dataClassification == "production",
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
    previewVisible: Boolean,
    requestBackFocus: Boolean,
    onBack: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val backRequester = remember { FocusRequester() }
    LaunchedEffect(requestBackFocus, backRequester) {
        if (requestBackFocus) {
            withFrameNanos { }
            runCatching { backRequester.requestFocus() }
        }
    }
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
            modifier = Modifier.fillMaxWidth(),
            color = NamazTvTheme.colors.accent,
            fontSize = if (compact) 16.sp else 20.sp,
            lineHeight = if (compact) 20.sp else 25.sp,
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
            text = appString(
                if (previewVisible) {
                    R.string.device_setup_preview_active_note
                } else {
                    R.string.device_setup_last_known_good_note
                },
            ),
            color = NamazTvTheme.colors.textSecondary,
            fontSize = if (compact) 14.sp else 17.sp,
            lineHeight = if (compact) 19.sp else 23.sp,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onBack,
            modifier = Modifier.focusRequester(backRequester),
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
                    appString(if (search.code == "setup_local_not_configured") R.string.device_setup_local_not_configured else R.string.device_setup_search_error),
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
    val keyboardController = LocalSoftwareKeyboardController.current
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
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        firstResultRequester?.requestFocus()
                        firstResultRequester != null
                    }
                    Key.DirectionCenter,
                    Key.Enter,
                    -> {
                        keyboardController?.show()
                        keyboardController != null
                    }
                    else -> false
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
    LaunchedEffect(available?.set?.choices?.map { it.id }, state.choices, state.submission) {
        val target = when {
            (state.submission as? ScheduleChoiceSubmissionUiState.Error)?.retryable == true -> {
                requestRetryRequester
            }
            (state.choices as? ScheduleChoicesUiState.Error)?.retryable == true -> {
                choicesRetryRequester
            }
            available?.set?.choices?.isNotEmpty() == true &&
                state.submission !is ScheduleChoiceSubmissionUiState.Submitting -> {
                firstChoiceRequester
            }
            else -> null
        }
        if (target != null) {
            withFrameNanos { }
            runCatching { target.requestFocus() }
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
                    is ScheduleChoiceSubmissionUiState.Preview -> Unit
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
    val evidenceLabels = choice.authorities
        .map { it.evidenceLabel }
        .distinct()
        .joinToString(", ")
    Button(
        onClick = {
            if ((choice.localPreview != null || choice.requestable && !choice.executable) &&
                !submitting
            ) {
                onClick()
            }
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
                text = choice.qualification?.let { qualification ->
                    appString(
                        R.string.device_setup_choice_qualification,
                        qualification.qualificationId,
                        qualification.freshThrough,
                    )
                } ?: choice.source.freshThrough?.let { freshThrough ->
                    appString(
                        R.string.device_setup_choice_provenance_fresh,
                        evidenceLabels,
                        choice.approvalId.orEmpty(),
                        freshThrough.toString(),
                    )
                } ?: appString(
                    R.string.device_setup_choice_provenance_undated,
                    evidenceLabels,
                    choice.approvalId.orEmpty(),
                ),
                fontSize = if (compact) 10.sp else 12.sp,
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
                    text = appString(if (choice.qualification != null) R.string.device_setup_choice_qualified_available else R.string.device_setup_choice_available),
                    color = NamazTvTheme.colors.backgroundBottom,
                    fontSize = if (compact) 11.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SchedulePreviewPanel(
    state: DeviceSetupUiState,
    onActivatePreview: () -> Unit,
    onOpenSource: (String) -> Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val preview = state.submission as? ScheduleChoiceSubmissionUiState.Preview
    val city = state.selectedCity
    val activateRequester = remember { FocusRequester() }
    val detailsRequester = remember { FocusRequester() }
    val activating = state.activation is ScheduleActivationUiState.Activating
    val productionPreview = preview?.schedule?.dataClassification == "production"
    var sourceLinkUnavailable by remember(preview?.choice?.id) { mutableStateOf(false) }
    var showDetails by rememberSaveable(preview?.choice?.id) { mutableStateOf(false) }
    var restoreDetailsFocus by rememberSaveable(preview?.choice?.id) { mutableStateOf(false) }
    val closeDetails = {
        showDetails = false
        restoreDetailsFocus = true
    }
    BackHandler(enabled = showDetails, onBack = closeDetails)
    LaunchedEffect(preview?.choice?.id, preview?.choice?.activationAllowed, showDetails) {
        if (!showDetails && (restoreDetailsFocus || preview?.choice?.activationAllowed == true)) {
            withFrameNanos { }
            runCatching {
                if (restoreDetailsFocus) detailsRequester.requestFocus() else activateRequester.requestFocus()
            }
        }
    }
    if (showDetails && preview != null) {
        ScheduleProvenancePanel(
            preview = preview,
            onBack = closeDetails,
            onOpenSource = onOpenSource,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier.testTag(DEVICE_SETUP_PREVIEW_TAG),
        verticalArrangement = Arrangement.spacedBy(if (productionPreview) 4.dp else if (compact) 7.dp else 12.dp),
    ) {
        Text(
            text = city?.canonicalName.orEmpty(),
            modifier = Modifier.semantics { heading() },
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 27.sp else 36.sp,
            lineHeight = if (compact) 32.sp else 43.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (preview == null) {
            SetupMessage(appString(R.string.device_setup_choice_request_error), compact, warning = true)
            return@Column
        }
        val production = preview.schedule.dataClassification == "production"
        val canonicalUrl = preview.choice.source.canonicalUrl?.takeIf { production && sourceLinkIntent(it) != null }
        Text(
            text = preview.choice.authorityLabel,
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 17.sp else 22.sp,
            lineHeight = if (compact) 21.sp else 27.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = if (production) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = appString(if (production) R.string.device_setup_signed_preview else R.string.device_setup_demo_result),
                modifier = Modifier.weight(0.46f),
                color = NamazTvTheme.colors.accent,
                fontSize = if (compact) 17.sp else 22.sp,
                lineHeight = if (compact) 21.sp else 27.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (production) "${preview.schedule.date} · ${preview.schedule.timezone}" else appString(
                    R.string.device_setup_preview_context,
                    preview.schedule.date.toString(),
                    preview.schedule.timezone,
                    preview.schedule.evidenceLabel,
                ),
                modifier = Modifier.weight(0.54f),
                color = NamazTvTheme.colors.textSecondary,
                fontSize = if (compact) 12.sp else 15.sp,
                lineHeight = if (compact) 15.sp else 19.sp,
                textAlign = TextAlign.End,
            )
        }
        TvGlassPanel(
            // Header plus six 26dp rows and 8dp vertical insets. Long provenance must
            // never take height away from the prayer values; full text has its own view.
            modifier = Modifier.fillMaxWidth().then(
                if (production) Modifier.height(198.dp) else Modifier.weight(1f),
            ),
            radius = 18.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(
                    horizontal = if (compact) 18.dp else 24.dp,
                    vertical = if (compact) 8.dp else 14.dp,
                ),
            ) {
                PreviewScheduleRow(
                    prayer = appString(R.string.prayer_column),
                    adhan = appString(R.string.adhan_column),
                    iqamah = appString(R.string.iqamah_column),
                    compact = compact,
                    header = true,
                    dense = production,
                )
                preview.schedule.rows.forEach { row ->
                    PreviewScheduleRow(
                        prayer = previewPrayerLabel(row.prayer),
                        adhan = previewTimeFormatter.format(row.adhan),
                        iqamah = row.iqamah?.let(previewTimeFormatter::format) ?: "—",
                        compact = compact,
                        dense = production,
                        modifier = Modifier.testTag(
                            "$DEVICE_SETUP_PREVIEW_ROW_TAG_PREFIX${row.prayer.name.lowercase()}",
                        ),
                    )
                }
            }
        }
        if (production) {
            Text(
                text = "${preview.choice.effectiveFrom} — ${preview.choice.effectiveTo}",
                color = NamazTvTheme.colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            if (state.activation !is ScheduleActivationUiState.Error && !sourceLinkUnavailable) {
                Text(
                    text = preview.choice.scopeDescription,
                    color = NamazTvTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                preview.schedule.provenance?.attribution?.let { attribution ->
                    Text(
                        text = attribution,
                        color = NamazTvTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
          if (preview.choice.activationAllowed) {
            Button(
                onClick = onActivatePreview,
                enabled = !activating,
                colors = setupButtonColors(),
                modifier = Modifier
                    .testTag(DEVICE_SETUP_ACTIVATE_PREVIEW_TAG)
                    .focusRequester(activateRequester),
            ) {
                Text(
                    appString(
                        if (activating) {
                            R.string.device_setup_activating_preview
                        } else {
                            R.string.device_setup_activate_preview
                        },
                    ),
                    fontSize = if (production) 14.sp else 20.sp,
                    lineHeight = if (production) 18.sp else 24.sp,
                )
            }
          }
          if (canonicalUrl != null) {
            Button(
                onClick = { sourceLinkUnavailable = !onOpenSource(canonicalUrl) },
                colors = setupButtonColors(),
                modifier = Modifier.testTag(DEVICE_SETUP_SOURCE_LINK_TAG),
            ) { Text(appString(R.string.device_setup_source_link), fontSize = 14.sp, lineHeight = 18.sp) }
          }
          if (production) {
            Button(
                onClick = { showDetails = true },
                colors = setupButtonColors(),
                modifier = Modifier.focusRequester(detailsRequester),
            ) { Text(appString(R.string.device_setup_provenance_action), fontSize = 14.sp, lineHeight = 18.sp) }
          }
        }
        val activationError = state.activation as? ScheduleActivationUiState.Error
        if (activationError != null) {
            Text(
                text = appString(R.string.device_setup_activation_error),
                color = NamazTvTheme.colors.warning,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        if (sourceLinkUnavailable) {
            Text(
                appString(R.string.device_setup_source_browser_unavailable),
                color = NamazTvTheme.colors.warning,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        if (!production) {
            Text(
                text = appString(R.string.device_setup_demo_explanation),
                color = NamazTvTheme.colors.textSecondary,
                fontSize = if (compact) 12.sp else 15.sp,
                lineHeight = if (compact) 15.sp else 19.sp,
            )
        }
    }
}

@Composable
private fun ScheduleProvenancePanel(
    preview: ScheduleChoiceSubmissionUiState.Preview,
    onBack: () -> Unit,
    onOpenSource: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val firstParagraphRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 96.dp.roundToPx() }
    var textFocused by remember { mutableStateOf(false) }
    var sourceLinkUnavailable by remember(preview.choice.id) { mutableStateOf(false) }
    val choice = preview.choice
    val schedule = preview.schedule
    val canonicalUrl = choice.source.canonicalUrl?.takeIf { sourceLinkIntent(it) != null }
    val paragraphs = buildList {
        add(appString(R.string.field_authority) to choice.authorityLabel)
        add(appString(R.string.device_setup_scope) to choice.scopeDescription)
        add(appString(R.string.field_coverage) to "${choice.effectiveFrom} — ${choice.effectiveTo}")
        add(appString(R.string.device_setup_provenance_choice) to choice.displayLabel)
        add(appString(R.string.field_source_type) to "${choice.source.kind} · ${choice.source.id}")
        add(appString(R.string.field_timezone) to "${schedule.date} · ${schedule.timezone}")
        add(appString(R.string.device_setup_provenance_basis) to (
            choice.qualification?.let {
                appString(R.string.device_setup_choice_qualification, it.qualificationId, it.freshThrough)
            } ?: choice.source.freshThrough?.let { freshThrough -> appString(
                R.string.device_setup_choice_provenance_fresh,
                schedule.evidenceLabel,
                choice.approvalId.orEmpty(),
                freshThrough.toString(),
            ) } ?: appString(
                R.string.device_setup_choice_provenance_undated,
                schedule.evidenceLabel,
                choice.approvalId.orEmpty(),
            )
        ))
        choice.qualification?.let {
            add(appString(R.string.field_qualification_id) to "${it.qualificationId}\n${it.sha256}\n${schedule.evidenceLabel}")
        }
        add(appString(R.string.field_source_id) to "${choice.policyId} · ${choice.source.id} · ${choice.scopeId}")
        schedule.provenance?.let { provenance ->
            add(appString(R.string.field_snapshot_id) to provenance.snapshotId)
            add(appString(R.string.field_raw_hash) to provenance.rawSha256)
            add(appString(R.string.device_setup_snapshot_hash) to provenance.canonicalSha256)
            add(appString(R.string.field_parser) to "${provenance.parserVersion} · ${provenance.retrievedAt}")
            provenance.attribution?.let { add(appString(R.string.device_setup_attribution) to it) }
        }
        canonicalUrl?.let { add(appString(R.string.device_setup_source_link) to it) }
        add(appString(R.string.device_setup_provenance_note) to appString(
            if (choice.qualification != null) R.string.device_setup_public_preview_explanation
            else R.string.device_setup_legacy_preview_explanation,
        ))
    }
    LaunchedEffect(preview.choice.id) {
        withFrameNanos { }
        firstParagraphRequester.requestFocus()
    }
    Column(
        modifier = modifier
            .testTag(DEVICE_SETUP_PROVENANCE_TAG)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                    onBack()
                    true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            appString(R.string.device_setup_provenance_title),
            modifier = Modifier.semantics { heading() },
            color = NamazTvTheme.colors.textPrimary,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            appString(R.string.device_setup_provenance_scroll),
            color = NamazTvTheme.colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(DEVICE_SETUP_PROVENANCE_CONTENT_TAG)
                .border(
                    if (textFocused) 2.dp else 1.dp,
                    if (textFocused) NamazTvTheme.colors.focus else NamazTvTheme.colors.surfaceOutline,
                    RoundedCornerShape(10.dp),
                )
                .focusRequester(firstParagraphRequester)
                .onFocusChanged { textFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val delta = when (event.key) {
                        Key.DirectionDown -> scrollStep
                        Key.DirectionUp -> -scrollStep
                        else -> return@onPreviewKeyEvent false
                    }
                    val target = (scrollState.value + delta).coerceIn(0, scrollState.maxValue)
                    if (target == scrollState.value) return@onPreviewKeyEvent false
                    scrollScope.launch { scrollState.scrollTo(target) }
                    true
                }
                .focusable()
                .verticalScroll(scrollState)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            paragraphs.forEach { (label, value) ->
                key(label) { ProvenanceParagraph(label, value) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack, colors = setupButtonColors()) {
                Text(appString(R.string.device_setup_back), fontSize = 14.sp)
            }
            if (canonicalUrl != null) {
                Button(
                    onClick = { sourceLinkUnavailable = !onOpenSource(canonicalUrl) },
                    modifier = Modifier.testTag(DEVICE_SETUP_SOURCE_LINK_TAG),
                    colors = setupButtonColors(),
                ) { Text(appString(R.string.device_setup_source_link), fontSize = 14.sp) }
            }
        }
        if (sourceLinkUnavailable) {
            Text(
                appString(R.string.device_setup_source_browser_unavailable),
                color = NamazTvTheme.colors.warning,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun ProvenanceParagraph(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                NamazTvTheme.colors.surfaceOutline,
                RoundedCornerShape(10.dp),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, color = NamazTvTheme.colors.accent, fontSize = 12.sp, lineHeight = 16.sp)
        Text(value, color = NamazTvTheme.colors.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun PreviewScheduleRow(
    prayer: String,
    adhan: String,
    iqamah: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    header: Boolean = false,
    dense: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(if (dense) 26.dp else if (compact) 32.dp else 42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = prayer,
            modifier = Modifier.weight(1.4f),
            color = if (header) NamazTvTheme.colors.textSecondary else NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 14.sp else 18.sp,
            lineHeight = if (compact) 20.sp else 26.sp,
            fontWeight = if (header) FontWeight.Medium else FontWeight.SemiBold,
        )
        Text(
            text = adhan,
            modifier = Modifier.weight(1f),
            color = if (header) NamazTvTheme.colors.textSecondary else NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 14.sp else 18.sp,
            lineHeight = if (compact) 20.sp else 26.sp,
            fontWeight = if (header) FontWeight.Medium else FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = iqamah,
            modifier = Modifier.weight(1f),
            color = if (header) NamazTvTheme.colors.textSecondary else NamazTvTheme.colors.accent,
            fontSize = if (compact) 14.sp else 18.sp,
            lineHeight = if (compact) 20.sp else 26.sp,
            fontWeight = if (header) FontWeight.Medium else FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun previewPrayerLabel(prayer: DeviceSchedulePreviewPrayer): String = appString(
    when (prayer) {
        DeviceSchedulePreviewPrayer.FAJR -> R.string.prayer_fajr
        DeviceSchedulePreviewPrayer.SUNRISE -> R.string.prayer_sunrise
        DeviceSchedulePreviewPrayer.DHUHR -> R.string.prayer_dhuhr
        DeviceSchedulePreviewPrayer.ASR -> R.string.prayer_asr
        DeviceSchedulePreviewPrayer.MAGHRIB -> R.string.prayer_maghrib
        DeviceSchedulePreviewPrayer.ISHA -> R.string.prayer_isha
    },
)

private val previewTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
            SetupMessage(
                appString(R.string.device_setup_pending_explanation),
                compact,
            )
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
