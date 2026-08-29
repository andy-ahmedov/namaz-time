package ru.namaztime.tv.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class SnapshotSyncSchedulerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun localOnlyModeSchedulesNoNetworkWork() {
        SnapshotSyncScheduler(workManager).scheduleIfProvisioned(isProvisioned = false)

        assertEquals(0, workManager.getWorkInfosByTag(SNAPSHOT_SYNC_WORK_TAG).get().size)
    }

    @Test
    fun provisionedModeSchedulesOneImmediateAndOneUniquePeriodicSync() {
        val scheduler = SnapshotSyncScheduler(workManager)

        scheduler.scheduleIfProvisioned(isProvisioned = true)
        scheduler.scheduleIfProvisioned(isProvisioned = true)

        assertEquals(2, workManager.getWorkInfosByTag(SNAPSHOT_SYNC_WORK_TAG).get().size)
        assertEquals(
            1,
            workManager.getWorkInfosForUniqueWork(SNAPSHOT_SYNC_IMMEDIATE_WORK).get().size,
        )
        assertEquals(
            1,
            workManager.getWorkInfosForUniqueWork(SNAPSHOT_SYNC_PERIODIC_WORK).get().size,
        )
    }

    @Test
    fun removingProvisioningCancelsPreviouslyScheduledRemoteWork() {
        val scheduler = SnapshotSyncScheduler(workManager)
        scheduler.scheduleIfProvisioned(isProvisioned = true)

        scheduler.scheduleIfProvisioned(isProvisioned = false)

        assertEquals(
            setOf(WorkInfo.State.CANCELLED),
            workManager.getWorkInfosByTag(SNAPSHOT_SYNC_WORK_TAG).get().map { it.state }.toSet(),
        )
    }
}
