package ru.namaztime.tv.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.namaztime.tv.sync.CanonicalCityCandidate
import ru.namaztime.tv.sync.DeviceCitySearchGateway
import ru.namaztime.tv.sync.DeviceSetupResult

enum class DeviceSetupStep {
    SEARCH,
    CHOICES,
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

data class DeviceSetupUiState(
    val query: String = "",
    val step: DeviceSetupStep = DeviceSetupStep.SEARCH,
    val search: CitySearchUiState = CitySearchUiState.EmptyInput,
    val selectedCity: CanonicalCityCandidate? = null,
    val pendingReview: Boolean = false,
)

interface DeviceSetupController {
    val state: StateFlow<DeviceSetupUiState>
    fun onQueryChanged(query: String)
    fun retrySearch()
    fun selectCity(city: CanonicalCityCandidate)
    fun backToSearch()
    fun resetAfterExit()
}

class DeviceSetupViewModel(
    private val cityGateway: DeviceCitySearchGateway,
    private val savedStateHandle: SavedStateHandle,
    private val debounceMillis: Long = DEFAULT_CITY_SEARCH_DEBOUNCE_MILLIS,
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

    init {
        if (initialQuery.isNotBlank()) scheduleSearch(initialQuery.trim())
    }

    override fun onQueryChanged(query: String) {
        val bounded = query.takeCodePoints(MAX_CITY_QUERY_CODE_POINTS)
        savedStateHandle[SAVED_QUERY] = bounded
        searchJob?.cancel()
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
            pendingReview = false,
        )
        scheduleSearch(normalized)
    }

    override fun retrySearch() {
        val normalized = mutableState.value.query.trim()
        if (normalized.isEmpty()) {
            mutableState.value = mutableState.value.copy(search = CitySearchUiState.EmptyInput)
            return
        }
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(
            step = DeviceSetupStep.SEARCH,
            search = CitySearchUiState.Waiting,
            selectedCity = null,
            pendingReview = false,
        )
        scheduleSearch(normalized)
    }

    override fun selectCity(city: CanonicalCityCandidate) {
        val candidates = (mutableState.value.search as? CitySearchUiState.Results)?.candidates
            ?: return
        val exact = candidates.singleOrNull { it.id == city.id } ?: return
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(
            step = DeviceSetupStep.CHOICES,
            selectedCity = exact,
            pendingReview = false,
        )
    }

    override fun backToSearch() {
        mutableState.value = mutableState.value.copy(
            step = DeviceSetupStep.SEARCH,
            selectedCity = null,
            pendingReview = false,
        )
    }

    override fun resetAfterExit() {
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(
            step = DeviceSetupStep.SEARCH,
            selectedCity = null,
            pendingReview = false,
        )
    }

    private fun scheduleSearch(query: String) {
        searchJob = viewModelScope.launch {
            delay(debounceMillis)
            mutableState.value = mutableState.value.copy(search = CitySearchUiState.Loading)
            val result = try {
                cityGateway.searchCities(query)
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
}

private fun String.takeCodePoints(maximum: Int): String {
    val count = codePointCount(0, length)
    return if (count <= maximum) this else substring(0, offsetByCodePoints(0, maximum))
}

private const val SAVED_QUERY = "device_setup_city_query"
private const val MAX_CITY_QUERY_CODE_POINTS = 200
private const val DEFAULT_CITY_SEARCH_DEBOUNCE_MILLIS = 300L
