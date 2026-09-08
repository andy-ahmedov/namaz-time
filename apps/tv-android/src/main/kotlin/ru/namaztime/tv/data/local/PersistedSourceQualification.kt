package ru.namaztime.tv.data.local

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.namaztime.tv.data.snapshot.SnapshotDateRange
import ru.namaztime.tv.data.snapshot.SnapshotDecoder
import ru.namaztime.tv.data.snapshot.SnapshotIntegrity
import ru.namaztime.tv.data.snapshot.SnapshotMosque
import ru.namaztime.tv.data.snapshot.SnapshotPayload
import ru.namaztime.tv.data.snapshot.SnapshotPrayerDay
import ru.namaztime.tv.data.snapshot.SnapshotSource
import ru.namaztime.tv.data.snapshot.SnapshotSourceQualification
import ru.namaztime.tv.data.snapshot.SourceQualificationValidation

// Revalidate on reopen and on local projection, including every row, not only
// qualification metadata or the independently sampled comparison days.
internal fun persistedSourceQualification(
    snapshot: SnapshotEntity,
    days: List<PrayerDayEntity>,
    hasLocalPrayerPolicy: Boolean,
): SnapshotSourceQualification? {
    if (snapshot.schemaVersion == "1.0") {
        require(snapshot.qualificationId == null && snapshot.qualificationSha256 == null && snapshot.qualificationJson == null)
        return null
    }
    require(snapshot.schemaVersion == "2.0" && !hasLocalPrayerPolicy)
    require(listOf(snapshot.approvalId, snapshot.approvalStatus, snapshot.approvedBy, snapshot.approvedAt, snapshot.approvalScope, snapshot.approvalNote).all { it == null })
    val q = SourceQualificationValidation.decode(requireNotNull(snapshot.qualificationJson))
    require(snapshot.qualificationId == q.qualificationId && snapshot.qualificationSha256 == q.sha256)
    val payload = SnapshotPayload(
        schemaVersion = snapshot.schemaVersion,
        snapshotId = snapshot.snapshotId,
        dataClassification = snapshot.dataClassification,
        generatedAt = snapshot.generatedAt,
        mosque = SnapshotMosque(snapshot.mosqueId, snapshot.mosqueName, snapshot.countryCode, snapshot.region, snapshot.locality, snapshot.timezoneId),
        source = SnapshotSource(
            sourceId = snapshot.sourceId, kind = snapshot.sourceKind, authorityName = snapshot.authorityName,
            authorityBranch = snapshot.authorityBranch, geographicScope = snapshot.geographicScope,
            canonicalUrl = snapshot.canonicalUrl, retrievedAt = snapshot.retrievedAt,
            effectiveFrom = snapshot.sourceEffectiveFrom, effectiveTo = snapshot.sourceEffectiveTo,
            rawSha256 = snapshot.rawSha256, parserVersion = snapshot.parserVersion,
            calculationProfile = snapshot.calculationProfile, licenseReference = snapshot.licenseReference,
            attribution = snapshot.attribution, qualification = q,
        ),
        coverage = SnapshotDateRange(snapshot.coverageFrom, snapshot.coverageTo),
        prayerDays = days.map { day ->
            require(day.snapshotId == snapshot.snapshotId)
            SnapshotPrayerDay(day.localDate, day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha,
                day.duha, day.middleOfNight, day.lastThirdOfNight, Json.decodeFromString(day.flagsJson))
        },
        integrity = SnapshotIntegrity(snapshot.canonicalSha256, snapshot.signingKeyId, snapshot.signatureEd25519Base64),
    )
    SnapshotDecoder.validate(payload)
    SourceQualificationValidation.validate(payload)
    return q
}
