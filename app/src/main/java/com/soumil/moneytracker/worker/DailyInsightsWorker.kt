package com.soumil.moneytracker.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.soumil.moneytracker.MoneyTrackerApp
import java.util.concurrent.TimeUnit

class DailyInsightsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyTrackerApp
        app.container.repository.refreshRecurringSuggestions()
        return Result.success()
    }
}

object InsightsScheduler {
    private const val UNIQUE_WORK_NAME = "daily_insights"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyInsightsWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            InsightsScheduler.schedule(context)
        }
    }
}

