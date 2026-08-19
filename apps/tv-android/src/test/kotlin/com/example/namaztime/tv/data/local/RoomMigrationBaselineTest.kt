package com.example.namaztime.tv.data.local

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMigrationBaselineTest {
    @Test
    fun versionOneSchemaIsExportedForFutureMigrationTests() {
        val schema = File(
            "schemas/com.example.namaztime.tv.data.local.NamazDatabase/1.json",
        )

        assertTrue("Room v1 schema must be committed", schema.isFile)
        val root = Json.parseToJsonElement(schema.readText()).jsonObject
        assertEquals(1, root.getValue("formatVersion").jsonPrimitive.content.toInt())

        val database = root.getValue("database").jsonObject
        assertEquals(1, database.getValue("version").jsonPrimitive.content.toInt())
        assertTrue(
            database.getValue("identityHash").jsonPrimitive.content.matches(
                Regex("^[a-f0-9]{32}$"),
            ),
        )

        val entities = database.getValue("entities").jsonArray
            .associateBy { it.jsonObject.getValue("tableName").jsonPrimitive.content }
        assertEquals(
            setOf(
                "snapshots",
                "prayer_days",
                "iqamah_rules",
                "iqamah_date_overrides",
                "jumuah_sessions",
                "campaigns",
                "themes",
                "snapshot_selection",
            ),
            entities.keys,
        )

        val snapshotColumns = entities.getValue("snapshots").jsonObject
            .getValue("fields").jsonArray
            .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
            .toSet()
        assertTrue(
            snapshotColumns.containsAll(
                setOf(
                    "snapshotId",
                    "timezoneId",
                    "sourceKind",
                    "rawSha256",
                    "approvalId",
                    "canonicalSha256",
                    "signingKeyId",
                    "signatureEd25519Base64",
                ),
            ),
        )

        val selection = entities.getValue("snapshot_selection").jsonObject
        val selectionForeignKeys = selection.getValue("foreignKeys").jsonArray
        assertEquals(2, selectionForeignKeys.size)
        selectionForeignKeys.forEach { foreignKey ->
            assertEquals(
                "snapshots",
                foreignKey.jsonObject.getValue("table").jsonPrimitive.content,
            )
            assertEquals(
                "RESTRICT",
                foreignKey.jsonObject.getValue("onDelete").jsonPrimitive.content,
            )
        }
        assertEquals(2, selection.getValue("indices").jsonArray.size)
    }

    @Test
    fun versionTwoAddsPrayerDayFlagsAndKeepsVersionOneBaseline() {
        val schema = File(
            "schemas/com.example.namaztime.tv.data.local.NamazDatabase/2.json",
        )

        assertTrue("Room v2 schema must be committed", schema.isFile)
        val root = Json.parseToJsonElement(schema.readText()).jsonObject
        val database = root.getValue("database").jsonObject
        assertEquals(2, database.getValue("version").jsonPrimitive.content.toInt())
        val prayerDays = database.getValue("entities").jsonArray
            .single { it.jsonObject.getValue("tableName").jsonPrimitive.content == "prayer_days" }
            .jsonObject
        val flags = prayerDays.getValue("fields").jsonArray.single {
            it.jsonObject.getValue("columnName").jsonPrimitive.content == "flagsJson"
        }.jsonObject
        assertEquals("'[]'", flags.getValue("defaultValue").jsonPrimitive.content)
    }

    @Test
    fun versionThreePersistsOptionalProvenanceAndThemeAssetReferences() {
        val schema = File(
            "schemas/com.example.namaztime.tv.data.local.NamazDatabase/3.json",
        )

        assertTrue("Room v3 schema must be committed", schema.isFile)
        val database = Json.parseToJsonElement(schema.readText()).jsonObject
            .getValue("database").jsonObject
        assertEquals(3, database.getValue("version").jsonPrimitive.content.toInt())
        val entities = database.getValue("entities").jsonArray.associateBy {
            it.jsonObject.getValue("tableName").jsonPrimitive.content
        }
        val snapshotFields = entities.getValue("snapshots").jsonObject.getValue("fields").jsonArray
            .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
        assertTrue(
            snapshotFields.containsAll(
                setOf(
                    "authorityBranch",
                    "canonicalUrl",
                    "calculationProfile",
                    "licenseReference",
                    "attribution",
                    "approvalStatus",
                    "approvalNote",
                ),
            ),
        )
        val themeFields = entities.getValue("themes").jsonObject.getValue("fields").jsonArray
            .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
        assertTrue(themeFields.containsAll(setOf("landscapeAssetJson", "portraitAssetJson")))
    }
}
