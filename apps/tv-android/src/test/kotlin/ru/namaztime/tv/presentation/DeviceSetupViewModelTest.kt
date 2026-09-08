package ru.namaztime.tv.presentation

import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.namaztime.tv.sync.CanonicalCityCandidate
import java.time.LocalDate
import ru.namaztime.tv.sync.DeviceCityScheduleChoiceSet
import ru.namaztime.tv.sync.DeviceSetupGateway
import ru.namaztime.tv.sync.DeviceSetupResult
import ru.namaztime.tv.sync.PendingDeviceScheduleChoiceRequest

class DeviceSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyQueryDoesNotSearchAndNonEmptyQueryDebounces() = runTest(dispatcher) {
        val gateway = RecordingCityGateway()
        val model = DeviceSetupViewModel(gateway, SavedStateHandle())

        model.onQueryChanged("")
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(model.state.value.search is CitySearchUiState.EmptyInput)
        assertEquals(emptyList<String>(), gateway.queries)

        model.onQueryChanged("Ульяновск")
        runCurrent()
        advanceTimeBy(299)
        runCurrent()
        assertEquals(emptyList<String>(), gateway.queries)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("Ульяновск"), gateway.queries)
        assertTrue(model.state.value.search is CitySearchUiState.Results)
    }

    @Test
    fun supersededSearchIsCancelledAndCannotReplaceLatestResults() = runTest(dispatcher) {
        val firstCancelled = AtomicBoolean(false)
        val gateway = RecordingCityGateway { query ->
            if (query == "Киров") {
                try {
                    delay(5_000)
                } catch (error: CancellationException) {
                    firstCancelled.set(true)
                    throw error
                }
            }
            DeviceSetupResult.Success(listOf(city(query, "RU-ULY")))
        }
        val model = DeviceSetupViewModel(gateway, SavedStateHandle())

        model.onQueryChanged("Киров")
        advanceTimeBy(300)
        runCurrent()
        model.onQueryChanged("Ульяновск")
        advanceTimeBy(300)
        runCurrent()

        assertTrue(firstCancelled.get())
        assertEquals(listOf("Киров", "Ульяновск"), gateway.queries)
        val result = model.state.value.search as CitySearchUiState.Results
        assertEquals("Ульяновск", result.candidates.single().canonicalName)
    }

    @Test
    fun lateNonCooperativeCatalogReadCannotOverwriteTheNewQuery() = runTest(dispatcher) {
        val gateway = RecordingCityGateway { query ->
            if (query == "Киров") withContext(NonCancellable) { delay(2_000) }
            DeviceSetupResult.Success(listOf(city(query, "RU-ULY")))
        }
        val model = DeviceSetupViewModel(gateway, SavedStateHandle())
        model.onQueryChanged("Киров")
        advanceTimeBy(300); runCurrent()
        model.onQueryChanged("Ульяновск")
        advanceTimeBy(300); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertEquals("Ульяновск", (model.state.value.search as CitySearchUiState.Results).candidates.single().canonicalName)
    }

    @Test
    fun exposesLoadingEmptyFailuresAndAuthorizationWithoutGuessing() = runTest(dispatcher) {
        val gateway = RecordingCityGateway {
            delay(1)
            DeviceSetupResult.Success(emptyList())
        }
        val model = DeviceSetupViewModel(gateway, SavedStateHandle())
        model.onQueryChanged("Нетгорода")
        advanceTimeBy(300)
        runCurrent()
        assertTrue(model.state.value.search is CitySearchUiState.Loading)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(model.state.value.search is CitySearchUiState.NoResults)

        gateway.result = { DeviceSetupResult.Failure("setup_io", retryable = true) }
        model.retrySearch()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(CitySearchUiState.Error("setup_io", true), model.state.value.search)

        gateway.result = { DeviceSetupResult.Unauthorized }
        model.retrySearch()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(CitySearchUiState.Unauthorized, model.state.value.search)

        gateway.result = { DeviceSetupResult.NotProvisioned }
        model.retrySearch()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(CitySearchUiState.NotProvisioned, model.state.value.search)
    }

    @Test
    fun duplicateCitiesRequireExplicitCandidateAndSavedQueryNeverActivatesAnything() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val candidates = listOf(city("Киров", "RU-KIR"), city("Киров", "RU-KLU"))
        val gateway = RecordingCityGateway { DeviceSetupResult.Success(candidates) }
        val model = DeviceSetupViewModel(gateway, savedState)

        model.onQueryChanged("Киров")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(DeviceSetupStep.SEARCH, model.state.value.step)
        assertEquals(null, model.state.value.selectedCity)

        model.selectCity(candidates[1])
        assertEquals(DeviceSetupStep.CHOICES, model.state.value.step)
        assertEquals("RU-KLU", model.state.value.selectedCity?.federalSubjectCode)
        assertEquals(0, gateway.selectionRequests)

        model.backToSearch()
        assertEquals("Киров", model.state.value.query)
        assertTrue(model.state.value.search is CitySearchUiState.Results)

        val recreated = DeviceSetupViewModel(gateway, savedState)
        runCurrent()
        assertEquals("Киров", recreated.state.value.query)
        assertEquals(DeviceSetupStep.SEARCH, recreated.state.value.step)
        assertEquals(null, recreated.state.value.selectedCity)
        assertEquals(0, gateway.selectionRequests)
        assertTrue(recreated.state.value.submission is ScheduleChoiceSubmissionUiState.Idle)
    }

    private fun city(name: String, subject: String) = CanonicalCityCandidate(
        id = "city-${subject.lowercase()}-0001",
        canonicalName = name,
        aliases = if (name == "Ульяновск") listOf("Ulyanovsk") else listOf("Kirov"),
        federalSubjectCode = subject,
        federalSubjectName = "Синтетический субъект $subject",
        settlementType = "PPL",
        timezone = if (subject == "RU-KIR") "Europe/Kirov" else "Europe/Moscow",
        latitude = 55.0,
        longitude = 49.0,
        geographicSourceId = "synthetic-test-only:$subject",
        geographicRevision = "fixture-v1",
        geographicLicense = "synthetic-test-only",
    )
}

