package com.example.namaztime.tv.data.snapshot

import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import java.security.MessageDigest
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

class SnapshotAuthenticityVerifier(
    trustedPublicKeys: Map<String, ByteArray>,
) {
    private val trustedPublicKeys = trustedPublicKeys.mapValues { (_, key) -> key.copyOf() }

    fun verifyAndDecode(bytes: ByteArray): VerifiedSnapshot {
        if (bytes.size > MAX_SIGNED_SNAPSHOT_BYTES) {
            throw SnapshotAuthenticityException("snapshot_too_large")
        }
        val root = try {
            Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)).jsonObject
        } catch (_: Exception) {
            throw SnapshotAuthenticityException("invalid_json")
        }
        val integrity = try {
            root.getValue("integrity").jsonObject
        } catch (_: Exception) {
            throw SnapshotAuthenticityException("integrity_missing")
        }
        val keyId = integrity.string("signing_key_id")
        val expectedHash = integrity.string("canonical_sha256")
        val signature = try {
            Base64.getDecoder().decode(integrity.string("signature_ed25519_base64"))
        } catch (_: IllegalArgumentException) {
            throw SnapshotAuthenticityException("signature_encoding_invalid")
        }
        val publicKey = trustedPublicKeys[keyId]
            ?: throw SnapshotAuthenticityException("unknown_signing_key")
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

    private fun JsonObject.string(name: String): String = try {
        getValue(name).jsonPrimitive.content
    } catch (_: Exception) {
        throw SnapshotAuthenticityException("integrity_invalid")
    }
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
