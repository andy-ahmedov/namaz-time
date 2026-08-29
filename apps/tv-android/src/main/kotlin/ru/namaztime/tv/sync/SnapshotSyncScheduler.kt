package ru.namaztime.tv.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SnapshotSyncScheduler(private val workManager: WorkManager) {
    fun scheduleIfProvisioned(isProvisioned: Boolean) {
        if (!isProvisioned) {
            workManager.cancelUniqueWork(SNAPSHOT_SYNC_IMMEDIATE_WORK)
            workManager.cancelUniqueWork(SNAPSHOT_SYNC_PERIODIC_WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val immediate = OneTimeWorkRequestBuilder<SnapshotSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(SNAPSHOT_SYNC_WORK_TAG)
            .build()
        val periodic = PeriodicWorkRequestBuilder<SnapshotSyncWorker>(
            24,
            TimeUnit.HOURS,
            6,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(SNAPSHOT_SYNC_WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            SNAPSHOT_SYNC_IMMEDIATE_WORK,
            ExistingWorkPolicy.KEEP,
            immediate,
        )
        workManager.enqueueUniquePeriodicWork(
            SNAPSHOT_SYNC_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
    }
}

const val SNAPSHOT_SYNC_WORK_TAG = "snapshot-sync"
const val SNAPSHOT_SYNC_IMMEDIATE_WORK = "snapshot-sync-immediate"
const val SNAPSHOT_SYNC_PERIODIC_WORK = "snapshot-sync-periodic"
