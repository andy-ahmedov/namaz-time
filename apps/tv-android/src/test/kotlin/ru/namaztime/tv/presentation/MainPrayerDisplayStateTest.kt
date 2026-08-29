package ru.namaztime.tv.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ru.namaztime.tv.domain.PrayerTimeEngine
import ru.namaztime.tv.domain.PrayerTimeResolution
import ru.namaztime.tv.repository.LocalIqamahRule
import ru.namaztime.tv.repository.LocalJumuahSession
import ru.namaztime.tv.repository.LocalPrayerDay
import ru.namaztime.tv.repository.LocalPrayerSchedule
import ru.namaztime.tv.repository.LocalSnapshotDiagnostics
import ru.namaztime.tv.repository.toTimeEngineInput
import ru.namaztime.tv.repository.OperatorMosquePresentationIdentity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class MainPrayerDisplayStateTest {
    @Test
    fun explicitlySelectedDateControlsDisplayedPrayerRow() {
        val schedule = schedule()
        val resolution = PrayerTimeEngine().resolve(
            schedule.toTimeEngineInput(),
            Instant.parse("2026-08-19T23:20:00Z"),
        ) as PrayerTimeResolution.Available
        val state = schedule.toPrayerDisplayUiState(resolution, strings())

        assertEquals("Синтетическая мечеть", state.mosqueName)
        assertEquals("20 августа 2026", state.dateLabel)
        assertEquals("Четверг", state.weekdayLabel)
        assertEquals("03:20:00", state.mosqueLocalTime)
        assertEquals("Фаджр · икамат", state.nextPrayerLabel)
        assertEquals("До икамата", state.nextEventKindLabel)
        assertEquals("03:39", state.nextEventTime)
        assertEquals("00:19:00", state.countdown)
        assertEquals("Икамат · Фаджр", state.iqamahSummary?.label)
        assertEquals("03:39", state.iqamahSummary?.time)
        assertEquals("через 00:19:00", state.iqamahSummary?.countdownLabel)
        assertEquals("03:14", state.rows.first().adhan)
        assertEquals("03:39", state.rows.first().iqamah)
    }

    @Test
    fun pilotMosqueUsesItsShortPublicDisplayIdentity() {
        val schedule = schedule().copy(
            mosqueId = "second-cathedral-mosque-ulyanovsk",
            mosqueName = "Вторая Соборная мечеть Ульяновска",
            locality = "Ульяновск, ул. Дзержинского, 18А",
        )
        val resolution = PrayerTimeEngine().resolve(
            schedule.toTimeEngineInput(),
            Instant.parse("2026-08-19T23:20:00Z"),
        ) as PrayerTimeResolution.Available

        val state = schedule.toPrayerDisplayUiState(resolution, strings())

        assertEquals("Вторая Соборная Мечеть", state.mosqueName)
        assertEquals("Ульяновск", state.location)
    }

    @Test
    fun localPresentationIdentityOverridesOnlyDisplayedNameAndAddress() {
        val schedule = schedule().copy(
            mosqueId = "second-cathedral-mosque-ulyanovsk",
            mosqueName = "Вторая Соборная мечеть Ульяновска",
            locality = "Ульяновск, ул. Дзержинского, 18А",
        )
        val resolution = PrayerTimeEngine().resolve(
            schedule.toTimeEngineInput(),
            Instant.parse("2026-08-19T23:20:00Z"),
        ) as PrayerTimeResolution.Available

        val state = schedule.toPrayerDisplayUiState(
            resolution,
            strings(),
            schedule.toMosqueDisplayIdentity(
                OperatorMosquePresentationIdentity(
                    displayName = "Мечеть Аль-Ихлас",
                    displayAddress = "ул. Мира, 10",
                ),
            ),
        )

        assertEquals("Мечеть Аль-Ихлас", state.mosqueName)
        assertEquals("ул. Мира, 10", state.location)
        assertEquals("second-cathedral-mosque-ulyanovsk", schedule.mosqueId)
        assertEquals("Europe/Ulyanovsk", schedule.timezoneId)
    }

    @Test
    fun blankLocalPresentationIdentityFallsBackToPilotIdentity() {
        val schedule = schedule().copy(
            mosqueId = "second-cathedral-mosque-ulyanovsk",
            mosqueName = "Вторая Соборная мечеть Ульяновска",
            locality = "Ульяновск, ул. Дзержинского, 18А",
        )

        val identity = schedule.toMosqueDisplayIdentity(OperatorMosquePresentationIdentity())

        assertEquals("Вторая Соборная Мечеть", identity.name)
        assertEquals("Ульяновск", identity.locality)
    }

    @Test
    fun sourceStateFailsConservativelyWithoutAuthenticityVerification() {
        val base = schedule()
        val cases = listOf(
            base to "ТЕСТОВЫЕ ДАННЫЕ",
            base.copy(
                sourceKind = "calculation_profile",
                diagnostics = base.diagnostics?.copy(dataClassification = "production"),
            ) to "РАСЧЁТНОЕ — НЕ ОФИЦИАЛЬНО",
            base.copy(
                diagnostics = base.diagnostics?.copy(dataClassification = "production"),
            ) to "УТВЕРЖДЁННЫЕ ДАННЫЕ",
            base.copy(
                diagnostics = base.diagnostics?.copy(approvalStatus = "pending"),
            ) to "НЕ ОДОБРЕНО",
        )

        cases.forEach { (schedule, expectedLabel) ->
            assertEquals(
                expectedLabel,
                schedule.toPrayerDisplayUiState(
                    PrayerTimeEngine().resolve(
                        schedule.toTimeEngineInput(),
                        Instant.parse("2026-08-19T23:20:00Z"),
                    ) as PrayerTimeResolution.Available,
                    strings(),
                ).sourceLabel,
            )
        }
    }

    @Test
    fun fridaySessionsStaySeparateFromTheDhuhrRow() {
        val schedule = schedule().copy(
            jumuahSessions = listOf(
                LocalJumuahSession(
                    id = "first",
                    label = "Первая",
                    khutbahTime = "12:40",
                    salahTime = "13:00",
                    validFrom = "2026-08-01",
                    validTo = "2026-08-31",
                ),
            ),
        )
        val resolution = PrayerTimeEngine().resolve(
            schedule.toTimeEngineInput(),
            Instant.parse("2026-08-21T08:30:00Z"),
        ) as PrayerTimeResolution.Available

        val state = schedule.toPrayerDisplayUiState(resolution, strings())

        assertEquals(listOf("Первая 13:00"), state.jumuahSessions.map { it.text })
        assertEquals("12:08", state.rows.first { it.id == "dhuhr" }.adhan)
        assertEquals("Джума · Первая", state.nextPrayerLabel)
    }

    @Test
    fun nextDayFajrIsLabeledTomorrowAndDoesNotHighlightTodaysFajrRow() {
        val schedule = schedule()
        val resolution = PrayerTimeEngine().resolve(
            schedule.toTimeEngineInput(),
            Instant.parse("2026-08-20T17:00:00Z"),
        ) as PrayerTimeResolution.Available

        val state = schedule.toPrayerDisplayUiState(resolution, strings())

        assertEquals("Фаджр · завтра", state.nextPrayerLabel)
        assertEquals("03:14", state.rows.first { it.id == "fajr" }.adhan)
        assertEquals(false, state.rows.any { it.isNextEvent })
        assertEquals("До азана завтра", state.nextEventKindLabel)
        assertEquals("03:16", state.nextEventTime)
        assertEquals(null, state.iqamahSummary)
    }

    @Test
    fun absentResolvedIqamahStaysAbsentInSummary() {
        val schedule = schedule().copy(iqamahRules = emptyList())
        val resolution = PrayerTimeEngine().resolve(
            schedule.toTimeEngineInput(),
            Instant.parse("2026-08-19T23:20:00Z"),
        ) as PrayerTimeResolution.Available

        val state = schedule.toPrayerDisplayUiState(resolution, strings())

        assertEquals("До азана", state.nextEventKindLabel)
        assertEquals("12:08", state.nextEventTime)
        assertEquals(null, state.iqamahSummary)
    }

    @Test
    fun englishLanguageLocalizesTheWholeDerivedDisplayState() {
        val schedule = schedule()
        val resolution = PrayerTimeEngine().resolve(
            schedule.toTimeEngineInput(),
            Instant.parse("2026-08-19T23:20:00Z"),
        ) as PrayerTimeResolution.Available

        val state = schedule.toPrayerDisplayUiState(resolution, strings(AppLanguage.ENGLISH))

        assertEquals("20 August 2026", state.dateLabel)
        assertEquals("Thursday", state.weekdayLabel)
        assertEquals("Fajr", state.currentPrayerLabel)
        assertEquals("Fajr · iqamah", state.nextPrayerLabel)
        assertEquals("Until iqamah", state.nextEventKindLabel)
        assertEquals("Iqamah · Fajr", state.iqamahSummary?.label)
        assertEquals("in 00:19:00", state.iqamahSummary?.countdownLabel)
        assertEquals("Fajr", state.rows.first().label)
        assertEquals("TEST DATA", state.sourceLabel)
    }

    @Test
    fun currentPrayerLabelComesFromTheResolvedPrayerTimeline() {
        val schedule = schedule()
        val cases = listOf(
            "2026-08-19T22:00:00Z" to "Иша",
            "2026-08-19T23:20:00Z" to "Фаджр",
            "2026-08-20T09:00:00Z" to "Зухр",
            "2026-08-20T13:00:00Z" to "Аср",
            "2026-08-20T15:00:00Z" to "Магриб",
            "2026-08-20T18:00:00Z" to "Иша",
        )

        cases.forEach { (instant, expectedLabel) ->
            val resolution = PrayerTimeEngine().resolve(
                schedule.toTimeEngineInput(),
                Instant.parse(instant),
            ) as PrayerTimeResolution.Available

            assertEquals(
                expectedLabel,
                schedule.toPrayerDisplayUiState(resolution, strings()).currentPrayerLabel,
            )
        }
    }

    @Test
    fun currentPrayerLabelUsesResolvedFridaySessionAfterItsSalah() {
        val schedule = schedule().copy(
            jumuahSessions = listOf(
                LocalJumuahSession(
                    id = "first",
                    label = "Первая",
                    khutbahTime = "12:40",
                    salahTime = "13:00",
                    validFrom = "2026-08-01",
                    validTo = "2026-08-31",
                ),
            ),
        )
        val resolution = PrayerTimeEngine().resolve(
            schedule.toTimeEngineInput(),
            Instant.parse("2026-08-21T09:30:00Z"),
        ) as PrayerTimeResolution.Available

        assertEquals(
            "Джума",
            schedule.toPrayerDisplayUiState(resolution, strings()).currentPrayerLabel,
        )
    }

    private fun strings(language: AppLanguage = AppLanguage.RUSSIAN): AppStrings =
        appStringsFor(ApplicationProvider.getApplicationContext<Context>(), language)

    private fun schedule() = LocalPrayerSchedule(
        snapshotId = "synthetic-ulsk-demo-2026-08-v1",
        mosqueId = "mosque-demo-ulsk",
        mosqueName = "Синтетическая мечеть",
        timezoneId = "Europe/Ulyanovsk",
        sourceKind = "manual_import",
        authorityName = "Synthetic test fixture",
        coverageFrom = "2026-08-19",
        coverageTo = "2026-08-21",
        diagnostics = LocalSnapshotDiagnostics(
            dataClassification = "synthetic",
            rawSha256 = "1".repeat(64),
            parserVersion = "synthetic/1",
            approvalId = "approval-test",
            approvalStatus = "approved",
            signingKeyId = "test-placeholder-key",
        ),
        days = listOf(
            LocalPrayerDay("2026-08-19", "03:12", "05:21", "12:08", "16:47", "18:53", "21:01"),
            LocalPrayerDay("2026-08-20", "03:14", "05:23", "12:08", "16:45", "18:51", "20:58"),
            LocalPrayerDay("2026-08-21", "03:16", "05:25", "12:08", "16:43", "18:48", "20:55"),
        ),
        iqamahRules = listOf(
            LocalIqamahRule(
                id = "fajr-offset",
                prayer = "fajr",
                validFrom = "2026-08-19",
                validTo = "2026-08-21",
                weekdaysMask = 0b1111111,
                priority = 1,
                mode = "offset_after_adhan",
                fixedTime = null,
                offsetMinutes = 25,
                reason = "synthetic",
            ),
        ),
    )
}
