package com.brandon.expensestracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brandon.expensestracker.ExpenseTrackerApplication

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as ExpenseTrackerApplication).container.repository
        return repo.syncCloudData(forcePull = false).fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
