package com.example.namaztime.tv.data.snapshot

import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val MAX_SIGNED_SNAPSHOT_BYTES = 5 * 1024 * 1024

class SnapshotAuthenticityException(val code: String) : IllegalArgumentException(code)

enum class SnapshotAuthenticity {
    BUNDLED_SYNTHETIC,
    AUTHENTICATED,
}

class VerifiedSnapshot internal constructor(val payload: SnapshotPayload)

class SnapshotSelectionTrust internal constructor(
    private val policy: SnapshotTrustPolicy,
) {
    internal fun allows(keyId: String, generatedAt: Instant): Boolean =
        policy.allowsPersistedSnapshot(keyId, generatedAt)
}

class ActivatableSnapshot private constructor(
    val payload: SnapshotPayload,
    val authenticity: SnapshotAuthenticity,
) {
    init {
        require(payload.dataClassification == "synthetic" || authenticity == SnapshotAuthenticity.AUTHENTICATED) {
            "Production snapshots require authenticated bytes"
        }
    }

    companion object {
        internal fun bundledSynthetic(payload: SnapshotPayload): ActivatableSnapshot {
            if (payload.dataClassification != "synthetic") {
                throw SnapshotValidationException(
                    path = "data_classification",
                    code = "bundled_requires_synthetic",
                )
            }
            return ActivatableSnapshot(payload, SnapshotAuthenticity.BUNDLED_SYNTHETIC)
        }

        internal fun authenticated(
            bytes: ByteArray,
            verifier: SnapshotAuthenticityVerifier,
        ): ActivatableSnapshot = ActivatableSnapshot(
            payload = verifier.verifyAndDecode(bytes).payload,
            authenticity = SnapshotAuthenticity.AUTHENTICATED,
        )
    }
}

object SnapshotActivationGate {
    fun bundledSynthetic(bytes: ByteArray): ActivatableSnapshot =
        bundledSynthetic(SnapshotDecoder.decode(bytes))

    fun bundledSynthetic(payload: SnapshotPayload): ActivatableSnapshot =
        ActivatableSnapshot.bundledSynthetic(payload)

    fun authenticated(
        bytes: ByteArray,
        verifier: SnapshotAuthenticityVerifier,
    ): ActivatableSnapshot = ActivatableSnapshot.authenticated(bytes, verifier)
}

enum class SnapshotTrustKeyStatus {
    SCHEDULED,
    ACTIVE,
    RETIRED,
    REVOKED,
}

internal class SnapshotTrustKey(
    val keyId: String,
    val publicKey: ByteArray,
    val status: SnapshotTrustKeyStatus,
    val notBefore: Instant,
    val notAfter: Instant?,
    val revokedAt: Instant?,
    val revocationReason: String?,
)

