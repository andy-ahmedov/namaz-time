package ru.namaztime.tv.sync

import java.nio.file.Files
import java.time.LocalDate
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class LocalSetupBundleTest {
    @Test
    fun rehashedCatalogSchemaDriftCannotSilentlyChangeTypesKeysOrQueryIndex() {
        val mutations: List<(String) -> String> = listOf(
            { it.replace("latitude REAL", "latitude TEXT") },
            { it.replace("name TEXT NOT NULL", "name TEXT") },
            { it.replace(" REFERENCES regions(id)", "") },
            { it.replace(" REFERENCES cities(id)", "") },
            { it.replace("ON cities(region_id,name,id)", "ON cities(name,region_id,id)") },
        )
        mutations.forEachIndexed { variant, transform ->
            val directory = Files.createTempDirectory("local-setup-schema-").toFile()
            val fixture = LocalSetupBundleFixture(directory, transform)
            assertTrue("schema mutation $variant accepted", runCatching {
                LocalSetupBundleLoader(fixture.source(), directory, fixture.anchors).load().close()
            }.isFailure)
        }
    }

    @Test
    fun presentNullQualificationIsNotAHiddenLegacyBranchAndRehashedPolicyMutationsStayRejected() {
        for (variant in 0..7) {
            val directory = Files.createTempDirectory("local-setup-proof-").toFile()
            val fixture = LocalSetupBundleFixture(directory)
            val choices = parseBundleJson(fixture.files.getValue("choices.json"))
            val policies = choices.getValue("policies").jsonArray.toMutableList()
            val original = policies[0].jsonObject
            val changed = original.toMutableMap()
            when (variant) {
                0 -> changed["qualification"] = JsonNull
                1 -> changed["policy"] = JsonObject(original.getValue("policy").jsonObject + ("approval_id" to JsonPrimitive("invented-human")))
                2 -> changed["policy"] = JsonObject(original.getValue("policy").jsonObject + ("mosque_ids" to JsonArray(listOf(JsonPrimitive("invented-mosque")))))
                3 -> changed["source"] = JsonObject(original.getValue("source").jsonObject + ("status" to JsonPrimitive("approved")))
                4 -> changed["qualification"] = JsonObject(original.getValue("qualification").jsonObject + ("sha256" to JsonPrimitive("0".repeat(64))))
                5 -> changed["snapshot"] = JsonObject(original.getValue("snapshot").jsonObject + ("display_context" to JsonObject(
                    original.getValue("snapshot").jsonObject.getValue("display_context").jsonObject + ("name" to JsonPrimitive("Not the signed locality")),
                )))
                6 -> changed["unknown_field"] = JsonPrimitive(true)
                7 -> changed["policy"] = JsonObject(original.getValue("policy").jsonObject + ("source_id" to JsonPrimitive("different-source")))
            }
            policies[0] = JsonObject(changed)
            fixture.files["choices.json"] = JsonObject(choices + ("policies" to JsonArray(policies))).toString().encodeToByteArray()
            fixture.rehashManifest()
            assertTrue("rehashing admitted malformed variant $variant", runCatching {
                LocalSetupBundleLoader(fixture.source(), directory, fixture.anchors).load().close()
            }.isFailure)
        }
    }

    @Test
    fun publicNullMosqueBindingsAreLosslessButOverlappingDateBindingsAreNotAccepted() {
        val directory = Files.createTempDirectory("local-setup-null-bindings-").toFile()
        val fixture = LocalSetupBundleFixture(directory)
        val choices = parseBundleJson(fixture.files.getValue("choices.json"))
        val changed = JsonObject(choices + ("policies" to JsonArray(choices.getValue("policies").jsonArray.map { item ->
            JsonObject(item.jsonObject + ("policy" to JsonObject(item.jsonObject.getValue("policy").jsonObject + ("mosque_ids" to JsonNull))))
        })))
        fixture.files["choices.json"] = changed.toString().encodeToByteArray()
        fixture.rehashManifest()
        LocalSetupBundleLoader(fixture.source(), directory, fixture.anchors).load().close()
        val bindings = changed.getValue("bindings").jsonArray
        fixture.files["choices.json"] = JsonObject(changed + ("bindings" to JsonArray(listOf(bindings[0]) + bindings))).toString().encodeToByteArray()
        fixture.rehashManifest()
        assertTrue(runCatching { LocalSetupBundleLoader(fixture.source(), directory, fixture.anchors).load().close() }.isFailure)
    }

    @Test
    fun cachedIndexHashIsRecheckedOnReopenAndCannotBeSilentlyReplaced() {
        val directory = Files.createTempDirectory("local-setup-cache-").toFile()
        val fixture = LocalSetupBundleFixture(directory)
        LocalSetupBundleLoader(fixture.source(), directory, fixture.anchors).load().close()
        val cached = directory.listFiles()!!.single { it.name.startsWith("catalog-") && it.name.endsWith(".sqlite") }
        cached.appendBytes(byteArrayOf(1))
        assertTrue(runCatching { LocalSetupBundleLoader(fixture.source(), directory, fixture.anchors).load().close() }.isFailure)
    }

    @Test
    fun fullCatalogExactAliasesAndHomonymsRemainIndependentAndUnavailableCitiesStaySearchable() {
        val directory = Files.createTempDirectory("local-setup-fixture-").toFile()
        val fixture = LocalSetupBundleFixture(directory)
        LocalSetupBundleLoader(fixture.source(), directory, fixture.anchors).load().use { bundle ->
            assertEquals(listOf(fixture.cityId, "city-same-name"), bundle.searchCities("  SYNTHETIC\u00a0CITY  ").map { it.id })
            assertEquals(fixture.cityId, bundle.searchCities("ТЕСТОВЫЙ\u0085ГОРОД").single().id)
            assertEquals("city-unavailable", bundle.searchCities("Unavailable Alias").single().id)
            assertTrue(bundle.searchCities("Synthetic").isEmpty())
            assertTrue(bundle.searchCities("' OR 1=1 --").isEmpty())
            assertTrue(bundle.choiceSet("city-unavailable", DATE).choices.isEmpty())
            val choices = bundle.choiceSet(fixture.cityId, DATE)
            assertEquals(2, choices.choices.size)
            assertTrue(choices.selectionRequired)
            assertTrue(choices.choices.all { it.qualification != null && it.approvalId == null })
        }
    }

    @Test
    fun unknownMembersMissingFilesWrongHashAndUnanchoredTrustNeverBecomeAUsableBundle() {
        for (variant in 0..4) {
            val directory = Files.createTempDirectory("local-setup-invalid-").toFile()
            val fixture = LocalSetupBundleFixture(directory)
            when (variant) {
                0 -> fixture.files["extra.json"] = byteArrayOf(1)
                1 -> fixture.files.remove(fixture.files.keys.first { it.startsWith("snapshots/") })
                2 -> fixture.files["choices.json"] = "{}".encodeToByteArray()
                3 -> {
                    fixture.files["trust/production.json"] = fixture.files.getValue("trust/production.json") + byteArrayOf(32)
                    fixture.rehashManifest()
                }
                4 -> {
                    fixture.files["../escape"] = byteArrayOf(1)
                    fixture.rehashManifest()
                }
            }
            assertTrue("invalid variant $variant accepted", runCatching {
                LocalSetupBundleLoader(fixture.source(), directory, fixture.anchors).load().close()
            }.isFailure)
        }
    }

    @Test
    fun datesOutsideExplicitBindingHaveNoFallbackAndSnapshotValuesRemainAuthentic() {
        val directory = Files.createTempDirectory("local-setup-date-").toFile()
        val fixture = LocalSetupBundleFixture(directory)
        LocalSetupBundleLoader(fixture.source(), directory, fixture.anchors).load().use { bundle ->
            assertTrue(bundle.choiceSet(fixture.cityId, LocalDate.parse("2026-10-01")).choices.isEmpty())
            val snapshot = bundle.snapshotFor(fixture.cityId, fixture.choiceId(0), DATE).payload
            assertEquals("04:00", snapshot.prayerDays.first().fajr)
            assertTrue(snapshot.iqamahRules.isEmpty())
            assertTrue(snapshot.jumuahSessions.isEmpty())
            assertEquals("production", snapshot.dataClassification)
        }
    }

    @Test
    fun normalizerUsesSimpleUnicodeLowerAndOnlyGoWhitespace() {
        assertEquals("i σ σ", normalizeLocalSetupQuery("İ Σ Σ"))
        assertEquals("ёж е", normalizeLocalSetupQuery(" \u2003ЁЖ\u0085Е\u00a0"))
        assertEquals("а\u200bб", normalizeLocalSetupQuery("А\u200bБ"))
        assertEquals("", normalizeLocalSetupQuery("\t\n\u202f\u3000"))
    }

    companion object { val DATE: LocalDate = LocalDate.parse("2026-09-08") }
}
