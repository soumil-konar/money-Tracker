package com.moneytracker.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneytracker.app.MoneyTrackerApp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

import com.moneytracker.app.data.repo.FinanceRepository
import com.moneytracker.app.domain.usecase.GenerateSpendingInsightsUseCase
import com.moneytracker.app.notification.AppNotificationManager
import org.koin.core.component.KoinComponent

class DailyInsightsWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repository: FinanceRepository? = null,
    private val notificationManager: AppNotificationManager? = null,
    private val generateSpendingInsightsUseCase: GenerateSpendingInsightsUseCase? = null,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val repo: FinanceRepository by lazy {
        repository ?: getKoin().getOrNull<FinanceRepository>() ?: (applicationContext as? MoneyTrackerApp)?.container?.repository ?: error("FinanceRepository not available")
    }
    private val notifManager: AppNotificationManager by lazy {
        notificationManager ?: getKoin().getOrNull<AppNotificationManager>() ?: (applicationContext as? MoneyTrackerApp)?.container?.appNotificationManager ?: AppNotificationManager(applicationContext)
    }
    private val insightsUseCase: GenerateSpendingInsightsUseCase by lazy {
        generateSpendingInsightsUseCase ?: getKoin().getOrNull<GenerateSpendingInsightsUseCase>() ?: repo.generateSpendingInsightsUseCase
    }

    override suspend fun doWork(): Result {
        // 1. Refresh recurring suggestions
        repo.refreshRecurringSuggestions()

        // 2. Dispatch Daily Recap if user has recorded transactions today and has permission
        if (notifManager.hasNotificationPermission()) {
            val recap = insightsUseCase.getDailyRecapData()
            if (recap.txCount > 0) {
                notifManager.showDailyRecap(
                    spentToday = recap.spentToday,
                    txCount = recap.txCount,
                    remainingBuffer = recap.remainingBuffer,
                )
            }

            // 3. Inspect upcoming bills due within 24 to 48 hours
            val now = System.currentTimeMillis()
            val windowStart = now + TimeUnit.HOURS.toMillis(24)
            val windowEnd = now + TimeUnit.HOURS.toMillis(48)
            val upcomingBills = insightsUseCase.getUpcomingBillReminders(windowStart, windowEnd)
            val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            for (bill in upcomingBills) {
                notifManager.showBillReminder(
                    merchant = bill.merchant,
                    amount = bill.amount,
                    dueDateFormatted = dateFormat.format(Date(bill.scheduledForMillis)),
                )
            }
        }

        return Result.success()
    }
}

object InsightsScheduler {
    const val UNIQUE_WORK_NAME = "daily_insights"
    const val TARGET_EVENING_HOUR = 21 // 9:00 PM

    fun calculateInitialDelayToNinePm(nowMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, TARGET_EVENING_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    fun schedule(context: Context) {
        val initialDelayMillis = calculateInitialDelayToNinePm()
        val request = PeriodicWorkRequestBuilder<DailyInsightsWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )

        EmailSyncWorker.schedule(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            InsightsScheduler.schedule(context)
        }
    }
}
