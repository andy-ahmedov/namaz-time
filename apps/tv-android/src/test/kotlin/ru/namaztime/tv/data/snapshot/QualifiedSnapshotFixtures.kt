package ru.namaztime.tv.data.snapshot

import com.google.crypto.tink.subtle.Ed25519Sign
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

// Entirely synthetic authority, evidence and constant rows. Never bundled in an app.
internal fun qualifiedSnapshotDocument(): JsonObject {
    val days = JsonArray((1..30).map { day ->
        buildJsonObject {
            put("date", "2026-09-${day.toString().padStart(2, '0')}")
            put("fajr", "04:00")
            put("sunrise", "06:00")
            put("dhuhr", "12:00")
            put("asr", "15:00")
            put("maghrib", "18:00")
            put("isha", "20:00")
        }
    })
    val proof = buildJsonObject {
        put("schema_version", "namaztime-source-qualification/v1")
        put("state", "qualified")
        put("decision_system", "namaztime:source-qualification/v1")
        put("qualified_at", "2026-09-08T10:00:00Z")
        put("authority", buildJsonObject {
            put("id", "authority-synthetic")
            put("name", "Synthetic test authority")
            put("website", "https://authority.example")
            put("evidence_label", "CONFIRMED_PUBLIC")
        })
        put("source_id", "source-synthetic")
        put("source_kind", "official_html")
        put("canonical_url", "https://authority.example/calendar")
        put("scope", buildJsonObject {
            put("id", "scope-synthetic-city")
            put("kind", "city")
            put("city_id", "city-synthetic")
            put("region_id", "region-synthetic")
            put("description", "Synthetic city only")
        })
        put("catalog_revision", "catalog-synthetic-v1")
        put("timezone", "Europe/Moscow")
        put("coverage", buildJsonObject {
            put("from", "2026-09-01")
            put("to", "2026-09-30")
        })
        put("fresh_through", "2026-09-30")
        put("artifact", buildJsonObject {
            put("filename", "https://authority.example/calendar")
            put("content_type", "text/html")
            put("captured_at", "2026-09-08T09:00:00Z")
            put("byte_length", 1234)
            put("sha256", "a".repeat(64))
        })
        put("retrieval", buildJsonObject {
            put("url", "https://authority.example/calendar")
            put("http_status", 200)
            put("content_type", "text/html")
        })
        put("parser_version", "synthetic-test/v1")
        put("candidate_id", "candidate-synthetic")
        put("normalized_sha256", "b".repeat(64))
        put("onset_sha256", fixtureHash(fixtureCanonical(days)))
        put("transcription_sha256", "a".repeat(64))
        put("diff_sha256", "c".repeat(64))
        put("validation_sha256", "d".repeat(64))
        put("validated_days", 30)
        put("terms_assessment", "public_transport_no_restriction_observed")
        put("evidence", JsonArray(listOf(
            "ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison",
        ).map { purpose ->
            buildJsonObject {
                put("id", "evidence-$purpose")
                put("purpose", purpose)
                put("label", "CONFIRMED_PUBLIC")
                put("url", "https://authority.example/$purpose")
                put("retrieved_at", "2026-09-08T09:00:00Z")
                put("sha256", "e".repeat(64))
                put("claim", "Synthetic evidence for $purpose")
            }
        }))
        put("comparisons", JsonArray(listOf(0, 14, 29).map { index ->
            buildJsonObject {
                put("evidence_id", "evidence-value_comparison")
                put("day", days[index])
            }
        }))
    }.withQualificationHash()
    val legacy = Json.parseToJsonElement(syntheticSnapshotBytes().decodeToString()).jsonObject
    return JsonObject(legacy.toMutableMap().apply {
        put("schema_version", JsonPrimitive("2.0"))
        put("snapshot_id", JsonPrimitive("synthetic-qualified-snapshot-v2"))
        put("generated_at", JsonPrimitive("2026-09-08T11:00:00Z"))
        put("mosque", buildJsonObject {
            put("id", "public-scope-" + fixtureHash("scope-synthetic-city".encodeToByteArray()).take(32))
            put("name", "Synthetic city")
            put("country_code", "RU")
            put("locality", "Synthetic city")
            put("timezone", "Europe/Moscow")
        })
        put("source", buildJsonObject {
            put("source_id", "source-synthetic")
            put("kind", "official_html")
            put("authority_name", "Synthetic test authority")
            put("geographic_scope", "Synthetic city only")
            put("canonical_url", "https://authority.example/calendar")
            put("retrieved_at", "2026-09-08T09:00:00Z")
            put("effective_from", "2026-09-01")
            put("effective_to", "2026-09-30")
            put("raw_sha256", "a".repeat(64))
            put("parser_version", "synthetic-test/v1")
            put("qualification", proof)
        })
        put("coverage", proof.getValue("coverage"))
        put("prayer_days", days)
        listOf("iqamah_rules", "iqamah_date_overrides", "jumuah_sessions", "campaigns", "theme")
            .forEach(::remove)
    })
}

