package ru.namaztime.tv.data.snapshot

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test

class QualifiedSnapshotDecoderTest {
    @Test
    fun publicQualificationDecodesWithoutHumanApproval() {
        val snapshot = SnapshotDecoder.decode(qualifiedSnapshotDocument().toString().encodeToByteArray())
        assertEquals("2.0", snapshot.schemaVersion)
        assertEquals(30, snapshot.prayerDays.size)
        assertEquals(null, snapshot.source.approval)
        assertEquals(emptyList<SnapshotIqamahRule>(), snapshot.iqamahRules)
        assertEquals(emptyList<SnapshotJumuahSession>(), snapshot.jumuahSessions)
    }

    @Test
    fun admissionBranchesCannotBeMixedMissingOrReinterpreted() {
        val valid = qualifiedSnapshotDocument()
        val approval = kotlinx.serialization.json.Json.parseToJsonElement(syntheticSnapshotBytes().decodeToString())
            .jsonObject.getValue("source").jsonObject.getValue("approval")
        listOf(
            valid.mutateObject("source") { put("approval", approval) },
            valid.mutateObject("source") { put("approval", JsonObject(emptyMap())) },
            valid.mutateObject("source") { put("approval", JsonNull) },
            valid.mutateObject("source") { remove("qualification") },
            JsonObject(valid + ("schema_version" to JsonPrimitive("1.0"))),
        ).forEach(::reject)
    }

    @Test
    fun proofValidationRejectsRehashedButInsufficientEvidence() {
        val valid = qualifiedSnapshotDocument()
        val textCases = mapOf(
            "schema_version" to "1.0", "state" to "approved", "decision_system" to "external-approver",
            "source_id" to " ", "catalog_revision" to "", "parser_version" to "bad\nparser",
            "canonical_url" to "http://authority.example/calendar", "source_kind" to "calculation_profile",
            "qualified_at" to "2026-09-08T10:00:00.000Z", "fresh_through" to "2026-09-07",
            "timezone" to "+03:00", "terms_assessment" to "permission_pending", "onset_sha256" to "F".repeat(64),
        )
        textCases.forEach { (field, value) -> reject(valid.mutateQualification { put(field, JsonPrimitive(value)) }) }
        listOf(
            valid.mutateQualification { put("validated_days", JsonPrimitive(29)) },
            valid.mutateQualification { put("unknowns", JsonArray(listOf(JsonPrimitive(" ")))) },
            valid.mutateQualification { put("evidence", JsonArray(getValue("evidence").jsonArray.drop(1))) },
            valid.mutateQualification { put("evidence", JsonArray(getValue("evidence").jsonArray + getValue("evidence").jsonArray.first())) },
            valid.mutateQualification { put("comparisons", JsonArray(getValue("comparisons").jsonArray.take(2))) },
            valid.mutateQualification { put("authority", JsonObject(getValue("authority").jsonObject + ("evidence_label" to JsonPrimitive("INFERENCE")))) },
            valid.mutateQualification { put("scope", JsonObject(getValue("scope").jsonObject - "city_id")) },
            valid.mutateQualification { put("scope", JsonObject(getValue("scope").jsonObject + ("kind" to JsonPrimitive("region")))) },
            valid.mutateQualification { put("artifact", JsonObject(getValue("artifact").jsonObject + ("captured_at" to JsonPrimitive("2026-09-08T10:01:00Z")))) },
            valid.mutateQualification { put("retrieval", JsonObject(getValue("retrieval").jsonObject + ("http_status" to JsonPrimitive(403)))) },
        ).forEach(::reject)
    }

    @Test
    fun proofAndEveryOnsetRowRemainHashBoundWithoutLossyDefaults() {
        val valid = qualifiedSnapshotDocument()
        reject(valid.mutateQualification(rehash = false) { put("candidate_id", JsonPrimitive("different")) })
        reject(valid.mutateQualification(rehash = false) { put("qualification_id", JsonPrimitive("qualification-" + "0".repeat(32))) })
        // Go omitempty drops these defaults. Hashing only the raw or only the typed
        // proof would accept one of these two forms; both must agree.
        reject(valid.mutateQualification { put("unknowns", JsonArray(emptyList())) })
        reject(valid.mutateQualification(rehash = false) { put("unknowns", JsonArray(emptyList())) })
        reject(valid.mutateQualification { put("authority", JsonObject(getValue("authority").jsonObject + ("branch" to JsonPrimitive("")))) })
        val changed = valid.changeDay(7) { put("fajr", JsonPrimitive("04:01")) }
        reject(changed)
        val emptyFlags = valid.changeDay(7) { put("flags", JsonArray(emptyList())) }
        reject(emptyFlags.bindOnset())
        val disordered = valid.changeDay(7) { put("isha", JsonPrimitive("00:20")) }.bindOnset()
        reject(disordered)
        val changedComparison = valid.changeDay(14) { put("fajr", JsonPrimitive("04:01")) }.bindOnset()
        reject(changedComparison)
    }

