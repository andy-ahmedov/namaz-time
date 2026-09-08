package ru.namaztime.tv.data.snapshot

import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal object SourceQualificationValidation {
    private val json = Json { ignoreUnknownKeys = false }
    private val kinds = setOf("official_api", "official_file", "official_html", "mosque_calendar")
    private val requiredPurposes = setOf("ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison")
    private val allowedPurposes = requiredPurposes + setOf("seasonal_transition", "authority_chain")
    private val clockPattern = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")
    private val datePattern = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private val timestampPattern = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")

    fun validateWire(root: JsonObject, snapshot: SnapshotPayload) {
        val source = root.getValue("source").jsonObject
        if (snapshot.schemaVersion != "2.0") {
            check("qualification" !in source, "qualification_requires_v2")
            check("approval" in source, "approval_required")
            return
        }
        check("qualification" in source && "approval" !in source, "conflicting_admission")
        val proof = source["qualification"] as? JsonObject ?: fail("qualification_required")
        val q = snapshot.source.qualification ?: fail("qualification_required")
        check(hash(proof.withoutFingerprint()) == q.sha256, "qualification_wire_hash_mismatch")
        check(root["prayer_days"] is JsonArray, "qualified_onset_mismatch")
        check(hash(root.getValue("prayer_days")) == q.onsetSha256, "qualified_onset_wire_hash_mismatch")
        validate(snapshot)
    }

    // Also used after Room reconstruction: typed encoding must match the same
    // Go omitempty/canonical model. A changed non-sample row cannot escape it.
    fun validate(snapshot: SnapshotPayload) {
        val q = snapshot.source.qualification ?: fail("qualification_required")
        check(snapshot.schemaVersion == "2.0" && snapshot.source.approval == null, "conflicting_admission")
        validateProof(q)
        val source = snapshot.source
        check(
            snapshot.mosque.id == "public-scope-" + hashBytes(q.scope.id.encodeToByteArray()).take(32) &&
                snapshot.mosque.countryCode == "RU" && snapshot.mosque.timezone == q.timezone &&
                source.sourceId == q.sourceId && source.kind == q.sourceKind && source.canonicalUrl == q.canonicalUrl &&
                source.authorityName == q.authority.name && source.authorityBranch.orEmpty() == q.authority.branch &&
                source.geographicScope == q.scope.description && source.rawSha256 == q.artifact.sha256 &&
                source.retrievedAt == q.artifact.capturedAt && source.parserVersion == q.parserVersion &&
                snapshot.coverage == q.coverage && source.effectiveFrom == q.coverage.from &&
                source.effectiveTo == q.coverage.to && source.calculationProfile.isNullOrEmpty(),
            "qualification_binding_mismatch",
        )
        check(
            snapshot.iqamahRules.isEmpty() && snapshot.iqamahDateOverrides.isEmpty() && snapshot.jumuahSessions.isEmpty(),
            "local_prayer_policy_not_qualified",
        )
        check(
            q.termsAssessment != "public_transport_attribution_required" || !source.attribution.isNullOrEmpty(),
            "qualification_attribution_missing",
        )
        val generated = timestamp(snapshot.generatedAt)
        check(generated >= timestamp(q.qualifiedAt) && generated.atZone(ZoneId.of(q.timezone)).toLocalDate() <= date(q.freshThrough), "qualification_not_current")
        check(snapshot.prayerDays.size == q.validatedDays && hash(json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(SnapshotPrayerDay.serializer()), snapshot.prayerDays)) == q.onsetSha256, "qualified_onset_mismatch")
        snapshot.prayerDays.forEach { ordered(it) }
        // Proof comparisons must describe the materialized rows, not another
        // timetable whose onset hash was inserted into an otherwise valid proof.
        val days = snapshot.prayerDays.associateBy { it.date }
        q.comparisons.forEach { comparison ->
            val actual = days[comparison.day.date]
            // Flags and optional annotations are covered by the full onset hash,
            // while independent source comparisons deliberately contain six clocks.
            check(actual != null && onsetValues(actual) == onsetValues(comparison.day), "qualified_comparison_mismatch")
        }
    }

    fun encode(q: SnapshotSourceQualification): String = json.encodeToString(q)

    fun decode(value: String): SnapshotSourceQualification {
        check(value.encodeToByteArray().size <= MAX_SNAPSHOT_BYTES, "qualification_too_large")
        StrictJsonObjectKeyScanner(value, "invalid_qualification_json").scan()
        val q = json.decodeFromString(SnapshotSourceQualification.serializer(), value)
        val raw = json.parseToJsonElement(value).jsonObject
        check(hash(raw.withoutFingerprint()) == q.sha256, "qualification_wire_hash_mismatch")
        validateProof(q)
        return q
    }

    private fun validateProof(q: SnapshotSourceQualification) {
        check(q.schemaVersion == "namaztime-source-qualification/v1" && q.state == "qualified" && q.decisionSystem == "namaztime:source-qualification/v1", "qualification_decision_invalid")
        listOf(q.sourceId, q.authority.id, q.parserVersion, q.candidateId, q.catalogRevision, q.scope.id, q.scope.regionId).forEach { text(it, 128) }
        text(q.authority.name, 240)
        check(q.authority.evidenceLabel == "CONFIRMED_PUBLIC", "qualification_authority_invalid")
        publicUrl(q.authority.website)
        if (q.authority.branch.isNotEmpty()) text(q.authority.branch, 240)
        publicUrl(q.canonicalUrl)
        text(q.scope.description, 1000)
        when (q.scope.kind) {
            "city" -> text(q.scope.cityId, 128)
            "region" -> check(q.scope.cityId.isEmpty(), "qualification_scope_invalid")
            else -> fail("qualification_scope_invalid")
        }
        check(q.sourceKind in kinds, "qualification_kind_invalid")
        text(q.timezone, 64)
        check(q.timezone.contains('/') && SnapshotFormatValidation.isNamedIanaTimezone(q.timezone), "qualification_timezone_invalid")
        val zone = ZoneId.of(q.timezone)
        val qualified = timestamp(q.qualifiedAt)
        check(timestamp(q.artifact.capturedAt) <= qualified, "qualification_capture_after_decision")
        val from = date(q.coverage.from)
        val to = date(q.coverage.to)
        val fresh = date(q.freshThrough)
        check(to >= from && fresh in from..to && fresh >= qualified.atZone(zone).toLocalDate(), "qualification_freshness_invalid")
        val count = ChronoUnit.DAYS.between(from, to) + 1
        check(count in 1..400 && q.validatedDays.toLong() == count, "qualification_coverage_invalid")
        text(q.artifact.filename, 2048)
        text(q.artifact.contentType, 240)
        check(q.artifact.byteLength > 0 && q.retrieval.httpStatus == 200 && q.retrieval.contentType == q.artifact.contentType, "qualification_capture_invalid")
        publicUrl(q.retrieval.url)
        check(q.retrieval.etag.encodeToByteArray().size <= 1024 && q.retrieval.lastModified.encodeToByteArray().size <= 128, "qualification_retrieval_invalid")
        listOf(q.artifact.sha256, q.transcriptionSha256, q.normalizedSha256, q.onsetSha256, q.diffSha256, q.validationSha256, q.sha256).forEach { check(SnapshotFormatValidation.isSha256(it), "qualification_hash_invalid") }
        check(q.termsAssessment in setOf("public_transport_no_restriction_observed", "public_transport_attribution_required"), "qualification_terms_invalid")
        check(q.evidence.size in 6..64, "qualification_evidence_invalid")
        val evidence = mutableMapOf<String, SnapshotSourceEvidence>()
        q.evidence.forEach { item ->
            text(item.id, 128)
            check(evidence.put(item.id, item) == null && item.label == "CONFIRMED_PUBLIC" && item.purpose in allowedPurposes, "qualification_evidence_invalid")
            publicUrl(item.url)
            check(SnapshotFormatValidation.isSha256(item.sha256) && timestamp(item.retrievedAt) <= qualified, "qualification_evidence_invalid")
            text(item.claim, 4000)
        }
        check(evidence.values.map { it.purpose }.containsAll(requiredPurposes), "qualification_evidence_missing")
        check(q.comparisons.size in minOf(3, count.toInt())..400, "qualification_comparisons_invalid")
        val comparisonDates = mutableSetOf<LocalDate>()
        val quarters = mutableSetOf<Int>()
        q.comparisons.forEach { comparison ->
            val day = comparison.day
            val parsed = date(day.date)
            check(parsed in from..to && comparisonDates.add(parsed) && evidence[comparison.evidenceId]?.purpose == "value_comparison", "qualification_comparisons_invalid")
            ordered(day)
            check(day.duha == null && day.middleOfNight == null && day.lastThirdOfNight == null && day.flags.isEmpty(), "qualification_comparison_fields_invalid")
            quarters.add(quarter(parsed))
        }
        var current = from
        while (current <= to) {
            check(quarter(current) in quarters, "qualification_comparison_season_missing")
            current = current.plusDays(1)
        }
        check(q.warningResolutions.size <= 64 && q.unknowns.size <= 64, "qualification_notes_invalid")
        val warningCodes = mutableSetOf<String>()
        q.warningResolutions.forEach { resolution ->
            text(resolution.code, 128)
            text(resolution.reason, 2000)
            check(warningCodes.add(resolution.code) && resolution.evidenceId in evidence, "qualification_warning_invalid")
        }
        q.unknowns.forEach { text(it, 1000) }
        val proofHash = hash(json.encodeToJsonElement(SnapshotSourceQualification.serializer(), q).jsonObject.withoutFingerprint())
        check(proofHash == q.sha256 && q.qualificationId == "qualification-" + proofHash.take(32), "qualification_hash_mismatch")
    }

    private fun quarter(date: LocalDate): Int = date.year * 4 + (date.monthValue - 1) / 3

    private fun ordered(day: SnapshotPrayerDay) {
        val values = onsetValues(day)
        check(values.all(clockPattern::matches) && values.zipWithNext().all { (a, b) -> a < b }, "qualified_onset_order")
    }

    private fun onsetValues(day: SnapshotPrayerDay) = listOf(day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha)

    private fun text(value: String, maximum: Int) {
        val points = value.codePoints().toArray()
        check(points.size in 1..maximum && value.trim() == value && points.none { Character.isISOControl(it) || it in 0xD800..0xDFFF }, "qualification_text_invalid")
    }

    private fun publicUrl(value: String) {
        text(value, 2048)
        val uri = runCatching { URI(value) }.getOrNull()
        check(uri != null && uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.rawUserInfo == null && uri.rawFragment == null, "qualification_url_invalid")
    }

    private fun timestamp(value: String): Instant {
        check(timestampPattern.matches(value), "qualification_datetime_invalid")
        val instant = runCatching { Instant.parse(value) }.getOrNull() ?: fail("qualification_datetime_invalid")
        check(instant.toString() == value, "qualification_datetime_invalid")
        return instant
    }

    private fun date(value: String): LocalDate {
        check(datePattern.matches(value), "qualification_date_invalid")
        return runCatching { LocalDate.parse(value) }.getOrNull() ?: fail("qualification_date_invalid")
    }

    private fun JsonObject.withoutFingerprint() = JsonObject(filterKeys { it != "qualification_id" && it != "sha256" })
    private fun hash(element: JsonElement): String = hashBytes(canonicalJson(element))
    private fun hashBytes(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun check(condition: Boolean, code: String) { if (!condition) fail(code) }
    private fun fail(code: String): Nothing = throw SnapshotValidationException("source.qualification", code)
}
