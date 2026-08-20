package com.example.namaztime.tv.repository

import com.example.namaztime.tv.data.local.SnapshotDao
import com.example.namaztime.tv.data.snapshot.SnapshotFormatValidation
import com.example.namaztime.tv.domain.IqamahDateOverrideInput
import com.example.namaztime.tv.domain.CampaignInput
import com.example.namaztime.tv.domain.IqamahRuleInput
import com.example.namaztime.tv.domain.JumuahSessionInput
import com.example.namaztime.tv.domain.PrayerDayInput
import com.example.namaztime.tv.domain.PrayerScheduleInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    val locality: String? = null,
    val timezoneId: String,
    val sourceKind: String,
    val authorityName: String,
    val coverageFrom: String,
    val coverageTo: String,
    val days: List<LocalPrayerDay>,
    val iqamahRules: List<LocalIqamahRule> = emptyList(),
    val iqamahDateOverrides: List<LocalIqamahDateOverride> = emptyList(),
    val jumuahSessions: List<LocalJumuahSession> = emptyList(),
    val campaigns: List<LocalCampaign> = emptyList(),
    val diagnostics: LocalSnapshotDiagnostics? = null,
)

data class LocalIqamahRule(
    val id: String,
    val prayer: String,
    val validFrom: String,
    val validTo: String,
    val weekdaysMask: Int,
    val priority: Int,
    val mode: String,
    val fixedTime: String?,
    val offsetMinutes: Int?,
    val reason: String?,
)

data class LocalIqamahDateOverride(
    val localDate: String,
    val prayer: String,
    val mode: String,
    val fixedTime: String?,
    val offsetMinutes: Int?,
    val reason: String?,
)

data class LocalJumuahSession(
    val id: String,
    val label: String,
    val khutbahTime: String?,
    val salahTime: String,
    val validFrom: String,
    val validTo: String,
)

data class LocalCampaign(
    val id: String,
    val kind: String,
    val httpsUrl: String,
    val title: String,
    val subtitle: String?,
    val startsAt: String?,
    val endsAt: String?,
    val placement: String,
)

data class LocalSnapshotDiagnostics(
    val dataClassification: String,
    val rawSha256: String,
    val parserVersion: String,
    val approvalId: String,
    val approvalStatus: String = "approved",
    val signingKeyId: String,
)

class CorruptLocalSnapshotException(val code: String) : IllegalStateException(code)

fun LocalPrayerSchedule.toTimeEngineInput() = PrayerScheduleInput(
    timezoneId = timezoneId,
    days = days.map { day ->
        PrayerDayInput(
            localDate = day.localDate,
            fajr = day.fajr,
            sunrise = day.sunrise,
            dhuhr = day.dhuhr,
            asr = day.asr,
            maghrib = day.maghrib,
            isha = day.isha,
        )
    },
    iqamahRules = iqamahRules.map { rule ->
        IqamahRuleInput(
            id = rule.id,
            prayer = rule.prayer,
            validFrom = rule.validFrom,
            validTo = rule.validTo,
            weekdaysMask = rule.weekdaysMask,
            priority = rule.priority,
            mode = rule.mode,
            fixedTime = rule.fixedTime,
            offsetMinutes = rule.offsetMinutes,
        )
    },
    iqamahDateOverrides = iqamahDateOverrides.map { override ->
        IqamahDateOverrideInput(
            localDate = override.localDate,
            prayer = override.prayer,
            mode = override.mode,
            fixedTime = override.fixedTime,
            offsetMinutes = override.offsetMinutes,
        )
    },
    jumuahSessions = jumuahSessions.map { session ->
        JumuahSessionInput(
            id = session.id,
            label = session.label,
            khutbahTime = session.khutbahTime,
            salahTime = session.salahTime,
            validFrom = session.validFrom,
            validTo = session.validTo,
        )
    },
)

fun LocalPrayerSchedule.toCampaignInputs(): List<CampaignInput> = campaigns.mapNotNull { campaign ->
    val startsAt = campaign.startsAt ?: return@mapNotNull null
    val endsAt = campaign.endsAt ?: return@mapNotNull null
    CampaignInput(
        id = campaign.id,
        kind = campaign.kind,
        httpsUrl = campaign.httpsUrl,
        title = campaign.title,
        subtitle = campaign.subtitle,
        startsAt = startsAt,
        endsAt = endsAt,
        placement = campaign.placement,
    )
}

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
                combine(
                    dao.observePrayerDays(snapshot.snapshotId),
                    dao.observeIqamahRules(snapshot.snapshotId),
                    dao.observeIqamahOverrides(snapshot.snapshotId),
                    dao.observeJumuahSessions(snapshot.snapshotId),
                    dao.observeCampaigns(snapshot.snapshotId),
                ) { days, rules, overrides, sessions, campaigns ->
                    LocalPrayerSchedule(
                        snapshotId = snapshot.snapshotId,
                        mosqueId = snapshot.mosqueId,
                        mosqueName = snapshot.mosqueName,
                        locality = snapshot.locality,
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
                            approvalStatus = snapshot.approvalStatus,
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
                        iqamahRules = rules.map { rule ->
                            LocalIqamahRule(
                                id = rule.ruleId,
                                prayer = rule.prayer,
                                validFrom = rule.validFrom,
                                validTo = rule.validTo,
                                weekdaysMask = rule.weekdaysMask,
                                priority = rule.priority,
                                mode = rule.mode,
                                fixedTime = rule.fixedTime,
                                offsetMinutes = rule.offsetMinutes,
                                reason = rule.reason,
                            )
                        },
                        iqamahDateOverrides = overrides.map { override ->
                            LocalIqamahDateOverride(
                                localDate = override.localDate,
                                prayer = override.prayer,
                                mode = override.mode,
                                fixedTime = override.fixedTime,
                                offsetMinutes = override.offsetMinutes,
                                reason = override.reason,
                            )
                        },
                        jumuahSessions = sessions.map { session ->
                            LocalJumuahSession(
                                id = session.sessionId,
                                label = session.label,
                                khutbahTime = session.khutbahTime,
                                salahTime = session.salahTime,
                                validFrom = session.validFrom,
                                validTo = session.validTo,
                            )
                        },
                        campaigns = campaigns.map { campaign ->
                            LocalCampaign(
                                id = campaign.campaignId,
                                kind = campaign.kind,
                                httpsUrl = campaign.httpsUrl,
                                title = campaign.title,
                                subtitle = campaign.subtitle,
                                startsAt = campaign.startsAt,
                                endsAt = campaign.endsAt,
                                placement = campaign.placement,
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
