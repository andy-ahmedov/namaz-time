package ru.namaztime.tv.presentation

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.namaztime.tv.sync.CanonicalCityCandidate
import ru.namaztime.tv.sync.DeviceCityScheduleChoiceSet
import ru.namaztime.tv.sync.DeviceScheduleAuthority
import ru.namaztime.tv.sync.DeviceScheduleChoice
import ru.namaztime.tv.sync.DeviceSchedulePreview
import ru.namaztime.tv.sync.DeviceSchedulePreviewPrayer
import ru.namaztime.tv.sync.DeviceSchedulePreviewRow
import ru.namaztime.tv.sync.DeviceScheduleSource
import ru.namaztime.tv.sync.DeviceSetupGateway
import ru.namaztime.tv.sync.DeviceSetupResult
import ru.namaztime.tv.sync.PendingDeviceScheduleChoiceRequest

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSetupChoiceViewModelTest {
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
    fun citySelectionUsesCityTimezoneAndExposesUnavailableWithoutFallback() = runTest(dispatcher) {
        val city = setupCity(timezone = "Europe/Ulyanovsk")
        val gateway = ChoiceGateway(city, choiceCount = 0)
        val model = setupModel(gateway)
        searchAndSelect(model, city)
        runCurrent()

        assertEquals(listOf(city.id to LocalDate.parse("2026-08-30")), gateway.choiceLoads)
        assertTrue(model.state.value.choices is ScheduleChoicesUiState.Unavailable)
        assertEquals(DeviceSetupStep.CHOICES, model.state.value.step)
        assertTrue(gateway.requests.isEmpty())
    }

    @Test
    fun exposesEveryOneTwoFiveAndEightChoiceWithoutImplicitFirstSelection() = runTest(dispatcher) {
        listOf(1, 2, 5, 8).forEach { count ->
            val city = setupCity(idSuffix = count.toString())
            val gateway = ChoiceGateway(city, choiceCount = count)
            val model = setupModel(gateway)

            searchAndSelect(model, city)
            runCurrent()

            val available = model.state.value.choices as ScheduleChoicesUiState.Available
            assertEquals("count=$count", count, available.set.choices.size)
            assertEquals(count > 1, available.set.selectionRequired)
            assertTrue(gateway.requests.isEmpty())
            assertTrue(model.state.value.submission is ScheduleChoiceSubmissionUiState.Idle)
        }
    }

    @Test
    fun duplicateLabelsRemainDistinctAndOnlyExplicitChoiceCreatesPendingReview() = runTest(dispatcher) {
        val city = setupCity()
        val gateway = ChoiceGateway(city, choiceCount = 2, duplicateLabels = true)
        val model = setupModel(gateway)
        searchAndSelect(model, city)
        runCurrent()
        val choices = (model.state.value.choices as ScheduleChoicesUiState.Available).set.choices

        assertEquals(1, choices.map { it.authorityLabel }.distinct().size)
        assertEquals(2, choices.map { it.id }.distinct().size)
        assertTrue(gateway.requests.isEmpty())

        model.selectScheduleChoice(choices[1])
        runCurrent()

        assertEquals(listOf(choices[1].id), gateway.requests.map { it.choiceId })
        assertEquals(DeviceSetupStep.PENDING, model.state.value.step)
        val pending = model.state.value.submission as ScheduleChoiceSubmissionUiState.Pending
        assertEquals(choices[1].id, pending.choice.id)
        assertEquals("pending_review", pending.request.status)
    }

    @Test
    fun syntheticDebugChoiceNeverCreatesPendingReviewRequest() = runTest(dispatcher) {
        val city = setupCity()
        val gateway = ChoiceGateway(
            city = city,
            choiceCount = 1,
            sourceStatus = "synthetic_debug",
        )
        val model = setupModel(gateway)
        searchAndSelect(model, city)
        runCurrent()
        val selected = (model.state.value.choices as ScheduleChoicesUiState.Available)
            .set.choices.single()

        model.selectScheduleChoice(selected)
        runCurrent()

        assertTrue(gateway.requests.isEmpty())
        assertFalse(model.state.value.submission is ScheduleChoiceSubmissionUiState.Pending)
    }

    @Test
    fun explicitPreviewActivationUsesExactChoiceAndCompletes() = runTest(dispatcher) {
        val city = setupCity()
        val gateway = ChoiceGateway(
            city = city,
            choiceCount = 1,
            sourceStatus = "synthetic_debug",
            activationAllowed = true,
        )
        val model = setupModel(gateway)
        searchAndSelect(model, city)
        runCurrent()
        val selected = (model.state.value.choices as ScheduleChoicesUiState.Available)
            .set.choices.single()
        model.selectScheduleChoice(selected)

        model.activatePreview()
        runCurrent()

        assertEquals(listOf(selected.id), gateway.activations.map { it.choiceId })
        assertTrue(model.state.value.activation is ScheduleActivationUiState.Activated)
        assertTrue(gateway.requests.isEmpty())
    }

    @Test
    fun failedPreviewActivationKeepsPreviewAndExposesRetryableError() = runTest(dispatcher) {
        val city = setupCity()
        val gateway = ChoiceGateway(
            city = city,
            choiceCount = 1,
            sourceStatus = "synthetic_debug",
            activationAllowed = true,
        ).apply {
            activationResults += DeviceSetupResult.Failure("setup_activation_io", retryable = true)
        }
        val model = setupModel(gateway)
        searchAndSelect(model, city)
        runCurrent()
        val selected = (model.state.value.choices as ScheduleChoicesUiState.Available)
            .set.choices.single()
        model.selectScheduleChoice(selected)

        model.activatePreview()
        runCurrent()

        assertEquals(DeviceSetupStep.PREVIEW, model.state.value.step)
        val failure = model.state.value.activation as ScheduleActivationUiState.Error
        assertEquals("setup_activation_io", failure.code)
        assertTrue(failure.retryable)
    }

    @Test
    fun backFromSelectionResultReturnsToAuthorityChoicesBeforeCitySearch() = runTest(dispatcher) {
        val city = setupCity()
        val gateway = ChoiceGateway(city, choiceCount = 1)
        val model = setupModel(gateway)
        searchAndSelect(model, city)
        runCurrent()
        val selected = (model.state.value.choices as ScheduleChoicesUiState.Available)
            .set.choices.single()
        model.selectScheduleChoice(selected)
        runCurrent()

        model.backToSearch()

        assertEquals(DeviceSetupStep.CHOICES, model.state.value.step)
        assertEquals(city.id, model.state.value.selectedCity?.id)
    }

    @Test
    fun retryKeepsInteractionIdentityAndFailedRequestLeavesChoiceScreenIntact() = runTest(dispatcher) {
        val city = setupCity()
        val gateway = ChoiceGateway(city, choiceCount = 1).apply {
            requestResults += DeviceSetupResult.Failure("setup_io", retryable = true)
            requestResults += DeviceSetupResult.Success(pendingRequest(city, choice(0)))
        }
        val model = setupModel(gateway, interactionId = "interaction-stable-0001")
        searchAndSelect(model, city)
        runCurrent()
        val choice = (model.state.value.choices as ScheduleChoicesUiState.Available).set.choices.single()

        model.selectScheduleChoice(choice)
        runCurrent()
        assertEquals(DeviceSetupStep.CHOICES, model.state.value.step)
        assertTrue(model.state.value.submission is ScheduleChoiceSubmissionUiState.Error)
        assertEquals(choice.id, (model.state.value.choices as ScheduleChoicesUiState.Available).set.choices.single().id)

        model.retryScheduleChoiceRequest()
        runCurrent()
        assertEquals(
            listOf("interaction-stable-0001", "interaction-stable-0001"),
            gateway.requests.map { it.interactionId },
        )
        assertEquals(DeviceSetupStep.PENDING, model.state.value.step)
    }

    @Test
    fun backPreservesSearchStateAndProcessRecreationCannotSubmitOrActivate() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val city = setupCity()
        val gateway = ChoiceGateway(city, choiceCount = 2, choiceDelayMillis = 5_000)
        val model = setupModel(gateway, savedState = savedState)
        searchAndSelect(model, city)
        runCurrent()

        model.backToSearch()
        runCurrent()
        assertEquals("Ульяновск", model.state.value.query)
        assertTrue(model.state.value.search is CitySearchUiState.Results)
        assertEquals(null, model.state.value.selectedCity)
        assertTrue(gateway.requests.isEmpty())

        val recreated = setupModel(gateway, savedState = savedState)
        runCurrent()
        assertEquals(DeviceSetupStep.SEARCH, recreated.state.value.step)
        assertEquals("Ульяновск", recreated.state.value.query)
        assertEquals(null, recreated.state.value.selectedCity)
        assertTrue(recreated.state.value.submission is ScheduleChoiceSubmissionUiState.Idle)
        assertTrue(gateway.requests.isEmpty())
    }

    private fun setupModel(
        gateway: DeviceSetupGateway,
        savedState: SavedStateHandle = SavedStateHandle(),
        interactionId: String = "interaction-generated-0001",
    ) = DeviceSetupViewModel(
        setupGateway = gateway,
        savedStateHandle = savedState,
        debounceMillis = 0,
        clock = Clock.fixed(Instant.parse("2026-08-29T20:30:00Z"), ZoneOffset.UTC),
        interactionIdFactory = { interactionId },
    )

    private fun TestScope.searchAndSelect(
        model: DeviceSetupViewModel,
        city: CanonicalCityCandidate,
    ) {
        model.onQueryChanged("Ульяновск")
        runCurrent()
        model.selectCity(city)
    }
}

