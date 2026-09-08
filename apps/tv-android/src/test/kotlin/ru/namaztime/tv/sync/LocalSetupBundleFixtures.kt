package ru.namaztime.tv.sync

import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import kotlinx.serialization.json.*
import ru.namaztime.tv.data.snapshot.*

// Small synthetic catalog, evidence, keys and schedules; never application assets.
internal class LocalSetupBundleFixture(directory: File, schemaTransform: (String) -> String = { it }) {
    val signer = QualifiedSnapshotSigner()
    val files = signer.localSetupTrustFiles().toMutableMap()
    val anchors = files.mapValues { fixtureHash(it.value) }
    val cityId = "city-synthetic"
    val revision = buildJsonObject {
        put("id", "registry-synthetic-v2")
        put("schema_version", 2)
        put("catalog_revision_id", "catalog-synthetic-v1")
        put("content_sha256", "f".repeat(64))
        put("created_at", "2026-09-08T12:00:00Z")
        put("created_by", "synthetic-test")
        put("reason", "Synthetic local setup fixture")
    }
    val documents = (0..1).map { index ->
        val base = qualifiedSnapshotDocument()
        val q = JsonObject(base.getValue("source").jsonObject.getValue("qualification").jsonObject + mapOf(
            "source_id" to JsonPrimitive("source-synthetic-$index"),
            "authority" to JsonObject(base.getValue("source").jsonObject.getValue("qualification").jsonObject
                .getValue("authority").jsonObject + mapOf("id" to JsonPrimitive("authority-synthetic-$index"))),
        )).withQualificationHash()
        JsonObject(base + mapOf(
            "snapshot_id" to JsonPrimitive("snapshot-synthetic-$index"),
            "source" to JsonObject(base.getValue("source").jsonObject + mapOf(
                "source_id" to q.getValue("source_id"), "qualification" to q,
            )),
            "mosque" to JsonObject(base.getValue("mosque").jsonObject + mapOf("region" to JsonPrimitive("Synthetic region"))),
        ))
    }

