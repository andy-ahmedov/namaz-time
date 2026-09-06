package ru.namaztime.tv.sync

import java.time.LocalDate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.namaztime.tv.repository.PrayerScheduleRepository

class DevelopmentDeviceSetupGatewayTest {
    @Test
    fun debugGatewayOffersOmskForRussianAndLatinSearch() = runTest {
        val gateway = DevelopmentDeviceSetupGateway()

        val russian = gateway.searchCities("Омск") as DeviceSetupResult.Success
        val latin = gateway.searchCities("Omsk") as DeviceSetupResult.Success

        assertEquals("Омск", russian.value.single().canonicalName)
        assertEquals(russian.value.single().id, latin.value.single().id)
    }

    @Test
    fun debugGatewaySearchesMoscowAndExposesEverySyntheticAuthorityChoice() = runTest {
        val gateway = DevelopmentDeviceSetupGateway()

        val search = gateway.searchCities("моск") as DeviceSetupResult.Success
        val cities = search.value
        assertTrue(cities.size >= 2)
        val moscow = cities.single { it.canonicalName == "Москва" }
        assertTrue(moscow.federalSubjectName.startsWith("Демо-каталог · "))

        val choicesResult = gateway.loadScheduleChoices(
            cityId = moscow.id,
            date = LocalDate.parse("2026-09-03"),
        ) as DeviceSetupResult.Success
        val choices = choicesResult.value.choices

        assertEquals(2, choices.size)
        assertEquals(2, choices.map { it.authorityLabel }.distinct().size)
        assertTrue(choices.all { "Москва" in it.displayLabel })
        assertTrue(choices.all { it.authorities.single().evidenceLabel == "PROPOSAL" })
        assertTrue(choices.all { !it.requestable })
        assertTrue(choices.all { it.activationAllowed })
        assertTrue(choices.all { it.localPreview?.rows?.size == 6 })
        assertEquals("04:20", choices.first().localPreview?.rows?.first()?.adhan.toString())
        assertEquals("04:25", choices.last().localPreview?.rows?.first()?.adhan.toString())
        assertTrue(!choicesResult.value.requestAllowed)

        val activation = gateway.activateScheduleChoice(
            cityId = moscow.id,
            choiceId = choices.first().id,
            date = LocalDate.parse("2026-09-03"),
        )
        assertTrue(activation is DeviceSetupResult.Success)
    }

    @Test
    fun activatedChoiceBecomesPersistentLocalDisplayScheduleWithoutChangingRoomSource() = runTest {
        val selections = InMemoryDevelopmentScheduleSelectionStore()
        val gateway = DevelopmentDeviceSetupGateway(selections)
        val baseRepository = object : PrayerScheduleRepository {
            override fun observeActiveSchedule() = flowOf(null)
        }
        val repository = DevelopmentPrayerScheduleRepository(
            baseRepository = baseRepository,
            selectionStore = selections,
            clock = Clock.fixed(Instant.parse("2026-09-03T09:00:00Z"), ZoneOffset.UTC),
        )
        val city = (gateway.searchCities("Омск") as DeviceSetupResult.Success).value.single()
        val choice = (gateway.loadScheduleChoices(
            city.id,
            LocalDate.parse("2026-09-03"),
        ) as DeviceSetupResult.Success).value.choices.single()

        val result = gateway.activateScheduleChoice(
            cityId = city.id,
            choiceId = choice.id,
            date = LocalDate.parse("2026-09-03"),
        )
        val active = requireNotNull(repository.observeActiveSchedule().first())

        assertTrue(result is DeviceSetupResult.Success)
        assertEquals("Омск", active.locality)
        assertEquals("Asia/Omsk", active.timezoneId)
        assertEquals(choice.authorityLabel, active.authorityName)
        assertEquals("synthetic", active.diagnostics?.dataClassification)
        assertEquals("proposal", active.diagnostics?.approvalStatus)
        assertTrue(active.days.any { it.localDate == "2026-09-03" })
        assertTrue(active.days.any { it.localDate == "2026-09-04" })
        assertEquals(5, active.iqamahRules.size)
    }

    @Test
    fun debugActivationStorageFailureIsReportedWithoutFalseSuccess() = runTest {
        val selection = MutableStateFlow<DevelopmentScheduleSelection?>(null)
        val failingStore = object : DevelopmentScheduleSelectionStore {
            override val selection = selection
            override suspend fun save(selection: DevelopmentScheduleSelection) {
                throw IOException("synthetic storage failure")
            }
        }
        val gateway = DevelopmentDeviceSetupGateway(failingStore)
        val city = (gateway.searchCities("Омск") as DeviceSetupResult.Success).value.single()
        val choice = (gateway.loadScheduleChoices(
            city.id,
            LocalDate.parse("2026-09-03"),
        ) as DeviceSetupResult.Success).value.choices.single()

        val result = gateway.activateScheduleChoice(city.id, choice.id, LocalDate.parse("2026-09-03"))

        assertEquals(
            DeviceSetupResult.Failure("setup_activation_io", retryable = true),
            result,
        )
        assertNull(selection.value)
    }
}
