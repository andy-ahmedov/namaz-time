package com.example.namaztime.tv.data.snapshot

import java.io.File
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SnapshotAuthenticityVerifierTest {
    private val fixture = File(
        "../../fixtures/verification/synthetic-signed-snapshot.json",
    ).readBytes()
    private val keyDocument = Json.parseToJsonElement(
        File("../../fixtures/verification/phase1-public-key.json").readText(),
    ).jsonObject
    private val keyId = keyDocument.getValue("signing_key_id").jsonPrimitive.content
    private val publicKey = Base64.getDecoder().decode(
        keyDocument.getValue("public_key_ed25519_base64").jsonPrimitive.content,
    )

    @Test
    fun `Go publication fixture verifies and decodes on Android`() {
        val verified = verifier().verifyAndDecode(fixture)

        assertEquals("synthetic-android-verification-v1", verified.payload.snapshotId)
        assertEquals(keyId, verified.payload.integrity.signingKeyId)
    }

    @Test
    fun `whitespace and object member order do not change canonical verification`() {
        val root = Json.parseToJsonElement(fixture.decodeToString()).jsonObject
        val reordered = JsonObject(root.entries.reversed().associate { it.key to it.value })
            .toString()
            .encodeToByteArray()

        assertEquals(
            "synthetic-android-verification-v1",
            verifier().verifyAndDecode(reordered).payload.snapshotId,
        )
    }

    @Test
    fun `tampered payload unknown key and invalid signature fail closed`() {
        val cases = listOf(
            fixture.decodeToString().replaceFirst("03:00", "03:01").encodeToByteArray() to
                "canonical_hash_mismatch",
            fixture.decodeToString().replaceFirst(keyId, "unknown-test-key").encodeToByteArray() to
                "unknown_signing_key",
            fixture.decodeToString().replaceFirst(
                "S7Esv+f17JKgZLK+CmKTWUYUV6x+cfvrVu9UiAZhBpOBsF6O2EQf5JGsEshVo5n8iPUXiftrdptQH+veFB5XAA==",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
            ).encodeToByteArray() to "signature_invalid",
        )

        cases.forEach { (bytes, code) ->
            val error = assertThrows(SnapshotAuthenticityException::class.java) {
                verifier().verifyAndDecode(bytes)
            }
            assertEquals(code, error.code)
        }
    }

    @Test
    fun `malformed UTF-8 fails closed before verification`() {
        val malformed = fixture.copyOf()
        malformed[malformed.indexOf('s'.code.toByte())] = 0xff.toByte()

        val error = assertThrows(SnapshotAuthenticityException::class.java) {
            verifier().verifyAndDecode(malformed)
        }

        assertEquals("invalid_json", error.code)
    }

    @Test
    fun `authenticated activation input carries verifier evidence`() {
        val activatable = SnapshotActivationGate.authenticated(fixture, verifier())

        assertEquals("synthetic-android-verification-v1", activatable.payload.snapshotId)
        assertEquals(SnapshotAuthenticity.AUTHENTICATED, activatable.authenticity)
    }

    @Test
    fun `oversized snapshot fails before parsing`() {
        val error = assertThrows(SnapshotAuthenticityException::class.java) {
            verifier().verifyAndDecode(ByteArray(5 * 1024 * 1024 + 1))
        }

        assertEquals("snapshot_too_large", error.code)
    }

    @Test
    fun `trusted public keys are copied at the trust boundary`() {
        val mutableKey = publicKey.copyOf()
        val verifier = SnapshotAuthenticityVerifier(mapOf(keyId to mutableKey))
        mutableKey.fill(0)

        assertEquals(
            "synthetic-android-verification-v1",
            verifier.verifyAndDecode(fixture).payload.snapshotId,
        )
    }

    private fun verifier() = SnapshotAuthenticityVerifier(mapOf(keyId to publicKey))
}
