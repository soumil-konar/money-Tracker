package com.soumil.moneytracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.soumil.moneytracker.MainActivity
import com.soumil.moneytracker.R
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.db.FinanceDatabase
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.ui.asCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BalanceWidgetProvider : AppWidgetProvider() {

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
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, BalanceWidgetProvider::class.java)
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
        val accounts = database.accountDao().getAccounts()
        val totalBankBalance = accounts.filter { it.kind != AccountKind.CARD }.sumOf { it.currentBalance }

        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val updateTime = timeFormatter.format(Date())

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_balance)

            // Total tracked balance
            views.setTextViewText(R.id.tv_widget_total_balance, totalBankBalance.asCurrency())
            views.setTextViewText(
                R.id.tv_widget_status,
                "Updated $updateTime • ${accounts.size} active account${if (accounts.size == 1) "" else "s"}",
            )

            // Setup Main App open click
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            // Setup Refresh Button
            val refreshIntent = Intent(context, BalanceWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.btn_widget_refresh, refreshPendingIntent)

            // Setup Add Transaction Button
            val addTxIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_ADD_TRANSACTION
                putExtra(EXTRA_OPEN_ADD_TRANSACTION, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val addTxPendingIntent = PendingIntent.getActivity(
                context,
                2,
                addTxIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.btn_widget_add, addTxPendingIntent)

            // Populate top accounts
            populateAccountRows(views, accounts)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun populateAccountRows(views: RemoteViews, accounts: List<AccountEntity>) {
        if (accounts.isEmpty()) {
            views.setViewVisibility(R.id.layout_account_1, View.GONE)
            views.setViewVisibility(R.id.layout_account_2, View.GONE)
            views.setViewVisibility(R.id.layout_account_3, View.GONE)
            views.setViewVisibility(R.id.tv_widget_empty, View.VISIBLE)
            return
        }

        views.setViewVisibility(R.id.tv_widget_empty, View.GONE)

        // Sort: bank accounts with non-zero balances first, then cards
        val sortedAccounts = accounts.sortedWith(
            compareByDescending<AccountEntity> { it.kind != AccountKind.CARD }
                .thenByDescending { it.currentBalance }
        )

        val rowLayouts = listOf(R.id.layout_account_1, R.id.layout_account_2, R.id.layout_account_3)
        val nameViews = listOf(R.id.tv_acc_name_1, R.id.tv_acc_name_2, R.id.tv_acc_name_3)
        val balanceViews = listOf(R.id.tv_acc_balance_1, R.id.tv_acc_balance_2, R.id.tv_acc_balance_3)

        for (i in 0 until 3) {
            if (i < sortedAccounts.size) {
                val acc = sortedAccounts[i]
                val formattedName = if (!acc.lastFourDigits.isNullOrBlank()) {
                    "${acc.name} (•••• ${acc.lastFourDigits})"
                } else {
                    acc.name
                }
                views.setViewVisibility(rowLayouts[i], View.VISIBLE)
                views.setTextViewText(nameViews[i], formattedName)
                views.setTextViewText(balanceViews[i], acc.currentBalance.asCurrency())
            } else {
                views.setViewVisibility(rowLayouts[i], View.GONE)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.soumil.moneytracker.action.REFRESH_WIDGET"
        const val ACTION_ADD_TRANSACTION = "com.soumil.moneytracker.action.ADD_TRANSACTION"
        const val EXTRA_OPEN_ADD_TRANSACTION = "extra_open_add_transaction"

        /**
         * Broadcasts a widget update request to all active MoneyTracker balance widgets.
         */
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, BalanceWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            context.sendBroadcast(intent)
        }
    }
}
