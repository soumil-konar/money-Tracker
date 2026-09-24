package com.soumil.moneytracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
