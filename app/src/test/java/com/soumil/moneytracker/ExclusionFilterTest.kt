package com.soumil.moneytracker

import com.soumil.moneytracker.data.local.ExclusionPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusionFilterTest {

    private fun matchKeyword(keywords: Set<String>, isEnabled: Boolean, vararg texts: String?): String? {
        if (!isEnabled || keywords.isEmpty()) return null
        for (keyword in keywords) {
            val trimmed = keyword.trim()
            if (trimmed.isBlank()) continue
            val pattern = if (trimmed.length <= 3) {
                Regex("""\b${Regex.escape(trimmed)}\b""", RegexOption.IGNORE_CASE)
            } else {
                Regex(Regex.escape(trimmed), RegexOption.IGNORE_CASE)
            }
            for (text in texts) {
                if (text != null && pattern.containsMatchIn(text)) {
                    return trimmed
                }
            }
        }
        return null
    }

    @Test
    fun `default keywords exclude Steam and Epic Games purchases`() {
        val keywords = ExclusionPreferences.DEFAULT_KEYWORDS

        val steamText = "Rs. 2,499 debited from A/C **1234 on 24-Sep-26 at STEAM GAMES. Avl Bal: Rs. 45,000."
        val matchedSteam = matchKeyword(keywords, true, "HDFC Bank", steamText)
        assertNotNull("Steam transaction should be matched", matchedSteam)
        assertEquals("Steam", matchedSteam)

        val epicText = "Alert: You have paid Rs. 1,299 to Epic Games Store via UPI."
        val matchedEpic = matchKeyword(keywords, true, "Google Pay", epicText)
        assertNotNull("Epic Games transaction should be matched", matchedEpic)
        assertEquals("Epic Games", matchedEpic)
    }

    @Test
    fun `default keywords exclude refunds declined and failed transactions`() {
        val keywords = ExclusionPreferences.DEFAULT_KEYWORDS

        val refundText = "Refund of Rs. 450.00 credited to your account from Swiggy."
        val matchedRefund = matchKeyword(keywords, true, "SBI", refundText)
        assertEquals("Refund", matchedRefund)

        val declinedText = "Transaction of Rs. 3,500 on Card ending 9999 was declined due to limit."
        val matchedDeclined = matchKeyword(keywords, true, "ICICI Bank", declinedText)
        assertEquals("Declined", matchedDeclined)

        val failedText = "Payment of Rs. 500 failed. Amount will be reversed if debited."
        val matchedFailed = matchKeyword(keywords, true, "Paytm", failedText)
        assertEquals("Failed", matchedFailed)
    }

    @Test
    fun `short keyword OTP uses word boundary to avoid false positives on words like hotpot`() {
        val keywords = setOf("OTP")

        val otpText = "Your OTP for transaction of Rs. 500 is 849201. Do not share."
        val matchedOtp = matchKeyword(keywords, true, "Bank", otpText)
        assertEquals("OTP", matchedOtp)

        val foodText = "Spent Rs. 1,200 at Beijing Hotpot Indiranagar using Debit Card."
        val matchedFood = matchKeyword(keywords, true, "HDFC", foodText)
        assertNull("Word 'hotpot' should NOT trigger word-boundary match for 'OTP'", matchedFood)
    }

    @Test
    fun `user can dynamically add custom exclusion keywords without code changes`() {
        val customKeywords = ExclusionPreferences.DEFAULT_KEYWORDS.toMutableSet()
        customKeywords.add("Roblox")
        customKeywords.add("Twitch")

        val robloxAlert = "Paid Rs. 399 to Roblox Corporation via Apple Services."
        val matchedRoblox = matchKeyword(customKeywords, true, "Apple", robloxAlert)
        assertEquals("Roblox", matchedRoblox)

        val twitchAlert = "Recurring subscription of Rs. 450 to Twitch Interactive."
        val matchedTwitch = matchKeyword(customKeywords, true, "Twitch", twitchAlert)
        assertEquals("Twitch", matchedTwitch)

        // Valid everyday expenses are NOT excluded
        val swiggyAlert = "Sent Rs. 350 to Swiggy from HDFC Bank."
        assertNull(matchKeyword(customKeywords, true, "HDFC", swiggyAlert))

        val petrolAlert = "Paid Rs. 2,000 at Indian Oil Corporation."
        assertNull(matchKeyword(customKeywords, true, "Axis", petrolAlert))
    }

    @Test
    fun `disabling exclusion filter allows all transactions through`() {
        val keywords = ExclusionPreferences.DEFAULT_KEYWORDS
        val steamText = "Rs. 2,499 debited at STEAM GAMES."

        val resultDisabled = matchKeyword(keywords, isEnabled = false, "HDFC", steamText)
        assertNull("When filter is disabled, no transactions should be excluded", resultDisabled)

        val resultEnabled = matchKeyword(keywords, isEnabled = true, "HDFC", steamText)
        assertEquals("Steam", resultEnabled)
    }

    @Test
    fun `exclusion checks against merchant name in addition to raw alert text`() {
        val keywords = setOf("Steam", "PlayStation")

        val sender = "JM-BANK-S"
        val body = "Rs. 4,999 debited for purchase on 24-Sep."
        val extractedMerchant = "PlayStation Network"

        val matched = matchKeyword(keywords, true, sender, body, extractedMerchant)
        assertEquals("PlayStation", matched)
    }
}