class SnapshotTrustPolicy private constructor(
    val environment: String,
    val revision: Long,
    private val generatedAt: Instant,
    internal val keys: Map<String, SnapshotTrustKey>,
    internal val legacyTestOnly: Boolean,
    internal var environmentSeparationValidated: Boolean = false,
) {
    companion object {
        fun decode(bytes: ByteArray): SnapshotTrustPolicy {
            if (bytes.isEmpty() || bytes.size > 256 * 1024) throw SnapshotAuthenticityException("trust_bundle_size_invalid")
			val text = try { bytes.decodeToString(throwOnInvalidSequence = true) } catch (_: Exception) {
				throw SnapshotAuthenticityException("trust_bundle_invalid")
			}
			StrictJsonObjectKeyScanner(text, "trust_bundle_invalid").scan()
            val root = try {
				Json.parseToJsonElement(text).jsonObject
            } catch (_: Exception) {
                throw SnapshotAuthenticityException("trust_bundle_invalid")
            }
			root.requireExactKeys(setOf("schema_version", "revision", "environment", "generated_at", "keys"))
            if (root.string("schema_version") != "1.0") throw SnapshotAuthenticityException("trust_bundle_version_unsupported")
			val revision = root.positiveLong("revision")
			if (revision < 1) throw SnapshotAuthenticityException("trust_revision_invalid")
            val environment = root.string("environment")
            if (environment !in setOf("test", "staging", "production")) throw SnapshotAuthenticityException("trust_environment_invalid")
            val generatedAt = canonicalTrustInstant(root.string("generated_at"), "trust_generated_at_invalid")
            val entries = try { root.getValue("keys") as JsonArray } catch (_: Exception) {
                throw SnapshotAuthenticityException("trust_keys_invalid")
            }
            if (entries.isEmpty() || entries.size > 32) throw SnapshotAuthenticityException("trust_keys_invalid")
            val keys = linkedMapOf<String, SnapshotTrustKey>()
            val material = mutableSetOf<String>()
            entries.forEach { element ->
                val entry = try { element.jsonObject } catch (_: Exception) { throw SnapshotAuthenticityException("trust_key_invalid") }
                val allowed = setOf("key_id", "algorithm", "public_key_ed25519_base64", "status", "not_before", "not_after", "revoked_at", "revocation_reason")
                entry.requireAllowedAndRequiredKeys(allowed, setOf("key_id", "algorithm", "public_key_ed25519_base64", "status", "not_before"))
                val keyId = entry.string("key_id")
                if (!TRUST_KEY_ID.matches(keyId) || entry.string("algorithm") != "ed25519") throw SnapshotAuthenticityException("trust_key_invalid")
                val encodedKey = entry.string("public_key_ed25519_base64")
                val publicKey = try { Base64.getDecoder().decode(encodedKey) } catch (_: Exception) { throw SnapshotAuthenticityException("trust_key_invalid") }
                if (publicKey.size != 32 || Base64.getEncoder().encodeToString(publicKey) != encodedKey || !material.add(encodedKey)) {
                    throw SnapshotAuthenticityException("trust_key_invalid")
                }
                val status = try { SnapshotTrustKeyStatus.valueOf(entry.string("status").uppercase()) } catch (_: Exception) {
                    throw SnapshotAuthenticityException("trust_key_invalid")
                }
                val notBefore = canonicalTrustInstant(entry.string("not_before"), "trust_key_invalid")
                val notAfter = entry.optionalString("not_after")?.let { canonicalTrustInstant(it, "trust_key_invalid") }
                if (notAfter != null && notAfter < notBefore) throw SnapshotAuthenticityException("trust_key_invalid")
                val revokedAtText = entry.optionalString("revoked_at")
                val reason = entry.optionalString("revocation_reason")
                when (status) {
                    SnapshotTrustKeyStatus.SCHEDULED, SnapshotTrustKeyStatus.ACTIVE -> if (revokedAtText != null || reason != null) throw SnapshotAuthenticityException("trust_key_invalid")
                    SnapshotTrustKeyStatus.RETIRED -> if (notAfter == null || revokedAtText != null || reason != null) throw SnapshotAuthenticityException("trust_key_invalid")
                    SnapshotTrustKeyStatus.REVOKED -> {
                        if (revokedAtText == null || reason == null || reason.length !in 3..240) throw SnapshotAuthenticityException("trust_key_invalid")
                    }
                }
                val revokedAt = revokedAtText?.let { canonicalTrustInstant(it, "trust_key_invalid") }
                if ((status == SnapshotTrustKeyStatus.RETIRED && notAfter != null && notAfter > generatedAt) ||
                    (status == SnapshotTrustKeyStatus.REVOKED && revokedAt != null && revokedAt > generatedAt)
                ) {
                    throw SnapshotAuthenticityException("trust_key_invalid")
                }
                if (keys.put(keyId, SnapshotTrustKey(keyId, publicKey.copyOf(), status, notBefore, notAfter, revokedAt, reason)) != null) {
                    throw SnapshotAuthenticityException("trust_key_invalid")
                }
            }
            return SnapshotTrustPolicy(environment, revision, generatedAt, keys, false)
        }

        internal fun legacyTestOnly(keys: Map<String, ByteArray>) = SnapshotTrustPolicy(
            "test",
            1,
            Instant.MIN,
            keys.mapValues { (keyId, key) -> SnapshotTrustKey(keyId, key.copyOf(), SnapshotTrustKeyStatus.ACTIVE, Instant.MIN, null, null, null) },
            true,
        )

        internal fun validateTransition(previous: SnapshotTrustPolicy, current: SnapshotTrustPolicy) {
            if (previous.environment != current.environment ||
                previous.revision == Long.MAX_VALUE || current.revision != previous.revision + 1 ||
                current.generatedAt < previous.generatedAt
            ) {
                throw SnapshotAuthenticityException("trust_transition_invalid")
            }
            val previousMaterial = previous.keys.values.associateBy { Base64.getEncoder().encodeToString(it.publicKey) }
            previous.keys.forEach { (keyId, old) ->
                val updated = current.keys[keyId]
                if (updated == null) {
                    if (old.status in setOf(SnapshotTrustKeyStatus.SCHEDULED, SnapshotTrustKeyStatus.ACTIVE)) {
                        throw SnapshotAuthenticityException("trust_transition_invalid")
                    }
                    return@forEach
                }
                if (!old.publicKey.contentEquals(updated.publicKey) || old.notBefore != updated.notBefore ||
                    (old.notAfter != null && old.notAfter != updated.notAfter)
                ) {
                    throw SnapshotAuthenticityException("trust_transition_invalid")
                }
                val allowed = when (old.status) {
                    SnapshotTrustKeyStatus.SCHEDULED -> setOf(SnapshotTrustKeyStatus.SCHEDULED, SnapshotTrustKeyStatus.ACTIVE, SnapshotTrustKeyStatus.REVOKED)
                    SnapshotTrustKeyStatus.ACTIVE -> setOf(SnapshotTrustKeyStatus.ACTIVE, SnapshotTrustKeyStatus.RETIRED, SnapshotTrustKeyStatus.REVOKED)
                    SnapshotTrustKeyStatus.RETIRED -> setOf(SnapshotTrustKeyStatus.RETIRED, SnapshotTrustKeyStatus.REVOKED)
                    SnapshotTrustKeyStatus.REVOKED -> setOf(SnapshotTrustKeyStatus.REVOKED)
                }
                if (updated.status !in allowed ||
                    (old.status == SnapshotTrustKeyStatus.REVOKED &&
                        (old.notAfter != updated.notAfter || old.revokedAt != updated.revokedAt || old.revocationReason != updated.revocationReason))
                ) {
                    throw SnapshotAuthenticityException("trust_transition_invalid")
                }
            }
            current.keys.forEach { (keyId, key) ->
                if (keyId !in previous.keys) {
                    if (key.status != SnapshotTrustKeyStatus.SCHEDULED) {
                        throw SnapshotAuthenticityException("trust_transition_invalid")
                    }
                    val old = previousMaterial[Base64.getEncoder().encodeToString(key.publicKey)]
                    if (old != null) throw SnapshotAuthenticityException("trust_transition_invalid")
                }
            }
        }

        internal fun validateEnvironmentSeparation(policies: List<SnapshotTrustPolicy>) {
            val environments = policies.associateBy { it.environment }
            if (environments.size != policies.size || environments.keys != setOf("test", "staging", "production")) {
                throw SnapshotAuthenticityException("trust_environment_separation_required")
            }
            val keyIds = mutableSetOf<String>()
            val material = mutableSetOf<String>()
            policies.forEach { policy ->
                policy.keys.values.forEach { key ->
                    if (!keyIds.add(key.keyId) || !material.add(Base64.getEncoder().encodeToString(key.publicKey))) {
                        throw SnapshotAuthenticityException("trust_environment_separation_invalid")
                    }
                }
            }
            policies.forEach { it.environmentSeparationValidated = true }
        }
    }

    internal fun verificationKey(keyId: String, generatedAt: Instant): ByteArray {
        val key = keys[keyId] ?: throw SnapshotAuthenticityException("unknown_signing_key")
        if (key.status == SnapshotTrustKeyStatus.REVOKED) throw SnapshotAuthenticityException("signing_key_revoked")
        if (key.status !in setOf(SnapshotTrustKeyStatus.ACTIVE, SnapshotTrustKeyStatus.RETIRED)) {
            throw SnapshotAuthenticityException("signing_key_not_verifiable")
        }
        if (generatedAt < key.notBefore || (key.notAfter != null && generatedAt > key.notAfter)) {
            throw SnapshotAuthenticityException("signing_key_outside_window")
        }
        return key.publicKey.copyOf()
    }

    internal fun allowsPersistedSnapshot(keyId: String, generatedAt: Instant): Boolean =
        environment == "production" && !legacyTestOnly &&
            environmentSeparationValidated &&
            runCatching { verificationKey(keyId, generatedAt) }.isSuccess
}

