package com.soumil.moneytracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.soumil.moneytracker.MoneyTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val application = context.applicationContext as MoneyTrackerApp
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val groupedMessages = messages.groupBy { it.originatingAddress.orEmpty() to it.timestampMillis }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                groupedMessages.forEach { (meta, parts) ->
                    val sender = meta.first
                    val timestamp = meta.second
                    val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
                    application.container.repository.processIncomingSms(
                        sender = sender,
                        body = body,
                        receivedAtMillis = timestamp,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
