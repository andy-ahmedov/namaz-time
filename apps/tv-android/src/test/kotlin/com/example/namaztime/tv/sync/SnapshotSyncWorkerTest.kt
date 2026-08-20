package com.example.namaztime.tv.sync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = SyncWorkerTestApplication::class)
class SnapshotSyncWorkerTest {
    @After
    fun resetRunner() {
        SyncWorkerTestApplication.runner = null
    }

    @Test
    fun mapsSuccessfulRetryableAndRejectedOutcomesWithoutExposingSecrets() = runTest {
        val cases = listOf(
            SnapshotSyncResult.Updated("snapshot-fixture-0001", null) to ListenableWorker.Result.success(),
            SnapshotSyncResult.NotModified("snapshot-fixture-0001") to ListenableWorker.Result.success(),
            SnapshotSyncResult.Retry("manifest_io") to ListenableWorker.Result.retry(),
            SnapshotSyncResult.Rejected("snapshot_signature_invalid") to
                ListenableWorker.Result.failure(androidx.work.workDataOf("sync_code" to "snapshot_signature_invalid")),
            SnapshotSyncResult.AuthFailure to
                ListenableWorker.Result.failure(androidx.work.workDataOf("sync_code" to "auth_failure")),
        )
        cases.forEach { (syncResult, expected) ->
            SyncWorkerTestApplication.runner = SnapshotSyncRunner { syncResult }
            val worker = TestListenableWorkerBuilder.from(
                context(),
                SnapshotSyncWorker::class.java,
            ).build()

            val actual = worker.doWork()

            assertEquals(expected, actual)
        }
    }

    @Test
    fun missingRemoteProvisioningIsAQuietNoOpForLocalOnlyMode() = runTest {
        SyncWorkerTestApplication.runner = null
        val worker = TestListenableWorkerBuilder.from(
            context(),
            SnapshotSyncWorker::class.java,
        ).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()
}

class SyncWorkerTestApplication : Application(), SnapshotSyncRunnerProvider {
    override fun snapshotSyncRunner(): SnapshotSyncRunner? = runner

    companion object {
        var runner: SnapshotSyncRunner? = null
    }
}
