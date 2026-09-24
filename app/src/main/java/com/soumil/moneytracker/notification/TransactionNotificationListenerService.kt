package com.soumil.moneytracker.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.soumil.moneytracker.MoneyTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class TransactionNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return

        // Skip internal app notifications
        if (packageName == applicationContext.packageName) return

        val notification = sbn.notification ?: return

        // Skip ongoing foreground services without clear user notification context
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isForegroundService = (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        if (isOngoing && isForegroundService) {
            // Still inspect if it's a payment app (some show ongoing transfer status)
            // but generally we want transactional alert alerts
        }

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty().trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty().trim()

        val fullText = when {
            bigText.isNotBlank() && bigText.length > text.length -> bigText
            text.isNotBlank() -> text
            else -> bigText
        }

        if (title.isBlank() && fullText.isBlank()) return

        val app = applicationContext as? MoneyTrackerApp ?: return
        val postTime = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()

        serviceScope.launch {
            try {
                withTimeoutOrNull(12_000L) {
                    app.container.repository.processIncomingNotification(
                        packageName = packageName,
                        title = title,
                        text = fullText,
                        subText = subText,
                        postTimeMillis = postTime,
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error processing incoming notification from $packageName", t)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Transaction Notification Listener Service connected successfully.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Transaction Notification Listener Service disconnected.")
    }

    companion object {
        private const val TAG = "TxNotificationListener"
    }
}
