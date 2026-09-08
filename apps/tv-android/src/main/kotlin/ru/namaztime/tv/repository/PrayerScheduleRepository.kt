package ru.namaztime.tv.repository

import ru.namaztime.tv.data.local.SnapshotDao
import ru.namaztime.tv.data.local.persistedSourceQualification
import ru.namaztime.tv.data.snapshot.SnapshotFormatValidation
import ru.namaztime.tv.domain.IqamahDateOverrideInput
import ru.namaztime.tv.domain.CampaignInput
import ru.namaztime.tv.domain.IqamahRuleInput
import ru.namaztime.tv.domain.JumuahSessionInput
import ru.namaztime.tv.domain.PrayerDayInput
import ru.namaztime.tv.domain.PrayerScheduleInput
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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
    val countryCode: String? = null,
    val region: String? = null,
    val sourceId: String = "",
    val authorityBranch: String? = null,
    val geographicScope: String = "",
    val canonicalUrl: String? = null,
    val retrievedAt: String = "",
    val sourceEffectiveFrom: String = "",
    val sourceEffectiveTo: String = "",
    val licenseReference: String? = null,
    val attribution: String? = null,
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
    val generatedAt: String = "",
    val rawSha256: String,
    val parserVersion: String,
    val approvalId: String?,
    val approvalStatus: String? = "approved",
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val approvalScope: String? = null,
    val qualification: LocalQualificationDiagnostics? = null,
    val signingKeyId: String,
    val canonicalSha256: String = "",
)

data class LocalQualificationDiagnostics(
    val qualificationId: String,
    val sha256: String,
    val state: String,
    val decisionSystem: String,
    val qualifiedAt: String,
    val freshThrough: String,
    val scopeId: String,
    val catalogRevision: String,
)

val LocalPrayerSchedule.hasPublicSourceQualification: Boolean
    get() = sourceKind in setOf("official_api", "official_file", "official_html", "mosque_calendar") &&
        diagnostics?.let { metadata ->
            metadata.qualification?.state == "qualified" &&
                metadata.qualification.decisionSystem == "namaztime:source-qualification/v1" &&
                metadata.approvalId == null && metadata.approvalStatus == null &&
                metadata.approvedBy == null && metadata.approvedAt == null && metadata.approvalScope == null
        } == true

class CorruptLocalSnapshotException(val code: String) : IllegalStateException(code)

fun LocalPrayerSchedule.toTimeEngineInput(
    operatorIqamahConfiguration: OperatorIqamahConfiguration = OperatorIqamahConfiguration(),
    currentInstant: Instant? = null,
): PrayerScheduleInput {
    val projectedDates = currentInstant?.let { instant ->
        val currentDate = instant.atZone(ZoneId.of(timezoneId)).toLocalDate()
        listOf(currentDate, currentDate.plusDays(1))
    }.orEmpty()
    val dhuhrFixedTime = operatorIqamahConfiguration.dhuhrFixedTimeMinutes?.toLocalTime()
    val canApplyDhuhr = dhuhrFixedTime != null && projectedDates.all { date ->
        days.firstOrNull { it.localDate == date.toString() }
            ?.let { !dhuhrFixedTime.isBefore(LocalTime.parse(it.dhuhr)) }
            ?: true
    }
    val appliedPrayerIds = if (currentInstant == null) {
        emptySet()
    } else {
        operatorIqamahConfiguration.configuredPrayerIds
            .filterTo(linkedSetOf()) { it != "dhuhr" || canApplyDhuhr }
    }
    val operatorOverrides = if (appliedPrayerIds.isEmpty() || currentInstant == null) {
        emptyList()
    } else {
        projectedDates.flatMap { date ->
            val day = days.firstOrNull { it.localDate == date.toString() }
                ?: return@flatMap emptyList()
            appliedPrayerIds.mapNotNull { prayerId ->
                if (prayerId == "dhuhr") {
                    return@mapNotNull IqamahDateOverrideInput(
                        localDate = date.toString(),
                        prayer = prayerId,
                        mode = "fixed_time",
                        fixedTime = requireNotNull(dhuhrFixedTime).toString(),
                        offsetMinutes = null,
                    )
                }
                val offsetMinutes = operatorIqamahConfiguration.offsetForPrayer(prayerId)
                    ?: return@mapNotNull null
                val adhan = LocalTime.parse(day.adhanFor(prayerId))
                if (adhan.plusMinutes(offsetMinutes.toLong()).isBefore(adhan)) {
                    return@mapNotNull null
                }
                IqamahDateOverrideInput(
                    localDate = date.toString(),
                    prayer = prayerId,
                    mode = "offset_after_adhan",
                    fixedTime = null,
                    offsetMinutes = offsetMinutes,
                )
            }
        }
    }
    return PrayerScheduleInput(
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
        iqamahRules = iqamahRules.filterNot { it.prayer in appliedPrayerIds }.map { rule ->
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
        iqamahDateOverrides = iqamahDateOverrides.filterNot {
            it.prayer in appliedPrayerIds
        }.map { override ->
            IqamahDateOverrideInput(
                localDate = override.localDate,
                prayer = override.prayer,
                mode = override.mode,
                fixedTime = override.fixedTime,
                offsetMinutes = override.offsetMinutes,
            )
        } + operatorOverrides,
        jumuahSessions = jumuahSessions.map { session ->
            JumuahSessionInput(
                id = session.id,
                label = session.label,
                khutbahTime = session.khutbahTime,
                salahTime = if ("dhuhr" in appliedPrayerIds) {
                    requireNotNull(dhuhrFixedTime).toString()
                } else {
                    session.salahTime
                },
                validFrom = session.validFrom,
                validTo = session.validTo,
            )
        },
    )
}

private fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)

