package com.example.namaztime.tv.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.namaztime.tv.data.local.NamazDatabase
import com.example.namaztime.tv.data.local.SnapshotImporter
import com.example.namaztime.tv.data.snapshot.AndroidSnapshotAssetSource
import com.example.namaztime.tv.data.snapshot.SnapshotActivationGate
import com.example.namaztime.tv.data.snapshot.SnapshotAuthenticityVerifier
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class AuthenticatedRoomSnapshotActivatorTest {
    private lateinit var database: NamazDatabase
    private lateinit var snapshot: ByteArray
    private lateinit var keyId: String
    private lateinit var verifier: SnapshotAuthenticityVerifier
    private lateinit var activator: AuthenticatedRoomSnapshotActivator

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NamazDatabase::class.java,
        ).allowMainThreadQueries().build()
        snapshot = File("../../fixtures/verification/synthetic-signed-snapshot.json").readBytes()
        val key = Json.parseToJsonElement(
            File("../../fixtures/verification/phase1-public-key.json").readText(),
        ).jsonObject
        keyId = key.getValue("signing_key_id").jsonPrimitive.content
        val publicKey = Base64.getDecoder().decode(
            key.getValue("public_key_ed25519_base64").jsonPrimitive.content,
        )
        verifier = SnapshotAuthenticityVerifier(mapOf(keyId to publicKey))
        activator = AuthenticatedRoomSnapshotActivator(
            verifier = verifier,
            importer = SnapshotImporter(database),
            snapshotDao = database.snapshotDao(),
            expectedMosqueId = "synthetic-verification-mosque",
            expectedMosqueTimezone = "Europe/Ulyanovsk",
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun signedManifestBoundPayloadActivatesThroughRoomAndPreservesPrevious() = runTest {
        val bundled = SnapshotActivationGate.bundledSynthetic(
            AndroidSnapshotAssetSource(ApplicationProvider.getApplicationContext()).read(),
        )
        SnapshotImporter(database).importAndActivate(bundled)
        val manifest = matchingManifest()

        val previous = activator.activate(snapshot, manifest)

        assertEquals(bundled.payload.snapshotId, previous)
        assertEquals(manifest.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
        assertEquals(bundled.payload.snapshotId, database.snapshotDao().getSelection()?.previousSnapshotId)
    }

    @Test
    fun manifestSnapshotOrKeySubstitutionFailsBeforeRoomMutation() = runTest {
        val bundled = SnapshotActivationGate.bundledSynthetic(
            AndroidSnapshotAssetSource(ApplicationProvider.getApplicationContext()).read(),
        )
        SnapshotImporter(database).importAndActivate(bundled)
        listOf(
            matchingManifest().copy(snapshotId = "different-snapshot-0001") to "manifest_snapshot_id_mismatch",
            matchingManifest().copy(signingKeyId = "different-test-key") to "manifest_signing_key_mismatch",
        ).forEach { (manifest, expectedCode) ->
            try {
                activator.activate(snapshot, manifest)
                fail("expected manifest binding rejection")
            } catch (error: SnapshotActivationRejectedException) {
                assertEquals(expectedCode, error.code)
            }
            assertEquals(bundled.payload.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
            assertEquals(1, database.snapshotDao().countSnapshots())
        }
    }

    @Test
    fun signedSnapshotForDifferentProvisionedMosqueFailsBeforeRoomMutation() = runTest {
        val wrongMosqueActivator = AuthenticatedRoomSnapshotActivator(
            verifier = verifier,
            importer = SnapshotImporter(database),
            snapshotDao = database.snapshotDao(),
            expectedMosqueId = "different-provisioned-mosque",
            expectedMosqueTimezone = "Europe/Ulyanovsk",
        )

        try {
            wrongMosqueActivator.activate(snapshot, matchingManifest())
            fail("expected mosque binding rejection")
        } catch (error: SnapshotActivationRejectedException) {
            assertEquals("provisioned_mosque_mismatch", error.code)
        }
        assertEquals(0, database.snapshotDao().countSnapshots())
    }

    @Test
    fun signatureTamperMapsToBoundedRejectionAndKeepsLastKnownGood() = runTest {
        val bundled = SnapshotActivationGate.bundledSynthetic(
            AndroidSnapshotAssetSource(ApplicationProvider.getApplicationContext()).read(),
        )
        SnapshotImporter(database).importAndActivate(bundled)
        val tampered = snapshot.copyOf().also { bytes ->
            val index = bytes.indexOfFirst { it == 'S'.code.toByte() }
            bytes[index] = 'X'.code.toByte()
        }

        try {
            activator.activate(tampered, matchingManifest(tampered))
            fail("expected authenticity rejection")
        } catch (error: SnapshotActivationRejectedException) {
            assertEquals("snapshot_canonical_hash_mismatch", error.code)
        }
        assertEquals(bundled.payload.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
        assertEquals(1, database.snapshotDao().countSnapshots())
    }

    private fun matchingManifest(bytes: ByteArray = snapshot) = DeviceSnapshotManifest(
        manifestVersion = 1,
        snapshotId = "synthetic-android-verification-v1",
        snapshotUrl = "https://cdn.example.invalid/v1/snapshots/synthetic-android-verification-v1",
        snapshotSha256 = bytes.sha256(),
        snapshotByteLength = bytes.size.toLong(),
        signingKeyId = keyId,
    )
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
