package ru.namaztime.tv.data.snapshot

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class PilotLocalAssetContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun pilotLocalPackageContainsApprovedUlyanovskSnapshotAndNoSyntheticDemo() {
        val packagedAssets = context.assets.list("").orEmpty().toSet()

        assertTrue(PILOT_LOCAL_SNAPSHOT_ASSET in packagedAssets)
        assertTrue(PILOT_LOCAL_PRODUCTION_TRUST_ASSET in packagedAssets)
        assertTrue(PILOT_LOCAL_STAGING_TRUST_ASSET in packagedAssets)
        assertTrue(PILOT_LOCAL_TEST_TRUST_ASSET in packagedAssets)
        assertFalse(BUNDLED_SNAPSHOT_ASSET in packagedAssets)

        val snapshotBytes = context.assets.open(PILOT_LOCAL_SNAPSHOT_ASSET).use { it.readBytes() }
        val root = Json.parseToJsonElement(snapshotBytes.decodeToString()).jsonObject
        assertEquals("ulyanovsk-second-cathedral-2026-pilot-local-v1", root.getValue("snapshot_id").jsonPrimitive.content)
        assertEquals("production", root.getValue("data_classification").jsonPrimitive.content)
        assertEquals("2026-01-01", root.getValue("coverage").jsonObject.getValue("from").jsonPrimitive.content)
        assertEquals("2026-12-31", root.getValue("coverage").jsonObject.getValue("to").jsonPrimitive.content)
        assertEquals(365, root.getValue("prayer_days").jsonArray.size)
        assertEquals(PILOT_LOCAL_SNAPSHOT_SHA256, snapshotBytes.sha256())
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte) }
