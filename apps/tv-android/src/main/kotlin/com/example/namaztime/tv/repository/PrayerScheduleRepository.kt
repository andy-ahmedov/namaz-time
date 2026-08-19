package com.example.namaztime.tv.repository

import com.example.namaztime.tv.data.local.SnapshotDao
import com.example.namaztime.tv.data.snapshot.SnapshotFormatValidation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

data class LocalPrayerDay(
    val localDate: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val flags: List<String> = emptyList(),
)

data class LocalPrayerSchedule(
    val snapshotId: String,
    val mosqueId: String,
    val mosqueName: String,
    val timezoneId: String,
    val sourceKind: String,
    val authorityName: String,
    val coverageFrom: String,
    val coverageTo: String,
    val days: List<LocalPrayerDay>,
    val diagnostics: LocalSnapshotDiagnostics? = null,
)

data class LocalSnapshotDiagnostics(
    val dataClassification: String,
    val rawSha256: String,
    val parserVersion: String,
    val approvalId: String,
    val signingKeyId: String,
)

class CorruptLocalSnapshotException(val code: String) : IllegalStateException(code)

interface PrayerScheduleRepository {
    fun observeActiveSchedule(): Flow<LocalPrayerSchedule?>
}

object EmptyPrayerScheduleRepository : PrayerScheduleRepository {
    override fun observeActiveSchedule(): Flow<LocalPrayerSchedule?> = flowOf(null)
}

class RoomPrayerScheduleRepository(
    private val dao: SnapshotDao,
) : PrayerScheduleRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActiveSchedule(): Flow<LocalPrayerSchedule?> =
        dao.observeActiveSnapshot().flatMapLatest { snapshot ->
            if (snapshot == null) {
                flowOf(null)
            } else {
                dao.observePrayerDays(snapshot.snapshotId).map { days ->
                    LocalPrayerSchedule(
                        snapshotId = snapshot.snapshotId,
                        mosqueId = snapshot.mosqueId,
                        mosqueName = snapshot.mosqueName,
                        timezoneId = snapshot.timezoneId,
                        sourceKind = snapshot.sourceKind,
                        authorityName = snapshot.authorityName,
                        coverageFrom = snapshot.coverageFrom,
                        coverageTo = snapshot.coverageTo,
                        diagnostics = LocalSnapshotDiagnostics(
                            dataClassification = snapshot.dataClassification,
                            rawSha256 = snapshot.rawSha256,
                            parserVersion = snapshot.parserVersion,
                            approvalId = snapshot.approvalId,
                            signingKeyId = snapshot.signingKeyId,
                        ),
                        days = days.map { day ->
                            LocalPrayerDay(
                                localDate = day.localDate,
                                fajr = day.fajr,
                                sunrise = day.sunrise,
                                dhuhr = day.dhuhr,
                                asr = day.asr,
                                maghrib = day.maghrib,
                                isha = day.isha,
                                flags = decodePrayerDayFlags(day.flagsJson),
                            )
                        },
                    )
                }
            }
        }
}

private fun decodePrayerDayFlags(value: String): List<String> {
    val flags = try {
        Json.decodeFromString<List<String>>(value)
    } catch (error: Exception) {
        throw CorruptLocalSnapshotException("invalid_prayer_day_flags")
    }
    if (
        flags.distinct().size != flags.size ||
        flags.any { SnapshotFormatValidation.codePointLength(it) > 128 }
    ) {
        throw CorruptLocalSnapshotException("invalid_prayer_day_flags")
    }
    return flags
}
