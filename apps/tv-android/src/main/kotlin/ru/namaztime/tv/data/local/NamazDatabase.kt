package ru.namaztime.tv.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SnapshotEntity::class,
        PrayerDayEntity::class,
        IqamahRuleEntity::class,
        IqamahDateOverrideEntity::class,
        JumuahSessionEntity::class,
        CampaignEntity::class,
        ThemeEntity::class,
        SnapshotSelectionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class NamazDatabase : RoomDatabase() {
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        @Volatile
        private var instance: NamazDatabase? = null

        fun open(context: Context): NamazDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NamazDatabase::class.java,
                "namaz-time.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE prayer_days ADD COLUMN flagsJson TEXT NOT NULL DEFAULT '[]'",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE snapshots ADD COLUMN authorityBranch TEXT")
        db.execSQL("ALTER TABLE snapshots ADD COLUMN canonicalUrl TEXT")
        db.execSQL("ALTER TABLE snapshots ADD COLUMN calculationProfile TEXT")
        db.execSQL("ALTER TABLE snapshots ADD COLUMN licenseReference TEXT")
        db.execSQL("ALTER TABLE snapshots ADD COLUMN attribution TEXT")
        db.execSQL(
            "ALTER TABLE snapshots ADD COLUMN approvalStatus TEXT NOT NULL DEFAULT 'approved'",
        )
        db.execSQL("ALTER TABLE snapshots ADD COLUMN approvalNote TEXT")
        db.execSQL("ALTER TABLE themes ADD COLUMN landscapeAssetJson TEXT")
        db.execSQL("ALTER TABLE themes ADD COLUMN portraitAssetJson TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite cannot relax NOT NULL in place. Dropping a referenced parent
        // cascades child deletion even with deferred foreign keys, so preserve
        // the fixed set of child tables before rebuilding the parent. Room runs
        // this migration atomically; any failure rolls back all tables/pointers.
        val children = listOf("prayer_days", "iqamah_rules", "iqamah_date_overrides", "jumuah_sessions", "campaigns", "themes", "snapshot_selection")
        val definitions = children.associateWith { table ->
            db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { cursor ->
                check(cursor.moveToFirst()) { "Missing migration table: $table" }
                cursor.getString(0)
            }
        }
        val indexes = children.flatMap { table ->
            db.query("SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL", arrayOf(table)).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
        }
        children.forEach { table ->
            db.execSQL("CREATE TEMP TABLE migration_3_4_$table AS SELECT * FROM `$table`")
            db.execSQL("DROP TABLE `$table`")
        }
        db.execSQL("""
            CREATE TABLE snapshots_v4 (
                snapshotId TEXT NOT NULL PRIMARY KEY, schemaVersion TEXT NOT NULL,
                dataClassification TEXT NOT NULL, generatedAt TEXT NOT NULL,
                mosqueId TEXT NOT NULL, mosqueName TEXT NOT NULL, countryCode TEXT,
                region TEXT, locality TEXT, timezoneId TEXT NOT NULL,
                sourceId TEXT NOT NULL, sourceKind TEXT NOT NULL, authorityName TEXT NOT NULL,
                authorityBranch TEXT, geographicScope TEXT NOT NULL, canonicalUrl TEXT,
                retrievedAt TEXT NOT NULL, sourceEffectiveFrom TEXT NOT NULL, sourceEffectiveTo TEXT NOT NULL,
                rawSha256 TEXT NOT NULL, parserVersion TEXT NOT NULL, calculationProfile TEXT,
                licenseReference TEXT, attribution TEXT, approvalId TEXT, approvalStatus TEXT,
                approvedBy TEXT, approvedAt TEXT, approvalScope TEXT, approvalNote TEXT,
                qualificationId TEXT, qualificationSha256 TEXT, qualificationJson TEXT,
                coverageFrom TEXT NOT NULL, coverageTo TEXT NOT NULL, canonicalSha256 TEXT NOT NULL,
                signingKeyId TEXT NOT NULL, signatureEd25519Base64 TEXT NOT NULL
            )
        """.trimIndent())
        val legacyColumns = "snapshotId, schemaVersion, dataClassification, generatedAt, mosqueId, mosqueName, countryCode, region, locality, timezoneId, sourceId, sourceKind, authorityName, authorityBranch, geographicScope, canonicalUrl, retrievedAt, sourceEffectiveFrom, sourceEffectiveTo, rawSha256, parserVersion, calculationProfile, licenseReference, attribution, approvalId, approvalStatus, approvedBy, approvedAt, approvalScope, approvalNote, coverageFrom, coverageTo, canonicalSha256, signingKeyId, signatureEd25519Base64"
        db.execSQL("INSERT INTO snapshots_v4 ($legacyColumns) SELECT $legacyColumns FROM snapshots")
        db.execSQL("DROP TABLE snapshots")
        db.execSQL("ALTER TABLE snapshots_v4 RENAME TO snapshots")
        db.execSQL("CREATE INDEX index_snapshots_generatedAt ON snapshots (generatedAt)")
        children.forEach { table ->
            db.execSQL(definitions.getValue(table))
            db.execSQL("INSERT INTO `$table` SELECT * FROM temp.migration_3_4_$table")
            db.execSQL("DROP TABLE temp.migration_3_4_$table")
        }
        indexes.forEach(db::execSQL)
        db.query("PRAGMA foreign_key_check").use { check(it.count == 0) { "Foreign key violation after admission migration" } }
    }
}