class SnapshotAuthenticityVerifier private constructor(
    private val trustPolicy: SnapshotTrustPolicy,
    minimumTrustBundleRevision: Long,
    previousTrustPolicy: SnapshotTrustPolicy?,
    environmentTrustPolicies: List<SnapshotTrustPolicy>,
) {
    init {
        if (minimumTrustBundleRevision < 0 || trustPolicy.revision < minimumTrustBundleRevision) {
            throw SnapshotAuthenticityException("trust_bundle_rollback")
        }
        if (trustPolicy.environment == "production" && minimumTrustBundleRevision == 0L) {
            throw SnapshotAuthenticityException("production_trust_revision_required")
        }
        if (trustPolicy.revision > 1 && previousTrustPolicy == null) {
            throw SnapshotAuthenticityException("trust_transition_required")
        }
        if (previousTrustPolicy != null) {
            SnapshotTrustPolicy.validateTransition(previousTrustPolicy, trustPolicy)
        }
        if (trustPolicy.environment == "production") {
            SnapshotTrustPolicy.validateEnvironmentSeparation(environmentTrustPolicies + trustPolicy)
        } else if (environmentTrustPolicies.isNotEmpty()) {
            throw SnapshotAuthenticityException("trust_environment_separation_invalid")
        }
    }

    constructor(trustedPublicKeys: Map<String, ByteArray>) : this(SnapshotTrustPolicy.legacyTestOnly(trustedPublicKeys), 0, null, emptyList())
    constructor(
        trustBundle: ByteArray,
        minimumTrustBundleRevision: Long = 0,
        previousTrustBundle: ByteArray? = null,
        environmentTrustBundles: List<ByteArray> = emptyList(),
    ) : this(
        SnapshotTrustPolicy.decode(trustBundle),
        minimumTrustBundleRevision,
        previousTrustBundle?.let(SnapshotTrustPolicy::decode),
        environmentTrustBundles.map(SnapshotTrustPolicy::decode),
    )

    fun verifyAndDecode(bytes: ByteArray): VerifiedSnapshot {
        if (bytes.size > MAX_SIGNED_SNAPSHOT_BYTES) {
            throw SnapshotAuthenticityException("snapshot_too_large")
        }
		val text = try { bytes.decodeToString(throwOnInvalidSequence = true) } catch (_: Exception) {
			throw SnapshotAuthenticityException("invalid_json")
		}
		StrictJsonObjectKeyScanner(text, "invalid_json").scan()
        val root = try {
			Json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            throw SnapshotAuthenticityException("invalid_json")
        }
        val integrity = try {
            root.getValue("integrity").jsonObject
        } catch (_: Exception) {
            throw SnapshotAuthenticityException("integrity_missing")
        }
        val keyId = integrity.string("signing_key_id")
        val generatedAt = try {
            Instant.parse(root.string("generated_at"))
        } catch (_: Exception) {
            throw SnapshotAuthenticityException("snapshot_generated_at_invalid")
        }
        val classification = try {
            root.string("data_classification")
        } catch (_: Exception) {
            throw SnapshotAuthenticityException("data_classification_invalid")
        }
        if (classification == "production" && (trustPolicy.environment != "production" || trustPolicy.legacyTestOnly)) {
            throw SnapshotAuthenticityException("production_trust_policy_required")
        }
        if (classification != "production" && trustPolicy.environment == "production") {
            throw SnapshotAuthenticityException("trust_environment_mismatch")
        }
        val expectedHash = integrity.string("canonical_sha256")
        val signature = try {
            Base64.getDecoder().decode(integrity.string("signature_ed25519_base64"))
        } catch (_: IllegalArgumentException) {
            throw SnapshotAuthenticityException("signature_encoding_invalid")
        }
        val publicKey = trustPolicy.verificationKey(keyId, generatedAt)
        if (publicKey.size != 32) {
            throw SnapshotAuthenticityException("public_key_invalid")
        }
        val canonical = canonicalPayload(root)
        val actualHash = MessageDigest.getInstance("SHA-256")
            .digest(canonical)
            .toHex()
        if (!MessageDigest.isEqual(actualHash.encodeToByteArray(), expectedHash.encodeToByteArray())) {
            throw SnapshotAuthenticityException("canonical_hash_mismatch")
        }
        try {
            Ed25519Verify(publicKey).verify(signature, canonical)
        } catch (_: GeneralSecurityException) {
            throw SnapshotAuthenticityException("signature_invalid")
        }
        return VerifiedSnapshot(SnapshotDecoder.decode(bytes))
    }

    internal fun selectionTrust(): SnapshotSelectionTrust = SnapshotSelectionTrust(trustPolicy)
}

