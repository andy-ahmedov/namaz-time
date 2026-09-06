package ru.namaztime.tv.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.namaztime.tv.sync.CanonicalCityCandidate
import ru.namaztime.tv.sync.DeviceCityScheduleChoiceSet
import ru.namaztime.tv.sync.DeviceScheduleChoice
import ru.namaztime.tv.sync.DeviceSchedulePreview
import ru.namaztime.tv.sync.DeviceSetupGateway
import ru.namaztime.tv.sync.DeviceSetupResult
import ru.namaztime.tv.sync.PendingDeviceScheduleChoiceRequest

enum class DeviceSetupStep {
    SEARCH,
    CHOICES,
    PREVIEW,
    PENDING,
}

sealed interface CitySearchUiState {
    data object EmptyInput : CitySearchUiState
    data object Waiting : CitySearchUiState
    data object Loading : CitySearchUiState
    data class Results(val candidates: List<CanonicalCityCandidate>) : CitySearchUiState
    data object NoResults : CitySearchUiState
    data object NotProvisioned : CitySearchUiState
    data object Unauthorized : CitySearchUiState
    data class Error(val code: String, val retryable: Boolean) : CitySearchUiState
}

sealed interface ScheduleChoicesUiState {
    data object Idle : ScheduleChoicesUiState
    data object Loading : ScheduleChoicesUiState
    data class Available(val set: DeviceCityScheduleChoiceSet) : ScheduleChoicesUiState
    data class Unavailable(val reason: String) : ScheduleChoicesUiState
    data object NotProvisioned : ScheduleChoicesUiState
    data object Unauthorized : ScheduleChoicesUiState
    data class Error(val code: String, val retryable: Boolean) : ScheduleChoicesUiState
}

sealed interface ScheduleChoiceSubmissionUiState {
    data object Idle : ScheduleChoiceSubmissionUiState
    data class Submitting(val choice: DeviceScheduleChoice) : ScheduleChoiceSubmissionUiState
    data class Preview(
        val choice: DeviceScheduleChoice,
        val schedule: DeviceSchedulePreview,
    ) : ScheduleChoiceSubmissionUiState
    data class Pending(
        val request: PendingDeviceScheduleChoiceRequest,
        val choice: DeviceScheduleChoice,
    ) : ScheduleChoiceSubmissionUiState
    data class Error(
        val choice: DeviceScheduleChoice,
        val interactionId: String,
        val code: String,
        val retryable: Boolean,
    ) : ScheduleChoiceSubmissionUiState
}

sealed interface ScheduleActivationUiState {
    data object Idle : ScheduleActivationUiState
    data class Activating(val choice: DeviceScheduleChoice) : ScheduleActivationUiState
    data class Activated(val choice: DeviceScheduleChoice) : ScheduleActivationUiState
    data class Error(
        val choice: DeviceScheduleChoice,
        val code: String,
        val retryable: Boolean,
    ) : ScheduleActivationUiState
}

data class DeviceSetupUiState(
    val query: String = "",
    val step: DeviceSetupStep = DeviceSetupStep.SEARCH,
    val search: CitySearchUiState = CitySearchUiState.EmptyInput,
    val selectedCity: CanonicalCityCandidate? = null,
    val choices: ScheduleChoicesUiState = ScheduleChoicesUiState.Idle,
    val submission: ScheduleChoiceSubmissionUiState = ScheduleChoiceSubmissionUiState.Idle,
    val activation: ScheduleActivationUiState = ScheduleActivationUiState.Idle,
)

interface DeviceSetupController {
    val state: StateFlow<DeviceSetupUiState>
    fun onQueryChanged(query: String)
    fun retrySearch()
    fun selectCity(city: CanonicalCityCandidate)
    fun retryScheduleChoices()
    fun selectScheduleChoice(choice: DeviceScheduleChoice)
    fun activatePreview() = Unit
    fun retryScheduleChoiceRequest()
    fun backToSearch()
    fun resetAfterExit()
}

