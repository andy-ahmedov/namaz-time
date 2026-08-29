package ru.namaztime.tv.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

fun interface SnapshotSyncRunner {
    suspend fun run(): SnapshotSyncResult
}

interface SnapshotSyncRunnerProvider {
    fun snapshotSyncRunner(): SnapshotSyncRunner?
}

class SnapshotSyncWorker(
    applicationContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(applicationContext, parameters) {
    override suspend fun doWork(): Result {
        val runner = (applicationContext as? SnapshotSyncRunnerProvider)
            ?.snapshotSyncRunner()
            ?: return Result.failure(
                workDataOf(SYNC_CODE_OUTPUT to "sync_runner_unavailable"),
            )
        return when (val result = runner.run()) {
            is SnapshotSyncResult.Updated,
            is SnapshotSyncResult.NotModified,
            -> Result.success()
            is SnapshotSyncResult.Retry -> Result.retry()
            is SnapshotSyncResult.Rejected -> Result.failure(
                workDataOf(SYNC_CODE_OUTPUT to result.code),
            )
            SnapshotSyncResult.AuthFailure -> Result.failure(
                workDataOf(SYNC_CODE_OUTPUT to "auth_failure"),
            )
        }
    }
}

const val SYNC_CODE_OUTPUT = "sync_code"
