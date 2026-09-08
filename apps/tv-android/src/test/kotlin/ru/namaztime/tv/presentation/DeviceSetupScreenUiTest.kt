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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.sync.CanonicalCityCandidate
import ru.namaztime.tv.sync.DeviceCityScheduleChoiceSet
import ru.namaztime.tv.sync.DeviceScheduleAuthority
import ru.namaztime.tv.sync.DeviceScheduleChoice
import ru.namaztime.tv.sync.DeviceSchedulePreview
import ru.namaztime.tv.sync.DeviceSchedulePreviewPrayer
import ru.namaztime.tv.sync.DeviceSchedulePreviewRow
import ru.namaztime.tv.sync.DeviceScheduleSource
import ru.namaztime.tv.sync.PendingDeviceScheduleChoiceRequest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class DeviceSetupScreenUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun missingLocalBundleHasAnHonestNotConfiguredMessage() {
        compose.setContent { NamazTvTheme {
            DeviceScheduleSetupScreen(state = DeviceSetupUiState(query = "Москва",
                search = CitySearchUiState.Error("setup_local_not_configured", false)),
                activeSchedule = null, onQueryChanged = {}, onRetrySearch = {}, onCitySelected = {}, onBack = {})
        } }
        compose.onNodeWithText("Локальный каталог источников не установлен в этой сборке").assertIsDisplayed()
        compose.onNodeWithText("Демонстрационный режим").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [28, 35], qualifiers = "w960dp-h540dp-land-xhdpi")
    @OptIn(ExperimentalTestApi::class)
    fun productionPreviewIsNotDemoAndSourceLinkRequiresExplicitDpadActionWithLocalBrowserError() {
        val preview = debugPreview().copy(
            dataClassification = "production",
            provenance = ru.namaztime.tv.sync.DeviceSchedulePreviewProvenance(
                "synthetic-protocol-production-test", "a".repeat(64), "b".repeat(64), "synthetic-parser/v1", "2026-08-30T09:00:00Z",
            ),
        )
        val item = scheduleChoice(0).copy(localPreview = preview, activationAllowed = true,
            source = scheduleChoice(0).source.copy(canonicalUrl = "https://dumso.ru/raspisanie"))
        var clickedUrl: String? = null
        var activated = false
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(listOf(item)).copy(step = DeviceSetupStep.PREVIEW,
                        submission = ScheduleChoiceSubmissionUiState.Preview(item, preview)),
                    activeSchedule = activeScheduleSummary(), onQueryChanged = {}, onRetrySearch = {},
                    onCitySelected = {}, onBack = {}, onActivatePreview = { activated = true },
                    onOpenSource = { clickedUrl = it; false },
                )
            }
        }
        compose.onNodeWithText("Демонстрационный режим").assertDoesNotExist()
        compose.onNodeWithText("Подписанное расписание").assertIsDisplayed()
        assertNull(clickedUrl)
        compose.onNodeWithTag(DEVICE_SETUP_ACTIVATE_PREVIEW_TAG).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(DEVICE_SETUP_SOURCE_LINK_TAG).assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        assertEquals("https://dumso.ru/raspisanie", clickedUrl)
        assertEquals(false, activated)
        compose.onNodeWithText("На этом телевизоре нет доступного браузера").assertIsDisplayed()
        assertAllPreviewRowsVisible()
    }

    @Test
    fun executableLegacyChoiceDescribesAvailabilityNotAnUnverifiedActiveIdentity() {
        val item = longLegacyChoice()
        compose.setContent { NamazTvTheme {
            DeviceScheduleSetupScreen(
                state = choiceState(listOf(item)), activeSchedule = activeScheduleSummary(),
                onQueryChanged = {}, onRetrySearch = {}, onCitySelected = {}, onBack = {},
            )
        } }
        compose.onNodeWithText("Доступно для выбора").assertIsDisplayed()
        compose.onNodeWithText("Уже используется").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun longLegacyPreviewKeepsAllSixRowsAndFullCoverageAt720p() = assertLongLegacyPreviewFits()

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun longLegacyPreviewKeepsAllSixRowsAndFullCoverageAt1080p() = assertLongLegacyPreviewFits()

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun longLegacyPreviewKeepsAllSixRowsAndFullCoverageAt4k() = assertLongLegacyPreviewFits()

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    @OptIn(ExperimentalTestApi::class)
    fun longLegacyFullProvenanceIsDpadReachableAndBackDoesNotActivate() {
        val item = longLegacyChoice()
        var activated = false
        renderPreview(item, onActivate = { activated = true })
        compose.onNodeWithTag(DEVICE_SETUP_ACTIVATE_PREVIEW_TAG).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Подробнее").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        assertFullTextVisible(item.authorityLabel)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        assertFullTextVisible(item.scopeDescription)
        // The focusable reader scrolls within paragraphs, including ones taller than its viewport.
        repeat(16) { compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        assertFullTextVisible(item.localPreview!!.provenance!!.attribution!!)
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithText("Подробнее").assertIsFocused()
        assertAllPreviewRowsVisible()
        assertFalse(activated)
    }

    @Test
    @Config(sdk = [35], qualifiers = "ldrtl-w960dp-h540dp-land-xhdpi")
    fun mixedArabicLegacyPreviewKeepsSixRowsAndCoverageReadable() {
        val item = longLegacyChoice().copy(
            authorityLabel = "مركز إسلامي اصطناعي لاختبار الواجهة فقط · Синтетическая организация · Synthetic test only",
        )
        renderPreview(item)
        assertAllPreviewRowsVisible()
        assertFullTextVisible("2026-01-01 — 2026-12-31")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    @OptIn(ExperimentalTestApi::class)
    fun dpadCanReadTheMiddleAndEndOfAttributionTallerThanTheDetailsViewport() {
        val base = longLegacyChoice()
        val attribution = (1..55).joinToString("\n") { "Синтетическая строка $it · attribution test only" }
        val item = base.copy(localPreview = base.localPreview!!.copy(
            provenance = base.localPreview.provenance!!.copy(attribution = attribution),
        ))
        renderPreview(item)
        compose.onNodeWithTag(DEVICE_SETUP_ACTIVATE_PREVIEW_TAG).performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Подробнее").performKeyInput { pressKey(Key.Enter) }
        var middleWasReadable = false
        var endWasReadable = false
        repeat(80) {
            if (!middleWasReadable || !endWasReadable) {
                middleWasReadable = middleWasReadable || textMarkerIsInsideDetailsViewport(attribution, "Синтетическая строка 28")
                endWasReadable = endWasReadable || textMarkerIsInsideDetailsViewport(attribution, "Синтетическая строка 55")
                if (!middleWasReadable || !endWasReadable) compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            }
        }
        assertTrue("D-pad skipped the middle of a tall proof paragraph", middleWasReadable)
        assertTrue("D-pad skipped the end of a tall proof paragraph", endWasReadable)
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithText("Подробнее").assertIsFocused()
        assertAllPreviewRowsVisible()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun longLegacyActivationFailureLeavesAllSixRowsAndReadableError() {
        val item = longLegacyChoice()
        renderPreview(item, activation = ScheduleActivationUiState.Error(item, "selection_changed", true))
        assertAllPreviewRowsVisible()
        assertFullTextVisible("2026-01-01 — 2026-12-31")
        assertFullTextVisible("Не удалось применить расписание. Предыдущее расписание продолжает работать.")
    }

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
        compose.onNodeWithText(
            "Доступен один вариант расписания. Проверьте организацию и источник.",
        ).assertIsDisplayed()
        compose.onNodeWithText(
            "Доступен один утверждённый вариант. Проверьте источник и выберите его явно.",
        ).assertDoesNotExist()
        compose.onNodeWithText("Synthetic city scope 0").assertIsDisplayed()
        compose.onNodeWithText("official_file · synthetic-source-0").assertIsDisplayed()
        compose.onNodeWithText(
            "Основание: PROPOSAL · подтверждение synthetic-approval-0 · актуально до 2026-12-31",
        ).assertIsDisplayed()
        assertNull(selected)

        compose.onNodeWithTag("$DEVICE_SETUP_CHOICE_TAG_PREFIX${item.id}")
            .performKeyInput { pressKey(Key.Enter) }
        assertEquals(item.id, selected?.id)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun localPreviewChoiceIsSelectableWithoutBeingRequestable() {
        val item = scheduleChoice(0).copy(
            requestable = false,
            source = scheduleChoice(0).source.copy(status = "synthetic_debug"),
            localPreview = debugPreview(),
        )
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

        compose.onNodeWithTag("$DEVICE_SETUP_CHOICE_TAG_PREFIX${item.id}")
            .assertIsFocused()
        compose.onNodeWithText("Просмотр не изменяет текущее активное расписание")
            .assertIsDisplayed()
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
    @OptIn(ExperimentalTestApi::class)
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
        compose.onNodeWithTag("$DEVICE_SETUP_CHOICE_TAG_PREFIX${first.id}")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("$DEVICE_SETUP_CHOICE_TAG_PREFIX${second.id}")
            .assertIsFocused()
        compose.onNodeWithText("synthetic-policy-1 · synthetic-source-1").assertIsDisplayed()
    }

    @Test
    fun undatedFreshnessRemainsExplicitInsteadOfBeingOmitted() {
        val base = scheduleChoice(0)
        val undated = base.copy(source = base.source.copy(freshThrough = null))
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(listOf(undated)),
                    activeSchedule = null,
                    onQueryChanged = {},
                    onRetrySearch = {},
                    onCitySelected = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText(
            "Основание: PROPOSAL · подтверждение synthetic-approval-0 · срок актуальности не указан",
        ).assertIsDisplayed()
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
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun debugFixtureSelectionShowsPrayerTimesWithoutClaimingARequestReachedAnOperator() {
        val item = scheduleChoice(0).let { choice ->
            choice.copy(
                source = choice.source.copy(status = "synthetic_debug"),
                activationAllowed = true,
            )
        }
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(listOf(item)).copy(
                        step = DeviceSetupStep.PREVIEW,
                        submission = ScheduleChoiceSubmissionUiState.Preview(
                            choice = item,
                            schedule = debugPreview(),
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

        compose.onNodeWithText("Демонстрационный режим").assertIsDisplayed()
        compose.onNodeWithText("Фаджр").assertIsDisplayed()
        compose.onNodeWithText("04:20").assertIsDisplayed()
        compose.onNodeWithText("Восход").assertIsDisplayed()
        compose.onNodeWithText("06:01").assertIsDisplayed()
        compose.onNodeWithText("Иша").assertIsDisplayed()
        compose.onNodeWithText("20:41").assertIsDisplayed()
        compose.onNodeWithText(
            "Показано локальное демонстрационное расписание. Это не реальные времена намаза. " +
                "Запрос на сервер не отправлялся; активное подписанное расписание не изменено.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Просмотр не изменяет текущее активное расписание")
            .assertIsDisplayed()
        compose.onNodeWithText("Использовать на этом телевизоре").assertIsDisplayed()
        compose.onNodeWithText("Ожидает подтверждения").assertDoesNotExist()
        compose.onNodeWithText("Использовать на этом телевизоре").assertIsFocused()
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

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun choiceListFits720pSafeFrame() = assertChoiceSetupFitsSafeFrame()

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun choiceListFits1080pDensitySafeFrame() = assertChoiceSetupFitsSafeFrame()

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun choiceListFits4kDensitySafeFrame() = assertChoiceSetupFitsSafeFrame()

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun previewFits720pSafeFrame() = assertPreviewFitsSafeFrame()

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun previewFits1080pDensitySafeFrame() = assertPreviewFitsSafeFrame()

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun previewFits4kDensitySafeFrame() = assertPreviewFitsSafeFrame()

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun productionPreviewFits720pWithoutRowsOverlappingActions() = assertPreviewFitsSafeFrame(production = true)

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun productionPreviewFits1080pWithoutRowsOverlappingActions() = assertPreviewFitsSafeFrame(production = true)

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun productionPreviewFits4kWithoutRowsOverlappingActions() = assertPreviewFitsSafeFrame(production = true)

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

    private fun assertChoiceSetupFitsSafeFrame() {
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(List(8) { scheduleChoice(it) }),
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
        compose.onNodeWithTag(DEVICE_SETUP_CHOICE_LIST_TAG).assertIsDisplayed()
        compose.onNodeWithTag("$DEVICE_SETUP_CHOICE_TAG_PREFIX${scheduleChoice(0).id}")
            .assertIsDisplayed()
        val rootWidth = root.right - root.left
        val rootHeight = root.bottom - root.top
        assert(screen.left - root.left >= rootWidth * 0.04f)
        assert(root.right - screen.right >= rootWidth * 0.04f)
        assert(screen.top - root.top >= rootHeight * 0.04f)
        assert(root.bottom - screen.bottom >= rootHeight * 0.04f)
    }

    private fun assertPreviewFitsSafeFrame(production: Boolean = false) {
        val item = scheduleChoice(0).copy(
            requestable = false,
            source = scheduleChoice(0).source.copy(status = "synthetic_debug"),
            localPreview = debugPreview().copy(
                dataClassification = if (production) "production" else "synthetic",
                provenance = if (production) ru.namaztime.tv.sync.DeviceSchedulePreviewProvenance(
                    "synthetic-production-fixture", "a".repeat(64), "b".repeat(64), "test-parser/v1", "2026-08-30T00:00:00Z",
                    attribution = "Синтетическая организация — исходное расписание: https://authority.example/calendar",
                ) else null,
            ),
            activationAllowed = true,
        )
        compose.setContent {
            NamazTvTheme {
                DeviceScheduleSetupScreen(
                    state = choiceState(listOf(item)).copy(
                        step = DeviceSetupStep.PREVIEW,
                        submission = ScheduleChoiceSubmissionUiState.Preview(
                            choice = item,
                            schedule = item.localPreview!!,
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

        compose.onNodeWithTag(DEVICE_SETUP_PREVIEW_TAG).assertIsDisplayed()
        DeviceSchedulePreviewPrayer.entries.forEach { prayer ->
            compose.onNodeWithTag(
                "$DEVICE_SETUP_PREVIEW_ROW_TAG_PREFIX${prayer.name.lowercase()}",
            ).assertIsDisplayed()
        }
        compose.onNodeWithText("Использовать на этом телевизоре").assertIsFocused()
        if (production) {
            val finalRow = compose.onNodeWithTag("${DEVICE_SETUP_PREVIEW_ROW_TAG_PREFIX}isha").getUnclippedBoundsInRoot()
            val action = compose.onNodeWithTag(DEVICE_SETUP_ACTIVATE_PREVIEW_TAG).getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue("last prayer row overlaps activation", finalRow.bottom <= action.top)
            compose.onNodeWithTag(DEVICE_SETUP_SOURCE_LINK_TAG).assertIsDisplayed()
            assertFullTextVisible("Источник")
            assertFullTextVisible("Подробнее")
        }
    }

    private fun assertLongLegacyPreviewFits() {
        renderPreview(longLegacyChoice())
        assertAllPreviewRowsVisible()
        assertFullTextVisible("2026-01-01 — 2026-12-31")
        val heading = compose.onNodeWithText("Текущее активное расписание")
        val headingLayout = mutableListOf<TextLayoutResult>()
        heading.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(headingLayout) }
        assertFalse("left context heading is clipped", headingLayout.single().hasVisualOverflow)
        val headingBounds = heading.getUnclippedBoundsInRoot()
        val previewBounds = compose.onNodeWithTag(DEVICE_SETUP_PREVIEW_TAG).getUnclippedBoundsInRoot()
        val layout = headingLayout.single()
        for (index in layout.layoutInput.text.indices) {
            val glyphRight = layout.getBoundingBox(index).right / compose.density.density + headingBounds.left.value
            assertTrue("left context glyph crosses the preview column", glyphRight <= previewBounds.left.value)
        }
    }

    private fun assertAllPreviewRowsVisible() {
        val action = compose.onNodeWithTag(DEVICE_SETUP_ACTIVATE_PREVIEW_TAG).getUnclippedBoundsInRoot()
        DeviceSchedulePreviewPrayer.entries.forEach { prayer ->
            val row = compose.onNodeWithTag("$DEVICE_SETUP_PREVIEW_ROW_TAG_PREFIX${prayer.name.lowercase()}")
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$prayer overlaps activation", row.bottom <= action.top)
        }
    }

    private fun assertFullTextVisible(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("text is visually truncated: $text", layouts.single().hasVisualOverflow)
    }

    private fun textMarkerIsInsideDetailsViewport(text: String, marker: String): Boolean {
        if (compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) return false
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val glyph = layouts.single().getBoundingBox(text.indexOf(marker))
        val bounds = node.getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag(DEVICE_SETUP_PROVENANCE_CONTENT_TAG).getUnclippedBoundsInRoot()
        val top = bounds.top.value + glyph.top / compose.density.density
        val bottom = bounds.top.value + glyph.bottom / compose.density.density
        return top >= viewport.top.value && bottom <= viewport.bottom.value
    }

    private fun renderPreview(
        item: DeviceScheduleChoice,
        onActivate: () -> Unit = {},
        activation: ScheduleActivationUiState = ScheduleActivationUiState.Idle,
    ) {
        compose.setContent { NamazTvTheme {
            DeviceScheduleSetupScreen(
                state = choiceState(listOf(item)).copy(
                    step = DeviceSetupStep.PREVIEW,
                    submission = ScheduleChoiceSubmissionUiState.Preview(item, item.localPreview!!),
                    activation = activation,
                ),
                activeSchedule = activeScheduleSummary(), onQueryChanged = {}, onRetrySearch = {},
                onCitySelected = {}, onBack = {}, onActivatePreview = onActivate,
            )
        } }
    }

    private fun longLegacyChoice(): DeviceScheduleChoice = scheduleChoice(
        0,
        "Синтетическое региональное духовное управление мусульман длинной области в составе " +
            "независимой синтетической организации / synthetic-attributed schedule publisher " +
            "(legal name deliberately unconfirmed in this synthetic UI fixture)",
    ).copy(
        executable = true,
        requestable = false,
        activationAllowed = true,
        source = scheduleChoice(0).source.copy(canonicalUrl = null),
        scopeDescription = "Синтетическая Вторая соборная мечеть тестового города, " +
            "только расписание этой мечети; географический охват не расширяется на соседние населённые пункты",
        localPreview = debugPreview().copy(
            dataClassification = "production",
            evidenceLabel = "CONFIRMED_PUBLIC · UNKNOWN",
            provenance = ru.namaztime.tv.sync.DeviceSchedulePreviewProvenance(
                "synthetic-long-legacy-fixture", "a".repeat(64), "b".repeat(64),
                "synthetic-effective-schedule/v1", "2026-08-20T11:31:33Z",
                attribution = "Синтетическое региональное духовное управление мусульман длинной области " +
                    "в составе независимой синтетической организации · source.example.invalid · " +
                    "Synthetic August source marks: first spelling / second spelling / third spelling",
            ),
        ),
    )

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

    private fun debugPreview() = DeviceSchedulePreview(
        date = LocalDate.parse("2026-09-03"),
        timezone = "Europe/Moscow",
        evidenceLabel = "PROPOSAL",
        rows = listOf(
            DeviceSchedulePreviewRow(
                DeviceSchedulePreviewPrayer.FAJR,
                LocalTime.parse("04:20"),
                LocalTime.parse("04:35"),
            ),
            DeviceSchedulePreviewRow(
                DeviceSchedulePreviewPrayer.SUNRISE,
                LocalTime.parse("06:01"),
                null,
            ),
            DeviceSchedulePreviewRow(
                DeviceSchedulePreviewPrayer.DHUHR,
                LocalTime.parse("12:28"),
                LocalTime.parse("13:00"),
            ),
            DeviceSchedulePreviewRow(
                DeviceSchedulePreviewPrayer.ASR,
                LocalTime.parse("16:24"),
                LocalTime.parse("16:40"),
            ),
            DeviceSchedulePreviewRow(
                DeviceSchedulePreviewPrayer.MAGHRIB,
                LocalTime.parse("19:03"),
                LocalTime.parse("19:13"),
            ),
            DeviceSchedulePreviewRow(
                DeviceSchedulePreviewPrayer.ISHA,
                LocalTime.parse("20:41"),
                LocalTime.parse("21:00"),
            ),
        ),
    )
}
