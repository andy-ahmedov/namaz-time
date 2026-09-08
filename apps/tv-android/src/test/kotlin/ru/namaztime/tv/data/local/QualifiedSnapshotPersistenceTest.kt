package ru.namaztime.tv.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.namaztime.tv.data.snapshot.QualifiedSnapshotSigner
import ru.namaztime.tv.data.snapshot.SnapshotActivationGate
import ru.namaztime.tv.data.snapshot.SnapshotDecoder
import ru.namaztime.tv.data.snapshot.qualifiedSnapshotDocument
import ru.namaztime.tv.data.snapshot.changeDay
import ru.namaztime.tv.data.snapshot.syntheticSnapshotBytes
import ru.namaztime.tv.repository.RoomPrayerScheduleRepository
import ru.namaztime.tv.repository.CorruptLocalSnapshotException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class QualifiedSnapshotPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "qualified-snapshot-persistence-test.db"
    private var database: NamazDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(name)
    }

    @Test
    fun authenticatedPublicQualificationSurvivesReopenWithoutHumanApproval() = runTest {
        val signer = QualifiedSnapshotSigner()
        val verifier = signer.verifier()
        val input = SnapshotActivationGate.authenticated(signer.sign(), verifier)
        val first = open()
        SnapshotImporter(first).importAndActivate(input)
        val stored = first.snapshotDao().getSnapshot(input.payload.snapshotId)
        assertEquals(null, stored?.approvalId)
        assertEquals(null, stored?.approvedBy)
        assertEquals(null, stored?.approvalStatus)
        assertEquals(input.payload.source.qualification?.qualificationId, stored?.qualificationId)
        assertEquals(input.payload.source.qualification?.sha256, stored?.qualificationSha256)
        assertTrue(stored?.qualificationJson?.contains("\"onset_sha256\"") == true)
        assertEquals(0, first.snapshotDao().countIqamahRules(input.payload.snapshotId))
        first.close()
        val reopened = open()
        assertEquals(SnapshotSelectionResolution.Active(input.payload.snapshotId), SnapshotSelectionGuard(reopened, verifier.selectionTrust()).resolve())
        assertEquals(30, reopened.snapshotDao().countPrayerDays(input.payload.snapshotId))
        val schedule = requireNotNull(RoomPrayerScheduleRepository(reopened.snapshotDao()).observeActiveSchedule().first())
        assertEquals(input.payload.source.qualification?.qualificationId, schedule.diagnostics?.qualification?.qualificationId)
        assertEquals(null, schedule.diagnostics?.approvalId)
        assertEquals(null, schedule.diagnostics?.approvedBy)
        assertEquals(0, schedule.iqamahRules.size)
        assertEquals(0, schedule.jumuahSessions.size)
    }

    @Test
    fun corruptNonSampleOnsetRowRestoresLegacyLastKnownGoodAfterReopen() = runTest {
        val signer = QualifiedSnapshotSigner()
        val verifier = signer.verifier()
        val first = open()
        val legacy = SnapshotDecoder.decode(syntheticSnapshotBytes())
        SnapshotImporter(first).importAndActivate(legacy)
        val input = SnapshotActivationGate.authenticated(signer.sign(), verifier)
        SnapshotImporter(first).importAndActivate(input)
        first.openHelper.writableDatabase.execSQL("UPDATE prayer_days SET fajr='04:01' WHERE snapshotId=? AND localDate='2026-09-08'", arrayOf(input.payload.snapshotId))
        first.close()
        val reopened = open()
        assertEquals(SnapshotSelectionResolution.Recovered(legacy.snapshotId), SnapshotSelectionGuard(reopened, verifier.selectionTrust()).resolve())
        assertEquals(legacy.snapshotId, reopened.snapshotDao().getSelection()?.activeSnapshotId)
    }

    @Test
    fun invalidQualificationAndConflictingApprovalFailLocalProjection() = runTest {
        val signer = QualifiedSnapshotSigner()
        val verifier = signer.verifier()
        val db = open()
        val input = SnapshotActivationGate.authenticated(signer.sign(), verifier)
        SnapshotImporter(db).importAndActivate(input)
        val original = requireNotNull(db.snapshotDao().getSnapshot(input.payload.snapshotId))
        val changes = listOf(
            original.copy(qualificationId = "qualification-unrelated"),
            original.copy(qualificationSha256 = "0".repeat(64)),
            original.copy(qualificationJson = "{}"),
            original.copy(approvalId = "fabricated-approval"),
            original.copy(authorityName = "Another authority"),
            original.copy(parserVersion = "changed-parser"),
            original.copy(schemaVersion = "1.0"),
        )
        changes.forEach { changed ->
            db.updateMetadataForTest(changed)
            val error = runCatching { RoomPrayerScheduleRepository(db.snapshotDao()).observeActiveSchedule().first() }.exceptionOrNull()
            assertTrue("must reject ${changed.snapshotId}", error is CorruptLocalSnapshotException)
        }
        db.updateMetadataForTest(original)
        assertEquals(input.payload.snapshotId, RoomPrayerScheduleRepository(db.snapshotDao()).observeActiveSchedule().first()?.snapshotId)
    }

    @Test
    fun failedActivationAndCorrectlySignedTamperLeaveLastKnownGoodUntouched() = runTest {
        val signer = QualifiedSnapshotSigner()
        val verifier = signer.verifier()
        val db = open()
        val legacy = SnapshotDecoder.decode(syntheticSnapshotBytes())
        SnapshotImporter(db).importAndActivate(legacy)
        val input = SnapshotActivationGate.authenticated(signer.sign(), verifier)
        val error = runCatching {
            SnapshotImporter(db, beforeActivation = BeforeSnapshotActivation { throw IllegalStateException("synthetic transaction failure") }).importAndActivate(input)
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(legacy.snapshotId, db.snapshotDao().getSelection()?.activeSnapshotId)
        assertEquals(false, db.snapshotDao().snapshotExists(input.payload.snapshotId))
        val invalid = qualifiedSnapshotDocument().changeDay(7) { put("fajr", JsonPrimitive("04:01")) }
        assertTrue(runCatching { SnapshotActivationGate.authenticated(signer.sign(invalid), verifier) }.isFailure)
        assertEquals(legacy.snapshotId, db.snapshotDao().getSelection()?.activeSnapshotId)
    }

    @Test
    fun revokedQualificationSigningKeyCannotResumeAfterReopen() = runTest {
        val signer = QualifiedSnapshotSigner()
        val db = open()
        SnapshotImporter(db).importAndActivate(SnapshotActivationGate.authenticated(signer.sign(), signer.verifier()))
        db.close()
        val reopened = open()
        assertEquals(SnapshotSelectionResolution.Corrupt, SnapshotSelectionGuard(reopened, signer.verifier("revoked").selectionTrust()).resolve())
        assertEquals(null, reopened.snapshotDao().getSelection()?.activeSnapshotId)
    }

    private fun open() = Room.databaseBuilder(context, NamazDatabase::class.java, name)
        .allowMainThreadQueries().build().also { database = it }

    private fun NamazDatabase.updateMetadataForTest(snapshot: SnapshotEntity) {
        openHelper.writableDatabase.execSQL(
            "UPDATE snapshots SET qualificationId=?, qualificationSha256=?, qualificationJson=?, approvalId=?, authorityName=?, parserVersion=?, schemaVersion=? WHERE snapshotId=?",
            arrayOf(snapshot.qualificationId, snapshot.qualificationSha256, snapshot.qualificationJson, snapshot.approvalId,
                snapshot.authorityName, snapshot.parserVersion, snapshot.schemaVersion, snapshot.snapshotId),
        )
    }
}
