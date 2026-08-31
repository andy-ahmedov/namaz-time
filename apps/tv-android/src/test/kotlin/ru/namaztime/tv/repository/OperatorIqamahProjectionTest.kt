package ru.namaztime.tv.repository

import ru.namaztime.tv.domain.PrayerTimeEngine
import ru.namaztime.tv.domain.PrayerTimeResolution
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorIqamahProjectionTest {
    private val engine = PrayerTimeEngine()
    private val now = Instant.parse("2026-08-20T00:00:00Z")

    @Test
    fun operatorConfigurationKeepsOffsetsButUsesOneFixedDhuhrAndJumuahTime() {
        val schedule = schedule()
        val settings = OperatorIqamahConfiguration(
            fajrOffsetMinutes = 5,
            dhuhrFixedTimeMinutes = 13 * 60 + 20,
            asrOffsetMinutes = 15,
            maghribOffsetMinutes = 7,
            ishaOffsetMinutes = 20,
        )

        val resolution = engine.resolve(
            schedule.toTimeEngineInput(settings, now),
            now,
        ) as PrayerTimeResolution.Available

        assertEquals("04:05", resolution.prayers.getValue("fajr").iqamah.toString())
        assertEquals("13:20", resolution.prayers.getValue("dhuhr").iqamah.toString())
        assertEquals("18:15", resolution.prayers.getValue("asr").iqamah.toString())
        assertEquals("20:07", resolution.prayers.getValue("maghrib").iqamah.toString())
        assertEquals("22:20", resolution.prayers.getValue("isha").iqamah.toString())
        assertTrue(schedule.iqamahRules.isEmpty())
        assertTrue(schedule.iqamahDateOverrides.isEmpty())

        val friday = Instant.parse("2026-08-21T10:00:00Z")
        val fridayResolution = engine.resolve(
            schedule.toTimeEngineInput(settings, friday),
            friday,
        ) as PrayerTimeResolution.Available
        assertEquals("13:00", fridayResolution.prayers.getValue("dhuhr").adhan.toString())
        assertEquals("13:20", fridayResolution.prayers.getValue("dhuhr").iqamah.toString())
        assertEquals("13:20", fridayResolution.jumuahSessions.single().salahTime.toString())
    }

    @Test
    fun absentOperatorOffsetLeavesApprovedBasePolicyActive() {
        val input = scheduleWithBasePolicy().toTimeEngineInput(OperatorIqamahConfiguration(), now)
        val result = engine.resolve(input, now)

        assertTrue(result is PrayerTimeResolution.Available)
        assertEquals(
            "04:05",
            (result as PrayerTimeResolution.Available).prayers.getValue("fajr").iqamah.toString(),
        )
    }

    @Test
    fun clearingIndividualOverridesRestoresApprovedFajrAndDhuhrWithoutChangingOthers() {
        val custom = OperatorIqamahConfiguration(
            fajrOffsetMinutes = 12,
            dhuhrFixedTimeMinutes = 13 * 60 + 25,
            asrOffsetMinutes = 17,
        )
        val reset = custom
            .withEditorValue("fajr", null)
            .withEditorValue("dhuhr", null)
        val result = engine.resolve(
            scheduleWithBasePolicy().toTimeEngineInput(reset, now),
            now,
        ) as PrayerTimeResolution.Available

        assertEquals(null, reset.fajrOffsetMinutes)
        assertEquals(null, reset.dhuhrFixedTimeMinutes)
        assertEquals(17, reset.asrOffsetMinutes)
        assertEquals("04:05", result.prayers.getValue("fajr").iqamah.toString())
        assertEquals("13:15", result.prayers.getValue("dhuhr").iqamah.toString())
        assertEquals("18:17", result.prayers.getValue("asr").iqamah.toString())
    }

    private fun scheduleWithBasePolicy() = schedule().copy(
        iqamahRules = listOf(
            LocalIqamahRule(
                id = "approved-fajr",
                prayer = "fajr",
                validFrom = "2026-08-20",
                validTo = "2026-08-21",
                weekdaysMask = 127,
                priority = 1,
                mode = "offset_after_adhan",
                fixedTime = null,
                offsetMinutes = 5,
                reason = "approved policy",
            ),
            LocalIqamahRule(
                id = "approved-dhuhr",
                prayer = "dhuhr",
                validFrom = "2026-08-20",
                validTo = "2026-08-21",
                weekdaysMask = 127,
                priority = 1,
                mode = "fixed_time",
                fixedTime = "13:15",
                offsetMinutes = null,
                reason = "approved policy",
            ),
        ),
    )

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
        jumuahSessions = listOf(
            LocalJumuahSession(
                id = "friday-1315",
                label = "Jumuah",
                khutbahTime = null,
                salahTime = "13:15",
                validFrom = "2026-08-20",
                validTo = "2026-08-21",
            ),
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
