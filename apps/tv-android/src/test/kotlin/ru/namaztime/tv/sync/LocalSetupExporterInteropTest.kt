package ru.namaztime.tv.sync

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opt-in Go→Android protocol check; no generated bundle or real rows in Git. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class LocalSetupExporterInteropTest {
    @Test
    fun authenticGoExporterPreservesEveryScopedDateIntervalAndIndependentAuthority() {
        val configured = System.getenv("NAMAZTIME_ANDROID_LOCAL_SETUP_INTEROP")
        assumeTrue("Generate the synthetic Go exporter fixture outside Git to run interop", configured != null)
        val parent = File(configured!!)
        val fixture = parseBundleJson(File(parent, "synthetic-fixture.json").readBytes())
        assertEquals("synthetic_protocol_test_only", fixture.bundleString("fixture_kind"))
        val anchors = fixture.getValue("anchors").jsonObject
        val anchored = mapOf("trust/production.json" to anchors.getValue("Production").jsonPrimitive.content,
            "trust/previous-production.json" to anchors.getValue("Previous").jsonPrimitive.content,
            "trust/test.json" to anchors.getValue("Test").jsonPrimitive.content,
            "trust/staging.json" to anchors.getValue("Staging").jsonPrimitive.content)
        val directory = File(parent, "bundle")
        val source = object : LocalSetupBundleFiles {
            override fun paths(): Set<String> = directory.walkTopDown().filter { it.isFile }.map { it.relativeTo(directory).invariantSeparatorsPath }.toSet()
            override fun open(path: String) = File(directory, path).inputStream()
        }
        LocalSetupBundleLoader(source, Files.createTempDirectory("local-setup-interop-").toFile(), anchored).load().use { bundle ->
            assertEquals(fixture.bundleString("bundle_id"), bundle.id)
            val first = bundle.choiceSet("city-a", LocalDate.parse("2026-09-08"))
            assertEquals(2, first.choices.size)
            assertTrue(first.choices.all { it.tier == "regional_official_timetable" })
            val specific = bundle.choiceSet("city-a", LocalDate.parse("2026-09-09"))
            assertEquals(setOf("exact_city_timetable", "regional_official_timetable"), specific.choices.map { it.tier }.toSet())
            assertEquals(2, bundle.choiceSet("city-b", LocalDate.parse("2026-09-09")).choices.size)
            assertTrue(bundle.choiceSet("city-c", LocalDate.parse("2026-09-09")).choices.isEmpty())
            assertTrue(bundle.choiceSet("city-a", LocalDate.parse("2026-09-11")).choices.isEmpty())
            assertEquals(setOf("city-a", "city-b"), bundle.searchCities(first.city.canonicalName).map { it.id }.toSet())
            assertTrue(specific.choices.all { it.approvalId == null && it.qualification != null })
        }
    }
}