private fun JsonObject.string(name: String): String = try {
	getValue(name).jsonPrimitive.let { primitive ->
		if (!primitive.isString) throw SnapshotAuthenticityException("json_field_invalid")
		primitive.content
	}
} catch (_: Exception) {
    throw SnapshotAuthenticityException("json_field_invalid")
}

private fun JsonObject.positiveLong(name: String): Long = try {
	getValue(name).jsonPrimitive.let { primitive ->
		if (primitive.isString || !primitive.content.matches(Regex("[1-9][0-9]*"))) {
			throw SnapshotAuthenticityException("trust_revision_invalid")
		}
		primitive.content.toLong()
	}
} catch (error: SnapshotAuthenticityException) {
	throw error
} catch (_: Exception) {
	throw SnapshotAuthenticityException("trust_revision_invalid")
}

private fun JsonObject.requireExactKeys(expected: Set<String>) {
    if (keys != expected) throw SnapshotAuthenticityException("trust_bundle_invalid")
}

private fun JsonObject.requireAllowedAndRequiredKeys(allowed: Set<String>, required: Set<String>) {
    if (!keys.all { it in allowed } || !keys.containsAll(required)) throw SnapshotAuthenticityException("trust_key_invalid")
}

private fun JsonObject.optionalString(name: String): String? = this[name]?.let {
	try {
		it.jsonPrimitive.let { primitive ->
			if (!primitive.isString) throw SnapshotAuthenticityException("trust_key_invalid")
			primitive.content
		}
	} catch (_: Exception) {
		throw SnapshotAuthenticityException("trust_key_invalid")
	}
}

