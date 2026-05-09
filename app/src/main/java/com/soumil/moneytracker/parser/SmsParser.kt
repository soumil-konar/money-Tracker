package com.soumil.moneytracker.parser

import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.ParsedSmsTransaction
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import kotlin.math.min

class SmsParser {

    private val promotionalKeywords = listOf(
        "otp",
        "offer",
        "sale",
        "reward points",
        "cashback offer",
        "coupon",
        "winning",
        "loan approved",
        "pre-approved",
        "emi card",
    )

    private val debitKeywords = listOf(
        "debited",
        "spent",
        "sent",
        "paid",
        "purchase",
        "withdrawn",
        "dr",
    )

    private val creditKeywords = listOf(
        "credited",
        "received",
        "refund",
        "salary",
        "deposited",
        "cr",
    )

    private val amountRegexes = listOf(
        Regex("(?i)(?:rs\\.?|inr)\\s*([0-9,]+(?:\\.\\d{1,2})?)"),
        Regex("(?i)([0-9,]+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|inr)"),
    )

    private val merchantRegexes = listOf(
        Regex("(?i)(?:to|at|towards)\\s+([a-z0-9 &._-]{3,40})"),
        Regex("(?i)(?:from)\\s+([a-z0-9 &._-]{3,40})"),
        Regex("(?i)(?:via upi to)\\s+([a-z0-9 &._-]{3,40})"),
    )

    fun parse(sender: String, body: String): ParsedSmsTransaction {
        val normalized = body.lowercase()
        val shouldIgnore = promotionalKeywords.any(normalized::contains) || !looksTransactional(normalized)
        if (shouldIgnore) {
            return ParsedSmsTransaction(
                amount = null,
                direction = null,
                merchant = null,
                inferredCategory = TransactionCategory.OTHER,
                accountLabel = null,
                accountKind = inferAccountKind(sender, body),
                confidence = 0.0,
                shouldIgnore = true,
            )
        }

        val amount = extractAmount(body)
        val direction = extractDirection(normalized)
        val merchant = extractMerchant(body)
        val accountKind = inferAccountKind(sender, body)
        val accountLabel = inferAccountLabel(sender, body, accountKind)
        val inferredCategory = inferCategory(merchant = merchant, sender = sender, body = normalized, direction = direction)

        var confidence = 0.15
        if (amount != null) confidence += 0.35
        if (direction != null) confidence += 0.25
        if (!merchant.isNullOrBlank()) confidence += 0.15
        if (accountLabel != null) confidence += 0.1
        if (normalized.contains("upi") || normalized.contains("a/c") || normalized.contains("card")) {
            confidence += 0.1
        }

        return ParsedSmsTransaction(
            amount = amount,
            direction = direction,
            merchant = merchant,
            inferredCategory = inferredCategory,
            accountLabel = accountLabel,
            accountKind = accountKind,
            confidence = min(confidence, 0.95),
            shouldIgnore = false,
        )
    }

    private fun looksTransactional(body: String): Boolean {
        return listOf(
            "debited",
            "credited",
            "upi",
            "spent",
            "txn",
            "transaction",
            "withdrawn",
            "received",
            "purchase",
            "a/c",
            "account",
            "salary",
        ).any(body::contains)
    }

    private fun extractAmount(body: String): Double? {
        return amountRegexes.firstNotNullOfOrNull { regex ->
            regex.find(body)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
        }
    }

    private fun extractDirection(normalized: String): TransactionDirection? {
        if (debitKeywords.any(normalized::contains)) return TransactionDirection.DEBIT
        if (creditKeywords.any(normalized::contains)) return TransactionDirection.CREDIT
        return null
    }

    private fun extractMerchant(body: String): String? {
        val match = merchantRegexes.firstNotNullOfOrNull { regex ->
            regex.find(body)?.groupValues?.getOrNull(1)
        } ?: return null

        return match
            .replace(Regex("(?i)(upi|ref|info|avl|bal|available balance).*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.length >= 3 }
    }

    private fun inferCategory(
        merchant: String?,
        sender: String,
        body: String,
        direction: TransactionDirection?,
    ): TransactionCategory {
        val source = listOfNotNull(merchant, sender, body).joinToString(" ").lowercase()
        return when {
            direction == TransactionDirection.CREDIT && source.contains("salary") -> TransactionCategory.SALARY
            listOf("swiggy", "zomato", "restaurant", "cafe", "domino", "food").any(source::contains) -> TransactionCategory.FOOD
            listOf("uber", "ola", "irctc", "metro", "air", "travel").any(source::contains) -> TransactionCategory.TRAVEL
            listOf("airtel", "jio", "electricity", "water", "rent", "bill").any(source::contains) -> TransactionCategory.BILLS
            listOf("netflix", "spotify", "prime", "youtube", "hotstar", "apple.com").any(source::contains) -> TransactionCategory.SUBSCRIPTION
            listOf("amazon", "flipkart", "myntra", "shopping").any(source::contains) -> TransactionCategory.SHOPPING
            source.contains("upi") || source.contains("transfer") || source.contains("neft") || source.contains("imps") -> TransactionCategory.TRANSFER
            else -> TransactionCategory.OTHER
        }
    }

    private fun inferAccountKind(sender: String, body: String): AccountKind {
        val source = "${sender.lowercase()} ${body.lowercase()}"
        return when {
            listOf("card", "visa", "mastercard", "credit").any(source::contains) -> AccountKind.CARD
            listOf("paytm", "phonepe", "wallet").any(source::contains) -> AccountKind.WALLET
            listOf("upi", "gpay", "google pay").any(source::contains) -> AccountKind.UPI
            else -> AccountKind.BANK
        }
    }

    private fun inferAccountLabel(sender: String, body: String, kind: AccountKind): String? {
        val lastFour = Regex("(?i)(?:xx|x{2,}|\\*{2,}|acct|a/c|card)[\\s:-]*([0-9]{4})")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
        val senderLabel = when {
            sender.contains("hdfc", ignoreCase = true) -> "HDFC"
            sender.contains("icici", ignoreCase = true) -> "ICICI"
            sender.contains("sbi", ignoreCase = true) -> "SBI"
            sender.contains("axis", ignoreCase = true) -> "Axis"
            sender.contains("kotak", ignoreCase = true) -> "Kotak"
            sender.contains("paytm", ignoreCase = true) -> "Paytm"
            else -> sender.takeIf { it.isNotBlank() }?.uppercase()?.take(6)
        } ?: return null

        val typeLabel = when (kind) {
            AccountKind.BANK -> "Bank"
            AccountKind.CARD -> "Card"
            AccountKind.WALLET -> "Wallet"
            AccountKind.UPI -> "UPI"
            AccountKind.CASH -> "Cash"
        }

        return listOfNotNull(senderLabel, typeLabel, lastFour?.let { "ending $it" }).joinToString(" ")
    }
}
