package ru.namaztime.tv.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.data.snapshot.ActivatableSnapshot
import ru.namaztime.tv.data.snapshot.PILOT_LOCAL_SNAPSHOT_ASSET
import ru.namaztime.tv.data.snapshot.PilotLocalSnapshotTrust
import ru.namaztime.tv.data.snapshot.QualifiedSnapshotSigner
import ru.namaztime.tv.data.snapshot.SnapshotActivationGate
import ru.namaztime.tv.data.snapshot.qualifiedSnapshotDocument
import ru.namaztime.tv.data.snapshot.syntheticSnapshotBytes
import kotlinx.serialization.json.JsonObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class ExplicitSnapshotSelectionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, NamazDatabase::class.java)
        .allowMainThreadQueries().build()
    private val signer = QualifiedSnapshotSigner()

    @After
    fun close() = database.close()

    @Test
    fun explicitAuthenticatedSelectionRetainsExactPilotAsPrevious() = runTest {
        val pilot = pilot()
        val importer = SnapshotImporter(database)
        importer.importAndActivate(pilot)
        val original = database.snapshotDao().getSnapshot(pilot.payload.snapshotId)
        val rows = database.snapshotDao().getPrayerDays(pilot.payload.snapshotId)
        val incoming = qualified("selected-real-protocol-fixture")

        val result = importer.activateSelected(incoming, pilot.payload.snapshotId)

        assertEquals(SnapshotImportResult.Activated(incoming.payload.snapshotId, pilot.payload.snapshotId), result)
        assertEquals(pilot.payload.snapshotId, database.snapshotDao().getSelection()?.previousSnapshotId)
        assertEquals(original, database.snapshotDao().getSnapshot(pilot.payload.snapshotId))
        assertEquals(rows, database.snapshotDao().getPrayerDays(pilot.payload.snapshotId))
        assertEquals(0, database.snapshotDao().countIqamahRules(incoming.payload.snapshotId))
    }

    @Test
    fun changedActiveSelectionRejectsPreviewWithoutInsertingOrDeletingRows() = runTest {
        val importer = SnapshotImporter(database)
        val pilot = pilot()
        importer.importAndActivate(pilot)
        val concurrent = qualified("concurrent-selection-fixture")
        importer.importAndActivate(concurrent)
        val incoming = qualified("stale-preview-fixture")

        val result = importer.activateSelected(incoming, pilot.payload.snapshotId)

        assertEquals(SnapshotImportResult.SelectionChanged(concurrent.payload.snapshotId), result)
        assertEquals(pilot.payload.snapshotId, database.snapshotDao().getSelection()?.previousSnapshotId)
        assertFalse(database.snapshotDao().snapshotExists(incoming.payload.snapshotId))
        assertTrue(database.snapshotDao().snapshotExists(pilot.payload.snapshotId))
    }

    @Test
    fun expectedEmptySelectionDoesNotOverwriteConcurrentBootstrap() = runTest {
        val importer = SnapshotImporter(database)
        val incoming = qualified("empty-selection-fixture")
        val pilot = pilot()
        importer.importAndActivate(pilot)

        assertEquals(SnapshotImportResult.SelectionChanged(pilot.payload.snapshotId), importer.activateSelected(incoming, null))
        assertFalse(database.snapshotDao().snapshotExists(incoming.payload.snapshotId))
    }

    @Test
    fun emptyDatabaseCanSelectAndRepeatedExactArtifactIsIdempotent() = runTest {
        val importer = SnapshotImporter(database)
        val incoming = qualified("initial-selection-fixture")
        assertEquals(SnapshotImportResult.Activated(incoming.payload.snapshotId, null), importer.activateSelected(incoming, null))
        assertEquals(SnapshotImportResult.AlreadyActive(incoming.payload.snapshotId), importer.activateSelected(incoming, incoming.payload.snapshotId))
    }

    @Test
    fun sameSnapshotIdWithDifferentAuthenticatedContentIsRejected() = runTest {
        val importer = SnapshotImporter(database)
        val incoming = qualified("immutable-selection-fixture")
        importer.importAndActivate(incoming)
        val changed = qualified("immutable-selection-fixture", generatedAt = "2026-09-08T11:00:01Z")

        val failure = runCatching { importer.activateSelected(changed, incoming.payload.snapshotId) }.exceptionOrNull()

        assertEquals("snapshot_id_conflict", (failure as? SnapshotImportException)?.code)
        assertEquals(incoming.payload.integrity.canonicalSha256, database.snapshotDao().getSnapshot(incoming.payload.snapshotId)?.canonicalSha256)
    }

    @Test
    fun transactionFailurePreservesPilotAndSyntheticCannotUseExplicitActivation() = runTest {
        val pilot = pilot()
        SnapshotImporter(database).importAndActivate(pilot)
        val incoming = qualified("failed-selection-fixture")
        val failing = SnapshotImporter(database, beforeActivation = BeforeSnapshotActivation { error("synthetic transaction interruption") })
        assertTrue(runCatching { failing.activateSelected(incoming, pilot.payload.snapshotId) }.isFailure)
        assertEquals(pilot.payload.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
        assertFalse(database.snapshotDao().snapshotExists(incoming.payload.snapshotId))
        val synthetic = SnapshotActivationGate.bundledSynthetic(syntheticSnapshotBytes())
        assertTrue(runCatching { SnapshotImporter(database).activateSelected(synthetic, pilot.payload.snapshotId) }.isFailure)
        assertEquals(pilot.payload.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
    }

    private fun pilot(): ActivatableSnapshot = SnapshotActivationGate.authenticated(
        context.assets.open(PILOT_LOCAL_SNAPSHOT_ASSET).use { it.readBytes() },
        PilotLocalSnapshotTrust.verifier(context),
    )

    private fun qualified(id: String, generatedAt: String = "2026-09-08T11:00:00Z"): ActivatableSnapshot {
        val document = JsonObject(qualifiedSnapshotDocument() + mapOf(
            "snapshot_id" to JsonPrimitive(id), "generated_at" to JsonPrimitive(generatedAt),
        ))
        return SnapshotActivationGate.authenticated(signer.sign(document), signer.verifier())
    }
}
