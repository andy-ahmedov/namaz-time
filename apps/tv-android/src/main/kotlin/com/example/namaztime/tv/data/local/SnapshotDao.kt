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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSelection(selection: SnapshotSelectionEntity)

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
}