    init {
        val dbFile = File(directory, "fixture-catalog.sqlite")
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL("PRAGMA page_size=4096")
            db.execSQL("PRAGMA user_version=1")
            db.execSQL("PRAGMA application_id=1314149186")
            listOf(
                "CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID",
                "CREATE TABLE regions(id TEXT PRIMARY KEY, federal_subject_code TEXT NOT NULL, name TEXT NOT NULL, country_code TEXT NOT NULL) WITHOUT ROWID",
                "CREATE TABLE cities(id TEXT PRIMARY KEY, name TEXT NOT NULL, region_id TEXT NOT NULL REFERENCES regions(id), settlement_type TEXT NOT NULL, timezone TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, geographic_source_id TEXT NOT NULL, geographic_revision TEXT NOT NULL, geographic_license TEXT NOT NULL, aliases_json TEXT NOT NULL, country_code TEXT NOT NULL, population INTEGER NOT NULL, geographic_source TEXT NOT NULL, source_modified_date TEXT NOT NULL, fallback_policy_id TEXT NOT NULL) WITHOUT ROWID",
                "CREATE TABLE search_names(normalized_name TEXT NOT NULL, city_id TEXT NOT NULL REFERENCES cities(id), PRIMARY KEY(normalized_name,city_id)) WITHOUT ROWID",
                "CREATE INDEX cities_region_name ON cities(region_id,name,id)",
            ).forEach { db.execSQL(schemaTransform(it)) }
            mapOf("schema_version" to "namaztime-local-city-index/v1", "catalog_revision" to "catalog-synthetic-v1",
                "content_sha256" to "c".repeat(64), "normalizer" to "go-simple-lower-unicode-space/v1",
                "region_count" to "2", "city_count" to "3", "alias_count" to "4", "search_name_count" to "7").forEach { (key, value) ->
                db.execSQL("INSERT INTO metadata VALUES(?,?)", arrayOf(key, value))
            }
            db.execSQL("INSERT INTO regions VALUES('region-synthetic','RU-MOS','Synthetic region','RU')")
            db.execSQL("INSERT INTO regions VALUES('region-other','RU-TVE','Other region','RU')")
            listOf(
                Triple(cityId, "region-synthetic", listOf("Тестовый Город", "Synthetic Alias")),
                Triple("city-unavailable", "region-synthetic", listOf("Unavailable Alias")),
                Triple("city-same-name", "region-other", listOf("Other Alias")),
            ).forEach { (id, region, aliases) ->
                val name = if (id == "city-unavailable") "Unavailable city" else "Synthetic city"
                db.execSQL("INSERT INTO cities VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any>(
                    id, name, region, "PPL", "Europe/Moscow", 55.0, 37.0, "geonames:test-$id",
                    "2026-09-08", "CC BY 4.0", JsonArray(aliases.map(::JsonPrimitive)).toString(), "RU", 0,
                    "geonames", "2026-09-08", "",
                ))
                (aliases + name).forEach { alias ->
                    db.execSQL("INSERT INTO search_names VALUES(?,?)", arrayOf(alias.lowercase(), id))
                }
            }
        }
        files["catalog.sqlite"] = dbFile.readBytes()
        val policies = documents.mapIndexed { index, document ->
            val bytes = signer.sign(document)
            val rawSha = fixtureHash(bytes)
            val path = "snapshots/$rawSha.json"
            files[path] = bytes
            val q = document.getValue("source").jsonObject.getValue("qualification").jsonObject
            buildJsonObject {
                put("policy_id", "policy-synthetic-$index")
                put("authority_label", "Synthetic test authority")
                put("policy", buildJsonObject {
                    put("id", "policy-synthetic-$index")
                    put("kind", "timetable")
                    put("geographic_scope_id", "scope-synthetic-city")
                    put("authority_ids", JsonArray(listOf(JsonPrimitive("authority-synthetic-$index"))))
                    put("source_id", "source-synthetic-$index")
                    put("timetable_id", "table-synthetic-$index")
                    put("mosque_ids", JsonArray(emptyList()))
                    put("effective", q.getValue("coverage"))
                    put("qualification_id", q.getValue("qualification_id"))
                })
                put("scope", q.getValue("scope"))
                put("authorities", JsonArray(listOf(q.getValue("authority"))))
                put("source", buildJsonObject {
                    put("id", "source-synthetic-$index")
                    put("kind", "official_html")
                    put("authority_ids", JsonArray(listOf(JsonPrimitive("authority-synthetic-$index"))))
                    put("geographic_scope_id", "scope-synthetic-city")
                    put("canonical_url", q.getValue("canonical_url"))
                    put("status", "qualified")
                    put("fresh_through", q.getValue("fresh_through"))
                    put("qualification_id", q.getValue("qualification_id"))
                })
                put("qualification", q)
                put("timetable", buildJsonObject {
                    put("id", "table-synthetic-$index")
                    put("source_id", "source-synthetic-$index")
                    put("geographic_scope_id", "scope-synthetic-city")
                    put("mosque_id", document.getValue("mosque").jsonObject.getValue("id"))
                    put("timezone", "Europe/Moscow")
                    put("effective", q.getValue("coverage"))
                    put("published_snapshot_id", document.getValue("snapshot_id"))
                })
                put("source_overrides", JsonArray(emptyList()))
                put("snapshot", buildJsonObject {
                    put("snapshot_id", document.getValue("snapshot_id"))
                    put("path", path)
                    put("sha256", rawSha)
                    put("byte_length", bytes.size)
                    put("display_context", document.getValue("mosque"))
                })
            }
        }
        files["choices.json"] = buildJsonObject {
            put("schema_version", "namaztime-local-setup-choices/v1")
            put("registry_revision_id", "registry-synthetic-v2")
            put("policies", JsonArray(policies))
            put("bindings", JsonArray(policies.mapIndexed { index, policy -> buildJsonObject {
                put("city_id", cityId)
                put("choice_id", choiceId(index))
                put("policy_id", policy.getValue("policy_id"))
                put("display_label", "Synthetic city (Synthetic authority $index)")
                put("tier", "exact_city_timetable")
                put("effective", documents[index].getValue("coverage"))
            } }.sortedBy { it.getValue("choice_id").jsonPrimitive.content }))
        }.toString().encodeToByteArray()
        rehashManifest()
    }

    fun choiceId(index: Int): String = "schedule-choice-" + fixtureHash(
        "namaztime-city-schedule-choice/v1\u0000$cityId\u0000policy-synthetic-$index".encodeToByteArray(),
    )

    fun rehashManifest() {
        val root = buildJsonObject {
            put("schema_version", "namaztime-local-setup-bundle/v1")
            put("created_at", "2026-09-08T12:00:00Z")
            put("registry_revision", revision)
            put("registry_state", "active")
            put("admission", buildJsonObject {
                put("kind", "persistent_service_verified_local")
                put("verified_at", "2026-09-08T12:00:00Z")
                put("actor_id", "synthetic-test")
                put("reason", "Synthetic bundle fixture")
            })
            put("catalog", buildJsonObject {
                put("revision_id", "catalog-synthetic-v1")
                put("content_sha256", "c".repeat(64))
                put("region_count", 2); put("city_count", 3); put("alias_count", 4); put("search_name_count", 7)
                put("license", "CC BY 4.0"); put("license_url", "https://creativecommons.org/licenses/by/4.0/")
                put("attribution", "Synthetic geographic test fixture")
            })
            put("minimum_trust_revision", 3)
            put("files", JsonArray(files.filterKeys { it != "manifest.json" }.toSortedMap().map { (path, bytes) ->
                buildJsonObject { put("path", path); put("byte_length", bytes.size); put("sha256", fixtureHash(bytes)) }
            }))
        }
        val hash = fixtureHash(fixtureCanonical(root))
        files["manifest.json"] = JsonObject(root + mapOf(
            "bundle_id" to JsonPrimitive("local-setup-" + hash.take(32)), "manifest_sha256" to JsonPrimitive(hash),
        )).toString().encodeToByteArray()
    }

    fun source(): LocalSetupBundleFiles = object : LocalSetupBundleFiles {
        override fun paths(): Set<String> = files.keys.toSet()
        override fun open(path: String) = ByteArrayInputStream(files[path] ?: throw FileNotFoundException(path))
    }
}
