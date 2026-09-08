package ru.namaztime.tv.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.data.snapshot.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class RetainedPilotLocalSetupPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val snapshotBytes = context.assets.open(PILOT_LOCAL_SNAPSHOT_ASSET).use { it.readBytes() }
    private val snapshot = SnapshotActivationGate.authenticated(snapshotBytes, PilotLocalSnapshotTrust.verifier(context)).payload
    private val repository = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "fixtures/pilot/ulyanovsk-2026/registry-policy-bindings.json").isFile }
    private val bindings = parseBundleJson(File(repository, "fixtures/pilot/ulyanovsk-2026/registry-policy-bindings.json").readBytes())
    private val policy = LocalSetupPolicy(
        "policy-ulyanovsk-second-cathedral-2026",
        bindings.getValue("authorities").jsonArray.joinToString(" / ") { it.jsonObject.bundleString("name") },
        bindings.getValue("policies").jsonArray.single().jsonObject,
        bindings.getValue("scopes").jsonArray.single().jsonObject,
        bindings.getValue("authorities").jsonArray,
        bindings.getValue("sources").jsonArray.first().jsonObject,
        null,
        bindings.getValue("timetables").jsonArray.single().jsonObject,
        bindings.getValue("source_overrides").jsonArray,
        LocalSetupSnapshotReference(snapshot.snapshotId, "snapshots/$PILOT_LOCAL_SNAPSHOT_SHA256.json", PILOT_LOCAL_SNAPSHOT_SHA256,
            snapshotBytes.size.toLong(), snapshot.mosque),
    )

    @Test
    fun exactLegacyCompositeKeepsOriginalAuthorityProvenanceAndIsNotPromotedToPublicScope() {
        assertEquals(PILOT_LOCAL_SNAPSHOT_SHA256, localSetupHash(snapshotBytes))
        policy.validateSnapshot(snapshot)
        assertNull(snapshot.source.qualification)
        assertEquals("Ульяновск, ул. Дзержинского, 18А", snapshot.mosque.locality)
        assertTrue(runCatching { policy.copy(snapshot = policy.snapshot.copy(sha256 = "0".repeat(64))).validateSnapshot(snapshot) }.isFailure)
        assertTrue(runCatching {
            policy.copy(scope = JsonObject(policy.scope + ("city_id" to JsonPrimitive("city-neighbor")))).validateSnapshot(snapshot)
        }.isFailure)
    }

    @Test
    fun augustOverrideProvenanceRemainsValidForJanuaryAndSeptemberOfSignedAnnualComposite() {
        for (date in listOf("2026-01-15", "2026-08-30", "2026-09-08")) {
            val city = CanonicalCityCandidate(CITY_ID, "Ульяновск", listOf("Ulyanovsk"), "RU-ULY", "Ульяновская область",
                "PPLA", "Europe/Ulyanovsk", 54.3, 48.4, "geonames:479123", "2026-09-08", "CC BY 4.0")
            val binding = LocalSetupBinding(CITY_ID, "schedule-choice-" + "1".repeat(64), policy.policyId,
                "Ульяновск (${snapshot.mosque.name})", "exact_city_timetable", snapshot.coverage)
            val response = buildJsonObject {
                put("schema_version", "device-city-schedule-choices/v1")
                put("revision", buildJsonObject {
                    put("id", "registry-pilot-test"); put("schema_version", 1); put("catalog_revision_id", "catalog-pilot-test")
                    put("content_sha256", "a".repeat(64)); put("created_at", "2026-09-08T00:00:00Z")
                    put("created_by", "synthetic-test"); put("reason", "Retained unchanged pilot test")
                })
                put("revision_state", "active"); put("status", "available")
                put("automatic_resolution_status", "resolved"); put("automatic_resolution_reason", "resolved")
                put("selection_required", false); put("date", date); put("city", city.candidateJson())
                put("choices", JsonArray(listOf(policy.choiceJson(binding)))); put("allowed_actions", JsonArray(emptyList()))
            }
            val result = decodeDeviceScheduleChoiceSet(response.toString().encodeToByteArray(), CITY_ID, LocalDate.parse(date))
            assertNotNull("annual composite rejected on $date", result)
            assertEquals(snapshot.source.approval!!.approvalId, result!!.choices.single().approvalId)
        }
    }

    companion object { const val CITY_ID = "city-4adcfc15932f3850d5dd5dbaa17e3a4c" }
}