internal fun JsonObject.withQualificationHash(): JsonObject {
    val content = JsonObject(filterKeys { it !in setOf("qualification_id", "sha256") })
    val hash = fixtureHash(fixtureCanonical(content))
    return JsonObject(content + mapOf(
        "qualification_id" to JsonPrimitive("qualification-" + hash.take(32)),
        "sha256" to JsonPrimitive(hash),
    ))
}

internal fun JsonObject.mutateObject(key: String, block: MutableMap<String, JsonElement>.() -> Unit) =
    JsonObject(this + (key to JsonObject(getValue(key).jsonObject.toMutableMap().apply(block))))

internal fun JsonObject.mutateQualification(rehash: Boolean = true, block: MutableMap<String, JsonElement>.() -> Unit): JsonObject =
    mutateObject("source") {
        val changed = JsonObject(getValue("qualification").jsonObject.toMutableMap().apply(block))
        put("qualification", if (rehash) changed.withQualificationHash() else changed)
    }

internal fun JsonObject.changeDay(index: Int, block: MutableMap<String, JsonElement>.() -> Unit): JsonObject {
    val days = getValue("prayer_days").jsonArray.toMutableList()
    days[index] = JsonObject(days[index].jsonObject.toMutableMap().apply(block))
    return JsonObject(this + ("prayer_days" to JsonArray(days)))
}

internal fun JsonObject.bindOnset(): JsonObject = mutateQualification {
    put("onset_sha256", JsonPrimitive(fixtureHash(fixtureCanonical(this@bindOnset.getValue("prayer_days")))))
}

// Independent fixture implementation: recursively sort JSON objects, then use
// kotlinx serialization's JSON escaping. No production canonical helper is called.
internal fun fixtureCanonical(element: JsonElement): ByteArray {
    fun sorted(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { sorted(it.value) })
        is JsonArray -> JsonArray(value.map(::sorted))
        else -> value
    }
    return sorted(element).toString().encodeToByteArray()
}

internal fun fixtureHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

// Ephemeral in-memory test keys. These are never product signing material.
internal class QualifiedSnapshotSigner {
    private val keyPair = Ed25519Sign.KeyPair.newKeyPair()
    private val keyId = "ephemeral-qualified-test"
    private val otherBundles = listOf("test", "staging").map { environment ->
        trustBundle(environment, "$environment-ephemeral", Ed25519Sign.KeyPair.newKeyPair().publicKey)
    }

    fun verifier(status: String = "active"): SnapshotAuthenticityVerifier = verifierForFixture(keyId, keyPair.publicKey, status)

    fun localSetupTrustFiles(): Map<String, ByteArray> {
        fun production(revision: Int) = JsonObject(
            Json.parseToJsonElement(trustBundle("production", keyId, keyPair.publicKey).decodeToString()).jsonObject +
                ("revision" to JsonPrimitive(revision)),
        ).toString().encodeToByteArray()
        return mapOf(
            "trust/production.json" to production(3),
            "trust/previous-production.json" to production(2),
            "trust/test.json" to otherBundles[0],
            "trust/staging.json" to otherBundles[1],
        )
    }

    fun verifierForFixture(id: String, publicKey: ByteArray, status: String = "active"): SnapshotAuthenticityVerifier = SnapshotAuthenticityVerifier(
        trustBundle("production", id, publicKey, status),
        minimumTrustBundleRevision = 1,
        environmentTrustBundles = otherBundles,
    )

    fun sign(document: JsonObject = qualifiedSnapshotDocument()): ByteArray {
        val root = JsonObject(document + ("data_classification" to JsonPrimitive("production")))
        val canonical = fixtureCanonical(JsonObject(root - "integrity"))
        return JsonObject(root + ("integrity" to buildJsonObject {
            put("canonical_sha256", fixtureHash(canonical))
            put("signing_key_id", keyId)
            put("signature_ed25519_base64", Base64.getEncoder().encodeToString(Ed25519Sign(keyPair.privateKey).sign(canonical)))
        })).toString().encodeToByteArray()
    }

    private fun trustBundle(environment: String, id: String, key: ByteArray, status: String = "active") =
        buildJsonObject {
            put("schema_version", "1.0")
            put("revision", 1)
            put("environment", environment)
            put("generated_at", "2026-09-10T00:00:00Z")
            put("keys", JsonArray(listOf(buildJsonObject {
                put("key_id", id)
                put("algorithm", "ed25519")
                put("public_key_ed25519_base64", Base64.getEncoder().encodeToString(key))
                put("status", status)
                put("not_before", "2024-01-01T00:00:00Z")
                if (status == "retired") put("not_after", "2026-09-09T00:00:00Z")
                if (status == "revoked") {
                    put("revoked_at", "2026-09-09T00:00:00Z")
                    put("revocation_reason", "Synthetic test revocation")
                }
            })))
        }.toString().encodeToByteArray()
}
