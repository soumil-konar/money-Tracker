package com.moneytracker.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.moneytracker.app.MoneyTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val application = context.applicationContext as? MoneyTrackerApp ?: run {
            pendingResult.finish()
            return
        }
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: emptyArray()
        val groupedMessages = messages.groupBy { it.originatingAddress.orEmpty() to it.timestampMillis }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9000L) {
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
                }
            } catch (t: Throwable) {
                Log.e("SmsReceiver", "Error processing incoming SMS", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
