package com.example.namaztime.tv.data.snapshot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.namaztime.tv.data.local.NamazDatabase
import com.example.namaztime.tv.data.local.SnapshotImporter
import com.example.namaztime.tv.data.local.SnapshotReplacementPolicy
import com.example.namaztime.tv.data.local.SnapshotSelectionGuard
import com.example.namaztime.tv.data.local.SnapshotSelectionResolver
import java.io.File
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
            assetSource = syntheticSnapshotAssetSource(),
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
        val corrupt = syntheticSnapshotBytes().decodeToString()
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
        val production = syntheticSnapshotBytes().decodeToString()
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
            assetSource = syntheticSnapshotAssetSource(),
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
    fun newerBundledPilotFamilySnapshotReplacesInstalledPredecessor() = runTest {
        val active = SnapshotDecoder.decode(syntheticSnapshotBytes()).copy(
            snapshotId = "ulyanovsk-second-cathedral-2026-pilot-local-v1",
            generatedAt = "2026-08-20T00:00:00Z",
        )
        val successor = active.copy(
            snapshotId = "ulyanovsk-second-cathedral-pilot-local-2026-09-v2",
            generatedAt = "2026-08-21T00:00:00Z",
        )
        SnapshotImporter(database).importAndActivate(active)
        val bootstrapper = BundledSnapshotBootstrapper(
            selectionGuard = SnapshotSelectionGuard(database),
            importer = SnapshotImporter(database),
            assetSource = SnapshotAssetSource { byteArrayOf(1) },
            activationGate = { SnapshotActivationGate.bundledSynthetic(successor) },
            replacementPolicy = SnapshotReplacementPolicy.pilotLocal(
                currentSnapshotId = successor.snapshotId,
                predecessorSnapshotIds = setOf(active.snapshotId),
                snapshotIdPrefix = "ulyanovsk-second-cathedral-pilot-local-",
            ),
        )

        bootstrapper.bootstrapIfNeeded()

        assertEquals(successor.snapshotId, database.snapshotDao().getSelection()?.activeSnapshotId)
        assertEquals(SnapshotBootstrapState.Ready(successor.snapshotId), bootstrapper.state.value)
    }

    @Test
    fun corruptActiveSnapshotRestoresCompletePreviousWithoutReadingAsset() = runTest {
        val first = SnapshotDecoder.decode(syntheticSnapshotBytes())
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
        val first = SnapshotDecoder.decode(syntheticSnapshotBytes())
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
        val snapshot = SnapshotDecoder.decode(syntheticSnapshotBytes())
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
            assetSource = syntheticSnapshotAssetSource(),
        )

        bootstrapper.bootstrapIfNeeded()

        assertEquals(
            SnapshotBootstrapState.Diagnostic("SNAPSHOT_DATABASE_READ_FAILED"),
            bootstrapper.state.value,
        )
    }

    @Test
    fun revokedProductionKeyInvalidatesPersistedActiveSelectionOnColdStart() = runTest {
        val fixture = File("../../fixtures/verification/synthetic-signed-snapshot.json").readBytes()
        val keyDocument = Json.parseToJsonElement(
            File("../../fixtures/verification/phase1-public-key.json").readText(),
        ).jsonObject
        val keyId = keyDocument.getValue("signing_key_id").jsonPrimitive.content
        val publicKey = Base64.getDecoder().decode(
            keyDocument.getValue("public_key_ed25519_base64").jsonPrimitive.content,
        )
        SnapshotImporter(database).importAndActivate(
            SnapshotActivationGate.authenticated(
                fixture,
                SnapshotAuthenticityVerifier(mapOf(keyId to publicKey)),
            ),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE snapshots SET dataClassification = 'production' WHERE snapshotId = ?",
            arrayOf("synthetic-android-verification-v1"),
        )
        val revokedBundle = File("../../fixtures/verification/phase1-trust-bundle.json").readText()
            .replace("\"environment\": \"test\"", "\"environment\": \"production\"")
            .replace("\"status\": \"active\"", "\"status\": \"revoked\"")
            .replace(
                "\n      \"not_before\"",
                "\n      \"revoked_at\": \"2026-08-20T00:00:00Z\",\n      \"revocation_reason\": \"compromise drill\",\n      \"not_before\"",
            )
            .encodeToByteArray()

        val resolution = SnapshotSelectionGuard(
            database,
            SnapshotAuthenticityVerifier(
                revokedBundle,
                minimumTrustBundleRevision = 1,
                environmentTrustBundles = listOf(
                    comparisonBundle("test", "test-comparison-key", 0x61),
                    comparisonBundle("staging", "staging-comparison-key", 0x62),
                ),
            ).selectionTrust(),
        ).resolve()

        assertEquals(
            com.example.namaztime.tv.data.local.SnapshotSelectionResolution.Corrupt,
            resolution,
        )
        assertNull(database.snapshotDao().getSelection()?.activeSnapshotId)
    }

    private fun comparisonBundle(environment: String, keyId: String, fill: Int): ByteArray {
        val encoded = Base64.getEncoder().encodeToString(ByteArray(32) { fill.toByte() })
        return """{"schema_version":"1.0","revision":1,"environment":"$environment","generated_at":"2026-08-20T00:00:00Z","keys":[{"key_id":"$keyId","algorithm":"ed25519","public_key_ed25519_base64":"$encoded","status":"active","not_before":"2026-08-20T00:00:00Z"}]}"""
            .encodeToByteArray()
    }
}
