package com.example.namaztime.tv.presentation

import com.example.namaztime.tv.domain.PrayerTimeEngine
import com.example.namaztime.tv.domain.PrayerTimeResolution
import com.example.namaztime.tv.repository.LocalIqamahRule
import com.example.namaztime.tv.repository.LocalJumuahSession
import com.example.namaztime.tv.repository.LocalPrayerDay
import com.example.namaztime.tv.repository.LocalPrayerSchedule
import com.example.namaztime.tv.repository.LocalSnapshotDiagnostics
import com.example.namaztime.tv.repository.toTimeEngineInput
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MainPrayerDisplayStateTest {
    @Test
    fun explicitlySelectedDateControlsDisplayedPrayerRow() {
        val schedule = schedule()
        val resolution = PrayerTimeEngine().resolve(
            schedule.toTimeEngineInput(),
            Instant.parse("2026-08-19T23:20:00Z"),
        ) as PrayerTimeResolution.Available
        val state = schedule.toPrayerDisplayUiState(resolution)

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
            ) to "ИСТОЧНИК НЕ ПРОВЕРЕН",
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

        val state = schedule.toPrayerDisplayUiState(resolution)

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

        val state = schedule.toPrayerDisplayUiState(resolution)

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

        val state = schedule.toPrayerDisplayUiState(resolution)

        assertEquals("До азана", state.nextEventKindLabel)
        assertEquals("12:08", state.nextEventTime)
        assertEquals(null, state.iqamahSummary)
    }

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
