package ru.namaztime.tv.sync

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File
import java.time.ZoneId
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal class LocalSetupCityIndex(file: File, catalog: LocalSetupCatalog) : Closeable {
    private val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)

    init {
        try {
            requireBundle(number("PRAGMA user_version") == 1L && number("PRAGMA application_id") == 0x4e545342L &&
                number("PRAGMA page_size") == 4096L && text("PRAGMA encoding") == "UTF-8" &&
                text("PRAGMA quick_check") == "ok", "setup_local_index_invalid")
            val tables = mutableSetOf<String>()
            db.rawQuery("SELECT type,name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getString(0); val name = cursor.getString(1); val sql = cursor.getString(2)
                    requireBundle((type == "table" && name in COLUMNS && sql.contains("WITHOUT ROWID", true)) ||
                        (type == "index" && name == "cities_region_name"), "setup_local_index_schema_invalid")
                    if (type == "table") tables += name
                }
            }
            requireBundle(tables == COLUMNS.keys, "setup_local_index_schema_invalid")
            COLUMNS.forEach { (table, columns) ->
                val actual = mutableListOf<String>()
                db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1)
                        val type = when {
                            table == "cities" && name in setOf("latitude", "longitude") -> "REAL"
                            table == "cities" && name == "population" -> "INTEGER"
                            else -> "TEXT"
                        }
                        val keyPosition = if (table == "search_names") columns.indexOf(name) + 1
                            else if (name == columns.first()) 1 else 0
                        requireBundle(cursor.getString(2) == type && cursor.getInt(3) == 1 &&
                            cursor.isNull(4) && cursor.getInt(5) == keyPosition, "setup_local_index_schema_invalid")
                        actual += name
                    }
                }
                requireBundle(actual == columns, "setup_local_index_schema_invalid")
                val foreignKeys = mutableListOf<List<String>>()
                db.rawQuery("PRAGMA foreign_key_list($table)", null).use { cursor ->
                    while (cursor.moveToNext()) foreignKeys += (0..7).map(cursor::getString)
                }
                val expectedForeignKeys = when (table) {
                    "cities" -> listOf(listOf("0", "0", "regions", "region_id", "id", "NO ACTION", "NO ACTION", "NONE"))
                    "search_names" -> listOf(listOf("0", "0", "cities", "city_id", "id", "NO ACTION", "NO ACTION", "NONE"))
                    else -> emptyList()
                }
                requireBundle(foreignKeys == expectedForeignKeys, "setup_local_index_schema_invalid")
            }
            val queryIndex = mutableListOf<String>()
            db.rawQuery("PRAGMA index_xinfo(cities_region_name)", null).use { cursor ->
                while (cursor.moveToNext()) {
                    requireBundle(cursor.getInt(3) == 0 && cursor.getString(4) == "BINARY" && cursor.getInt(5) == 1,
                        "setup_local_index_schema_invalid")
                    queryIndex += cursor.getString(2)
                }
            }
            requireBundle(queryIndex == listOf("region_id", "name", "id"), "setup_local_index_schema_invalid")
            val metadata = mutableMapOf<String, String>()
            db.rawQuery("SELECT key,value FROM metadata", null).use { cursor ->
                while (cursor.moveToNext()) metadata[cursor.getString(0)] = cursor.getString(1)
            }
            requireBundle(metadata == mapOf(
                "schema_version" to "namaztime-local-city-index/v1", "catalog_revision" to catalog.revisionId,
                "content_sha256" to catalog.contentSha256, "normalizer" to "go-simple-lower-unicode-space/v1",
                "region_count" to catalog.regionCount.toString(), "city_count" to catalog.cityCount.toString(),
                "alias_count" to catalog.aliasCount.toString(), "search_name_count" to catalog.searchNameCount.toString(),
            ) && number("SELECT COUNT(*) FROM regions") == catalog.regionCount &&
                number("SELECT COUNT(*) FROM cities") == catalog.cityCount &&
                number("SELECT COUNT(*) FROM search_names") == catalog.searchNameCount &&
                number("SELECT COUNT(*) FROM cities WHERE fallback_policy_id != '' OR country_code != 'RU'") == 0L &&
                number("SELECT COUNT(*) FROM cities c LEFT JOIN regions r ON c.region_id=r.id WHERE r.id IS NULL") == 0L &&
                number("SELECT COUNT(*) FROM search_names n LEFT JOIN cities c ON n.city_id=c.id WHERE c.id IS NULL") == 0L,
                "setup_local_index_catalog_mismatch")
            var aliasCount = 0L
            db.rawQuery("SELECT aliases_json FROM cities", null).use { cursor ->
                while (cursor.moveToNext()) aliasCount += bundleJson.parseToJsonElement(cursor.getString(0)).jsonArray.size
            }
            requireBundle(aliasCount == catalog.aliasCount, "setup_local_index_catalog_mismatch")
        } catch (failure: Throwable) {
            db.close()
            throw failure
        }
    }

    fun search(normalized: String): List<CanonicalCityCandidate> {
        if (normalized.isEmpty()) return emptyList()
        return query("JOIN search_names n ON n.city_id=c.id WHERE n.normalized_name=? ORDER BY r.federal_subject_code COLLATE BINARY,c.name COLLATE BINARY,c.id COLLATE BINARY", arrayOf(normalized))
    }

    fun city(id: String): CanonicalCityCandidate? = query("WHERE c.id=?", arrayOf(id)).singleOrNull()
    fun regionId(id: String): String? = db.rawQuery("SELECT region_id FROM cities WHERE id=?", arrayOf(id)).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    private fun query(suffix: String, args: Array<String>): List<CanonicalCityCandidate> =
        db.rawQuery("SELECT c.id,c.name,c.aliases_json,r.federal_subject_code,r.name,c.settlement_type,c.timezone,c.latitude,c.longitude,c.geographic_source_id,c.geographic_revision,c.geographic_license FROM cities c JOIN regions r ON c.region_id=r.id $suffix", args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.candidate()) }
        }

    private fun Cursor.candidate(): CanonicalCityCandidate = CanonicalCityCandidate(
        getString(0), getString(1), bundleJson.parseToJsonElement(getString(2)).jsonArray.map {
            requireBundle(it.jsonPrimitive.isString, "setup_local_index_catalog_mismatch"); it.jsonPrimitive.content
        }, getString(3), getString(4), getString(5), getString(6), getDouble(7), getDouble(8), getString(9), getString(10), getString(11),
    ).also {
        requireBundle(it.id.isNotBlank() && it.canonicalName.isNotBlank() && it.aliases.all(String::isNotBlank) &&
            it.federalSubjectCode.matches(Regex("RU-[A-Z]{2,3}")) && it.federalSubjectName.isNotBlank() &&
            it.timezone in ZoneId.getAvailableZoneIds() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0,
            "setup_local_index_catalog_mismatch")
    }

    private fun number(sql: String): Long = db.rawQuery(sql, null).use { requireBundle(it.moveToFirst(), "setup_local_index_invalid"); it.getLong(0) }
    private fun text(sql: String): String = db.rawQuery(sql, null).use { requireBundle(it.moveToFirst(), "setup_local_index_invalid"); it.getString(0) }
    override fun close() = db.close()

    private companion object {
        val COLUMNS = mapOf(
            "metadata" to listOf("key", "value"),
            "regions" to listOf("id", "federal_subject_code", "name", "country_code"),
            "cities" to listOf("id", "name", "region_id", "settlement_type", "timezone", "latitude", "longitude", "geographic_source_id", "geographic_revision", "geographic_license", "aliases_json", "country_code", "population", "geographic_source", "source_modified_date", "fallback_policy_id"),
            "search_names" to listOf("normalized_name", "city_id"),
        )
    }
}

internal fun normalizeLocalSetupQuery(value: String): String = buildString {
    var pendingSpace = false
    var offset = 0
    while (offset < value.length) {
        val point = value.codePointAt(offset)
        offset += Character.charCount(point)
        val whitespace = point in 0x09..0x0d || point in 0x2000..0x200a ||
            point in setOf(0x20, 0x85, 0xa0, 0x1680, 0x2028, 0x2029, 0x202f, 0x205f, 0x3000)
        if (whitespace) pendingSpace = isNotEmpty() else {
            if (pendingSpace) append(' ')
            appendCodePoint(Character.toLowerCase(point))
            pendingSpace = false
        }
    }
}
