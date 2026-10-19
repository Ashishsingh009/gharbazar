package com.ashish.gharbazaar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.ashish.gharbazaar.data.local.LocalRepository
import com.ashish.gharbazaar.data.remote.AgentClient
import com.ashish.gharbazaar.data.remote.FixtureRemote
import com.ashish.gharbazaar.domain.SyncCoordinator
import com.ashish.gharbazaar.domain.SyncState

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = LocalRepository(applicationContext)
        val remote = if (repository.agentEnabled()) {
            AgentClient(inputData.getString(KEY_BASE_URL).orEmpty().ifBlank { "http://10.0.2.2:8000" })
        } else {
            FixtureRemote()
        }
        val coordinator = SyncCoordinator(remote)
        repository.pendingOperations().forEach(coordinator::enqueue)
        val report = coordinator.flush()
        report.acknowledged.forEach { operation ->
            repository.enqueue(operation.copy(state = SyncState.ACKNOWLEDGED))
        }
        return if (report.pending.isEmpty()) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_BASE_URL = "base_url"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("ghar-sync", ExistingWorkPolicy.KEEP, request)
        }
    }
}
