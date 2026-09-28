package com.moneytracker.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneytracker.app.MainActivity
import com.moneytracker.app.R
import com.moneytracker.app.ui.navigation.AppDestination
import java.text.NumberFormat
import java.util.Locale

class AppNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val dailyInsights = NotificationChannel(
                CHANNEL_DAILY_INSIGHTS,
                "Daily Insights",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Daily recap of spending, transaction totals, and safe daily buffer."
            }

            val budgetAlerts = NotificationChannel(
                CHANNEL_BUDGET_ALERTS,
                "Budget & Bill Alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Urgent notifications for approaching budget limits and upcoming bill due dates."
            }

            val transactions = NotificationChannel(
                CHANNEL_TRANSACTIONS,
                "Transaction Alerts",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Transactional activity and automated capture notices."
            }

            notificationManager.createNotificationChannels(
                listOf(dailyInsights, budgetAlerts, transactions),
            )
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showDailyRecap(spentToday: Double, txCount: Int, remainingBuffer: Double) {
        if (!hasNotificationPermission()) return

        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val spentFormatted = currencyFormatter.format(spentToday)
        val bufferFormatted = currencyFormatter.format(remainingBuffer)

        val title = "Daily Spending Recap"
        val content = "Spent $spentFormatted across $txCount transaction(s) today. Remaining buffer: $bufferFormatted."

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DESTINATION, AppDestination.Home.route)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_DAILY_RECAP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_INSIGHTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_DAILY_RECAP, notification)
    }

    fun showBudgetWarning(category: String, spent: Double, limit: Double, percent: Int) {
        if (!hasNotificationPermission()) return

        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val spentFormatted = currencyFormatter.format(spent)
        val limitFormatted = currencyFormatter.format(limit)

        val title = "Budget Alert: $category ($percent% used)"
        val content = "You've spent $spentFormatted of your $limitFormatted monthly limit for $category."

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DESTINATION, AppDestination.BudgetHistory.route)
            putExtra(EXTRA_CATEGORY, category)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BUDGET_WARNING + category.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationId = NOTIFICATION_ID_BUDGET_BASE + (category.hashCode() and 0x7FFF)
        notificationManager.notify(notificationId, notification)
    }

    fun showBillReminder(merchant: String, amount: Double, dueDateFormatted: String) {
        if (!hasNotificationPermission()) return

        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val amountFormatted = currencyFormatter.format(amount)

        val title = "Upcoming Bill: $merchant"
        val content = "Bill payment of $amountFormatted is due on $dueDateFormatted."

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DESTINATION, AppDestination.Transactions.route)
            putExtra(EXTRA_MERCHANT, merchant)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BILL_REMINDER + merchant.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationId = NOTIFICATION_ID_BILL_BASE + (merchant.hashCode() and 0x7FFF)
        notificationManager.notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_DAILY_INSIGHTS = "channel_daily_insights"
        const val CHANNEL_BUDGET_ALERTS = "channel_budget_alerts"
        const val CHANNEL_TRANSACTIONS = "channel_transactions"

        const val EXTRA_DESTINATION = "extra_nav_destination"
        const val EXTRA_CATEGORY = "extra_nav_category"
        const val EXTRA_MERCHANT = "extra_nav_merchant"

        const val NOTIFICATION_ID_DAILY_RECAP = 1001
        const val NOTIFICATION_ID_BUDGET_BASE = 2000
        const val NOTIFICATION_ID_BILL_BASE = 3000

        private const val REQUEST_CODE_DAILY_RECAP = 101
        private const val REQUEST_CODE_BUDGET_WARNING = 201
        private const val REQUEST_CODE_BILL_REMINDER = 301
    }
}
