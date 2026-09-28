package com.moneytracker.app.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneytracker.app.MoneyTrackerApp
import java.util.concurrent.TimeUnit

import com.moneytracker.app.data.local.EmailPreferences
import com.moneytracker.app.domain.usecase.SyncGmailAlertsUseCase
import org.koin.core.component.KoinComponent

class EmailSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val emailPrefs: EmailPreferences? = null,
    private val syncAlertsUseCase: SyncGmailAlertsUseCase? = null,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val preferences: EmailPreferences by lazy {
        emailPrefs ?: getKoin().getOrNull<EmailPreferences>() ?: (applicationContext as? MoneyTrackerApp)?.container?.emailPreferences ?: EmailPreferences(applicationContext)
    }
    private val syncUseCase: SyncGmailAlertsUseCase by lazy {
        syncAlertsUseCase ?: getKoin().getOrNull<SyncGmailAlertsUseCase>() ?: (applicationContext as? MoneyTrackerApp)?.container?.syncGmailAlertsUseCase ?: error("SyncGmailAlertsUseCase not available")
    }

    override suspend fun doWork(): Result {
        if (!preferences.isEmailSyncEnabled.value) {
            Log.d(TAG, "Email sync is disabled by user setting. Skipping.")
            return Result.success()
        }

        val email = preferences.emailAddress.value
        val password = preferences.appPassword.value
        if (email.isBlank() || password.isBlank()) {
            Log.d(TAG, "Email sync credentials not configured. Skipping.")
            return Result.success()
        }

        return try {
            val syncResult = syncUseCase.syncRecentEmails(maxMessages = 25)
            syncResult.fold(
                onSuccess = { importedCount ->
                    Log.i(TAG, "Periodic email sync succeeded. Imported $importedCount transactions.")
                    Result.success()
                },
                onFailure = { error ->
                    Log.w(TAG, "Periodic email sync encountered error: ${error.message}", error)
                    if (runAttemptCount < 3) Result.retry() else Result.success()
                },
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Periodic email sync failed with exception", t)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "EmailSyncWorker"
        const val UNIQUE_WORK_NAME = "periodic_email_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<EmailSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
