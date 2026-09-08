package ru.namaztime.tv.sync

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.data.snapshot.PILOT_LOCAL_SNAPSHOT_ID

/** Local acceptance only: real operational artifacts stay outside Git. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class LocalSetupProductionBundleTest {
    @Test
    fun actualCompleteCatalogAndAnchoredSignedChoicesLoadWithoutAnyTestTrustOverride() {
        val configured = System.getenv("NAMAZTIME_ANDROID_PRODUCTION_SETUP_BUNDLE")
        assumeTrue("Supply an externally exported production-anchored local bundle", configured != null)
        val directory = File(configured!!)
        val source = object : LocalSetupBundleFiles {
            override fun paths(): Set<String> = directory.walkTopDown().filter { it.isFile }.map { it.relativeTo(directory).invariantSeparatorsPath }.toSet()
            override fun open(path: String) = File(directory, path).inputStream()
        }
        LocalSetupBundleLoader(source, Files.createTempDirectory("local-production-setup-").toFile()).load().use { bundle ->
            val ulyanovsk = bundle.searchCities("Ульяновск").single { it.geographicSourceId == "geonames:479123" }
            val ulySet = bundle.choiceSet(ulyanovsk.id, DATE)
            assertEquals(
                "The current bundle must preserve the exact legacy mosque AND the public CDUM choice",
                setOf(PILOT_LOCAL_SNAPSHOT_ID, "cdum-ulyanovsk-2026-t049-v1"),
                ulySet.choices.map { it.publishedSnapshotId }.toSet(),
            )
            assertTrue(ulySet.selectionRequired)
            val legacy = ulySet.choices.single { it.publishedSnapshotId == PILOT_LOCAL_SNAPSHOT_ID }
            assertNull(legacy.qualification)
            assertEquals("approval-second-cathedral-mosque-ulyanovsk-2026-002", legacy.approvalId)
            val original = bundle.snapshotFor(ulyanovsk.id, legacy.id, DATE).payload
            assertEquals(PILOT_LOCAL_SNAPSHOT_ID, original.snapshotId)
            assertEquals("1.0", original.schemaVersion)
            assertEquals("second-cathedral-mosque-ulyanovsk", original.mosque.id)
            assertEquals(365, original.prayerDays.size)
            assertEquals(5, original.iqamahRules.size)
            assertEquals(1, original.jumuahSessions.size)
            assertEquals(ORIGINAL_ULY_SHA, source.open("snapshots/$ORIGINAL_ULY_SHA.json").use { localSetupHash(it.readBytes()) })
            val cdumUly = ulySet.choices.single { it.source.id == "cdum-ulyanovsk-2026" }
            assertQualifiedSnapshot(bundle, ulyanovsk, cdumUly)
            val nalchikMatches = bundle.searchCities("Нальчик")
            assertEquals(3, nalchikMatches.size)
            assertTrue(nalchikMatches.all { it.federalSubjectCode == "RU-KB" })
            val nalchik = nalchikMatches.single { it.geographicSourceId == "geonames:523523" }
            val public = bundle.choiceSet(nalchik.id, DATE).choices
            assertTrue(public.isNotEmpty())
            assertTrue(public.all { it.qualification != null && it.approvalId == null })
            public.forEach { choice ->
                assertQualifiedSnapshot(bundle, nalchik, choice)
            }
            assertTrue(bundle.searchCities("Москва").isNotEmpty())
            assertTrue(bundle.searchCities("Saint Petersburg").isNotEmpty())
            val kazanMatches = bundle.searchCities("Казань")
            val kazan = kazanMatches.single { it.geographicSourceId == "geonames:551487" }
            val kazanChoices = bundle.choiceSet(kazan.id, DATE)
            assertEquals(setOf("cdum-kazan-2026", "ru-ta-dumrt-kazan-2026"), kazanChoices.choices.map { it.source.id }.toSet())
            assertTrue(kazanChoices.selectionRequired)
            assertEquals(2, kazanChoices.choices.flatMap { it.authorities.map { authority -> authority.id } }.toSet().size)
            kazanChoices.choices.forEach { assertQualifiedSnapshot(bundle, kazan, it) }
            val unrelatedKazan = kazanMatches.single { it.geographicSourceId == "geonames:551485" }
            assertTrue("A homonymous locality cannot inherit the capital's schedules", bundle.choiceSet(unrelatedKazan.id, DATE).choices.isEmpty())
            val omsk = bundle.searchCities("Омск").single { it.geographicSourceId == "geonames:1496153" }
            val omskChoice = bundle.choiceSet(omsk.id, DATE).choices.single { it.source.id == "ru-oms-omsk-json-monthly" }
            assertQualifiedSnapshot(bundle, omsk, omskChoice)
            assertEquals(LocalDate.parse("2026-06-01"), omskChoice.effectiveFrom)
            assertEquals(LocalDate.parse("2026-09-30"), omskChoice.effectiveTo)
            assertEquals("Asia/Omsk", omskChoice.scheduleTimezone)
            assertEquals(122, bundle.snapshotFor(omsk.id, omskChoice.id, DATE).payload.prayerDays.size)
            assertTrue(bundle.choiceSet(omsk.id, LocalDate.parse("2026-05-31")).choices.isEmpty())
            assertTrue(bundle.choiceSet(omsk.id, LocalDate.parse("2026-10-01")).choices.isEmpty())
        }
    }

    private fun assertQualifiedSnapshot(bundle: LocalSetupBundle, city: CanonicalCityCandidate, choice: DeviceScheduleChoice) {
        assertNotNull(choice.qualification)
        assertNull(choice.approvalId)
        val snapshot = bundle.snapshotFor(city.id, choice.id, DATE).payload
        assertEquals("2.0", snapshot.schemaVersion)
        assertEquals(choice.qualification!!.sha256, snapshot.source.qualification!!.sha256)
        assertEquals(city.timezone, snapshot.mosque.timezone)
        assertNull(snapshot.source.approval)
        assertTrue(snapshot.iqamahRules.isEmpty())
        assertTrue(snapshot.iqamahDateOverrides.isEmpty())
        assertTrue(snapshot.jumuahSessions.isEmpty())
        assertEquals(1, snapshot.prayerDays.count { it.date == DATE.toString() })
    }

    companion object {
        val DATE: LocalDate = LocalDate.parse("2026-09-08")
        const val ORIGINAL_ULY_SHA = "78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b"
    }
}
