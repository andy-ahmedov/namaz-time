package com.example.namaztime.tv.domain

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class CountdownPolicy(
    val includeSunrise: Boolean = false,
    val includeIqamah: Boolean = true,
    val includeJumuah: Boolean = true,
)

data class PrayerScheduleInput(
    val timezoneId: String,
    val days: List<PrayerDayInput>,
    val iqamahRules: List<IqamahRuleInput> = emptyList(),
    val iqamahDateOverrides: List<IqamahDateOverrideInput> = emptyList(),
    val jumuahSessions: List<JumuahSessionInput> = emptyList(),
)

data class PrayerDayInput(
    val localDate: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
)

data class IqamahRuleInput(
    val id: String,
    val prayer: String,
    val validFrom: String,
    val validTo: String,
    val weekdaysMask: Int,
    val priority: Int,
    val mode: String,
    val fixedTime: String?,
    val offsetMinutes: Int?,
)

data class IqamahDateOverrideInput(
    val localDate: String,
    val prayer: String,
    val mode: String,
    val fixedTime: String?,
    val offsetMinutes: Int?,
)

data class JumuahSessionInput(
    val id: String,
    val label: String,
    val khutbahTime: String?,
    val salahTime: String,
    val validFrom: String,
    val validTo: String,
)

enum class PrayerEventKind {
    ADHAN,
    IQAMAH,
    JUMUAH,
}

data class ResolvedPrayer(
    val id: String,
    val adhan: LocalTime,
    val iqamah: LocalTime?,
)

data class ResolvedJumuahSession(
    val id: String,
    val label: String,
    val khutbahTime: LocalTime?,
    val salahTime: LocalTime,
)

data class PrayerEvent(
    val kind: PrayerEventKind,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val instant: Instant,
    val prayer: String? = null,
    val jumuahSessionId: String? = null,
    val label: String,
)

sealed interface PrayerTimeResolution {
    data class Available(
        val localDate: LocalDate,
        val localTime: LocalTime,
        val prayers: Map<String, ResolvedPrayer>,
        val jumuahSessions: List<ResolvedJumuahSession>,
        val nextEvent: PrayerEvent?,
        val countdownSeconds: Long?,
    ) : PrayerTimeResolution

    data class Unavailable(
        val supportCode: String,
        val localDate: LocalDate? = null,
    ) : PrayerTimeResolution
}

