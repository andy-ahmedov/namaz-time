package com.example.namaztime.tv.data.local

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
    version = 3,
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
