package ru.namaztime.tv.data.snapshot

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QualifiedSnapshotAuthenticityTest {
    @Test
    fun goSignedQualifiedFixtureHasIdenticalCanonicalProofAndOnsetHashes() {
        val loader = requireNotNull(javaClass.classLoader)
        val bytes = requireNotNull(loader.getResourceAsStream("qualified-interop/snapshot.json")).use { it.readBytes() }
        val key = Json.parseToJsonElement(requireNotNull(loader.getResourceAsStream("qualified-interop/public-test-key.json"))
            .use { it.readBytes().decodeToString() }).jsonObject
        val verifier = QualifiedSnapshotSigner().verifierForFixture(key.getValue("key_id").jsonPrimitive.content,
            Base64.getDecoder().decode(key.getValue("public_key_base64").jsonPrimitive.content))
        val result = verifier.verifyAndDecode(bytes).payload
        assertEquals("2.0", result.schemaVersion)
        assertEquals("f4d52e1e0a7292355caa53f8574acd9c639bf0cf280d1143650caa83075f8f61", result.source.qualification?.sha256)
        assertEquals("640f2bc2d82a3bd1dbabb35676afc07943b3c119ca26f4bff09729efdf02d9f0", result.source.qualification?.onsetSha256)
        assertEquals(listOf("synthetic"), result.prayerDays.single().flags)
        assertEquals(null, result.source.approval)
    }

    @Test
    fun correctlySignedQualificationUsesProductionTrustWithoutExternalApprover() {
        val signer = QualifiedSnapshotSigner()
        val result = signer.verifier().verifyAndDecode(signer.sign()).payload
        assertEquals("production", result.dataClassification)
        assertEquals("qualified", result.source.qualification?.state)
        assertEquals(null, result.source.approval)
    }

    @Test
    fun validSignatureDoesNotExcuseStaleProofOrChangedUnsampledRows() {
        val signer = QualifiedSnapshotSigner()
        val good = qualifiedSnapshotDocument()
        val invalid = listOf(
            good.changeDay(7) { put("fajr", JsonPrimitive("04:01")) },
            good.mutateQualification(rehash = false) { put("candidate_id", JsonPrimitive("changed-candidate")) },
            good.mutateQualification { put("fresh_through", JsonPrimitive("2026-09-07")) },
            good.mutateObject("source") { put("authority_name", JsonPrimitive("Another authority")) },
        )
        invalid.forEach { document ->
            assertThrows(SnapshotValidationException::class.java) { signer.verifier().verifyAndDecode(signer.sign(document)) }
        }
    }

    @Test
    fun qualifiedSnapshotsRetainRetiredAndRevokedKeySemantics() {
        val signer = QualifiedSnapshotSigner()
        val signed = signer.sign()
        assertEquals("2.0", signer.verifier("retired").verifyAndDecode(signed).payload.schemaVersion)
        assertEquals("signing_key_revoked", assertThrows(SnapshotAuthenticityException::class.java) {
            signer.verifier("revoked").verifyAndDecode(signed)
        }.code)
        assertThrows(SnapshotAuthenticityException::class.java) { signer.verifier("scheduled").verifyAndDecode(signed) }
    }
}
