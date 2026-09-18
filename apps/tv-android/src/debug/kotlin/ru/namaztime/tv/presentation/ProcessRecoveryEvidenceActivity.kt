package ru.namaztime.tv.presentation

import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import ru.namaztime.tv.data.local.BeforeSnapshotActivation
import ru.namaztime.tv.data.local.NamazDatabase
import ru.namaztime.tv.data.local.SnapshotImporter
import ru.namaztime.tv.data.snapshot.SnapshotActivationGate
import ru.namaztime.tv.data.snapshot.SnapshotAuthenticityVerifier
import ru.namaztime.tv.repository.DataStoreOperatorPreferencesRepository
import ru.namaztime.tv.sync.*

/** Explicit synthetic evidence only: separate process, database, preferences and sync journal. */
class ProcessRecoveryEvidenceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runId = intent.getStringExtra("run_id").orEmpty()
        val phase = intent.getStringExtra("phase").orEmpty()
        val boundary = intent.getStringExtra("boundary").orEmpty()
        require(runId.matches(Regex("[a-f0-9]{32}")))
        require(phase in setOf("prepare", "resume"))
        require(boundary in setOf("transaction", "committed"))
        val root = File(filesDir, "recovery-evidence/$runId")
        require(root.isDirectory)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                exercise(root, phase, boundary)
            } catch (error: Exception) {
                marker(root, "error", JSONObject().put("type", error.javaClass.name)
                    .put("message", error.message))
                throw error
            }
        }
    }

    private suspend fun exercise(root: File, phase: String, boundary: String) {
        val database = Room.databaseBuilder(this, NamazDatabase::class.java, File(root, "evidence.db").path).build()
        val dao = database.snapshotDao()
        val preferences = DataStoreOperatorPreferencesRepository(
            PreferenceDataStoreFactory.create { File(root, "operator.preferences_pb") },
        )
        val baseline = SnapshotActivationGate.bundledSynthetic(File(root, "baseline.json").readBytes())
        val bytes = File(root, "signed.json").readBytes()
        val key = Json.parseToJsonElement(File(root, "public-key.json").readText()).jsonObject
        val keyId = key.getValue("signing_key_id").jsonPrimitive.content
        val verifier = SnapshotAuthenticityVerifier(mapOf(keyId to Base64.getDecoder().decode(
            key.getValue("public_key_ed25519_base64").jsonPrimitive.content,
        )))
        val incomingId = "synthetic-android-verification-v1"
        val manifest = JSONObject()
            .put("manifest_version", 1).put("snapshot_id", incomingId)
            .put("snapshot_url", "https://api.example.invalid/v1/snapshots/$incomingId")
            .put("snapshot_sha256", MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) })
            .put("snapshot_byte_length", bytes.size).put("signing_key_id", keyId)
        val storage = FileSnapshotSyncStorage(File(root, "sync"))
        if (phase == "prepare") {
            check(dao.getSelection() == null)
            SnapshotImporter(database).importAndActivate(baseline)
            preferences.setLanguageTag("en")
            preferences.setScheduleBlockTransparency(35)
        } else {
            val selected = dao.getSelection()
            check(selected?.activeSnapshotId == if (boundary == "transaction") baseline.payload.snapshotId else incomingId)
            check(dao.snapshotExists(incomingId) == (boundary == "committed"))
            check(dao.countPrayerDays(baseline.payload.snapshotId) == baseline.payload.prayerDays.size)
            check(preferences.preferences.first().languageTag == "en")
            check(preferences.preferences.first().scheduleBlockTransparency == 35)
            check(storage.load().pendingManifest?.snapshotId == incomingId)
        }
        suspend fun pauseForKill() {
            marker(root, "ready", JSONObject().put("pid", Process.myPid()).put("boundary", boundary))
            awaitCancellation()
        }
        val importer = SnapshotImporter(database, BeforeSnapshotActivation {
            if (phase == "prepare" && boundary == "transaction") pauseForKill()
        })
        var requests = 0
        val transport = object : DeviceSyncTransport {
            override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse {
                check(phase == "prepare") { "Recovery must use durable stage without network" }
                requests++
                return when (requests) {
                    1 -> SyncHttpResponse(200, mapOf("ETag" to "\"evidence-v1\""), manifest.toString().encodeToByteArray())
                    2 -> SyncHttpResponse(200, emptyMap(), bytes)
                    else -> error("Unexpected request")
                }
            }
        }
        val result = SnapshotSynchronizer(
            transport, storage,
            AuthenticatedRoomSnapshotActivator(verifier, importer, dao, "synthetic-verification-mosque", "Europe/Ulyanovsk"),
            object : SnapshotSyncInterruptionHook {
                override suspend fun afterStage() = Unit
                override suspend fun afterActivation() {
                    if (phase == "prepare" && boundary == "committed") pauseForKill()
                }
            },
        ).sync(DeviceSyncCredentials("device-fixture-0001", "fixture-device-token-not-production",
            "https://api.example.invalid/v1/devices/device-fixture-0001/manifest",
            "synthetic-verification-mosque", "Europe/Ulyanovsk"))
        check(phase == "resume")
        check(result == SnapshotSyncResult.Updated(incomingId, baseline.payload.snapshotId))
        check(dao.getSelection()?.previousSnapshotId == baseline.payload.snapshotId)
        check(dao.getSelection()?.activeSnapshotId == incomingId)
        check(dao.countSnapshots() == 2)
        check(storage.load().pendingManifest == null)
        check(storage.load().acceptedSnapshotId == incomingId)
        database.openHelper.readableDatabase.query("PRAGMA integrity_check").use {
            check(it.moveToFirst() && it.getString(0) == "ok")
        }
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { check(it.count == 0) }
        database.close()
        marker(root, "passed", JSONObject().put("pid", Process.myPid()).put("boundary", boundary)
            .put("network_requests", requests).put("active", incomingId)
            .put("previous", baseline.payload.snapshotId).put("preferences_preserved", true))
    }

    private fun marker(root: File, name: String, value: JSONObject) {
        FileOutputStream(File(root, "$name.json")).use {
            it.write(value.toString().encodeToByteArray())
            it.fd.sync()
        }
    }
}