private fun canonicalTrustInstant(value: String, code: String): Instant {
    if (!CANONICAL_TRUST_TIMESTAMP.matches(value)) throw SnapshotAuthenticityException(code)
    val instant = try { Instant.parse(value) } catch (_: Exception) { throw SnapshotAuthenticityException(code) }
    return instant
}

private val TRUST_KEY_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
private val CANONICAL_TRUST_TIMESTAMP = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")

private class StrictJsonObjectKeyScanner(
    private val input: String,
    private val errorCode: String,
) {
    private var index = 0

    fun scan() {
        try {
            whitespace()
            value()
            whitespace()
            if (index != input.length) fail()
        } catch (error: SnapshotAuthenticityException) {
            throw error
        } catch (_: Exception) {
            throw SnapshotAuthenticityException(errorCode)
        }
    }

    private fun value() {
        whitespace()
        when (peek()) {
            '{' -> objectValue()
            '[' -> arrayValue()
            '"' -> stringValue()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            '-', in '0'..'9' -> numberValue()
            else -> fail()
        }
    }

    private fun objectValue() {
        expect('{')
        whitespace()
        if (take('}')) return
        val seen = mutableSetOf<String>()
        while (true) {
            whitespace()
            val key = stringValue()
            if (!seen.add(key)) throw SnapshotAuthenticityException("duplicate_json_member")
            whitespace()
            expect(':')
            value()
            whitespace()
            if (take('}')) return
            expect(',')
        }
    }

    private fun arrayValue() {
        expect('[')
        whitespace()
        if (take(']')) return
        while (true) {
            value()
            whitespace()
            if (take(']')) return
            expect(',')
        }
    }

    private fun stringValue(): String {
        expect('"')
        val result = StringBuilder()
        while (index < input.length) {
            val character = input[index++]
            when (character) {
                '"' -> return result.toString()
                '\\' -> {
                    if (index >= input.length) fail()
                    when (val escaped = input[index++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            if (index + 4 > input.length) fail()
                            val code = input.substring(index, index + 4).toIntOrNull(16) ?: fail()
                            result.append(code.toChar())
                            index += 4
                        }
                        else -> fail()
                    }
                }
                else -> {
                    if (character.code < 0x20) fail()
                    result.append(character)
                }
            }
        }
        fail()
    }

    private fun numberValue() {
        while (index < input.length && input[index] !in charArrayOf(',', ']', '}', ' ', '\t', '\r', '\n')) index++
    }

    private fun literal(expected: String) {
        if (!input.startsWith(expected, index)) fail()
        index += expected.length
    }

    private fun whitespace() {
        while (index < input.length && input[index] in charArrayOf(' ', '\t', '\r', '\n')) index++
    }

    private fun peek(): Char = input.getOrNull(index) ?: fail()

    private fun take(expected: Char): Boolean {
        if (input.getOrNull(index) != expected) return false
        index++
        return true
    }

    private fun expect(expected: Char) {
        if (!take(expected)) fail()
    }

    private fun fail(): Nothing = throw SnapshotAuthenticityException(errorCode)
}

