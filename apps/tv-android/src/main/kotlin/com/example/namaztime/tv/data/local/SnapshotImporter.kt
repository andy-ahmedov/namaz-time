package com.example.namaztime.tv.data.local

import androidx.room.withTransaction
import com.example.namaztime.tv.data.snapshot.ActivatableSnapshot
import com.example.namaztime.tv.data.snapshot.SnapshotActivationGate
import com.example.namaztime.tv.data.snapshot.SnapshotPayload
import com.example.namaztime.tv.domain.IqamahDateOverrideInput
import com.example.namaztime.tv.domain.IqamahRuleInput
import com.example.namaztime.tv.domain.JumuahSessionInput
import com.example.namaztime.tv.domain.PrayerDayInput
import com.example.namaztime.tv.domain.PrayerScheduleInput
import com.example.namaztime.tv.domain.PrayerTimeEngine
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun interface BeforeSnapshotActivation {
    suspend fun run(snapshotId: String)
}

sealed interface SnapshotImportResult {
    data class Activated(
        val snapshotId: String,
        val previousSnapshotId: String?,
    ) : SnapshotImportResult

    data class AlreadyActive(val snapshotId: String) : SnapshotImportResult
}

class SnapshotImportException(val code: String) : IllegalStateException(code)

class SnapshotImporter(
    private val database: NamazDatabase,
    private val beforeActivation: BeforeSnapshotActivation = BeforeSnapshotActivation {},
    private val timeEngine: PrayerTimeEngine = PrayerTimeEngine(),
) {
    internal suspend fun importAndActivate(snapshot: SnapshotPayload): SnapshotImportResult =
        importAndActivate(SnapshotActivationGate.bundledSynthetic(snapshot))

    suspend fun importAndActivate(input: ActivatableSnapshot): SnapshotImportResult {
        val snapshot = input.payload
        timeEngine.validate(snapshot.toTimeEngineInput())?.let { code ->
            throw SnapshotImportException("time_engine_${code.lowercase(Locale.ROOT)}")
        }
        return database.withTransaction {
            val dao = database.snapshotDao()
            val selection = dao.getSelection()
            if (dao.snapshotExists(snapshot.snapshotId)) {
                if (selection?.activeSnapshotId == snapshot.snapshotId) {
                    return@withTransaction SnapshotImportResult.AlreadyActive(snapshot.snapshotId)
                }
                if (selection?.previousSnapshotId != snapshot.snapshotId || selection.activeSnapshotId == null) {
                    throw SnapshotImportException("snapshot_id_conflict")
                }
                // Re-import authenticated rollback bytes rather than trusting an old local copy.
                // The temporary pointer change and cascade delete are invisible until commit;
                // interruption rolls the whole operation back to the current active snapshot.
                dao.setSelection(
                    SnapshotSelectionEntity(
                        activeSnapshotId = selection.activeSnapshotId,
                        previousSnapshotId = null,
                    ),
                )
                dao.deleteSnapshot(snapshot.snapshotId)
            }

            dao.insertSnapshot(snapshot.toEntity())
            dao.insertPrayerDays(snapshot.prayerDays.map { it.toEntity(snapshot.snapshotId) })
            if (snapshot.iqamahRules.isNotEmpty()) {
                dao.insertIqamahRules(snapshot.iqamahRules.map { rule ->
                    IqamahRuleEntity(
                        snapshotId = snapshot.snapshotId,
                        ruleId = rule.id,
                        prayer = rule.prayer,
                        validFrom = rule.validFrom,
                        validTo = rule.validTo,
                        weekdaysMask = rule.weekdays.fold(0) { mask, day -> mask or (1 shl (day - 1)) },
                        priority = rule.priority,
                        mode = rule.value.mode,
                        fixedTime = rule.value.fixedTime,
                        offsetMinutes = rule.value.offsetMinutes,
                        reason = rule.reason,
                    )
                })
            }
            if (snapshot.iqamahDateOverrides.isNotEmpty()) {
                dao.insertIqamahOverrides(snapshot.iqamahDateOverrides.map { override ->
                    IqamahDateOverrideEntity(
                        snapshotId = snapshot.snapshotId,
                        localDate = override.date,
                        prayer = override.prayer,
                        mode = override.value.mode,
                        fixedTime = override.value.fixedTime,
                        offsetMinutes = override.value.offsetMinutes,
                        reason = override.reason,
                    )
                })
            }
            if (snapshot.jumuahSessions.isNotEmpty()) {
                dao.insertJumuahSessions(snapshot.jumuahSessions.map { session ->
                    JumuahSessionEntity(
                        snapshotId = snapshot.snapshotId,
                        sessionId = session.id,
                        label = session.label,
                        khutbahTime = session.khutbahTime,
                        salahTime = session.salahTime,
                        validFrom = session.validFrom,
                        validTo = session.validTo,
                    )
                })
            }
            if (snapshot.campaigns.isNotEmpty()) {
                dao.insertCampaigns(snapshot.campaigns.map { campaign ->
                    CampaignEntity(
                        snapshotId = snapshot.snapshotId,
                        campaignId = campaign.id,
                        kind = campaign.kind,
                        httpsUrl = campaign.url,
                        title = campaign.title,
                        subtitle = campaign.subtitle,
                        startsAt = campaign.startsAt,
                        endsAt = campaign.endsAt,
                        placement = campaign.placement,
                    )
                })
            }
            snapshot.theme?.let { theme ->
                dao.insertTheme(
                    ThemeEntity(
                        snapshotId = snapshot.snapshotId,
                        themeId = theme.themeId,
                        overlayOpacity = theme.overlayOpacity,
                        landscapeAssetJson = theme.landscapeAsset?.let(Json::encodeToString),
                        portraitAssetJson = theme.portraitAsset?.let(Json::encodeToString),
                    ),
                )
            }

            beforeActivation.run(snapshot.snapshotId)
            val previous = selection?.activeSnapshotId
            dao.setSelection(
                SnapshotSelectionEntity(
                    activeSnapshotId = snapshot.snapshotId,
                    previousSnapshotId = previous,
                ),
            )
            SnapshotImportResult.Activated(snapshot.snapshotId, previous)
        }
    }
}

