package com.example.namaztime.tv.data.snapshot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.namaztime.tv.data.local.NamazDatabase
import com.example.namaztime.tv.data.local.SnapshotImporter
import com.example.namaztime.tv.data.local.SnapshotSelectionGuard
import com.example.namaztime.tv.data.local.SnapshotSelectionResolver
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class BundledSnapshotBootstrapperTest {
    private lateinit var context: Context
    private lateinit var database: NamazDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NamazDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun coldStartReadsBundledAssetAndActivatesItWithoutNetwork() = runTest {
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database),
            importer = SnapshotImporter(database),
            assetSource = AndroidSnapshotAssetSource(context),
        )

        bootstrapper.bootstrapIfNeeded()

        assertEquals(
            "synthetic-ulsk-demo-2026-08-v1",
            database.snapshotDao().getSelection()?.activeSnapshotId,
        )
        assertEquals(
            SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
            bootstrapper.state.value,
        )
    }

    @Test
    fun corruptFixtureLeavesDatabaseEmptyAndExposesStableDiagnostic() = runTest {
        val corrupt = AndroidSnapshotAssetSource(context).read().decodeToString()
            .replaceFirst("Europe/Ulyanovsk", "Mars/Olympus_Mons")
            .encodeToByteArray()
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database),
            importer = SnapshotImporter(database),
            assetSource = SnapshotAssetSource { corrupt },
        )

        bootstrapper.bootstrapIfNeeded()

        assertNull(database.snapshotDao().getSelection())
        assertEquals(
            SnapshotBootstrapState.Diagnostic("SNAPSHOT_INVALID_TIMEZONE"),
            bootstrapper.state.value,
        )
    }

    @Test
    fun bundledBootstrapRejectsProductionClassificationUntilSignatureVerificationExists() = runTest {
        val production = AndroidSnapshotAssetSource(context).read().decodeToString()
            .replaceFirst("\"data_classification\": \"synthetic\"", "\"data_classification\": \"production\"")
            .encodeToByteArray()
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database),
            importer = SnapshotImporter(database),
            assetSource = SnapshotAssetSource { production },
        )

        bootstrapper.bootstrapIfNeeded()

        assertNull(database.snapshotDao().getSelection())
        assertEquals(
            SnapshotBootstrapState.Diagnostic("SNAPSHOT_BUNDLED_REQUIRES_SYNTHETIC"),
            bootstrapper.state.value,
        )
    }

    @Test
    fun existingActiveSnapshotSkipsBundledAssetRead() = runTest {
        val first = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database),
            importer = SnapshotImporter(database),
            assetSource = AndroidSnapshotAssetSource(context),
        )
        first.bootstrapIfNeeded()
        var reads = 0
        val resumed = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database),
            importer = SnapshotImporter(database),
            assetSource = SnapshotAssetSource {
                reads += 1
                error("asset must not be read when local data is active")
            },
        )

        resumed.bootstrapIfNeeded()

        assertEquals(0, reads)
        assertEquals(
            SnapshotBootstrapState.Ready("synthetic-ulsk-demo-2026-08-v1"),
            resumed.state.value,
        )
    }

    @Test
    fun corruptActiveSnapshotRestoresCompletePreviousWithoutReadingAsset() = runTest {
        val first = SnapshotDecoder.decode(AndroidSnapshotAssetSource(context).read())
        val second = first.copy(snapshotId = "synthetic-ulsk-demo-2026-08-v2")
        SnapshotImporter(database).apply {
            importAndActivate(first)
            importAndActivate(second)
        }
        database.openHelper.writableDatabase.delete(
            "prayer_days",
            "snapshotId = ? AND localDate = ?",
            arrayOf(second.snapshotId, "2026-08-20"),
        )
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database),
            importer = SnapshotImporter(database),
            assetSource = SnapshotAssetSource { error("recovery must not read bundled asset") },
        )

        bootstrapper.bootstrapIfNeeded()

        assertEquals(first.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
        assertNull(database.snapshotDao().getSelection()?.previousSnapshotId)
        assertEquals(
            SnapshotBootstrapState.Ready(first.snapshotId, "SNAPSHOT_PREVIOUS_RESTORED"),
            bootstrapper.state.value,
        )
    }

    @Test
    fun corruptActiveTimezoneRestoresMetadataValidPreviousSnapshot() = runTest {
        val first = SnapshotDecoder.decode(AndroidSnapshotAssetSource(context).read())
        val second = first.copy(snapshotId = "synthetic-ulsk-demo-2026-08-v2")
        SnapshotImporter(database).apply {
            importAndActivate(first)
            importAndActivate(second)
        }
        database.openHelper.writableDatabase.execSQL(
            "UPDATE snapshots SET timezoneId = 'invalid/timezone' WHERE snapshotId = ?",
            arrayOf(second.snapshotId),
        )
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database),
            importer = SnapshotImporter(database),
            assetSource = SnapshotAssetSource { error("recovery must not read bundled asset") },
        )

        bootstrapper.bootstrapIfNeeded()

        assertEquals(first.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
        assertEquals(
            SnapshotBootstrapState.Ready(first.snapshotId, "SNAPSHOT_PREVIOUS_RESTORED"),
            bootstrapper.state.value,
        )
    }

    @Test
    fun corruptActiveProvenanceWithoutPreviousShowsBoundedDiagnostic() = runTest {
        val snapshot = SnapshotDecoder.decode(AndroidSnapshotAssetSource(context).read())
        SnapshotImporter(database).importAndActivate(snapshot)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE snapshots SET rawSha256 = 'corrupt' WHERE snapshotId = ?",
            arrayOf(snapshot.snapshotId),
        )
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database),
            importer = SnapshotImporter(database),
            assetSource = SnapshotAssetSource { error("corruption must not read bundled asset") },
        )

        bootstrapper.bootstrapIfNeeded()

        assertNull(database.snapshotDao().getSelection()?.activeSnapshotId)
        assertEquals(
            SnapshotBootstrapState.Diagnostic("SNAPSHOT_ACTIVE_INVALID"),
            bootstrapper.state.value,
        )
    }

    @Test
    fun databaseReadFailureBecomesBoundedDiagnostic() = runTest {
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionResolver {
                throw IllegalStateException("synthetic database read failure")
            },
            importer = SnapshotImporter(database),
            assetSource = AndroidSnapshotAssetSource(context),
        )

        bootstrapper.bootstrapIfNeeded()

        assertEquals(
            SnapshotBootstrapState.Diagnostic("SNAPSHOT_DATABASE_READ_FAILED"),
            bootstrapper.state.value,
        )
    }
}