private fun LocalPrayerDay.adhanFor(prayerId: String): String = when (prayerId) {
    "fajr" -> fajr
    "dhuhr" -> dhuhr
    "asr" -> asr
    "maghrib" -> maghrib
    "isha" -> isha
    else -> throw IllegalArgumentException("unsupported iqamah prayer")
}

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
                    val qualification = try {
                        persistedSourceQualification(snapshot, days, rules.isNotEmpty() || overrides.isNotEmpty() || sessions.isNotEmpty())
                    } catch (_: IllegalArgumentException) {
                        throw CorruptLocalSnapshotException("invalid_source_qualification")
                    }
                    LocalPrayerSchedule(
                        snapshotId = snapshot.snapshotId,
                        mosqueId = snapshot.mosqueId,
                        mosqueName = snapshot.mosqueName,
                        locality = snapshot.locality,
                        timezoneId = snapshot.timezoneId,
                        sourceKind = snapshot.sourceKind,
                        authorityName = snapshot.authorityName,
                        countryCode = snapshot.countryCode,
                        region = snapshot.region,
                        sourceId = snapshot.sourceId,
                        authorityBranch = snapshot.authorityBranch,
                        geographicScope = snapshot.geographicScope,
                        canonicalUrl = snapshot.canonicalUrl,
                        retrievedAt = snapshot.retrievedAt,
                        sourceEffectiveFrom = snapshot.sourceEffectiveFrom,
                        sourceEffectiveTo = snapshot.sourceEffectiveTo,
                        licenseReference = snapshot.licenseReference,
                        attribution = snapshot.attribution,
                        coverageFrom = snapshot.coverageFrom,
                        coverageTo = snapshot.coverageTo,
                        diagnostics = LocalSnapshotDiagnostics(
                            dataClassification = snapshot.dataClassification,
                            generatedAt = snapshot.generatedAt,
                            rawSha256 = snapshot.rawSha256,
                            parserVersion = snapshot.parserVersion,
                            approvalId = snapshot.approvalId,
                            approvalStatus = snapshot.approvalStatus,
                            approvedBy = snapshot.approvedBy,
                            approvedAt = snapshot.approvedAt,
                            approvalScope = snapshot.approvalScope,
                            qualification = qualification?.let { q ->
                                LocalQualificationDiagnostics(q.qualificationId, q.sha256, q.state, q.decisionSystem,
                                    q.qualifiedAt, q.freshThrough, q.scope.id, q.catalogRevision)
                            },
                            signingKeyId = snapshot.signingKeyId,
                            canonicalSha256 = snapshot.canonicalSha256,
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
