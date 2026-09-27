package com.moneytracker.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.moneytracker.app.MainActivity
import com.moneytracker.app.R
import com.moneytracker.app.data.db.FinanceDatabase
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import com.moneytracker.app.ui.asCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BudgetWidgetProvider : AppWidgetProvider() {

    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        providerScope.launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_BUDGET_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, BudgetWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isNotEmpty()) {
                val pendingResult = goAsync()
                providerScope.launch {
                    try {
                        updateWidgets(context, appWidgetManager, allWidgetIds)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val database = FinanceDatabase.create(context)
        val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val budget = database.budgetDao().getOverallBudget(currentMonthKey)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonthMillis = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endOfMonthMillis = calendar.timeInMillis

        val monthTransactions = database.transactionDao().getTransactionsBetween(startOfMonthMillis, endOfMonthMillis)

        val monthSpent = monthTransactions.asSequence()
            .filter {
                (it.status == TransactionStatus.POSTED) &&
                    (it.direction == TransactionDirection.DEBIT) &&
                    (it.category != TransactionCategory.TRANSFER) &&
                    it.countsTowardBudget
            }
            .sumOf { it.amount }

        val budgetLimit = budget?.amountLimit ?: 0.0
        val today = Calendar.getInstance()
        val maxDays = today.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = today.get(Calendar.DAY_OF_MONTH)
        val daysRemaining = (maxDays - currentDay + 1).coerceAtLeast(1)

        val remainingBudget = if (budgetLimit > 0) (budgetLimit - monthSpent).coerceAtLeast(0.0) else 0.0
        val safeDailySpend = if (budgetLimit > 0) remainingBudget / daysRemaining else 0.0
        val percentUsed = if (budgetLimit > 0) ((monthSpent / budgetLimit) * 100).toInt().coerceIn(0, 100) else 0

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_budget)

            if (budgetLimit > 0) {
                views.setTextViewText(R.id.tv_widget_budget_daily, safeDailySpend.asCurrency())
                views.setTextViewText(R.id.tv_widget_budget_days_left, "/ day • $daysRemaining days left")
                views.setProgressBar(R.id.pb_widget_budget, 100, percentUsed, false)
                views.setTextViewText(R.id.tv_widget_budget_spent, "${monthSpent.asCurrency()} of ${budgetLimit.asCurrency()}")
                views.setTextViewText(R.id.tv_widget_budget_percent, "$percentUsed%")

                // Dynamic Health Pill
                val (statusText, statusTextColorRes) = when {
                    monthSpent > budgetLimit -> "EXCEEDED • OVER BUDGET" to R.color.widget_status_alert_text
                    percentUsed >= 80 -> "NEAR LIMIT • $percentUsed% SPENT" to R.color.widget_status_warning_text
                    else -> "ON TRACK • $daysRemaining DAYS LEFT" to R.color.widget_status_safe_text
                }
                views.setTextViewText(R.id.tv_widget_budget_status_pill, statusText)
                views.setTextColor(R.id.tv_widget_budget_status_pill, ContextCompat.getColor(context, statusTextColorRes))
            } else {
                views.setTextViewText(R.id.tv_widget_budget_daily, monthSpent.asCurrency())
                views.setTextViewText(R.id.tv_widget_budget_days_left, "Spent • No budget set")
                views.setProgressBar(R.id.pb_widget_budget, 100, 0, false)
                views.setTextViewText(R.id.tv_widget_budget_spent, "Tap to set monthly budget")
                views.setTextViewText(R.id.tv_widget_budget_percent, "--")

                views.setTextViewText(R.id.tv_widget_budget_status_pill, "TAP TO SET BUDGET")
                views.setTextColor(R.id.tv_widget_budget_status_pill, ContextCompat.getColor(context, R.color.widget_status_neutral_text))
            }

            // Open app click
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                10,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_budget_root, openAppPendingIntent)

            // Refresh click
            val refreshIntent = Intent(context, BudgetWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_BUDGET_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                11,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.btn_widget_budget_refresh, refreshPendingIntent)

            // Quick Add Transaction click
            val addTxIntent = Intent(context, MainActivity::class.java).apply {
                action = BalanceWidgetProvider.ACTION_ADD_TRANSACTION
                putExtra(BalanceWidgetProvider.EXTRA_OPEN_ADD_TRANSACTION, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val addTxPendingIntent = PendingIntent.getActivity(
                context,
                12,
                addTxIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.btn_widget_budget_add, addTxPendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        const val ACTION_REFRESH_BUDGET_WIDGET = "com.moneytracker.app.action.REFRESH_BUDGET_WIDGET"

        /**
         * Broadcasts a widget update request to all active MoneyTracker budget widgets.
         */
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, BudgetWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_BUDGET_WIDGET
            }
            context.sendBroadcast(intent)
        }
    }
}