private class RecordingCityGateway(
    var result: suspend (String) -> DeviceSetupResult<List<CanonicalCityCandidate>> = { query ->
        DeviceSetupResult.Success(
            listOf(
                CanonicalCityCandidate(
                    id = "city-ulyanovsk-0001",
                    canonicalName = query,
                    aliases = listOf("Ulyanovsk"),
                    federalSubjectCode = "RU-ULY",
                    federalSubjectName = "Ульяновская область",
                    settlementType = "PPLA",
                    timezone = "Europe/Ulyanovsk",
                    latitude = 54.3,
                    longitude = 48.4,
                    geographicSourceId = "synthetic-test-only:ulyanovsk",
                    geographicRevision = "fixture-v1",
                    geographicLicense = "synthetic-test-only",
                ),
            ),
        )
    },
) : DeviceSetupGateway {
    val queries = mutableListOf<String>()
    var selectionRequests = 0

    override suspend fun searchCities(query: String): DeviceSetupResult<List<CanonicalCityCandidate>> {
        queries += query
        return result(query)
    }

    override suspend fun loadScheduleChoices(
        cityId: String,
        date: LocalDate,
    ): DeviceSetupResult<DeviceCityScheduleChoiceSet> {
        selectionRequests += 1
        return DeviceSetupResult.Failure("not_used", retryable = false)
    }

    override suspend fun requestScheduleChoice(
        cityId: String,
        choiceId: String,
        date: LocalDate,
        interactionId: String,
    ): DeviceSetupResult<PendingDeviceScheduleChoiceRequest> {
        selectionRequests += 1
        return DeviceSetupResult.Failure("not_used", retryable = false)
    }
}
