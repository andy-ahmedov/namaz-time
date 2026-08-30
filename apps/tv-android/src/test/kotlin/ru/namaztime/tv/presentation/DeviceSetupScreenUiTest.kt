package ru.namaztime.tv.presentation

import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.sync.CanonicalCityCandidate
import ru.namaztime.tv.sync.DeviceCityScheduleChoiceSet
import ru.namaztime.tv.sync.DeviceScheduleAuthority
import ru.namaztime.tv.sync.DeviceScheduleChoice
import ru.namaztime.tv.sync.DeviceScheduleSource
import ru.namaztime.tv.sync.PendingDeviceScheduleChoiceRequest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class DeviceSetupScreenUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptySearchRequestsTextFieldFocusAndShowsCurrentScheduleSafety() {
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = DeviceSetupUiState(),
                    activeSchedule = activeScheduleSummary(),
                    onQueryChanged = {},
                    onRetrySearch = {},
                    onCitySelected = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag(DEVICE_SETUP_SEARCH_FIELD_TAG).assertIsFocused()
        compose.onNodeWithText("Введите название города").assertIsDisplayed()
        compose.onNodeWithText("Ульяновск").assertIsDisplayed()
        compose.onNodeWithText("Текущее расписание продолжает работать до подтверждения нового выбора")
            .assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun searchRendersLoadingEmptyFailureAndRetryStates() {
        var retried = false
        var search by mutableStateOf<CitySearchUiState>(CitySearchUiState.Loading)
        val states = listOf(
            CitySearchUiState.Loading to "Ищем города…",
            CitySearchUiState.NoResults to "Населённые пункты не найдены",
            CitySearchUiState.NotProvisioned to "Устройство ещё не подключено к серверу",
            CitySearchUiState.Unauthorized to "Доступ устройства отозван или недействителен",
        )

        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = DeviceSetupUiState(query = "Киров", search = search),
                    activeSchedule = null,
                    onQueryChanged = {},
                    onRetrySearch = { retried = true },
                    onCitySelected = {},
                    onBack = {},
                )
            }
        }
        states.forEach { (nextSearch, text) ->
            search = nextSearch
            compose.waitForIdle()
            compose.onNodeWithText(text).assertIsDisplayed()
        }

        search = CitySearchUiState.Error("setup_server_error", retryable = true)
        compose.waitForIdle()
        compose.onNodeWithText("Не удалось загрузить города").assertIsDisplayed()
        compose.onNodeWithTag(DEVICE_SETUP_SEARCH_FIELD_TAG).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithTag(DEVICE_SETUP_RETRY_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(true, retried)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun duplicateCitiesRemainDistinctAndDpadSelectsOnlyExplicitCandidate() {
        val kirovOblast = city(
            id = "geonames:548408",
            subjectCode = "RU-KIR",
            subjectName = "Кировская область",
            settlementType = "city",
            timezone = "Europe/Kirov",
        )
        val kirovKaluga = city(
            id = "geonames:548391",
            subjectCode = "RU-KLU",
            subjectName = "Калужская область",
            settlementType = "village",
            timezone = "Europe/Moscow",
        )
        var selected: CanonicalCityCandidate? = null
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = DeviceSetupUiState(
                        query = "Киров",
                        search = CitySearchUiState.Results(listOf(kirovOblast, kirovKaluga)),
                    ),
                    activeSchedule = null,
                    onQueryChanged = {},
                    onRetrySearch = {},
                    onCitySelected = { selected = it },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Кировская область").assertIsDisplayed()
        compose.onNodeWithText("Калужская область").assertIsDisplayed()
        compose.onNodeWithText("Город · Europe/Kirov").assertIsDisplayed()
        compose.onNodeWithText("Село · Europe/Moscow").assertIsDisplayed()
        assertNull(selected)

        compose.onNodeWithTag(DEVICE_SETUP_SEARCH_FIELD_TAG).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithTag("$DEVICE_SETUP_CITY_RESULT_TAG_PREFIX${kirovOblast.id}")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("$DEVICE_SETUP_CITY_RESULT_TAG_PREFIX${kirovKaluga.id}")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        assertEquals(kirovKaluga.id, selected?.id)
    }

    @Test
    fun textInputUsesHoistedCyrillicQuery() {
        var query = ""
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = DeviceSetupUiState(query = query),
                    activeSchedule = null,
                    onQueryChanged = { query = it },
                    onRetrySearch = {},
                    onCitySelected = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag(DEVICE_SETUP_SEARCH_FIELD_TAG).performTextReplacement("Ульяновск")

        assertEquals("Ульяновск", query)
    }

    @Test
    fun unavailableChoiceStateNeverOffersGenericFallback() {
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(
                        choices = ScheduleChoicesUiState.Unavailable("no_policy"),
                    ),
                    activeSchedule = activeScheduleSummary(),
                    onQueryChanged = {},
                    onRetrySearch = {},
                    onCitySelected = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Для этого города утверждённое расписание пока недоступно")
            .assertIsDisplayed()
        compose.onNodeWithText("Рассчитать автоматически").assertDoesNotExist()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun choiceLoadFailureFocusesRetryAndKeepsActiveScheduleVisible() {
        var retried = false
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(
                        choices = ScheduleChoicesUiState.Error(
                            code = "setup_server_error",
                            retryable = true,
                        ),
                    ),
                    activeSchedule = activeScheduleSummary(),
                    onQueryChanged = {},
                    onRetrySearch = {},
                    onCitySelected = {},
                    onRetryScheduleChoices = { retried = true },
                    onBack = {},
                )
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Не удалось загрузить варианты расписания").assertIsDisplayed()
        compose.onNodeWithText("synthetic-active-source").assertIsDisplayed()
        compose.onNodeWithTag(DEVICE_SETUP_CHOICES_RETRY_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        assertEquals(true, retried)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun oneChoiceIsExplainedAndRequiresExplicitEnterBeforeRequest() {
        val item = scheduleChoice(0)
        var selected: DeviceScheduleChoice? = null
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(listOf(item)),
                    activeSchedule = activeScheduleSummary(),
                    onQueryChanged = {},
                    onRetrySearch = {},
                    onCitySelected = {},
                    onScheduleChoiceSelected = { selected = it },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(item.authorityLabel).assertIsDisplayed()
        compose.onNodeWithText("Synthetic city scope 0").assertIsDisplayed()
        compose.onNodeWithText("official_file · synthetic-source-0").assertIsDisplayed()
        assertNull(selected)

        compose.onNodeWithTag("$DEVICE_SETUP_CHOICE_TAG_PREFIX${item.id}")
            .performKeyInput { pressKey(Key.Enter) }
        assertEquals(item.id, selected?.id)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun allEightChoicesAreReachableWithoutTruncationOrDefaultSelection() {
        val items = List(8) { scheduleChoice(it) }
        var selected: DeviceScheduleChoice? = null
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(items),
                    activeSchedule = activeScheduleSummary(),
                    onQueryChanged = {},
                    onRetrySearch = {},
                    onCitySelected = {},
                    onScheduleChoiceSelected = { selected = it },
                    onBack = {},
                )
            }
        }

        assertNull(selected)
        compose.onNodeWithTag("$DEVICE_SETUP_CHOICE_TAG_PREFIX${items.first().id}")
            .assertIsFocused()
            .performKeyInput {
                repeat(7) { pressKey(Key.DirectionDown) }
            }
        compose.onNodeWithTag("$DEVICE_SETUP_CHOICE_TAG_PREFIX${items.last().id}")
            .assertIsDisplayed()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        assertEquals(items.last().id, selected?.id)
    }

    @Test
    fun duplicateAuthorityLabelsRemainDistinguishableByPolicyAndSource() {
        val first = scheduleChoice(0, authorityLabel = "Синтетическая организация")
        val second = scheduleChoice(1, authorityLabel = "Синтетическая организация")
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(listOf(first, second)),
                    activeSchedule = null,
                    onQueryChanged = {},
                    onRetrySearch = {},
                    onCitySelected = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("synthetic-policy-0 · synthetic-source-0").assertIsDisplayed()
        compose.onNodeWithText("synthetic-policy-1 · synthetic-source-1").assertIsDisplayed()
    }

    @Test
    fun pendingReviewNamesExplicitChoiceAndKeepsLastKnownGoodMessage() {
        val item = scheduleChoice(0)
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(listOf(item)).copy(
                        step = DeviceSetupStep.PENDING,
                        submission = ScheduleChoiceSubmissionUiState.Pending(
                            request = pendingRequest(item),
                            choice = item,
                        ),
                    ),
                    activeSchedule = activeScheduleSummary(),
                    onQueryChanged = {},
                    onRetrySearch = {},
                    onCitySelected = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Выбрано расписание: ${item.authorityLabel}").assertIsDisplayed()
        compose.onNodeWithText("Ожидает подтверждения").assertIsDisplayed()
        compose.onNodeWithText("Текущее расписание продолжает работать до подтверждения нового выбора")
            .assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun failedExplicitRequestFocusesRetryAndLeavesLastKnownGoodMessage() {
        val item = scheduleChoice(0)
        var retried = false
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(listOf(item)).copy(
                        submission = ScheduleChoiceSubmissionUiState.Error(
                            choice = item,
                            interactionId = "interaction-synthetic-0001",
                            code = "setup_io",
                            retryable = true,
                        ),
                    ),
                    activeSchedule = activeScheduleSummary(),
                    onQueryChanged = {},
                    onRetrySearch = {},
                    onCitySelected = {},
                    onRetryScheduleChoiceRequest = { retried = true },
                    onBack = {},
                )
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Не удалось отправить выбор. Текущее расписание не изменено.")
            .assertIsDisplayed()
        compose.onNodeWithText("Текущее расписание продолжает работать до подтверждения нового выбора")
            .assertIsDisplayed()
        compose.onNodeWithTag(DEVICE_SETUP_REQUEST_RETRY_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        assertEquals(true, retried)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun setupFits720pSafeFrame() = assertSetupFitsSafeFrame()

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun setupFits1080pDensitySafeFrame() = assertSetupFitsSafeFrame()

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun setupFits4kDensitySafeFrame() = assertSetupFitsSafeFrame()

    private fun assertSetupFitsSafeFrame() {
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = DeviceSetupUiState(
                        query = "Киров",
                        search = CitySearchUiState.Results(
                            List(8) { index ->
                                city(
                                    id = "synthetic-city-$index",
                                    subjectCode = "RU-TST",
                                    subjectName = "Синтетический субъект $index",
                                    settlementType = "city",
                                    timezone = "Europe/Moscow",
                                )
                            },
                        ),
                    ),
                    activeSchedule = activeScheduleSummary(),
                    onQueryChanged = {},
                    onRetrySearch = {},
                    onCitySelected = {},
                    onBack = {},
                )
            }
        }

        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val screen = compose.onNodeWithTag(DEVICE_SETUP_SCREEN_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val rootWidth = root.right - root.left
        val rootHeight = root.bottom - root.top
        assert(screen.left - root.left >= rootWidth * 0.04f)
        assert(root.right - screen.right >= rootWidth * 0.04f)
        assert(screen.top - root.top >= rootHeight * 0.04f)
        assert(root.bottom - screen.bottom >= rootHeight * 0.04f)
    }

    private fun city(
        id: String,
        subjectCode: String,
        subjectName: String,
        settlementType: String,
        timezone: String,
    ) = CanonicalCityCandidate(
        id = id,
        canonicalName = "Киров",
        aliases = listOf("Kirov"),
        federalSubjectCode = subjectCode,
        federalSubjectName = subjectName,
        settlementType = settlementType,
        timezone = timezone,
        latitude = 54.0,
        longitude = 49.0,
        geographicSourceId = "synthetic:geography",
        geographicRevision = "test-1",
        geographicLicense = "synthetic-test-only",
    )

    private fun activeScheduleSummary() = ActiveScheduleSummaryUi(
        cityName = "Ульяновск",
        authorityName = "Синтетическая действующая организация",
        sourceName = "synthetic-active-source",
        timezone = "Europe/Ulyanovsk",
    )

    private fun choiceState(
        items: List<DeviceScheduleChoice> = emptyList(),
        choices: ScheduleChoicesUiState = ScheduleChoicesUiState.Available(
            DeviceCityScheduleChoiceSet(
                revisionId = "synthetic-revision-0001",
                revisionState = "staged",
                status = if (items.isEmpty()) "unavailable" else "available",
                automaticResolutionStatus = when (items.size) {
                    0 -> "unavailable"
                    1 -> "resolved"
                    else -> "ambiguous"
                },
                automaticResolutionReason = when (items.size) {
                    0 -> "no_policy"
                    1 -> "resolved"
                    else -> "same_tier_ambiguous"
                },
                selectionRequired = items.size > 1,
                date = LocalDate.parse("2026-08-30"),
                city = setupCity(),
                choices = items,
                requestAllowed = items.isNotEmpty(),
            ),
        ),
    ) = DeviceSetupUiState(
        query = "Ульяновск",
        step = DeviceSetupStep.CHOICES,
        search = CitySearchUiState.Results(listOf(setupCity())),
        selectedCity = setupCity(),
        choices = choices,
    )

    private fun setupCity() = CanonicalCityCandidate(
        id = "city-ulyanovsk-0001",
        canonicalName = "Ульяновск",
        aliases = listOf("Ulyanovsk"),
        federalSubjectCode = "RU-ULY",
        federalSubjectName = "Ульяновская область",
        settlementType = "city",
        timezone = "Europe/Ulyanovsk",
        latitude = 54.3,
        longitude = 48.4,
        geographicSourceId = "synthetic:geography",
        geographicRevision = "fixture-v1",
        geographicLicense = "synthetic-test-only",
    )

    private fun scheduleChoice(
        index: Int,
        authorityLabel: String = "Синтетическая организация $index",
    ) = DeviceScheduleChoice(
        id = "schedule-choice-${index.toString(16).padStart(64, '0')}",
        displayLabel = "Ульяновск ($authorityLabel)",
        authorityLabel = authorityLabel,
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
        authorities = listOf(
            DeviceScheduleAuthority("synthetic-authority-$index", authorityLabel, "PROPOSAL"),
        ),
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

    private fun pendingRequest(item: DeviceScheduleChoice) = PendingDeviceScheduleChoiceRequest(
        id = "device-binding-request-synthetic-0001",
        revisionId = "synthetic-revision-0001",
        cityId = setupCity().id,
        policyId = item.policyId,
        choiceId = item.id,
        mosqueId = "synthetic-mosque-0001",
        deviceId = "synthetic-device-0001",
        date = LocalDate.parse("2026-08-30"),
        tier = item.tier,
        status = "pending_review",
        selectionSha256 = "a".repeat(64),
        origin = "local_tv_operator",
        interactionId = "interaction-synthetic-0001",
        requestedAt = Instant.parse("2026-08-30T09:00:00Z"),
    )
}