private data class RecordedChoiceRequest(
    val cityId: String,
    val choiceId: String,
    val date: LocalDate,
    val interactionId: String,
)

private data class RecordedChoiceActivation(
    val cityId: String,
    val choiceId: String,
    val date: LocalDate,
)

private class ChoiceGateway(
    private val city: CanonicalCityCandidate,
    private val choiceCount: Int,
    private val duplicateLabels: Boolean = false,
    private val choiceDelayMillis: Long = 0,
    private val sourceStatus: String = "approved",
    private val activationAllowed: Boolean = false,
) : DeviceSetupGateway {
    val choiceLoads = mutableListOf<Pair<String, LocalDate>>()
    val requests = mutableListOf<RecordedChoiceRequest>()
    val activations = mutableListOf<RecordedChoiceActivation>()
    val requestResults = ArrayDeque<DeviceSetupResult<PendingDeviceScheduleChoiceRequest>>()
    val activationResults = ArrayDeque<DeviceSetupResult<Unit>>()

    override suspend fun searchCities(query: String) = DeviceSetupResult.Success(listOf(city))

    override suspend fun loadScheduleChoices(
        cityId: String,
        date: LocalDate,
    ): DeviceSetupResult<DeviceCityScheduleChoiceSet> {
        choiceLoads += cityId to date
        if (choiceDelayMillis > 0) delay(choiceDelayMillis)
        val choices = (0 until choiceCount).map { index ->
            choice(index, duplicateLabels).let { item ->
                item.copy(
                    source = item.source.copy(status = sourceStatus),
                    localPreview = if (sourceStatus == "synthetic_debug") {
                        syntheticPreview()
                    } else {
                        null
                    },
                    activationAllowed = activationAllowed,
                )
            }
        }
        return DeviceSetupResult.Success(
            DeviceCityScheduleChoiceSet(
                revisionId = "synthetic-revision-0001",
                revisionState = "staged",
                status = if (choices.isEmpty()) "unavailable" else "available",
                automaticResolutionStatus = when (choices.size) {
                    0 -> "unavailable"
                    1 -> "resolved"
                    else -> "ambiguous"
                },
                automaticResolutionReason = when (choices.size) {
                    0 -> "no_policy"
                    1 -> "resolved"
                    else -> "same_tier_ambiguous"
                },
                selectionRequired = choices.size > 1,
                date = date,
                city = city,
                choices = choices,
                requestAllowed = choices.isNotEmpty(),
            ),
        )
    }

    override suspend fun requestScheduleChoice(
        cityId: String,
        choiceId: String,
        date: LocalDate,
        interactionId: String,
    ): DeviceSetupResult<PendingDeviceScheduleChoiceRequest> {
        requests += RecordedChoiceRequest(cityId, choiceId, date, interactionId)
        return requestResults.removeFirstOrNull()
            ?: DeviceSetupResult.Success(pendingRequest(city, choice(choiceId.takeLast(1).toInt())))
    }

    override suspend fun activateScheduleChoice(
        cityId: String,
        choiceId: String,
        date: LocalDate,
    ): DeviceSetupResult<Unit> {
        activations += RecordedChoiceActivation(cityId, choiceId, date)
        return activationResults.removeFirstOrNull() ?: DeviceSetupResult.Success(Unit)
    }
}