private fun canonicalPayload(root: JsonObject): ByteArray {
    if ("integrity" !in root) throw SnapshotAuthenticityException("integrity_missing")
    val output = StringBuilder(root.size * 32)
    appendCanonical(JsonObject(root.filterKeys { it != "integrity" }), output)
    return output.toString().encodeToByteArray()
}

private fun appendCanonical(element: JsonElement, output: StringBuilder) {
    when (element) {
        is JsonObject -> {
            output.append('{')
            element.keys.sorted().forEachIndexed { index, key ->
                if (index > 0) output.append(',')
                appendCanonicalString(key, output)
                output.append(':')
                appendCanonical(element.getValue(key), output)
            }
            output.append('}')
        }
        is JsonArray -> {
            output.append('[')
            element.forEachIndexed { index, child ->
                if (index > 0) output.append(',')
                appendCanonical(child, output)
            }
            output.append(']')
        }
        JsonNull -> output.append("null")
        is JsonPrimitive -> {
            if (element.isString) appendCanonicalString(element.content, output) else output.append(element.content)
        }
    }
}

private fun appendCanonicalString(value: String, output: StringBuilder) {
    output.append('"')
    value.forEach { character ->
        when (character) {
            '"', '\\' -> output.append('\\').append(character)
            '\b' -> output.append("\\b")
            '\u000C' -> output.append("\\f")
            '\n' -> output.append("\\n")
            '\r' -> output.append("\\r")
            '\t' -> output.append("\\t")
            else -> if (character.code < 0x20) {
                output.append("\\u00")
                    .append(HEX[character.code shr 4])
                    .append(HEX[character.code and 0x0f])
            } else {
                output.append(character)
            }
        }
    }
    output.append('"')
}

private fun ByteArray.toHex(): String = joinToString("") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}

private const val HEX = "0123456789abcdef"
