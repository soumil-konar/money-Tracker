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
        Regex("""(?i)(?:avl(?:[\.\s]+)?bal(?:ance)?|available\s+balance|avail(?:[\.\s]+)?bal(?:ance)?|total\s+avl\s+bal(?:ance)?|updated\s+bal(?:ance)?|current\s+bal(?:ance)?|cleared\s+bal(?:ance)?)\s*(?:is|:|-)?\s*(?:rs\.?|inr|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)"""),
        Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:is\s+)?(?:avl(?:[\.\s]+)?bal(?:ance)?|available\s+balance|avail\s+bal)"""),
        Regex("""(?i)\bbal(?:ance)?\s*[:=]\s*(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\b"""),
    )

    private val misleadingContextPhrases = listOf(
        "credit limit", "available limit", "avl limit", "card limit", "total limit", "limit available", "spend limit",
        "total due", "min due", "minimum due", "amount due", "payment due", "bill due", "outstanding due", "balance due",
        "points balance", "reward points", "pts bal", "point bal", "rewards bal",
        "pre-approved", "loan amount", "personal loan", "disbursed", "emi amount",
        "maintain", "minimum balance", "min bal required", "average monthly balance", "mab", "amb",
        "off on", "save up to", "save upto", "cashback up to", "use code", "flat discount", "discount on",
    )

    fun verifyBalance(
        sender: String,
        body: String,
        occurredAtMillis: Long = System.currentTimeMillis(),
    ): BalanceProofResult {
        if (body.isBlank()) {
            return BalanceProofResult(isVerified = false, rejectionReason = "Blank message body.")
        }

        val institution = BankDetector.resolveBankInstitution(sender, body)
        if (institution == null) {
            return BalanceProofResult(
                isVerified = false,
                rejectionReason = "Sender '$sender' is not a recognized banking institution.",
            )
        }

        val lowerBody = body.lowercase(Locale.ENGLISH)

        val isEntirelyPromotional = listOf("save up to", "off on", "pre-approved loan", "use code", "apply now").any { it in lowerBody } &&
            !listOf("debited", "credited", "spent", "received", "withdrawn", "avl bal").any { it in lowerBody }
        if (isEntirelyPromotional) {
            return BalanceProofResult(
                isVerified = false,
                rejectionReason = "Message is a promotional marketing offer.",
            )
        }

        for (pattern in availableBalancePatterns) {
            val match = pattern.find(body) ?: continue
            val rawAmountStr = match.groupValues.getOrNull(1)?.replace(",", "") ?: continue
            val balanceAmount = rawAmountStr.toDoubleOrNull() ?: continue

            if (balanceAmount < 0.0 || rawAmountStr.length > 12) {
                continue
            }

            val matchStart = match.range.first
            val matchEnd = match.range.last
            val contextStart = (matchStart - 50).coerceAtLeast(0)
            val contextEnd = (matchEnd + 50).coerceAtMost(body.length)
            val surroundingContext = body.substring(contextStart, contextEnd).lowercase(Locale.ENGLISH)

            val conflictingPhrase = misleadingContextPhrases.firstOrNull { surroundingContext.contains(it) }
            if (conflictingPhrase != null) {
                return BalanceProofResult(
                    isVerified = false,
                    rejectionReason = "Balance phrase rejected due to conflicting context: '$conflictingPhrase'.",
                )
            }

            val accountLastFour = BankDetector.extractBankAccountLastFour(body)

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
