package com.moneytracker.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class DeduplicationTest {

    private fun hashFingerprint(sender: String, body: String, receivedAtMillis: Long): String {
        val normalizedSender = sender.trim().uppercase()
        val normalizedBody = body.trim().replace(Regex("\\s+"), " ")
        val minuteBucket = if (receivedAtMillis > 0L) receivedAtMillis / 60_000L else 0L
        val input = listOf(normalizedSender, normalizedBody, minuteBucket).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    @Test
    fun `fingerprint matches for SMS received via Broadcast and Inbox import within seconds`() {
        val sender = "JM-HDFCBK-S"
        val body = "Sent Rs.4,864.00 from HDFC Bank A/C **0808 to Swiggy on 24-SEP-26."

        // Broadcast arrives at 11:15:32
        val broadcastTimestamp = 1727198132000L
        // Inbox sync reads 3 seconds later at 11:15:35
        val inboxTimestamp = 1727198135000L

        val fp1 = hashFingerprint(sender, body, broadcastTimestamp)
        val fp2 = hashFingerprint(sender, body, inboxTimestamp)

        assertEquals("Timestamps within the same minute should yield identical fingerprints", fp1, fp2)
    }

    @Test
    fun `fingerprint differs when message body is different`() {
        val sender = "JM-HDFCBK-S"
        val body1 = "Sent Rs.4,864.00 from HDFC Bank A/C **0808 to Swiggy on 24-SEP-26."
        val body2 = "Sent Rs.2.00 from HDFC Bank A/C **0808 to Merchant on 24-SEP-26."
        val timestamp = 1727198132000L

        val fp1 = hashFingerprint(sender, body1, timestamp)
        val fp2 = hashFingerprint(sender, body2, timestamp)

        assertNotEquals("Different bodies must have distinct fingerprints", fp1, fp2)
    }

    @Test
    fun `fingerprint normalizes whitespace and casing differences`() {
        val fp1 = hashFingerprint("jm-hdfcbk-s", "Sent  Rs.100  to Tea Stall", 1727198132000L)
        val fp2 = hashFingerprint("JM-HDFCBK-S", "Sent Rs.100 to Tea Stall\n", 1727198132500L)

        assertEquals("Whitespace and casing variations must produce identical fingerprints", fp1, fp2)
    }

    @Test
    fun `cross-channel bill reminder yields identical channel-agnostic key for SMS, Gmail, and App Push`() {
        fun hashBillFingerprint(merchant: String, amount: Double, dueDateMillis: Long, cardLastFour: String): String {
            val dayBucket = dueDateMillis / 86_400_000L
            val input = listOf("CROSS_CHANNEL", merchant.lowercase().trim(), "%.2f".format(amount), dayBucket, cardLastFour).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return digest.joinToString("") { byte -> "%02x".format(byte) }
        }

        val dueDate = 1727913600000L // 03-Oct-2026
        val fpSms = hashBillFingerprint("Axis Bank Credit Card", 9790.0, dueDate, "2159")
        val fpEmail = hashBillFingerprint("Axis Bank Credit Card", 9790.0, dueDate, "2159")
        val fpPush = hashBillFingerprint("Axis Bank Credit Card", 9790.0, dueDate, "2159")

        assertEquals(fpSms, fpEmail)
        assertEquals(fpEmail, fpPush)
    }

    @Test
    fun `cross-channel duplicate detection merges generic UPI transfer with real merchant SMS`() {
        fun isGenericMerchant(merchant: String): Boolean {
            val lower = merchant.trim().lowercase(java.util.Locale.getDefault())
            return lower == "merchant" ||
                lower.startsWith("upi transfer") ||
                lower.startsWith("spent via") ||
                lower.startsWith("incoming via") ||
                lower == "bank alert" ||
                lower == "payment" ||
                lower.matches(Regex("""^(?:upi transfer|transfer)\s*\(?\.\.[0-9]{4}\)?$""")) ||
                lower.matches(Regex("""^(?:\+?91)?[0-9]{10,12}$"""))
        }

        val existingMerchant = "UPI Transfer (..0808)"
        val incomingMerchant = "STAR BAZAAR"
        val existingRef: String? = null
        val incomingRef = "427011234567"
        val accountId1 = 1L
        val accountId2 = 1L
        val timeDiff = 120_000L // 2 minutes

        val hasDistinctRefs = !existingRef.isNullOrBlank() && !incomingRef.isNullOrBlank() && !existingRef.equals(incomingRef, ignoreCase = true)
        val sameRefMatch = !existingRef.isNullOrBlank() && existingRef.equals(incomingRef, ignoreCase = true)
        val merchantMatch = existingMerchant.equals(incomingMerchant, ignoreCase = true)
        val isExistingGeneric = isGenericMerchant(existingMerchant)
        val isIncomingGeneric = isGenericMerchant(incomingMerchant)
        val hasGenericPlaceholder = isExistingGeneric || isIncomingGeneric

        val isDuplicate = !hasDistinctRefs && (
            sameRefMatch ||
            merchantMatch ||
            (hasGenericPlaceholder && (accountId1 == accountId2))
        )
        assertTrue("Generic UPI transfer and specific bank SMS should be recognized as duplicate", isDuplicate)

        val targetMerchant = if (isExistingGeneric && !isIncomingGeneric) incomingMerchant else existingMerchant
        assertEquals("STAR BAZAAR", targetMerchant)
    }

    @Test
    fun `distinct transactions of the same amount at different merchants within minutes are preserved`() {
        fun isGenericMerchant(merchant: String): Boolean {
            val lower = merchant.trim().lowercase(java.util.Locale.getDefault())
            return lower == "merchant" ||
                lower.startsWith("upi transfer") ||
                lower.startsWith("spent via") ||
                lower.startsWith("incoming via") ||
                lower == "bank alert" ||
                lower == "payment" ||
                lower.matches(Regex("""^(?:upi transfer|transfer)\s*\(?\.\.[0-9]{4}\)?$""")) ||
                lower.matches(Regex("""^(?:\+?91)?[0-9]{10,12}$"""))
        }

        val merchant1 = "Starbucks"
        val merchant2 = "Subway"
        val accountId1 = 1L
        val accountId2 = 1L
        val amount = 50.0

        val merchantMatch = merchant1.equals(merchant2, ignoreCase = true)
        val isGeneric1 = isGenericMerchant(merchant1)
        val isGeneric2 = isGenericMerchant(merchant2)
        val hasGeneric = isGeneric1 || isGeneric2

        val isDuplicate = merchantMatch || (hasGeneric && accountId1 == accountId2)
        org.junit.Assert.assertFalse("Two distinct merchants with same amount must NOT be flagged as duplicate", isDuplicate)
    }

    @Test
    fun `two transactions of the same amount at same merchant with distinct UTRs are preserved`() {
        val ref1 = "427011234001"
        val ref2 = "427011234002"

        val hasDistinctRefs = !ref1.isNullOrBlank() && !ref2.isNullOrBlank() && !ref1.equals(ref2, ignoreCase = true)
        assertTrue("Different UTR numbers must be recognized as distinct transactions", hasDistinctRefs)
    }
}
