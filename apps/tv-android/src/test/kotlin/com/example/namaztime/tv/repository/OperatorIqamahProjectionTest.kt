package com.example.namaztime.tv.repository

import com.example.namaztime.tv.domain.PrayerTimeEngine
import com.example.namaztime.tv.domain.PrayerTimeResolution
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorIqamahProjectionTest {
    private val engine = PrayerTimeEngine()
    private val now = Instant.parse("2026-08-20T00:00:00Z")

    @Test
    fun operatorCanSetEachIqamahWithoutMutatingTheLocalSnapshot() {
        val schedule = schedule()
        val settings = OperatorIqamahTimes(
            fajr = "04:30",
            dhuhr = "13:30",
            asr = "18:15",
            maghrib = "20:30",
            isha = "22:30",
        )

        val resolution = engine.resolve(
            schedule.toTimeEngineInput(settings, now),
            now,
        ) as PrayerTimeResolution.Available

        assertEquals("04:30", resolution.prayers.getValue("fajr").iqamah.toString())
        assertEquals("13:30", resolution.prayers.getValue("dhuhr").iqamah.toString())
        assertEquals("18:15", resolution.prayers.getValue("asr").iqamah.toString())
        assertEquals("20:30", resolution.prayers.getValue("maghrib").iqamah.toString())
        assertEquals("22:30", resolution.prayers.getValue("isha").iqamah.toString())
        assertTrue(schedule.iqamahRules.isEmpty())
        assertTrue(schedule.iqamahDateOverrides.isEmpty())
    }

    @Test
    fun operatorIqamahBeforeAdhanFailsClosedWithoutBlankingPrayerTimes() {
        val result = engine.resolve(
            schedule().toTimeEngineInput(OperatorIqamahTimes(fajr = "03:30"), now),
            now,
        )

        assertTrue(result is PrayerTimeResolution.Available)
        assertNull((result as PrayerTimeResolution.Available).prayers.getValue("fajr").iqamah)
    }

    private fun schedule() = LocalPrayerSchedule(
        snapshotId = "snapshot",
        mosqueId = "mosque",
        mosqueName = "Мечеть",
        timezoneId = "UTC",
        sourceKind = "manual_import",
        authorityName = "Мечеть",
        coverageFrom = "2026-08-20",
        coverageTo = "2026-08-21",
        days = listOf(
            day("2026-08-20"),
            day("2026-08-21"),
        ),
    )

    private fun day(date: String) = LocalPrayerDay(
        localDate = date,
        fajr = "04:00",
        sunrise = "05:30",
        dhuhr = "13:00",
        asr = "18:00",
        maghrib = "20:00",
        isha = "22:00",
    )
}