private fun syntheticPreview() = DeviceSchedulePreview(
    date = LocalDate.parse("2026-08-30"),
    timezone = "Europe/Ulyanovsk",
    evidenceLabel = "PROPOSAL",
    rows = listOf(
        DeviceSchedulePreviewRow(
            prayer = DeviceSchedulePreviewPrayer.FAJR,
            adhan = LocalTime.parse("04:20"),
            iqamah = LocalTime.parse("04:35"),
        ),
    ),
)

private fun setupCity(
    idSuffix: String = "1",
    timezone: String = "Europe/Ulyanovsk",
) = CanonicalCityCandidate(
    id = "city-ulyanovsk-000$idSuffix",
    canonicalName = "Ульяновск",
    aliases = listOf("Ulyanovsk"),
    federalSubjectCode = "RU-ULY",
    federalSubjectName = "Ульяновская область",
    settlementType = "PPLA",
    timezone = timezone,
    latitude = 54.3,
    longitude = 48.4,
    geographicSourceId = "synthetic-test-only:ulyanovsk",
    geographicRevision = "fixture-v1",
    geographicLicense = "synthetic-test-only",
)

private fun choice(index: Int, duplicateLabels: Boolean = false): DeviceScheduleChoice {
    val label = if (duplicateLabels) "Синтетическая организация" else "Синтетическая организация $index"
    return DeviceScheduleChoice(
        id = "schedule-choice-${index.toString(16).padStart(64, '0')}",
        displayLabel = "Ульяновск ($label)",
        authorityLabel = label,
        tier = "exact_city_timetable",
        selectable = true,
        executable = false,
        requestable = true,
        policyId = "synthetic-policy-$index",
        policyKind = "timetable",
        approvalId = "synthetic-approval-$index",
        effectiveFrom = LocalDate.parse("2026-01-01"),
        effectiveTo = LocalDate.parse("2026-12-31"),
        scopeId = "synthetic-scope-$index",
        scopeKind = "city",
        scopeDescription = "Synthetic city scope $index",
        authorities = listOf(DeviceScheduleAuthority("synthetic-authority-$index", label, "PROPOSAL")),
        source = DeviceScheduleSource(
            id = "synthetic-source-$index",
            kind = "official_file",
            status = "approved",
            canonicalUrl = "https://example.invalid/synthetic/$index",
            freshThrough = LocalDate.parse("2026-12-31"),
        ),
        scheduleId = "synthetic-timetable-$index",
        scheduleKind = "timetable",
        scheduleTimezone = "Europe/Ulyanovsk",
        publishedSnapshotId = "synthetic-snapshot-$index",
    )
}

private fun pendingRequest(
    city: CanonicalCityCandidate,
    choice: DeviceScheduleChoice,
) = PendingDeviceScheduleChoiceRequest(
    id = "device-binding-request-synthetic-0001",
    revisionId = "synthetic-revision-0001",
    cityId = city.id,
    policyId = choice.policyId,
    choiceId = choice.id,
    mosqueId = "synthetic-mosque-0001",
    deviceId = "synthetic-device-0001",
    date = LocalDate.parse("2026-08-30"),
    tier = choice.tier,
    status = "pending_review",
    selectionSha256 = "a".repeat(64),
    origin = "local_tv_operator",
    interactionId = "interaction-generated-0001",
    requestedAt = Instant.parse("2026-08-30T09:00:00Z"),
)
