package com.example.namaztime.tv.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.namaztime.tv.data.snapshot.SnapshotActivationGate
import com.example.namaztime.tv.data.snapshot.SnapshotAuthenticityVerifier
import com.example.namaztime.tv.data.snapshot.SnapshotDecoder
import com.example.namaztime.tv.data.snapshot.SnapshotAssetReference
import com.example.namaztime.tv.data.snapshot.SnapshotIqamahOverride
import com.example.namaztime.tv.data.snapshot.SnapshotIqamahRule
import com.example.namaztime.tv.data.snapshot.SnapshotIqamahValue
import com.example.namaztime.tv.data.snapshot.SnapshotValidationException
import java.io.File
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class SnapshotImporterTest {
    private lateinit var database: NamazDatabase
    private lateinit var dao: SnapshotDao
    private var fileDatabaseName: String? = null

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NamazDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.snapshotDao()
    }

    @After
    fun closeDatabase() {
        database.close()
        fileDatabaseName?.let {
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(it)
        }
    }

    @Test
    fun firstImportPersistsEveryChildAndActivatesSnapshot() = runTest {
        val snapshot = SnapshotDecoder.decode(syntheticFixture())

        val result = SnapshotImporter(database).importAndActivate(snapshot)

        assertEquals(SnapshotImportResult.Activated(snapshot.snapshotId, null), result)
        assertEquals(snapshot.snapshotId, dao.getSelection()?.activeSnapshotId)
        assertEquals(3, dao.countPrayerDays(snapshot.snapshotId))
        assertEquals(2, dao.countIqamahRules(snapshot.snapshotId))
        assertEquals(0, dao.countIqamahOverrides(snapshot.snapshotId))
        assertEquals(1, dao.countJumuahSessions(snapshot.snapshotId))
        assertEquals(1, dao.countCampaigns(snapshot.snapshotId))
        assertEquals(1, dao.countThemes(snapshot.snapshotId))
        assertEquals(
            "[\"synthetic\"]",
            dao.getPrayerDayFlags(snapshot.snapshotId, "2026-08-19"),
        )
    }

    @Test
    fun repeatedBundledImportIsIdempotent() = runTest {
        val snapshot = SnapshotDecoder.decode(syntheticFixture())
        val importer = SnapshotImporter(database)
        importer.importAndActivate(snapshot)

        val result = importer.importAndActivate(snapshot)

        assertEquals(SnapshotImportResult.AlreadyActive(snapshot.snapshotId), result)
        assertEquals(1, dao.countSnapshots())
        assertEquals(3, dao.countPrayerDays(snapshot.snapshotId))
    }

    @Test
    fun replacementCannotOverwriteAnUnexpectedActiveSnapshot() = runTest {
        val active = SnapshotDecoder.decode(syntheticFixture())
        val replacement = active.copy(snapshotId = "synthetic-approved-pilot-replacement")
        val importer = SnapshotImporter(database)
        importer.importAndActivate(active)

        val result = importer.replaceAndActivate(
            SnapshotActivationGate.bundledSynthetic(replacement),
            replaceableActiveSnapshotIds = setOf("different-legacy-snapshot"),
        )

        assertEquals(SnapshotImportResult.SelectionChanged(active.snapshotId), result)
        assertEquals(active.snapshotId, dao.getSelection()?.activeSnapshotId)
        assertEquals(false, dao.snapshotExists(replacement.snapshotId))
    }

    @Test
    fun authenticatedGoFixturePassesTheOnlySignedActivationGate() = runTest {
        val fixture = File(
            "../../fixtures/verification/synthetic-signed-snapshot.json",
        ).readBytes()
        val key = Json.parseToJsonElement(
            File("../../fixtures/verification/phase1-public-key.json").readText(),
        ).jsonObject
        val keyId = key.getValue("signing_key_id").jsonPrimitive.content
        val publicKey = Base64.getDecoder().decode(
            key.getValue("public_key_ed25519_base64").jsonPrimitive.content,
        )
        val activatable = SnapshotActivationGate.authenticated(
            fixture,
            SnapshotAuthenticityVerifier(mapOf(keyId to publicKey)),
        )

        val result = SnapshotImporter(database).importAndActivate(activatable)

        assertEquals(
            SnapshotImportResult.Activated("synthetic-android-verification-v1", null),
            result,
        )
        assertEquals("synthetic-android-verification-v1", dao.getSelection()?.activeSnapshotId)
    }

    @Test
    fun unverifiedProductionPayloadCannotReachTheImporter() = runTest {
        val production = SnapshotDecoder.decode(
            syntheticFixture().decodeToString()
                .replaceFirst(
                    "\"data_classification\": \"synthetic\"",
                    "\"data_classification\": \"production\"",
                )
                .encodeToByteArray(),
        )

        try {
            SnapshotImporter(database).importAndActivate(production)
            fail("expected production authenticity rejection")
        } catch (error: SnapshotValidationException) {
            assertEquals("bundled_requires_synthetic", error.code)
        }
        assertEquals(0, dao.countSnapshots())
    }

    @Test
    fun timeEngineFailureCannotReplaceOrPersistAnInvalidSnapshot() = runTest {
        val active = SnapshotDecoder.decode(syntheticFixture())
        val importer = SnapshotImporter(database)
        importer.importAndActivate(active)
        val invalid = active.copy(
            snapshotId = "synthetic-invalid-time-engine",
            iqamahRules = active.iqamahRules + SnapshotIqamahRule(
                id = "iqamah-isha-crosses-midnight",
                prayer = "isha",
                validFrom = "2026-08-19",
                validTo = "2026-08-21",
                weekdays = listOf(1, 2, 3, 4, 5, 6, 7),
                priority = 200,
                value = SnapshotIqamahValue(
                    mode = "offset_after_adhan",
                    offsetMinutes = 240,
                ),
                reason = "Synthetic invalid rule",
            ),
        )

        try {
            importer.importAndActivate(invalid)
            fail("expected time-engine import rejection")
        } catch (error: SnapshotImportException) {
            assertEquals("time_engine_iqamah_crosses_local_date", error.code)
        }

        assertEquals(active.snapshotId, dao.getSelection()?.activeSnapshotId)
        assertEquals(1, dao.countSnapshots())
        assertFalse(dao.snapshotExists(invalid.snapshotId))
    }

    @Test
    fun invalidDailyPrayerOrderingIsRejectedBeforePersistence() = runTest {
        val valid = SnapshotDecoder.decode(syntheticFixture())
        val invalid = valid.copy(
            snapshotId = "synthetic-invalid-prayer-order",
            prayerDays = valid.prayerDays.mapIndexed { index, day ->
                if (index == 1) day.copy(fajr = "06:00", sunrise = "05:00") else day
            },
        )

        try {
            SnapshotImporter(database).importAndActivate(invalid)
            fail("expected prayer-order import rejection")
        } catch (error: SnapshotImportException) {
            assertEquals("time_engine_schedule_prayer_order_invalid", error.code)
        }

        assertEquals(0, dao.countSnapshots())
        assertEquals(null, dao.getSelection())
    }

    @Test
    fun secondActivationPreservesFullProvenanceAssetsOverridesAndPreviousPointer() = runTest {
        val first = SnapshotDecoder.decode(syntheticFixture())
        val importer = SnapshotImporter(database)
        importer.importAndActivate(first)
        val asset = SnapshotAssetReference(
            assetId = "asset-landscape-test",
            sha256 = "a".repeat(64),
            mediaType = "image/jpeg",
            byteLength = 1024,
            width = 1920,
            height = 1080,
        )
        val second = first.copy(
            snapshotId = "synthetic-ulsk-demo-2026-08-v2",
            iqamahDateOverrides = listOf(
                SnapshotIqamahOverride(
                    date = "2026-08-20",
                    prayer = "fajr",
                    value = SnapshotIqamahValue(mode = "fixed_time", fixedTime = "04:00"),
                    reason = "Synthetic override",
                ),
            ),
            theme = first.theme?.copy(landscapeAsset = asset),
        )

        val result = importer.importAndActivate(second)

        assertEquals(SnapshotImportResult.Activated(second.snapshotId, first.snapshotId), result)
        assertEquals(second.snapshotId, dao.getSelection()?.activeSnapshotId)
        assertEquals(first.snapshotId, dao.getSelection()?.previousSnapshotId)
        assertEquals(1, dao.countIqamahOverrides(second.snapshotId))
        val storedSnapshot = dao.getSnapshot(second.snapshotId)
        assertEquals("Synthetic fixture created for tests", storedSnapshot?.licenseReference)
        assertEquals("Not real prayer times; do not display in a mosque", storedSnapshot?.attribution)
        assertEquals("approved", storedSnapshot?.approvalStatus)
        val storedTheme = dao.getTheme(second.snapshotId)
        assertEquals(
            """{"asset_id":"asset-landscape-test","sha256":"${"a".repeat(64)}","media_type":"image/jpeg","byte_length":1024,"width":1920,"height":1080}""",
            storedTheme?.landscapeAssetJson,
        )
    }

    @Test
    fun authorizedRollbackReimportsExistingPreviousAndKeepsReplacedSnapshotRestorable() = runTest {
        val first = SnapshotDecoder.decode(syntheticFixture())
        val second = first.copy(snapshotId = "synthetic-ulsk-demo-2026-08-v2")
        val importer = SnapshotImporter(database)
        importer.importAndActivate(first)
        importer.importAndActivate(second)

        val result = importer.importAndActivate(first)

        assertEquals(SnapshotImportResult.Activated(first.snapshotId, second.snapshotId), result)
        assertEquals(first.snapshotId, dao.getSelection()?.activeSnapshotId)
        assertEquals(second.snapshotId, dao.getSelection()?.previousSnapshotId)
        assertEquals(2, dao.countSnapshots())
        assertEquals(3, dao.countPrayerDays(first.snapshotId))
    }

    @Test
    fun thirdActivationPrunesSnapshotOlderThanImmediateRollbackTarget() = runTest {
        val first = SnapshotDecoder.decode(syntheticFixture())
        val second = first.copy(snapshotId = "synthetic-ulsk-demo-2026-08-v2")
        val third = first.copy(snapshotId = "synthetic-ulsk-demo-2026-08-v3")
        val importer = SnapshotImporter(database)
        importer.importAndActivate(first)
        importer.importAndActivate(second)

        val result = importer.importAndActivate(third)

        assertEquals(SnapshotImportResult.Activated(third.snapshotId, second.snapshotId), result)
        assertEquals(third.snapshotId, dao.getSelection()?.activeSnapshotId)
        assertEquals(second.snapshotId, dao.getSelection()?.previousSnapshotId)
        assertEquals(2, dao.countSnapshots())
        assertFalse(dao.snapshotExists(first.snapshotId))
        assertEquals(0, dao.countPrayerDays(first.snapshotId))
    }

    @Test
    fun rollbackInterruptionLeavesCurrentActiveAndPreviousUntouchedAfterReopen() = runTest {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        fileDatabaseName = "snapshot-rollback-failure-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, NamazDatabase::class.java, fileDatabaseName!!)
            .allowMainThreadQueries()
            .build()
        dao = database.snapshotDao()
        val first = SnapshotDecoder.decode(syntheticFixture())
        val second = first.copy(snapshotId = "synthetic-ulsk-demo-2026-08-v2")
        SnapshotImporter(database).apply {
            importAndActivate(first)
            importAndActivate(second)
        }

        try {
            SnapshotImporter(
                database = database,
                beforeActivation = BeforeSnapshotActivation { throw SimulatedActivationFailure() },
            ).importAndActivate(first)
            throw AssertionError("expected simulated rollback interruption")
        } catch (_: SimulatedActivationFailure) {
            // Transaction must roll back before the file-backed database is reopened.
        }
        database.close()
        database = Room.databaseBuilder(context, NamazDatabase::class.java, fileDatabaseName!!)
            .allowMainThreadQueries()
            .build()
        dao = database.snapshotDao()

        assertEquals(second.snapshotId, dao.getSelection()?.activeSnapshotId)
        assertEquals(first.snapshotId, dao.getSelection()?.previousSnapshotId)
        assertEquals(2, dao.countSnapshots())
    }

    @Test
    fun transactionFailurePreservesActiveAfterFileDatabaseReopen() = runTest {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        fileDatabaseName = "snapshot-import-failure-${System.nanoTime()}.db"
        database = Room.databaseBuilder(context, NamazDatabase::class.java, fileDatabaseName!!)
            .allowMainThreadQueries()
            .build()
        dao = database.snapshotDao()
        val first = SnapshotDecoder.decode(syntheticFixture())
        SnapshotImporter(database).importAndActivate(first)
        val replacement = first.copy(snapshotId = "synthetic-ulsk-demo-2026-08-v2")
        val importer = SnapshotImporter(
            database = database,
            beforeActivation = BeforeSnapshotActivation {
                throw SimulatedActivationFailure()
            },
        )

        try {
            importer.importAndActivate(replacement)
            throw AssertionError("expected simulated activation failure")
        } catch (_: SimulatedActivationFailure) {
            // Expected: the transaction must roll back before assertions below.
        }

        database.close()
        database = Room.databaseBuilder(context, NamazDatabase::class.java, fileDatabaseName!!)
            .allowMainThreadQueries()
            .build()
        dao = database.snapshotDao()

        assertEquals(first.snapshotId, dao.getSelection()?.activeSnapshotId)
        assertEquals(null, dao.getSelection()?.previousSnapshotId)
        assertFalse(dao.snapshotExists(replacement.snapshotId))
        assertEquals(1, dao.countSnapshots())
    }

    private fun syntheticFixture(): ByteArray = File(
        "../../examples/synthetic-prayer-snapshot.json",
    ).readBytes()

    private class SimulatedActivationFailure : RuntimeException()
}
