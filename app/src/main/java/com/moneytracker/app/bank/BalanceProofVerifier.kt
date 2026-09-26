package com.moneytracker.app.bank

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BalanceProofResult(
    val isVerified: Boolean,
    val balance: Double? = null,
    val accountLastFour: String? = null,
    val institutionName: String? = null,
    val proofSnippet: String? = null,
    val proofSource: String? = null,
    val rejectionReason: String? = null,
)

object BalanceProofVerifier {

    private val availableBalancePatterns = listOf(
        // "Avl Bal: INR 45,230.50", "Available Balance is Rs. 10,000.00", "Total Avl Bal: Rs 5,000"
        Regex("""(?i)(?:avl(?:[\.\s]+)?bal(?:ance)?|available\s+balance|avail(?:[\.\s]+)?bal(?:ance)?|total\s+avl\s+bal(?:ance)?|updated\s+bal(?:ance)?|current\s+bal(?:ance)?|cleared\s+bal(?:ance)?)\s*(?:is|:|-)?\s*(?:rs\.?|inr|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)"""),
        // "INR 45,230.50 is your available balance", "Rs 10,000 Avl Bal"
        Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:is\s+)?(?:avl(?:[\.\s]+)?bal(?:ance)?|available\s+balance|avail\s+bal)"""),
        // "Bal: Rs 4,500.00", "Balance: INR 12,000"
        Regex("""(?i)\bbal(?:ance)?\s*[:=]\s*(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\b"""),
    )

    private val misleadingContextPhrases = listOf(
        // Credit card limits (NOT bank balances)
        "credit limit", "available limit", "avl limit", "card limit", "total limit", "limit available", "spend limit",
        // Bills and dues (you owe this, NOT what you have)
        "total due", "min due", "minimum due", "amount due", "payment due", "bill due", "outstanding due", "balance due",
        // Reward points
        "points balance", "reward points", "pts bal", "point bal", "rewards bal",
        // Loan & EMI
        "pre-approved", "loan amount", "personal loan", "disbursed", "emi amount",
        // Minimum balance requirements
        "maintain", "minimum balance", "min bal required", "average monthly balance", "mab", "amb",
        // Promotional / discounts
        "off on", "save up to", "save upto", "cashback up to", "use code", "flat discount", "discount on",
    )

    /**
     * Verifies if an SMS / Email message provides substantial proof of a genuine bank available balance.
     * Returns a BalanceProofResult containing the proven balance, exact evidence snippet, and source.
     */
    fun verifyBalance(
        sender: String,
        body: String,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ): BalanceProofResult {
        if (body.isBlank()) {
            return BalanceProofResult(isVerified = false, rejectionReason = "Blank message body.")
        }

        // 1. Bank Institution Check
        val institution = BankDetector.resolveBankInstitution(sender, body)
        if (institution == null) {
            return BalanceProofResult(
                isVerified = false,
                rejectionReason = "Sender '$sender' is not a recognized banking institution.",
            )
        }

        val lowerBody = body.lowercase(Locale.ENGLISH)

        // 2. Reject messages that are entirely promotional offers or bill due notices
        val isEntirelyPromotional = listOf("save up to", "off on", "pre-approved loan", "use code", "apply now").any { it in lowerBody } &&
            !listOf("debited", "credited", "spent", "received", "withdrawn", "avl bal").any { it in lowerBody }
        if (isEntirelyPromotional) {
            return BalanceProofResult(
                isVerified = false,
                rejectionReason = "Message is a promotional marketing offer.",
            )
        }

        // 3. Search for available balance patterns
        for (pattern in availableBalancePatterns) {
            val match = pattern.find(body) ?: continue
            val rawAmountStr = match.groupValues.getOrNull(1)?.replace(",", "") ?: continue
            val balanceAmount = rawAmountStr.toDoubleOrNull() ?: continue

            // Sanity check on extracted number (must be non-negative, reasonable range, not an OTP or ref number)
            if (balanceAmount < 0.0 || rawAmountStr.length > 12) {
                continue
            }

            // 4. Context Inspection: Extract text surrounding the balance pattern (+/- 50 chars)
            val matchStart = match.range.first
            val matchEnd = match.range.last
            val contextStart = (matchStart - 50).coerceAtLeast(0)
            val contextEnd = (matchEnd + 50).coerceAtMost(body.length)
            val surroundingContext = body.substring(contextStart, contextEnd).lowercase(Locale.ENGLISH)

            // Verify surrounding context does not mention misleading phrases
            val conflictingPhrase = misleadingContextPhrases.firstOrNull { surroundingContext.contains(it) }
            if (conflictingPhrase != null) {
                return BalanceProofResult(
                    isVerified = false,
                    rejectionReason = "Balance phrase rejected due to conflicting context: '$conflictingPhrase'.",
                )
            }

            // 5. Extract account binding
            val accountLastFour = BankDetector.extractBankAccountLastFour(body)

            // 6. Extract exact proof snippet
            val proofSnippet = extractProofSnippet(
                sender = sender,
                body = body,
                matchRange = match.range,
                occurredAtMillis = occurredAtMillis,
            )

            val cleanSender = sender.trim().substringAfter("-")
            val proofSource = "SMS (${cleanSender.ifBlank { sender.trim() }})"

            return BalanceProofResult(
                isVerified = true,
                balance = balanceAmount,
                accountLastFour = accountLastFour,
                institutionName = institution,
                proofSnippet = proofSnippet,
                proofSource = proofSource,
            )
        }

        return BalanceProofResult(
            isVerified = false,
            rejectionReason = "No verified available balance statement found in bank alert.",
        )
    }

    /**
     * Extracts the specific sentence or clause containing the balance statement to serve as substantial proof.
     */
    fun extractProofSnippet(
        sender: String,
        body: String,
        matchRange: IntRange? = null,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ): String {
        val cleanBody = body.replace("\r", " ").replace("\n", " ").trim()
        val sentences = cleanBody.split(Regex("""(?<=[.!?])\s+"""))

        val targetSentence = if (matchRange != null) {
            sentences.firstOrNull { sentence ->
                val startInBody = cleanBody.indexOf(sentence)
                val endInBody = startInBody + sentence.length
                matchRange.first in startInBody..endInBody || matchRange.last in startInBody..endInBody
            } ?: sentences.firstOrNull { it.contains("bal", ignoreCase = true) } ?: cleanBody.take(120)
        } else {
            sentences.firstOrNull { it.contains("bal", ignoreCase = true) } ?: cleanBody.take(120)
        }

        val dateStr = runCatching {
            val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
            formatter.format(Date(occurredAtMillis))
        }.getOrDefault("")

        val snippet = targetSentence.trim().take(140)
        return if (dateStr.isNotBlank()) {
            "$sender: \"$snippet\" ($dateStr)"
        } else {
            "$sender: \"$snippet\""
        }
    }
}
