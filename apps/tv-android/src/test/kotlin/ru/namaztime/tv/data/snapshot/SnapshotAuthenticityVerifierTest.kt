package ru.namaztime.tv.data.snapshot

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
	private val trustBundle = File("../../fixtures/verification/phase1-trust-bundle.json").readBytes()

    @Test
    fun `Go publication fixture verifies and decodes on Android`() {
		val verified = SnapshotAuthenticityVerifier(trustBundle).verifyAndDecode(fixture)

        assertEquals("synthetic-android-verification-v1", verified.payload.snapshotId)
        assertEquals(keyId, verified.payload.integrity.signingKeyId)
    }

	@Test
	fun `trust lifecycle rejects scheduled and revoked keys but permits historical retired snapshot`() {
		val active = trustBundle.decodeToString()
		val retired = active
			.replace("\"status\": \"active\"", "\"status\": \"retired\"")
			.replace("\"generated_at\": \"2026-08-20T00:00:00Z\"", "\"generated_at\": \"2026-08-21T00:00:00Z\"")
		assertEquals(
			"synthetic-android-verification-v1",
			SnapshotAuthenticityVerifier(retired.encodeToByteArray()).verifyAndDecode(fixture).payload.snapshotId,
		)
		val scheduled = active.replace("\"status\": \"active\"", "\"status\": \"scheduled\"")
		assertEquals(
			"signing_key_not_verifiable",
			assertThrows(SnapshotAuthenticityException::class.java) {
				SnapshotAuthenticityVerifier(scheduled.encodeToByteArray()).verifyAndDecode(fixture)
			}.code,
		)
		val revoked = active
			.replace("\"status\": \"active\"", "\"status\": \"revoked\"")
			.replace("\n      \"not_before\"", "\n      \"revoked_at\": \"2026-08-20T00:00:00Z\",\n      \"revocation_reason\": \"test compromise\",\n      \"not_before\"")
		assertEquals(
			"signing_key_revoked",
			assertThrows(SnapshotAuthenticityException::class.java) {
				SnapshotAuthenticityVerifier(revoked.encodeToByteArray()).verifyAndDecode(fixture)
			}.code,
		)
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
	fun `duplicate JSON members fail before canonical verification or trust parsing`() {
		val duplicateSnapshot = fixture.decodeToString().replaceFirst(
			"\"snapshot_id\": \"synthetic-android-verification-v1\"",
			"\"snapshot_id\": \"synthetic-android-verification-v1\", \"snapshot_id\": \"other\"",
		).encodeToByteArray()
		assertEquals(
			"duplicate_json_member",
			assertThrows(SnapshotAuthenticityException::class.java) { verifier().verifyAndDecode(duplicateSnapshot) }.code,
		)
		val duplicateTrust = trustBundle.decodeToString().replaceFirst(
			"\"environment\": \"test\"",
			"\"environment\": \"test\", \"environment\": \"production\"",
		).encodeToByteArray()
		assertEquals(
			"duplicate_json_member",
			assertThrows(SnapshotAuthenticityException::class.java) { SnapshotAuthenticityVerifier(duplicateTrust) }.code,
		)
	}

	@Test
	fun `production trust requires a pinned monotonic revision`() {
		assertEquals(
			"trust_bundle_rollback",
			assertThrows(SnapshotAuthenticityException::class.java) {
				SnapshotAuthenticityVerifier(trustBundle, minimumTrustBundleRevision = 2)
			}.code,
		)
		val production = trustBundle.decodeToString()
			.replace("\"environment\": \"test\"", "\"environment\": \"production\"")
			.encodeToByteArray()
		assertEquals(
			"production_trust_revision_required",
			assertThrows(SnapshotAuthenticityException::class.java) {
				SnapshotAuthenticityVerifier(production)
			}.code,
		)
		assertEquals(
			"trust_environment_separation_required",
			assertThrows(SnapshotAuthenticityException::class.java) {
				SnapshotAuthenticityVerifier(production, minimumTrustBundleRevision = 1)
			}.code,
		)
		val stringRevision = trustBundle.decodeToString()
			.replace("\"revision\": 1", "\"revision\": \"1\"")
			.encodeToByteArray()
		assertEquals(
			"trust_revision_invalid",
			assertThrows(SnapshotAuthenticityException::class.java) {
				SnapshotAuthenticityVerifier(stringRevision)
			}.code,
		)
	}

    @Test
    fun `trust bundle transition rejects missing predecessor and revoked key resurrection`() {
        val productionRevisionOne = trustBundle.decodeToString()
            .replace("\"environment\": \"test\"", "\"environment\": \"production\"")
        val productionRevisionTwo = productionRevisionOne
            .replace("\"revision\": 1", "\"revision\": 2")
            .encodeToByteArray()
        assertEquals(
            "trust_transition_required",
            assertThrows(SnapshotAuthenticityException::class.java) {
                SnapshotAuthenticityVerifier(productionRevisionTwo, minimumTrustBundleRevision = 2)
            }.code,
        )
        SnapshotAuthenticityVerifier(
            productionRevisionTwo,
            minimumTrustBundleRevision = 2,
            previousTrustBundle = productionRevisionOne.encodeToByteArray(),
            environmentTrustBundles = environmentTrustBundles(),
        )

        val revokedRevisionOne = productionRevisionOne
            .replace("\"status\": \"active\"", "\"status\": \"revoked\"")
            .replace(
                "\n      \"not_before\"",
                "\n      \"revoked_at\": \"2026-08-20T00:00:00Z\",\n      \"revocation_reason\": \"test compromise\",\n      \"not_before\"",
            )
            .encodeToByteArray()
        assertEquals(
            "trust_transition_invalid",
            assertThrows(SnapshotAuthenticityException::class.java) {
                SnapshotAuthenticityVerifier(
                    productionRevisionTwo,
                    minimumTrustBundleRevision = 2,
                    previousTrustBundle = revokedRevisionOne,
                )
            }.code,
        )
        val newKey = Base64.getEncoder().encodeToString(ByteArray(32) { 0x53.toByte() })
        val directActiveRevisionTwo = productionRevisionTwo.decodeToString()
            .replace(
                "\n  ]",
                ",\n    {\"key_id\":\"prod-new-key\",\"algorithm\":\"ed25519\",\"public_key_ed25519_base64\":\"$newKey\",\"status\":\"active\",\"not_before\":\"2026-08-20T00:00:00Z\"}\n  ]",
            )
            .encodeToByteArray()
        assertEquals(
            "trust_transition_invalid",
            assertThrows(SnapshotAuthenticityException::class.java) {
                SnapshotAuthenticityVerifier(
                    directActiveRevisionTwo,
                    minimumTrustBundleRevision = 2,
                    previousTrustBundle = productionRevisionOne.encodeToByteArray(),
                    environmentTrustBundles = environmentTrustBundles(),
                )
            }.code,
        )
    }

    @Test
    fun `trust timestamps use canonical whole UTC seconds on both platforms`() {
        val fractional = trustBundle.decodeToString()
            .replace("2026-08-20T00:00:00Z", "2026-08-20T00:00:00.100Z")
            .encodeToByteArray()
        assertEquals(
            "trust_generated_at_invalid",
            assertThrows(SnapshotAuthenticityException::class.java) {
                SnapshotTrustPolicy.decode(fractional)
            }.code,
        )
    }

    @Test
    fun `revoked trust key is not eligible for persisted production selection`() {
        val revoked = trustBundle.decodeToString()
            .replace("\"environment\": \"test\"", "\"environment\": \"production\"")
            .replace("\"status\": \"active\"", "\"status\": \"revoked\"")
            .replace(
                "\n      \"not_before\"",
                "\n      \"revoked_at\": \"2026-08-20T00:00:00Z\",\n      \"revocation_reason\": \"test compromise\",\n      \"not_before\"",
            )
            .encodeToByteArray()
        val selectionTrust = SnapshotAuthenticityVerifier(
            revoked,
            minimumTrustBundleRevision = 1,
            environmentTrustBundles = environmentTrustBundles(),
        ).selectionTrust()

        assertEquals(false, selectionTrust.allows(keyId, java.time.Instant.parse("2026-08-20T00:00:00Z")))
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

    private fun environmentTrustBundles(): List<ByteArray> = listOf(
        environmentBundle("test", "test-comparison-key", 0x51),
        environmentBundle("staging", "staging-comparison-key", 0x52),
    )

    private fun environmentBundle(environment: String, comparisonKeyId: String, fill: Int): ByteArray {
        val encoded = Base64.getEncoder().encodeToString(ByteArray(32) { fill.toByte() })
        return """{"schema_version":"1.0","revision":1,"environment":"$environment","generated_at":"2026-08-20T00:00:00Z","keys":[{"key_id":"$comparisonKeyId","algorithm":"ed25519","public_key_ed25519_base64":"$encoded","status":"active","not_before":"2026-08-20T00:00:00Z"}]}"""
            .encodeToByteArray()
    }
}
