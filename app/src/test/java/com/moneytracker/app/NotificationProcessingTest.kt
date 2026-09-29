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
        if (com.moneytracker.app.parser.PromotionalDetector.isPromotional(title, text)) {
            return false
        }
        val isGmail = packageName == NotificationPreferences.PACKAGE_GMAIL
        val isPaymentApp = NotificationPreferences.PAYMENT_APP_PACKAGES.contains(packageName)
        val isBankApp = NotificationPreferences.BANK_APP_PACKAGES.contains(packageName)

        val combined = "$title: $text".lowercase(Locale.getDefault())
        val hasMoneyIndicator = listOf("₹", "rs.", "inr", "rs ").any { it in combined }
        val hasTransactionVerb = listOf(
            "debited", "credited", "spent", "paid", "withdrawn", "received", "deducted",
            "sent", "transferred", "successful", "purchase", "bill payment", "vpa",
        ).any { it in combined }

        if (!hasMoneyIndicator || !hasTransactionVerb) {
            return false
        }

        val hasMarketingSignals = listOf(
            "loan", "pre-approved", "pre approved", "credit limit", "card limit", "limit enhanced",
            "limit increased", "offer", "discount", "cashback", "scratch card", "spin & win",
            "apply now", "avail now", "congratulations", "congrats", "invest", "fixed deposit"
        ).any { it in combined }
        if (hasMarketingSignals && !com.moneytracker.app.parser.PromotionalDetector.hasConfirmedTransactionSignal(combined)) {
            return false
        }

        return true
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
    fun `tata neu push notification parses merchant amount and category`() {
        val pkg = NotificationPreferences.PACKAGE_TATA_NEU
        val title = "Tata Neu"
        val text = "Payment of ₹1,499.00 to Croma successful via Tata Pay UPI"

        assertTrue(isEligibleNotification(pkg, title, text))

        val combined = "$title: $text"
        val parsed = onDeviceAi.parseSmsOnDevice(combined, "Tata Neu").getOrNull()
        assertNotNull(parsed)
        assertTrue(parsed!!.isTransaction)
        assertEquals(1499.0, parsed.amount)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertEquals("Croma", parsed.merchant)
        assertEquals(TransactionCategory.SHOPPING, parsed.category)
    }

    @Test
    fun `bhim upi push notification parses merchant amount and description`() {
        val pkg = NotificationPreferences.PACKAGE_BHIM
        val title = "BHIM"
        val text = "Paid ₹150.00 to Chai Point successfully."

        assertTrue(isEligibleNotification(pkg, title, text))

        val combined = "$title: $text"
        val parsed = onDeviceAi.parseSmsOnDevice(combined, "BHIM").getOrNull()
        assertNotNull(parsed)
        assertTrue(parsed!!.isTransaction)
        assertEquals(150.0, parsed.amount)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertEquals("Chai Point", parsed.merchant)
        assertEquals(TransactionCategory.FOOD, parsed.category)
    }

    @Test
    fun `bhim marketing push notification for earbuds is rejected without adding transaction`() {
        val pkg = NotificationPreferences.PACKAGE_BHIM
        val title = "🎧 Wireless Earbuds @ ₹199"
        val text = "Hear clearly, live fully! Grab ultra-light wireless earbuds for just ₹199. Shop now!"

        assertFalse(isEligibleNotification(pkg, title, text))
    }

    @Test
    fun `bhim marketing push notification for assured cashback is rejected without adding transaction`() {
        val pkg = NotificationPreferences.PACKAGE_BHIM
        val title = "Assured Cashback till 11 PM 🔥"
        val text = "Make any 2 UPI Lite payments of ₹20+ on BHIM today and get up to ₹20 cashback on each. Only till 11 PM."

        assertFalse(isEligibleNotification(pkg, title, text))
    }

    @Test
    fun `gpay marketing scratch card notification is rejected`() {
        val pkg = NotificationPreferences.PACKAGE_GPAY
        val title = "Scratch & Win up to ₹500"
        val text = "Send money to 3 friends to unlock your exclusive scratch card!"

        assertFalse(isEligibleNotification(pkg, title, text))
    }

    @Test
    fun `phonepe marketing coupon notification is rejected`() {
        val pkg = NotificationPreferences.PACKAGE_PHONEPE
        val title = "Special offer"
        val text = "Get flat 20% off up to ₹150 on your medicine orders with code PHARMEASY. Shop now!"

        assertFalse(isEligibleNotification(pkg, title, text))
    }

    @Test
    fun `paytm loan marketing push notification is rejected`() {
        val pkg = NotificationPreferences.PACKAGE_PAYTM
        val title = "Pre-approved Loan"
        val text = "Instant personal loan of ₹2,50,000 at zero processing fee. Apply now!"

        assertFalse(isEligibleNotification(pkg, title, text))
    }

    @Test
    fun `icici bank personal loan push notification shade is rejected`() {
        val pkg = "com.csam.icici.bank.imobile"
        val title = "Personal Loan Alert"
        val text = "Congratulations! Pre-approved personal loan of Rs 5,00,000 on ICICI Bank A/C 1007. Transfer up to Rs600000. Apply now"

        assertFalse(isEligibleNotification(pkg, title, text))
    }

    @Test
    fun `icici bank credit limit enhancement push notification shade is rejected`() {
        val pkg = "com.csam.icici.bank.imobile"
        val title = "Credit Limit Alert"
        val text = "Special offer! Credit limit on your ICICI Bank Card ending 1007 has been enhanced to Rs 6,00,000. Avail now"

        assertFalse(isEligibleNotification(pkg, title, text))
    }

    @Test
    fun `parseIncomingMessage returns non-transaction and avoids degraded recovery for promo notifications`() = kotlinx.coroutines.runBlocking {
        val body = "Personal Loan Alert: Congratulations! Pre-approved personal loan of Rs 5,00,000 on ICICI Bank A/C 1007. Transfer up to Rs600000. Apply now"
        val sender = "ICICI Bank"

        val result = onDeviceAi.parseIncomingMessage(body = body, sender = sender)
        val tx = result.transaction

        assertTrue(tx == null || !tx.isTransaction)
        assertEquals(0.0, result.confidence, 0.001)
    }

    @Test
    fun `package monitoring groups cover all key indian financial apps`() {
        assertTrue(NotificationPreferences.PAYMENT_APP_PACKAGES.contains("com.google.android.apps.nbu.paisa.user"))
        assertTrue(NotificationPreferences.PAYMENT_APP_PACKAGES.contains("com.phonepe.app"))
        assertTrue(NotificationPreferences.PAYMENT_APP_PACKAGES.contains("net.one97.paytm"))
        assertTrue(NotificationPreferences.PAYMENT_APP_PACKAGES.contains("com.dreamplug.androidapp"))
        assertTrue(NotificationPreferences.PAYMENT_APP_PACKAGES.contains("in.org.npci.upiapp"))
        assertTrue(NotificationPreferences.PAYMENT_APP_PACKAGES.contains("com.tatadigital.tcp"))
        assertTrue(NotificationPreferences.BANK_APP_PACKAGES.contains("com.snapwork.hdfc"))
        assertTrue(NotificationPreferences.BANK_APP_PACKAGES.contains("com.sbi.lotusintouch"))
        assertTrue(NotificationPreferences.BANK_APP_PACKAGES.contains("com.csam.icici.bank.imobile"))
    }
}