class DeviceSetupViewModel(
    private val setupGateway: DeviceSetupGateway,
    private val savedStateHandle: SavedStateHandle,
    private val debounceMillis: Long = DEFAULT_CITY_SEARCH_DEBOUNCE_MILLIS,
    private val clock: Clock = Clock.systemUTC(),
    private val interactionIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel(), DeviceSetupController {
    private val initialQuery = savedStateHandle.get<String>(SAVED_QUERY).orEmpty()
        .takeCodePoints(MAX_CITY_QUERY_CODE_POINTS)
    private val mutableState = MutableStateFlow(
        DeviceSetupUiState(
            query = initialQuery,
            search = if (initialQuery.isBlank()) {
                CitySearchUiState.EmptyInput
            } else {
                CitySearchUiState.Waiting
            },
        ),
    )
    override val state: StateFlow<DeviceSetupUiState> = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var choicesJob: Job? = null
    private var submissionJob: Job? = null
    private var activationJob: Job? = null

    init {
        if (initialQuery.isNotBlank()) scheduleSearch(initialQuery.trim())
    }

    override fun onQueryChanged(query: String) {
        val bounded = query.takeCodePoints(MAX_CITY_QUERY_CODE_POINTS)
        savedStateHandle[SAVED_QUERY] = bounded
        cancelSetupJobs()
        val normalized = bounded.trim()
        if (normalized.isEmpty()) {
            mutableState.value = DeviceSetupUiState(query = bounded)
            return
        }
        mutableState.value = mutableState.value.copy(
            query = bounded,
            step = DeviceSetupStep.SEARCH,
            search = CitySearchUiState.Waiting,
            selectedCity = null,
            choices = ScheduleChoicesUiState.Idle,
            submission = ScheduleChoiceSubmissionUiState.Idle,
            activation = ScheduleActivationUiState.Idle,
        )
        scheduleSearch(normalized)
    }

    override fun retrySearch() {
        val normalized = mutableState.value.query.trim()
        if (normalized.isEmpty()) {
            mutableState.value = mutableState.value.copy(search = CitySearchUiState.EmptyInput)
            return
        }
        cancelSetupJobs()
        mutableState.value = mutableState.value.copy(
            step = DeviceSetupStep.SEARCH,
            search = CitySearchUiState.Waiting,
            selectedCity = null,
            choices = ScheduleChoicesUiState.Idle,
            submission = ScheduleChoiceSubmissionUiState.Idle,
            activation = ScheduleActivationUiState.Idle,
        )
        scheduleSearch(normalized)
    }

    override fun selectCity(city: CanonicalCityCandidate) {
        val candidates = (mutableState.value.search as? CitySearchUiState.Results)?.candidates
            ?: return
        val exact = candidates.singleOrNull { it.id == city.id } ?: return
        cancelSetupJobs()
        mutableState.value = mutableState.value.copy(
            step = DeviceSetupStep.CHOICES,
            selectedCity = exact,
            choices = ScheduleChoicesUiState.Loading,
            submission = ScheduleChoiceSubmissionUiState.Idle,
            activation = ScheduleActivationUiState.Idle,
        )
        loadChoices(exact)
    }

    override fun retryScheduleChoices() {
        val city = mutableState.value.selectedCity ?: return
        choicesJob?.cancel()
        submissionJob?.cancel()
        activationJob?.cancel()
        mutableState.value = mutableState.value.copy(
            step = DeviceSetupStep.CHOICES,
            choices = ScheduleChoicesUiState.Loading,
            submission = ScheduleChoiceSubmissionUiState.Idle,
            activation = ScheduleActivationUiState.Idle,
        )
        loadChoices(city)
    }

    override fun selectScheduleChoice(choice: DeviceScheduleChoice) {
        val current = mutableState.value
        val set = (current.choices as? ScheduleChoicesUiState.Available)?.set ?: return
        val exact = set.choices.singleOrNull { it.id == choice.id } ?: return
        if (!exact.selectable) return
        exact.localPreview?.let { preview ->
            submissionJob?.cancel()
            activationJob?.cancel()
            mutableState.value = current.copy(
                step = DeviceSetupStep.PREVIEW,
                submission = ScheduleChoiceSubmissionUiState.Preview(exact, preview),
                activation = ScheduleActivationUiState.Idle,
            )
            return
        }
        if (!exact.requestable || exact.executable || !set.requestAllowed) return
        submissionJob?.cancel()
        submitChoice(
            city = current.selectedCity ?: return,
            set = set,
            choice = exact,
            interactionId = interactionIdFactory(),
        )
    }

    override fun activatePreview() {
        val current = mutableState.value
        val preview = current.submission as? ScheduleChoiceSubmissionUiState.Preview ?: return
        val city = current.selectedCity ?: return
        if (!preview.choice.activationAllowed ||
            current.activation is ScheduleActivationUiState.Activating ||
            current.activation is ScheduleActivationUiState.Activated
        ) {
            return
        }
        activationJob?.cancel()
        mutableState.value = current.copy(
            activation = ScheduleActivationUiState.Activating(preview.choice),
        )
        activationJob = viewModelScope.launch {
            val result = try {
                setupGateway.activateScheduleChoice(
                    cityId = city.id,
                    choiceId = preview.choice.id,
                    date = preview.schedule.date,
                )
            } catch (error: CancellationException) {
                throw error
            }
            val latest = mutableState.value
            val latestPreview = latest.submission as? ScheduleChoiceSubmissionUiState.Preview
            if (latest.step != DeviceSetupStep.PREVIEW ||
                latest.selectedCity?.id != city.id ||
                latestPreview?.choice?.id != preview.choice.id
            ) {
                return@launch
            }
            mutableState.value = latest.copy(
                activation = when (result) {
                    is DeviceSetupResult.Success -> ScheduleActivationUiState.Activated(
                        preview.choice,
                    )
                    DeviceSetupResult.NotProvisioned -> ScheduleActivationUiState.Error(
                        preview.choice,
                        "setup_not_provisioned",
                        retryable = false,
                    )
                    DeviceSetupResult.Unauthorized -> ScheduleActivationUiState.Error(
                        preview.choice,
                        "setup_unauthorized",
                        retryable = false,
                    )
                    is DeviceSetupResult.Failure -> ScheduleActivationUiState.Error(
                        preview.choice,
                        result.code,
                        result.retryable,
                    )
                },
            )
        }
    }

    override fun retryScheduleChoiceRequest() {
        val current = mutableState.value
        val failed = current.submission as? ScheduleChoiceSubmissionUiState.Error ?: return
        if (!failed.retryable) return
        val set = (current.choices as? ScheduleChoicesUiState.Available)?.set ?: return
        val city = current.selectedCity ?: return
        submissionJob?.cancel()
        submitChoice(city, set, failed.choice, failed.interactionId)
    }

    override fun backToSearch() {
        choicesJob?.cancel()
        submissionJob?.cancel()
        activationJob?.cancel()
        if (mutableState.value.step == DeviceSetupStep.PREVIEW ||
            mutableState.value.step == DeviceSetupStep.PENDING
        ) {
            mutableState.value = mutableState.value.copy(
                step = DeviceSetupStep.CHOICES,
                submission = ScheduleChoiceSubmissionUiState.Idle,
                activation = ScheduleActivationUiState.Idle,
            )
            return
        }
        mutableState.value = mutableState.value.copy(
            step = DeviceSetupStep.SEARCH,
            selectedCity = null,
            choices = ScheduleChoicesUiState.Idle,
            submission = ScheduleChoiceSubmissionUiState.Idle,
            activation = ScheduleActivationUiState.Idle,
        )
    }

    override fun resetAfterExit() {
        cancelSetupJobs()
        mutableState.value = mutableState.value.copy(
            step = DeviceSetupStep.SEARCH,
            selectedCity = null,
            choices = ScheduleChoicesUiState.Idle,
            submission = ScheduleChoiceSubmissionUiState.Idle,
            activation = ScheduleActivationUiState.Idle,
        )
    }

    private fun scheduleSearch(query: String) {
        searchJob = viewModelScope.launch {
            delay(debounceMillis)
            mutableState.value = mutableState.value.copy(search = CitySearchUiState.Loading)
            val result = try {
                setupGateway.searchCities(query)
            } catch (error: CancellationException) {
                throw error
            }
            mutableState.value = mutableState.value.copy(
                search = when (result) {
                    is DeviceSetupResult.Success -> if (result.value.isEmpty()) {
                        CitySearchUiState.NoResults
                    } else {
                        CitySearchUiState.Results(result.value.toList())
                    }
                    DeviceSetupResult.NotProvisioned -> CitySearchUiState.NotProvisioned
                    DeviceSetupResult.Unauthorized -> CitySearchUiState.Unauthorized
                    is DeviceSetupResult.Failure -> CitySearchUiState.Error(
                        code = result.code,
                        retryable = result.retryable,
                    )
                },
            )
        }
    }

    private fun loadChoices(city: CanonicalCityCandidate) {
        val date = LocalDate.now(clock.withZone(ZoneId.of(city.timezone)))
        choicesJob = viewModelScope.launch {
            val result = try {
                setupGateway.loadScheduleChoices(city.id, date)
            } catch (error: CancellationException) {
                throw error
            }
            if (mutableState.value.selectedCity?.id != city.id ||
                mutableState.value.step != DeviceSetupStep.CHOICES
            ) {
                return@launch
            }
            mutableState.value = mutableState.value.copy(
                choices = when (result) {
                    is DeviceSetupResult.Success -> if (result.value.choices.isEmpty()) {
                        ScheduleChoicesUiState.Unavailable(result.value.automaticResolutionReason)
                    } else {
                        ScheduleChoicesUiState.Available(result.value)
                    }
                    DeviceSetupResult.NotProvisioned -> ScheduleChoicesUiState.NotProvisioned
                    DeviceSetupResult.Unauthorized -> ScheduleChoicesUiState.Unauthorized
                    is DeviceSetupResult.Failure -> ScheduleChoicesUiState.Error(
                        result.code,
                        result.retryable,
                    )
                },
                submission = ScheduleChoiceSubmissionUiState.Idle,
                activation = ScheduleActivationUiState.Idle,
            )
        }
    }

    private fun submitChoice(
        city: CanonicalCityCandidate,
        set: DeviceCityScheduleChoiceSet,
        choice: DeviceScheduleChoice,
        interactionId: String,
    ) {
        mutableState.value = mutableState.value.copy(
            submission = ScheduleChoiceSubmissionUiState.Submitting(choice),
        )
        submissionJob = viewModelScope.launch {
            val result = try {
                setupGateway.requestScheduleChoice(city.id, choice.id, set.date, interactionId)
            } catch (error: CancellationException) {
                throw error
            }
            if (mutableState.value.selectedCity?.id != city.id) return@launch
            mutableState.value = when (result) {
                is DeviceSetupResult.Success -> mutableState.value.copy(
                    step = DeviceSetupStep.PENDING,
                    submission = ScheduleChoiceSubmissionUiState.Pending(result.value, choice),
                )
                DeviceSetupResult.NotProvisioned -> mutableState.value.copy(
                    step = DeviceSetupStep.CHOICES,
                    submission = ScheduleChoiceSubmissionUiState.Error(
                        choice,
                        interactionId,
                        "setup_not_provisioned",
                        retryable = false,
                    ),
                )
                DeviceSetupResult.Unauthorized -> mutableState.value.copy(
                    step = DeviceSetupStep.CHOICES,
                    submission = ScheduleChoiceSubmissionUiState.Error(
                        choice,
                        interactionId,
                        "setup_unauthorized",
                        retryable = false,
                    ),
                )
                is DeviceSetupResult.Failure -> mutableState.value.copy(
                    step = DeviceSetupStep.CHOICES,
                    submission = ScheduleChoiceSubmissionUiState.Error(
                        choice,
                        interactionId,
                        result.code,
                        result.retryable,
                    ),
                )
            }
        }
    }

    private fun cancelSetupJobs() {
        searchJob?.cancel()
        choicesJob?.cancel()
        submissionJob?.cancel()
        activationJob?.cancel()
    }
}

private fun String.takeCodePoints(maximum: Int): String {
    val count = codePointCount(0, length)
    return if (count <= maximum) this else substring(0, offsetByCodePoints(0, maximum))
}

private const val SAVED_QUERY = "device_setup_city_query"
private const val MAX_CITY_QUERY_CODE_POINTS = 200
private const val DEFAULT_CITY_SEARCH_DEBOUNCE_MILLIS = 300L
