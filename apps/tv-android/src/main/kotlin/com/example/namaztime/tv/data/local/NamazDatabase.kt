package com.example.namaztime.tv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

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
    version = 1,
    exportSchema = true,
)
abstract class NamazDatabase : RoomDatabase() {
    abstract fun snapshotDao(): SnapshotDao
}