private fun SnapshotPayload.toTimeEngineInput() = PrayerScheduleInput(
    timezoneId = mosque.timezone,
    days = prayerDays.map { day ->
        PrayerDayInput(
            localDate = day.date,
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
            weekdaysMask = rule.weekdays.fold(0) { mask, day -> mask or (1 shl (day - 1)) },
            priority = rule.priority,
            mode = rule.value.mode,
            fixedTime = rule.value.fixedTime,
            offsetMinutes = rule.value.offsetMinutes,
        )
    },
    iqamahDateOverrides = iqamahDateOverrides.map { override ->
        IqamahDateOverrideInput(
            localDate = override.date,
            prayer = override.prayer,
            mode = override.value.mode,
            fixedTime = override.value.fixedTime,
            offsetMinutes = override.value.offsetMinutes,
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

private fun SnapshotPayload.toEntity() = SnapshotEntity(
    snapshotId = snapshotId,
    schemaVersion = schemaVersion,
    dataClassification = dataClassification,
    generatedAt = generatedAt,
    mosqueId = mosque.id,
    mosqueName = mosque.name,
    countryCode = mosque.countryCode,
    region = mosque.region,
    locality = mosque.locality,
    timezoneId = mosque.timezone,
    sourceId = source.sourceId,
    sourceKind = source.kind,
    authorityName = source.authorityName,
    authorityBranch = source.authorityBranch,
    geographicScope = source.geographicScope,
    canonicalUrl = source.canonicalUrl,
    retrievedAt = source.retrievedAt,
    sourceEffectiveFrom = source.effectiveFrom,
    sourceEffectiveTo = source.effectiveTo,
    rawSha256 = source.rawSha256,
    parserVersion = source.parserVersion,
    calculationProfile = source.calculationProfile,
    licenseReference = source.licenseReference,
    attribution = source.attribution,
    approvalId = source.approval.approvalId,
    approvalStatus = source.approval.status,
    approvedBy = source.approval.approvedBy,
    approvedAt = source.approval.approvedAt,
    approvalScope = source.approval.approvalScope,
    approvalNote = source.approval.note,
    coverageFrom = coverage.from,
    coverageTo = coverage.to,
    canonicalSha256 = integrity.canonicalSha256,
    signingKeyId = integrity.signingKeyId,
    signatureEd25519Base64 = integrity.signatureEd25519Base64,
)

private fun com.example.namaztime.tv.data.snapshot.SnapshotPrayerDay.toEntity(snapshotId: String) =
    PrayerDayEntity(
        snapshotId = snapshotId,
        localDate = date,
        fajr = fajr,
        sunrise = sunrise,
        dhuhr = dhuhr,
        asr = asr,
        maghrib = maghrib,
        isha = isha,
        duha = duha,
        middleOfNight = middleOfNight,
        lastThirdOfNight = lastThirdOfNight,
        flagsJson = Json.encodeToString(flags),
    )
