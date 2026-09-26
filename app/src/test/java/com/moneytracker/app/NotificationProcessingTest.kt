package com.moneytracker.app

import com.moneytracker.app.ai.OnDeviceAiEngine
import com.moneytracker.app.data.local.NotificationPreferences
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class NotificationProcessingTest {

    private val onDeviceAi = OnDeviceAiEngine()

    private fun isEligibleNotification(
        packageName: String,
        title: String,
        text: String,
    ): Boolean {
        val isGmail = packageName == NotificationPreferences.PACKAGE_GMAIL
        val isPaymentApp = NotificationPreferences.PAYMENT_APP_PACKAGES.contains(packageName)
        val isBankApp = NotificationPreferences.BANK_APP_PACKAGES.contains(packageName)

        val combined = "$title: $text".lowercase(Locale.getDefault())
        val hasMoneyIndicator = listOf("₹", "rs.", "inr", "rs ").any { it in combined }
        val hasTransactionVerb = listOf(
            "debited", "credited", "spent", "paid", "withdrawn", "received", "deducted",
            "sent", "transfer", "successful", "purchase", "bill payment", "alert", "vpa",
        ).any { it in combined }

        return if (isGmail || (!isPaymentApp && !isBankApp)) {
            hasMoneyIndicator && hasTransactionVerb
        } else {
            hasMoneyIndicator || hasTransactionVerb
        }
    }

    @Test
    fun `gmail banking alert notification is recognized as eligible transaction`() {
        val pkg = NotificationPreferences.PACKAGE_GMAIL
        val title = "HDFC Bank Alert"
        val text = "Alert: You have spent Rs. 450.00 at Starbucks using Debit Card ending 1234 on 24-Sep-26."

        assertTrue(isEligibleNotification(pkg, title, text))

        val parsed = onDeviceAi.parseSmsOnDevice(text, title).getOrNull()
        assertNotNull(parsed)
        assertTrue(parsed!!.isTransaction)
        assertEquals(450.0, parsed.amount)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertEquals(TransactionCategory.FOOD, parsed.category)
        assertNotNull(parsed.detailedDescription)
        assertTrue(parsed.detailedDescription!!.contains("Starbucks"))
    }

    @Test
    fun `gmail personal newsletter is ignored without wasting processing`() {
        val pkg = NotificationPreferences.PACKAGE_GMAIL
        val title = "Medium Daily Digest"
        val text = "Top stories for you today: Understanding Kotlin Coroutines and Jetpack Compose."

        assertFalse(isEligibleNotification(pkg, title, text))
    }

    @Test
    fun `google pay push notification parses merchant amount and description`() {
        val pkg = NotificationPreferences.PACKAGE_GPAY
        val title = "Paid ₹280 to Chai Point"
        val text = "Paid using HDFC Bank ••5678"

        assertTrue(isEligibleNotification(pkg, title, text))

        val combined = "$title. $text"
        val parsed = onDeviceAi.parseSmsOnDevice(combined, "Google Pay").getOrNull()
        assertNotNull(parsed)
        assertTrue(parsed!!.isTransaction)
        assertEquals(280.0, parsed.amount)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertNotNull(parsed.detailedDescription)
    }

    @Test
    fun `phonepe payment successful notification parses cleanly`() {
        val pkg = NotificationPreferences.PACKAGE_PHONEPE
        val title = "Payment Successful"
        val text = "₹350 paid to Swiggy"

        assertTrue(isEligibleNotification(pkg, title, text))

        val combined = "$title: $text"
        val parsed = onDeviceAi.parseSmsOnDevice(combined, "PhonePe").getOrNull()
        assertNotNull(parsed)
        assertTrue(parsed!!.isTransaction)
        assertEquals(350.0, parsed.amount)
        assertEquals(TransactionCategory.FOOD, parsed.category)
        assertNotNull(parsed.detailedDescription)
        assertTrue(parsed.detailedDescription!!.contains("Swiggy"))
    }

    @Test
    fun `package monitoring groups cover all key indian financial apps`() {
        assertTrue(NotificationPreferences.PAYMENT_APP_PACKAGES.contains("com.google.android.apps.nbu.paisa.user"))
        assertTrue(NotificationPreferences.PAYMENT_APP_PACKAGES.contains("com.phonepe.app"))
        assertTrue(NotificationPreferences.PAYMENT_APP_PACKAGES.contains("net.one97.paytm"))
        assertTrue(NotificationPreferences.PAYMENT_APP_PACKAGES.contains("com.dreamplug.androidapp"))
        assertTrue(NotificationPreferences.BANK_APP_PACKAGES.contains("com.snapwork.hdfc"))
        assertTrue(NotificationPreferences.BANK_APP_PACKAGES.contains("com.sbi.lotusintouch"))
        assertTrue(NotificationPreferences.BANK_APP_PACKAGES.contains("com.csam.icici.bank.imobile"))
    }
}