class PrayerTimeEngine(
    private val countdownPolicy: CountdownPolicy = CountdownPolicy(),
) {
    fun validate(schedule: PrayerScheduleInput): String? = try {
        validateStructure(schedule)
        val zone = parseNamedZone(schedule.timezoneId)
        schedule.days.forEach { day ->
            val date = parseDate(day.localDate)
            resolveOrThrow(schedule, date.atTime(12, 0).atZone(zone).toInstant())
        }
        null
    } catch (failure: ResolutionFailure) {
        failure.code
    } catch (_: DateTimeException) {
        "SCHEDULE_INVALID_LOCAL_VALUE"
    } catch (_: IllegalArgumentException) {
        "SCHEDULE_INVALID_LOCAL_VALUE"
    }

    fun resolve(schedule: PrayerScheduleInput, now: Instant): PrayerTimeResolution = try {
        resolveOrThrow(schedule, now)
    } catch (failure: ResolutionFailure) {
        PrayerTimeResolution.Unavailable(failure.code, failure.localDate)
    } catch (_: DateTimeException) {
        PrayerTimeResolution.Unavailable("SCHEDULE_INVALID_LOCAL_VALUE")
    } catch (_: IllegalArgumentException) {
        PrayerTimeResolution.Unavailable("SCHEDULE_INVALID_LOCAL_VALUE")
    }

    private fun resolveOrThrow(
        schedule: PrayerScheduleInput,
        now: Instant,
    ): PrayerTimeResolution.Available {
        val zone = parseNamedZone(schedule.timezoneId)
        val zonedNow = now.atZone(zone)
        val localDate = zonedNow.toLocalDate()
        val day = schedule.days.singleOrNull { it.localDate == localDate.toString() }
            ?: fail("SCHEDULE_DATE_OUTSIDE_COVERAGE", localDate)
        val prayers = resolvePrayers(schedule, day, localDate)
        val jumuah = resolveJumuah(schedule.jumuahSessions, localDate)
        val events = buildEvents(localDate, prayers, jumuah, zone).toMutableList()

        schedule.days.singleOrNull { it.localDate == localDate.plusDays(1).toString() }?.let { tomorrow ->
            val tomorrowFajr = parseTime(tomorrow.fajr)
            events += event(
                kind = PrayerEventKind.ADHAN,
                date = localDate.plusDays(1),
                time = tomorrowFajr,
                zone = zone,
                prayer = "fajr",
                label = "Фаджр",
            )
        }

        val next = events
            .asSequence()
            .filter { !it.instant.isBefore(now) }
            .sortedWith(compareBy<PrayerEvent>({ it.instant }, { eventRank(it.kind) }, { it.label }))
            .firstOrNull()
        val countdownSeconds = next?.let { secondsUntil(now, it.instant) }
        return PrayerTimeResolution.Available(
            localDate = localDate,
            localTime = zonedNow.toLocalTime().withNano(0),
            prayers = prayers,
            jumuahSessions = jumuah,
            nextEvent = next,
            countdownSeconds = countdownSeconds,
        )
    }

    private fun validateStructure(schedule: PrayerScheduleInput) {
        if (schedule.days.isEmpty()) fail("SCHEDULE_EMPTY")
        val dates = schedule.days.map { parseDate(it.localDate) }
        if (dates.distinct().size != dates.size) fail("SCHEDULE_DUPLICATE_DATE")
        dates.zipWithNext().forEach { (previous, next) ->
            if (next != previous.plusDays(1)) fail("SCHEDULE_DATE_GAP")
        }

        val allowedIqamahPrayers = setOf("fajr", "dhuhr", "asr", "maghrib", "isha")
        if (schedule.iqamahRules.map { it.id }.distinct().size != schedule.iqamahRules.size) {
            fail("IQAMAH_RULE_DUPLICATE_ID")
        }
        schedule.iqamahRules.forEach { rule ->
            if (rule.prayer !in allowedIqamahPrayers) fail("IQAMAH_RULE_INVALID")
            val from = parseDate(rule.validFrom)
            val to = parseDate(rule.validTo)
            if (to.isBefore(from) || rule.weekdaysMask !in 1..127 || rule.priority !in 0..100_000) {
                fail("IQAMAH_RULE_INVALID")
            }
            validateValue(rule.mode, rule.fixedTime, rule.offsetMinutes, "IQAMAH_RULE_INVALID")
        }
        val overrideKeys = schedule.iqamahDateOverrides.map { it.localDate to it.prayer }
        if (overrideKeys.distinct().size != overrideKeys.size) fail("IQAMAH_OVERRIDE_DUPLICATE")
        schedule.iqamahDateOverrides.forEach { override ->
            parseDate(override.localDate)
            if (override.prayer !in allowedIqamahPrayers) fail("IQAMAH_OVERRIDE_INVALID")
            validateValue(
                override.mode,
                override.fixedTime,
                override.offsetMinutes,
                "IQAMAH_OVERRIDE_INVALID",
            )
        }
        if (schedule.jumuahSessions.map { it.id }.distinct().size != schedule.jumuahSessions.size) {
            fail("JUMUAH_DUPLICATE_ID")
        }
        schedule.jumuahSessions.forEach { session ->
            val from = parseDate(session.validFrom)
            val to = parseDate(session.validTo)
            if (to.isBefore(from)) fail("JUMUAH_SESSION_INVALID")
            session.khutbahTime?.let(::parseTime)
            parseTime(session.salahTime)
        }
    }

    private fun validateValue(
        mode: String,
        fixedTime: String?,
        offsetMinutes: Int?,
        code: String,
    ) {
        when (mode) {
            "fixed_time" -> {
                if (fixedTime == null || offsetMinutes != null) fail(code)
                parseTime(fixedTime)
            }
            "offset_after_adhan" -> {
                if (fixedTime != null || offsetMinutes !in 0..240) fail(code)
            }
            else -> fail(code)
        }
    }

    private fun resolvePrayers(
        schedule: PrayerScheduleInput,
        day: PrayerDayInput,
        date: LocalDate,
    ): LinkedHashMap<String, ResolvedPrayer> {
        val adhanByPrayer = linkedMapOf(
            "fajr" to parseTime(day.fajr),
            "sunrise" to parseTime(day.sunrise),
            "dhuhr" to parseTime(day.dhuhr),
            "asr" to parseTime(day.asr),
            "maghrib" to parseTime(day.maghrib),
            "isha" to parseTime(day.isha),
        )
        if (adhanByPrayer.values.zipWithNext().any { (earlier, later) -> !earlier.isBefore(later) }) {
            fail("SCHEDULE_PRAYER_ORDER_INVALID", date)
        }
        return adhanByPrayer.mapValuesTo(linkedMapOf()) { (prayer, adhan) ->
            val iqamah = if (prayer == "sunrise") {
                null
            } else {
                resolveIqamah(schedule, date, prayer, adhan)
            }
            ResolvedPrayer(prayer, adhan, iqamah)
        }
    }

    private fun resolveIqamah(
        schedule: PrayerScheduleInput,
        date: LocalDate,
        prayer: String,
        adhan: LocalTime,
    ): LocalTime? {
        val override = schedule.iqamahDateOverrides.singleOrNull {
            it.localDate == date.toString() && it.prayer == prayer
        }
        if (override != null) return resolveValue(date, adhan, override)

        val matches = schedule.iqamahRules.filter { rule ->
            rule.prayer == prayer &&
                !date.isBefore(parseDate(rule.validFrom)) &&
                !date.isAfter(parseDate(rule.validTo)) &&
                rule.matches(date.dayOfWeek)
        }
        val highestPriority = matches.maxOfOrNull { it.priority } ?: return null
        val winners = matches.filter { it.priority == highestPriority }
        if (winners.size != 1) fail("IQAMAH_RULE_AMBIGUOUS", date)
        return resolveValue(date, adhan, winners.single())
    }

    private fun resolveValue(
        date: LocalDate,
        adhan: LocalTime,
        value: IqamahDateOverrideInput,
    ): LocalTime = resolveValue(
        date = date,
        adhan = adhan,
        mode = value.mode,
        fixedTime = value.fixedTime,
        offsetMinutes = value.offsetMinutes,
    )

    private fun resolveValue(
        date: LocalDate,
        adhan: LocalTime,
        value: IqamahRuleInput,
    ): LocalTime = resolveValue(
        date = date,
        adhan = adhan,
        mode = value.mode,
        fixedTime = value.fixedTime,
        offsetMinutes = value.offsetMinutes,
    )

    private fun resolveValue(
        date: LocalDate,
        adhan: LocalTime,
        mode: String,
        fixedTime: String?,
        offsetMinutes: Int?,
    ): LocalTime {
        val adhanDateTime = LocalDateTime.of(date, adhan)
        val iqamahDateTime = when (mode) {
            "fixed_time" -> LocalDateTime.of(date, parseTime(fixedTime ?: fail("IQAMAH_VALUE_INVALID", date)))
            "offset_after_adhan" -> adhanDateTime.plusMinutes(
                offsetMinutes?.toLong() ?: fail("IQAMAH_VALUE_INVALID", date),
            )
            else -> fail("IQAMAH_VALUE_INVALID", date)
        }
        if (iqamahDateTime.toLocalDate() != date) fail("IQAMAH_CROSSES_LOCAL_DATE", date)
        if (iqamahDateTime.isBefore(adhanDateTime)) fail("IQAMAH_BEFORE_ADHAN", date)
        return iqamahDateTime.toLocalTime()
    }

    private fun resolveJumuah(
        sessions: List<JumuahSessionInput>,
        date: LocalDate,
    ): List<ResolvedJumuahSession> {
        if (date.dayOfWeek != DayOfWeek.FRIDAY) return emptyList()
        return sessions.asSequence()
            .filter { !date.isBefore(parseDate(it.validFrom)) && !date.isAfter(parseDate(it.validTo)) }
            .map {
                ResolvedJumuahSession(
                    id = it.id,
                    label = it.label,
                    khutbahTime = it.khutbahTime?.let(::parseTime),
                    salahTime = parseTime(it.salahTime),
                )
            }
            .sortedWith(compareBy<ResolvedJumuahSession>({ it.salahTime }, { it.id }))
            .toList()
    }

    private fun buildEvents(
        date: LocalDate,
        prayers: Map<String, ResolvedPrayer>,
        jumuah: List<ResolvedJumuahSession>,
        zone: ZoneId,
    ): List<PrayerEvent> {
        val events = mutableListOf<PrayerEvent>()
        prayers.values.forEach { prayer ->
            // A published Friday session is the congregation event replacing
            // Dhuhr. Keep Dhuhr visible in the daily table as source evidence,
            // but do not let it compete with Jumuah in countdown selection.
            if (prayer.id == "dhuhr" && jumuah.isNotEmpty()) return@forEach
            if (prayer.id != "sunrise" || countdownPolicy.includeSunrise) {
                events += event(
                    kind = PrayerEventKind.ADHAN,
                    date = date,
                    time = prayer.adhan,
                    zone = zone,
                    prayer = prayer.id,
                    label = prayerLabel(prayer.id),
                )
            }
            if (countdownPolicy.includeIqamah && prayer.iqamah != null) {
                events += event(
                    kind = PrayerEventKind.IQAMAH,
                    date = date,
                    time = prayer.iqamah,
                    zone = zone,
                    prayer = prayer.id,
                    label = "${prayerLabel(prayer.id)} · икамат",
                )
            }
        }
        if (countdownPolicy.includeJumuah) {
            jumuah.forEach { session ->
                events += event(
                    kind = PrayerEventKind.JUMUAH,
                    date = date,
                    time = session.salahTime,
                    zone = zone,
                    jumuahSessionId = session.id,
                    label = "Джума · ${session.label}",
                )
            }
        }
        return events
    }

    private fun event(
        kind: PrayerEventKind,
        date: LocalDate,
        time: LocalTime,
        zone: ZoneId,
        prayer: String? = null,
        jumuahSessionId: String? = null,
        label: String,
    ): PrayerEvent = PrayerEvent(
        kind = kind,
        localDate = date,
        localTime = time,
        instant = localInstant(date, time, zone),
        prayer = prayer,
        jumuahSessionId = jumuahSessionId,
        label = label,
    )

    private fun localInstant(date: LocalDate, time: LocalTime, zone: ZoneId): Instant {
        val local = LocalDateTime.of(date, time)
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.isEmpty()) fail("SCHEDULE_NONEXISTENT_LOCAL_TIME", date)
        if (offsets.size != 1) fail("SCHEDULE_AMBIGUOUS_LOCAL_TIME", date)
        return local.toInstant(offsets.single())
    }

    private fun parseNamedZone(value: String): ZoneId {
        val zone = ZoneId.of(value)
        if (zone is ZoneOffset || value !in ZoneId.getAvailableZoneIds()) {
            fail("SCHEDULE_INVALID_TIMEZONE")
        }
        return zone
    }

    private fun parseDate(value: String): LocalDate = LocalDate.parse(value)
    private fun parseTime(value: String): LocalTime = LocalTime.parse(value)

    private fun IqamahRuleInput.matches(day: DayOfWeek): Boolean =
        weekdaysMask and (1 shl (day.value - 1)) != 0

    private fun secondsUntil(now: Instant, event: Instant): Long {
        val duration = Duration.between(now, event)
        if (duration.isZero) return 0
        return duration.seconds + if (duration.nano > 0) 1 else 0
    }

    private fun eventRank(kind: PrayerEventKind): Int = when (kind) {
        PrayerEventKind.ADHAN -> 0
        PrayerEventKind.IQAMAH -> 1
        PrayerEventKind.JUMUAH -> 2
    }

    private fun prayerLabel(prayer: String): String = when (prayer) {
        "fajr" -> "Фаджр"
        "sunrise" -> "Восход"
        "dhuhr" -> "Зухр"
        "asr" -> "Аср"
        "maghrib" -> "Магриб"
        "isha" -> "Иша"
        else -> prayer
    }

    private fun fail(code: String, localDate: LocalDate? = null): Nothing =
        throw ResolutionFailure(code, localDate)
}

private class ResolutionFailure(
    val code: String,
    val localDate: LocalDate?,
) : IllegalStateException(code)
