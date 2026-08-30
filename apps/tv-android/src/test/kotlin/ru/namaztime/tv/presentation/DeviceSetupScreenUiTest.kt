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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.sync.CanonicalCityCandidate

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
}