    @Test
    fun presentEmptyComparisonClocksCannotDivergeFromGoOmitEmptyEncoding() {
        val valid = qualifiedSnapshotDocument()
        listOf("duha", "middle_of_night", "last_third_of_night").forEach { field ->
            reject(valid.mutateQualification {
                val comparisons = getValue("comparisons").jsonArray.toMutableList()
                comparisons[0] = comparisons[0].jsonObject.mutateObject("day") { put(field, JsonPrimitive("")) }
                put("comparisons", JsonArray(comparisons))
            })
        }
    }

    @Test
    fun qualifiedScopeSourceGenerationAndLocalRulesAreStrictlyBound() {
        val valid = qualifiedSnapshotDocument()
        listOf(
            valid.mutateObject("source") { put("authority_name", JsonPrimitive("Another authority")) },
            valid.mutateObject("source") { put("source_id", JsonPrimitive("neighbor-source")) },
            valid.mutateObject("source") { put("raw_sha256", JsonPrimitive("9".repeat(64))) },
            valid.mutateObject("source") { put("parser_version", JsonPrimitive("different/v1")) },
            valid.mutateObject("source") { put("geographic_scope", JsonPrimitive("Entire region")) },
            valid.mutateObject("mosque") { put("id", JsonPrimitive("mosque-implied")) },
            valid.mutateObject("mosque") { put("timezone", JsonPrimitive("Europe/Samara")) },
            JsonObject(valid + ("generated_at" to JsonPrimitive("2026-09-08T09:59:59Z"))),
            JsonObject(valid + ("generated_at" to JsonPrimitive("2026-09-30T21:00:00Z"))),
            valid.mutateQualification { put("terms_assessment", JsonPrimitive("public_transport_attribution_required")) },
        ).forEach(::reject)
        val legacy = kotlinx.serialization.json.Json.parseToJsonElement(syntheticSnapshotBytes().decodeToString()).jsonObject
        listOf("iqamah_rules", "iqamah_date_overrides", "jumuah_sessions").forEach { field ->
            legacy[field]?.takeIf { it.jsonArray.isNotEmpty() }?.let { reject(JsonObject(valid + (field to it))) }
        }
    }

    @Test
    fun duplicatesUnknownFieldsExplicitNullsAndMalformedTypesFailClosed() {
        val valid = qualifiedSnapshotDocument()
        reject(valid.mutateQualification { put("unexpected", JsonPrimitive("ignored?")) })
        reject(valid.mutateQualification { put("unknowns", JsonNull) })
        reject(valid.mutateQualification { put("validated_days", JsonPrimitive("30")) })
        reject(valid.mutateQualification { put("validated_days", JsonPrimitive(30.0)) })
        val duplicate = valid.toString().replaceFirst("\"qualified_at\":", "\"state\":\"qualified\",\"qualified_at\":")
        assertEquals("duplicate_json_member", assertThrows(SnapshotValidationException::class.java) {
            SnapshotDecoder.decode(duplicate.encodeToByteArray())
        }.code)
    }

    @Test
    fun excessiveBytesAndNestingAreRejectedWithoutParserStackOverflow() {
        val padded = qualifiedSnapshotDocument().toString() + " ".repeat(5 * 1024 * 1024)
        assertEquals("snapshot_too_large", assertThrows(SnapshotValidationException::class.java) {
            SnapshotDecoder.decode(padded.encodeToByteArray())
        }.code)
        val nested = "[".repeat(10_000) + "0" + "]".repeat(10_000)
        assertThrows(SnapshotValidationException::class.java) { SnapshotDecoder.decode(nested.encodeToByteArray()) }
        assertThrows(SnapshotAuthenticityException::class.java) {
            SnapshotAuthenticityVerifier(emptyMap()).verifyAndDecode(nested.encodeToByteArray())
        }
    }

    private fun reject(document: JsonObject) {
        assertThrows(SnapshotValidationException::class.java) { SnapshotDecoder.decode(document.toString().encodeToByteArray()) }
    }
}
