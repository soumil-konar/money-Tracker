package com.moneytracker.app.sms

import android.content.ContentResolver
import android.provider.Telephony

data class SmsImportMessage(
    val sender: String,
    val body: String,
    val timestampMillis: Long,
)

class SmsImportManager(
    private val contentResolver: ContentResolver,
) {
    fun readRecentMessages(limit: Int): List<SmsImportMessage> {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
        )

        val messages = mutableListOf<SmsImportMessage>()
        contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val dateSentIndex = cursor.getColumnIndex(Telephony.Sms.DATE_SENT)
            while (cursor.moveToNext() && messages.size < limit) {
                val dateSent = if (dateSentIndex >= 0) cursor.getLong(dateSentIndex) else 0L
                val date = cursor.getLong(dateIndex)
                val bestTimestamp = if (dateSent > 0L) dateSent else date
                messages += SmsImportMessage(
                    sender = cursor.getString(addressIndex).orEmpty(),
                    body = cursor.getString(bodyIndex).orEmpty(),
                    timestampMillis = bestTimestamp,
                )
            }
        }
        return messages
    }
}

