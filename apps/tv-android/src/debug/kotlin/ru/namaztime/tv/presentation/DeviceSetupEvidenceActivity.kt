package ru.namaztime.tv.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import ru.namaztime.tv.sync.CanonicalCityCandidate
import ru.namaztime.tv.sync.DeviceCityScheduleChoiceSet
import ru.namaztime.tv.sync.DeviceScheduleAuthority
import ru.namaztime.tv.sync.DeviceScheduleChoice
import ru.namaztime.tv.sync.DeviceScheduleSource
import ru.namaztime.tv.sync.PendingDeviceScheduleChoiceRequest

/** Debug-only synthetic renderer used to record controlled emulator evidence for T041. */
class DeviceSetupEvidenceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra(EXTRA_SCENARIO).orEmpty()
        setContent {
            NamazTvTheme {
                Box(Modifier.fillMaxSize()) {
                    TvAtmosphericBackground()
                    DeviceScheduleSetupScreen(
                        state = evidenceState(scenario),
                        activeSchedule = ActiveScheduleSummaryUi(
                            cityName = "Тестовый активный город",
                            authorityName = "Синтетическая действующая организация",
                            sourceName = "synthetic-active-source",
                            timezone = "Europe/Moscow",
                        ),
                        onQueryChanged = {},
                        onRetrySearch = {},
                        onCitySelected = {},
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}

private fun evidenceState(scenario: String): DeviceSetupUiState = when (scenario) {
    "duplicates" -> DeviceSetupUiState(
        query = "Киров",
        search = CitySearchUiState.Results(
            listOf(
                evidenceCity(
                    id = "synthetic-kirov-ru-kir",
                    name = "Киров",
                    subjectCode = "RU-KIR",
                    subjectName = "Кировская область",
                    settlementType = "city",
                    timezone = "Europe/Kirov",
                ),
                evidenceCity(
                    id = "synthetic-kirov-ru-klu",
                    name = "Киров",
                    subjectCode = "RU-KLU",
                    subjectName = "Калужская область",
                    settlementType = "village",
                    timezone = "Europe/Moscow",
                ),
            ),
        ),
    )
    "one-choice" -> evidenceChoiceState(1)
    "multiple-choices" -> evidenceChoiceState(5)
    "unavailable" -> evidenceChoiceState(0)
    "pending" -> {
        val base = evidenceChoiceState(1)
        val choice = (base.choices as ScheduleChoicesUiState.Available).set.choices.single()
        base.copy(
            step = DeviceSetupStep.PENDING,
            submission = ScheduleChoiceSubmissionUiState.Pending(
                request = evidencePendingRequest(choice),
                choice = choice,
            ),
        )
    }
    else -> DeviceSetupUiState(
        query = "Ульяновск",
        search = CitySearchUiState.Loading,
    )
}

private fun evidenceChoiceState(count: Int): DeviceSetupUiState {
    val city = evidenceCity(
        id = "synthetic-testograd",
        name = "Тестоград",
        subjectCode = "RU-TST",
        subjectName = "Синтетический субъект",
        settlementType = "city",
        timezone = "Europe/Moscow",
    )
    val choices = List(count, ::evidenceChoice)
    return DeviceSetupUiState(
        query = city.canonicalName,
        step = DeviceSetupStep.CHOICES,
        search = CitySearchUiState.Results(listOf(city)),
        selectedCity = city,
        choices = if (choices.isEmpty()) {
            ScheduleChoicesUiState.Unavailable("no_policy")
        } else {
            ScheduleChoicesUiState.Available(
                DeviceCityScheduleChoiceSet(
                    revisionId = "synthetic-evidence-revision",
                    revisionState = "staged",
                    status = "available",
                    automaticResolutionStatus = if (choices.size == 1) "resolved" else "ambiguous",
                    automaticResolutionReason = if (choices.size == 1) {
                        "resolved"
                    } else {
                        "same_tier_ambiguous"
                    },
                    selectionRequired = choices.size > 1,
                    date = LocalDate.parse("2026-08-30"),
                    city = city,
                    choices = choices,
                    requestAllowed = true,
                ),
            )
        },
    )
}

private fun evidenceCity(
    id: String,
    name: String,
    subjectCode: String,
    subjectName: String,
    settlementType: String,
    timezone: String,
) = CanonicalCityCandidate(
    id = id,
    canonicalName = name,
    aliases = emptyList(),
    federalSubjectCode = subjectCode,
    federalSubjectName = subjectName,
    settlementType = settlementType,
    timezone = timezone,
    latitude = 55.0,
    longitude = 49.0,
    geographicSourceId = "synthetic-runtime-evidence",
    geographicRevision = "t041-emulator",
    geographicLicense = "synthetic-test-only",
)

private fun evidenceChoice(index: Int): DeviceScheduleChoice {
    val authority = "Синтетическая организация ${index + 1}"
    return DeviceScheduleChoice(
        id = "schedule-choice-${index.toString(16).padStart(64, '0')}",
        displayLabel = "Тестоград ($authority)",
        authorityLabel = authority,
        tier = "exact_city_timetable",
        selectable = true,
        executable = false,
        requestable = true,
        policyId = "synthetic-policy-${index + 1}",
        policyKind = "timetable",
        approvalId = "synthetic-approval-${index + 1}",
        effectiveFrom = LocalDate.parse("2026-01-01"),
        effectiveTo = LocalDate.parse("2026-12-31"),
        scopeId = "synthetic-scope-${index + 1}",
        scopeKind = "city",
        scopeDescription = "Синтетическая городская область действия",
        authorities = listOf(
            DeviceScheduleAuthority(
                id = "synthetic-authority-${index + 1}",
                name = authority,
                evidenceLabel = "PROPOSAL",
            ),
        ),
        source = DeviceScheduleSource(
            id = "synthetic-source-${index + 1}",
            kind = "official_file",
            status = "approved",
            canonicalUrl = "https://example.invalid/synthetic/${index + 1}",
            freshThrough = LocalDate.parse("2026-12-31"),
        ),
        scheduleId = "synthetic-timetable-${index + 1}",
        scheduleKind = "timetable",
        scheduleTimezone = "Europe/Moscow",
        publishedSnapshotId = "synthetic-snapshot-${index + 1}",
    )
}

private fun evidencePendingRequest(choice: DeviceScheduleChoice) =
    PendingDeviceScheduleChoiceRequest(
        id = "device-binding-request-synthetic-evidence",
        revisionId = "synthetic-evidence-revision",
        cityId = "synthetic-testograd",
        policyId = choice.policyId,
        choiceId = choice.id,
        mosqueId = "synthetic-mosque-evidence",
        deviceId = "synthetic-device-evidence",
        date = LocalDate.parse("2026-08-30"),
        tier = choice.tier,
        status = "pending_review",
        selectionSha256 = "a".repeat(64),
        origin = "local_tv_operator",
        interactionId = "synthetic-interaction-evidence",
        requestedAt = Instant.parse("2026-08-30T09:00:00Z"),
    )

private const val EXTRA_SCENARIO = "scenario"
