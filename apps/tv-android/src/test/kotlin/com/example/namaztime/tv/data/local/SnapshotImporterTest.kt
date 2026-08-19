package com.example.namaztime.tv.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.namaztime.tv.data.snapshot.SnapshotDecoder
import com.example.namaztime.tv.data.snapshot.SnapshotAssetReference
import com.example.namaztime.tv.data.snapshot.SnapshotIqamahOverride
import com.example.namaztime.tv.data.snapshot.SnapshotIqamahValue
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
