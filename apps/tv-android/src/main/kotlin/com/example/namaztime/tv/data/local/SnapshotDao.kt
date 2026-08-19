package com.example.namaztime.tv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SnapshotDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshot(snapshot: SnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPrayerDays(days: List<PrayerDayEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIqamahRules(rules: List<IqamahRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIqamahOverrides(overrides: List<IqamahDateOverrideEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJumuahSessions(sessions: List<JumuahSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCampaigns(campaigns: List<CampaignEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTheme(theme: ThemeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSelection(selection: SnapshotSelectionEntity)

    @Query("SELECT * FROM snapshot_selection WHERE slot = :slot LIMIT 1")
    suspend fun getSelection(slot: String = DISPLAY_SELECTION_SLOT): SnapshotSelectionEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM snapshots WHERE snapshotId = :snapshotId)")
    suspend fun snapshotExists(snapshotId: String): Boolean

    @Query("SELECT * FROM snapshots WHERE snapshotId = :snapshotId LIMIT 1")
    suspend fun getSnapshot(snapshotId: String): SnapshotEntity?

    @Query("SELECT * FROM themes WHERE snapshotId = :snapshotId LIMIT 1")
    suspend fun getTheme(snapshotId: String): ThemeEntity?

    @Query("SELECT COUNT(*) FROM snapshots")
    suspend fun countSnapshots(): Int

    @Query("SELECT COUNT(*) FROM prayer_days WHERE snapshotId = :snapshotId")
    suspend fun countPrayerDays(snapshotId: String): Int

    @Query("SELECT COUNT(*) FROM iqamah_rules WHERE snapshotId = :snapshotId")
    suspend fun countIqamahRules(snapshotId: String): Int

    @Query("SELECT COUNT(*) FROM iqamah_date_overrides WHERE snapshotId = :snapshotId")
    suspend fun countIqamahOverrides(snapshotId: String): Int

    @Query("SELECT COUNT(*) FROM jumuah_sessions WHERE snapshotId = :snapshotId")
    suspend fun countJumuahSessions(snapshotId: String): Int

    @Query("SELECT COUNT(*) FROM campaigns WHERE snapshotId = :snapshotId")
    suspend fun countCampaigns(snapshotId: String): Int

    @Query("SELECT COUNT(*) FROM themes WHERE snapshotId = :snapshotId")
    suspend fun countThemes(snapshotId: String): Int

    @Query(
        "SELECT flagsJson FROM prayer_days WHERE snapshotId = :snapshotId AND localDate = :localDate",
    )
    suspend fun getPrayerDayFlags(snapshotId: String, localDate: String): String?

    @Query(
        """
        SELECT snapshots.*
        FROM snapshots
        INNER JOIN snapshot_selection
            ON snapshot_selection.activeSnapshotId = snapshots.snapshotId
        WHERE snapshot_selection.slot = :slot
        """,
    )
    fun observeActiveSnapshot(
        slot: String = DISPLAY_SELECTION_SLOT,
    ): Flow<SnapshotEntity?>

    @Query(
        """
        SELECT *
        FROM prayer_days
        WHERE snapshotId = :snapshotId
        ORDER BY localDate ASC
        """,
    )
    fun observePrayerDays(snapshotId: String): Flow<List<PrayerDayEntity>>

    @Query(
        """
        SELECT * FROM prayer_days
        WHERE snapshotId = :snapshotId
        ORDER BY localDate ASC
        """,
    )
    suspend fun getPrayerDays(snapshotId: String): List<PrayerDayEntity>
}
