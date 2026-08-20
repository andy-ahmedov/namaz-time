package com.example.namaztime.tv.domain

import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerTimeEngineTest {
    private val engine = PrayerTimeEngine()

    @Test
    fun `mosque timezone alone selects the local schedule date`() {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
        try {
            val result = engine.resolve(
                schedule = schedule(
                    timezone = "Europe/Ulyanovsk",
                    days = listOf(day("2026-08-19"), day("2026-08-20")),
                ),
                now = Instant.parse("2026-08-19T21:30:00Z"),
            ).available()

            assertEquals(LocalDate.parse("2026-08-20"), result.localDate)
            assertEquals("01:30", result.localTime.toString())
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `before exact boundary and immediately after resolve deterministically`() {
        val schedule = schedule(days = listOf(day("2026-08-20"), day("2026-08-21")))
        val cases = listOf(
            "2026-08-20T03:09:59Z" to Triple("fajr", PrayerEventKind.ADHAN, 1L),
            "2026-08-20T03:10:00Z" to Triple("fajr", PrayerEventKind.ADHAN, 0L),
            "2026-08-20T03:10:00.000000001Z" to Triple("dhuhr", PrayerEventKind.ADHAN, 31_800L),
            "2026-08-20T21:00:01Z" to Triple("fajr", PrayerEventKind.ADHAN, 22_199L),
        )

        cases.forEach { (instant, expected) ->
            val result = engine.resolve(schedule, Instant.parse(instant)).available()
            assertEquals(expected.first, result.nextEvent?.prayer)
            assertEquals(expected.second, result.nextEvent?.kind)
            assertEquals(expected.third, result.countdownSeconds)
        }
    }

    @Test
    fun `sunrise countdown participation is explicit and off by default`() {
        val schedule = schedule(days = listOf(day("2026-08-20")))

        val defaultResult = engine.resolve(
            schedule,
            Instant.parse("2026-08-20T04:00:00Z"),
        ).available()
        val sunriseResult = PrayerTimeEngine(
            CountdownPolicy(includeSunrise = true),
        ).resolve(schedule, Instant.parse("2026-08-20T04:00:00Z")).available()

        assertEquals("dhuhr", defaultResult.nextEvent?.prayer)
        assertEquals("sunrise", sunriseResult.nextEvent?.prayer)
    }

    @Test
    fun `exact override beats matching weekday range and base rules`() {
        val schedule = schedule(
            days = listOf(day("2026-08-20")),
            rules = listOf(
                rule("base", priority = 1, fixedTime = "03:25"),
                rule("range", priority = 5, fixedTime = "03:30"),
                rule("weekday", priority = 10, fixedTime = "03:35", weekdaysMask = 1 shl 3),
            ),
            overrides = listOf(
                IqamahDateOverrideInput(
                    localDate = "2026-08-20",
                    prayer = "fajr",
                    mode = "fixed_time",
                    fixedTime = "03:40",
                    offsetMinutes = null,
                ),
            ),
        )

        val result = engine.resolve(schedule, Instant.parse("2026-08-20T03:20:00Z")).available()

        assertEquals("03:40", result.prayers.getValue("fajr").iqamah.toString())
        assertEquals(PrayerEventKind.IQAMAH, result.nextEvent?.kind)
    }

    @Test
    fun `highest priority matching weekday rule wins without an override`() {
        val schedule = schedule(
            days = listOf(day("2026-08-20")),
            rules = listOf(
                rule("base", priority = 1, fixedTime = "03:25"),
                rule("weekday", priority = 10, fixedTime = "03:35", weekdaysMask = 1 shl 3),
                rule("friday", priority = 20, fixedTime = "03:45", weekdaysMask = 1 shl 4),
            ),
        )

        val result = engine.resolve(schedule, Instant.parse("2026-08-20T03:20:00Z")).available()

        assertEquals("03:35", result.prayers.getValue("fajr").iqamah.toString())
    }

    @Test
    fun `offset mode resolves from adhan while missing iqamah stays missing`() {
        val result = engine.resolve(
            schedule(
                days = listOf(day("2026-08-20")),
                rules = listOf(
                    rule(
                        id = "fajr-offset",
                        priority = 1,
                        mode = "offset_after_adhan",
                        fixedTime = null,
                        offsetMinutes = 20,
                    ),
                ),
            ),
            Instant.parse("2026-08-20T03:00:00Z"),
        ).available()

        assertEquals("03:30", result.prayers.getValue("fajr").iqamah.toString())
        assertNull(result.prayers.getValue("dhuhr").iqamah)
    }

    @Test
    fun `iqamah offset crossing local midnight fails closed`() {
        val result = engine.resolve(
            schedule(
                days = listOf(day("2026-08-20").copy(isha = "23:00")),
                rules = listOf(
                    rule(
                        id = "isha-crossing",
                        prayer = "isha",
                        priority = 1,
                        mode = "offset_after_adhan",
                        fixedTime = null,
                        offsetMinutes = 120,
                    ),
                ),
            ),
            Instant.parse("2026-08-20T12:00:00Z"),
        )

        assertEquals("IQAMAH_CROSSES_LOCAL_DATE", result.unavailable().supportCode)
    }

    @Test
    fun `ambiguous equal-priority matching rules fail closed`() {
        val result = engine.resolve(
            schedule(
                days = listOf(day("2026-08-20")),
                rules = listOf(
                    rule("a", priority = 7, fixedTime = "03:25"),
                    rule("b", priority = 7, fixedTime = "03:30"),
                ),
            ),
            Instant.parse("2026-08-20T03:00:00Z"),
        )

        assertEquals("IQAMAH_RULE_AMBIGUOUS", result.unavailable().supportCode)
    }

    @Test
    fun `Friday exposes multiple Jumuah sessions without mutating Dhuhr`() {
        val sessions = listOf(
            JumuahSessionInput("second", "Вторая", null, "14:00", "2026-08-01", "2026-08-31"),
            JumuahSessionInput("first", "Первая", "12:40", "13:00", "2026-08-01", "2026-08-31"),
            JumuahSessionInput("expired", "Старая", null, "12:50", "2026-07-01", "2026-07-31"),
        )
        val result = engine.resolve(
            schedule(days = listOf(day("2026-08-21")), sessions = sessions),
            Instant.parse("2026-08-21T12:30:00Z"),
        ).available()

        assertEquals(listOf("first", "second"), result.jumuahSessions.map { it.id })
        assertEquals("12:00", result.prayers.getValue("dhuhr").adhan.toString())
        assertEquals(PrayerEventKind.JUMUAH, result.nextEvent?.kind)
        assertEquals("first", result.nextEvent?.jumuahSessionId)
    }

    @Test
    fun `Jumuah sessions are absent on non-Friday local dates`() {
        val result = engine.resolve(
            schedule(
                days = listOf(day("2026-08-20")),
                sessions = listOf(
                    JumuahSessionInput(
                        "first", "Первая", null, "13:00", "2026-08-01", "2026-08-31",
                    ),
                ),
            ),
            Instant.parse("2026-08-20T12:30:00Z"),
        ).available()

        assertTrue(result.jumuahSessions.isEmpty())
        assertEquals("asr", result.nextEvent?.prayer)
    }

    @Test
    fun `leap day and year boundary retain next-day semantics`() {
        val leap = engine.resolve(
            schedule(days = listOf(day("2028-02-29"))),
            Instant.parse("2028-02-29T02:00:00Z"),
        ).available()
        val yearBoundary = engine.resolve(
            schedule(days = listOf(day("2026-12-31"), day("2027-01-01"))),
            Instant.parse("2026-12-31T21:01:00Z"),
        ).available()

        assertEquals(LocalDate.parse("2028-02-29"), leap.localDate)
        assertEquals(LocalDate.parse("2027-01-01"), yearBoundary.nextEvent?.localDate)
        assertEquals("fajr", yearBoundary.nextEvent?.prayer)
    }

    @Test
    fun `DST zone rollover uses its local date and rejects a nonexistent wall time`() {
        val beforeRollover = engine.resolve(
            schedule(
                timezone = "Europe/London",
                days = listOf(day("2026-03-28"), day("2026-03-29")),
            ),
            Instant.parse("2026-03-28T23:30:00Z"),
        ).available()
        val invalidGap = engine.resolve(
            schedule(
                timezone = "Europe/London",
                days = listOf(day("2026-03-29").copy(fajr = "01:30")),
            ),
            Instant.parse("2026-03-29T00:30:00Z"),
        )

        assertEquals(LocalDate.parse("2026-03-28"), beforeRollover.localDate)
        assertEquals("SCHEDULE_NONEXISTENT_LOCAL_TIME", invalidGap.unavailable().supportCode)
    }

    @Test
    fun `DST overlap fails closed without an explicit source fold`() {
        val result = engine.resolve(
            schedule(
                timezone = "Europe/London",
                days = listOf(day("2026-10-25").copy(fajr = "01:30")),
            ),
            Instant.parse("2026-10-25T00:15:00Z"),
        )

        assertEquals("SCHEDULE_AMBIGUOUS_LOCAL_TIME", result.unavailable().supportCode)
    }

    @Test
    fun `outside local coverage is explicit and does not choose an arbitrary day`() {
        val result = engine.resolve(
            schedule(days = listOf(day("2026-08-20"))),
            Instant.parse("2026-08-21T01:00:00Z"),
        )

        assertEquals("SCHEDULE_DATE_OUTSIDE_COVERAGE", result.unavailable().supportCode)
    }

    @Test
    fun `whole snapshot validation catches a future-day rule failure before display`() {
        val schedule = schedule(
            days = listOf(
                day("2026-08-20"),
                day("2026-08-21").copy(isha = "23:00"),
            ),
            rules = listOf(
                IqamahRuleInput(
                    id = "friday-isha-crossing",
                    prayer = "isha",
                    validFrom = "2026-08-21",
                    validTo = "2026-08-21",
                    weekdaysMask = 1 shl 4,
                    priority = 1,
                    mode = "offset_after_adhan",
                    fixedTime = null,
                    offsetMinutes = 120,
                ),
            ),
        )

        assertEquals("IQAMAH_CROSSES_LOCAL_DATE", engine.validate(schedule))
    }

    @Test
    fun `daily prayer times must remain in strict wall-clock order`() {
        val schedule = schedule(
            days = listOf(day("2026-08-20").copy(fajr = "06:00", sunrise = "05:00")),
        )

        assertEquals("SCHEDULE_PRAYER_ORDER_INVALID", engine.validate(schedule))
    }

    private fun PrayerTimeResolution.available(): PrayerTimeResolution.Available {
        assertTrue("expected available but was $this", this is PrayerTimeResolution.Available)
        return this as PrayerTimeResolution.Available
    }

    private fun PrayerTimeResolution.unavailable(): PrayerTimeResolution.Unavailable {
        assertTrue("expected unavailable but was $this", this is PrayerTimeResolution.Unavailable)
        return this as PrayerTimeResolution.Unavailable
    }

    private fun schedule(
        timezone: String = "UTC",
        days: List<PrayerDayInput>,
        rules: List<IqamahRuleInput> = emptyList(),
        overrides: List<IqamahDateOverrideInput> = emptyList(),
        sessions: List<JumuahSessionInput> = emptyList(),
    ) = PrayerScheduleInput(
        timezoneId = timezone,
        days = days,
        iqamahRules = rules,
        iqamahDateOverrides = overrides,
        jumuahSessions = sessions,
    )

    private fun day(date: String) = PrayerDayInput(
        localDate = date,
        fajr = "03:10",
        sunrise = "05:00",
        dhuhr = "12:00",
        asr = "16:00",
        maghrib = "19:00",
        isha = "21:00",
    )

    private fun rule(
        id: String,
        prayer: String = "fajr",
        priority: Int,
        mode: String = "fixed_time",
        fixedTime: String? = "03:25",
        offsetMinutes: Int? = null,
        weekdaysMask: Int = 0b1111111,
    ) = IqamahRuleInput(
        id = id,
        prayer = prayer,
        validFrom = "2026-01-01",
        validTo = "2026-12-31",
        weekdaysMask = weekdaysMask,
        priority = priority,
        mode = mode,
        fixedTime = fixedTime,
        offsetMinutes = offsetMinutes,
    )
}
