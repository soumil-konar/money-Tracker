package com.soumil.moneytracker.parser

import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.ParsedScheduledTransaction
import com.soumil.moneytracker.data.model.ParsedSmsMessage
import com.soumil.moneytracker.data.model.ParsedSmsTransaction
import com.soumil.moneytracker.data.model.ScheduledTransactionKind
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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

    private val requestOnlyKeywords = listOf(
        "collect request",
        "payment request",
        "request to pay",
        "approve in app",
        "approve on app",
        "upi collect",
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

    private val actualPaymentKeywords = listOf(
        "debited",
        "paid",
        "payment made",
        "payment received",
        "received towards",
        "transaction completed",
        "processed successfully",
        "successfully paid",
        "purchase",
        "spent",
        "sent",
    )

    private val statementOnlyKeywords = listOf(
        "total amount due",
        "minimum amount due",
        "statement generated",
        "payment due",
        "due date",
    )

    private val mandateKeywords = listOf(
        "mandate",
        "e mandate",
        "e-mandate",
        "autopay",
        "auto pay",
        "standing instruction",
        "nach",
        "ecs",
    )

    private val mandateFutureKeywords = listOf(
        "will be debited",
        "will be presented",
        "scheduled on",
        "scheduled for",
        "presented on",
        "to be debited on",
        "auto-debit on",
        "autodebit on",
        "debit on",
        "due on",
        "due date",
    )

    private val mandateExecutedKeywords = listOf(
        "has been debited",
        "debited successfully",
        "executed on",
        "payment received",
        "processed on",
        "was debited",
    )

    private val amountRegexes = listOf(
        Regex("(?i)(?:rs\\.?|inr)\\s*([0-9,]+(?:\\.\\d{1,2})?)"),
        Regex("(?i)([0-9,]+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|inr)"),
    )

    private val merchantRegexes = listOf(
        Regex("(?i)(?:to|at|towards|for|in favour of|in favor of)\\s+([a-z0-9 &._-]{3,60})"),
        Regex("(?i)(?:from)\\s+([a-z0-9 &._-]{3,60})"),
        Regex("(?i)(?:via upi to)\\s+([a-z0-9 &._-]{3,60})"),
        Regex("(?i)(?:merchant|biller)\\s*[:.-]?\\s*([a-z0-9 &._-]{3,60})"),
    )

    private val scheduledDateRegexes = listOf(
        Regex("(?i)(?:scheduled on|scheduled for|presented on|auto[ -]?debit on|autopay on|due on|due date|debit on|will be debited on|to be debited on)\\s*[:.-]?\\s*([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4})"),
        Regex("(?i)(?:scheduled on|scheduled for|presented on|auto[ -]?debit on|autopay on|due on|due date|debit on|will be debited on|to be debited on)\\s*[:.-]?\\s*([0-9]{1,2}[\\s-][A-Za-z]{3,9}[\\s-][0-9]{2,4})"),
        Regex("(?i)(?:scheduled on|scheduled for|presented on|auto[ -]?debit on|autopay on|due on|due date|debit on|will be debited on|to be debited on)\\s*[:.-]?\\s*([0-9]{1,2}[\\s-][A-Za-z]{3,9})"),
        Regex("(?i)(?:on)\\s*([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4})"),
        Regex("(?i)(?:on)\\s*([0-9]{1,2}[\\s-][A-Za-z]{3,9}[\\s-][0-9]{2,4})"),
    )

    private val fullDatePatterns = listOf(
        "d/M/yyyy",
        "d-M-yyyy",
        "d/M/yy",
        "d-M-yy",
        "d MMM yyyy",
        "d MMMM yyyy",
        "d-MMM-yyyy",
        "d-MMMM-yyyy",
    )

    fun parse(sender: String, body: String): ParsedSmsTransaction {
        val parsed = parseMessage(sender = sender, body = body)
        return parsed.transaction ?: ParsedSmsTransaction(
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

    fun parseMessage(sender: String, body: String): ParsedSmsMessage {
        val normalized = body.lowercase()
        val accountKind = inferAccountKind(sender, body)

        if (promotionalKeywords.any(normalized::contains)) {
            return ParsedSmsMessage(shouldIgnore = true)
        }

        parseScheduledMandate(sender = sender, body = body, normalized = normalized, accountKind = accountKind)?.let {
            return ParsedSmsMessage(scheduledTransaction = it)
        }

        if (requestOnlyKeywords.any(normalized::contains) && actualPaymentKeywords.none(normalized::contains)) {
            return ParsedSmsMessage(shouldIgnore = true)
        }

        val isCardBillPayment = isCreditCardBillPayment(normalized)
        if (!looksTransactional(normalized) && !isCardBillPayment) {
            return ParsedSmsMessage(shouldIgnore = true)
        }

        val amount = extractAmount(body)
        val direction = when {
            isCardBillPayment -> TransactionDirection.DEBIT
            else -> extractDirection(normalized)
        }

        if (amount == null || direction == null) {
            return ParsedSmsMessage(shouldIgnore = true)
        }

        val merchant = when {
            isCardBillPayment -> extractCardBillMerchant(sender, body)
            else -> extractMerchant(body)
        }
        val accountLabel = inferAccountLabel(sender, body, accountKind)
        val inferredCategory = when {
            isCardBillPayment -> TransactionCategory.TRANSFER
            else -> inferCategory(
                merchant = merchant,
                sender = sender,
                body = normalized,
                direction = direction,
            )
        }

        var confidence = 0.2
        confidence += 0.35
        confidence += 0.2
        if (!merchant.isNullOrBlank()) confidence += 0.15
        if (accountLabel != null) confidence += 0.1
        if (normalized.contains("upi") || normalized.contains("a/c") || normalized.contains("card") || normalized.contains("payment")) {
            confidence += 0.1
        }
        if (isCardBillPayment) confidence += 0.1

        return ParsedSmsMessage(
            transaction = ParsedSmsTransaction(
                amount = amount,
                direction = direction,
                merchant = merchant,
                inferredCategory = inferredCategory,
                accountLabel = accountLabel,
                accountKind = accountKind,
                confidence = min(confidence, 0.98),
                shouldIgnore = false,
            ),
        )
    }

    private fun parseScheduledMandate(
        sender: String,
        body: String,
        normalized: String,
        accountKind: AccountKind,
    ): ParsedScheduledTransaction? {
        val hasMandateSignal = mandateKeywords.any(normalized::contains)
        val hasFutureSignal = mandateFutureKeywords.any(normalized::contains)
        val looksExecuted = mandateExecutedKeywords.any(normalized::contains)
        if (!hasMandateSignal || !hasFutureSignal || looksExecuted) {
            return null
        }

        val amount = extractAmount(body) ?: return null
        val scheduledForMillis = extractScheduledDate(body) ?: return null
        val merchant = extractMerchant(body) ?: fallbackScheduledMerchant(sender)
        val accountLabel = inferAccountLabel(sender, body, accountKind)
        val category = inferCategory(
            merchant = merchant,
            sender = sender,
            body = normalized,
            direction = TransactionDirection.DEBIT,
        )

        var confidence = 0.45
        confidence += 0.2
        confidence += 0.2
        if (accountLabel != null) confidence += 0.1

        return ParsedScheduledTransaction(
            amount = amount,
            merchant = merchant,
            scheduledForMillis = scheduledForMillis,
            inferredCategory = category,
            accountLabel = accountLabel,
            accountKind = accountKind,
            kind = ScheduledTransactionKind.MANDATE,
            confidence = min(confidence, 0.98),
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
            "payment",
            "bill",
            "mandate",
            "autopay",
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
            .replace(
                Regex(
                    "(?i)(upi|ref|info|avl|bal|available balance|due date|scheduled on|scheduled for|presented on|will be debited|will be presented|from a/c|from acct|from account).*",
                ),
                "",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', ',', '-', ':')
            .takeIf { it.length >= 3 }
    }

    private fun extractScheduledDate(body: String): Long? {
        val token = scheduledDateRegexes.firstNotNullOfOrNull { regex ->
            regex.find(body)?.groupValues?.getOrNull(1)
        } ?: return null
        val cleaned = token
            .replace(",", " ")
            .replace(Regex("(?i)(st|nd|rd|th)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        fullDatePatterns.forEach { pattern ->
            val parsedDate = runCatching {
                LocalDate.parse(cleaned, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
            }.getOrNull()
            if (parsedDate != null) {
                return parsedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }

        parseDateWithoutYear(cleaned)?.let { date ->
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        return null
    }

    private fun parseDateWithoutYear(value: String): LocalDate? {
        val today = LocalDate.now()
        val numericParts = value.split(Regex("[/-]"))
        if (numericParts.size == 2 && numericParts.all { part -> part.all(Char::isDigit) }) {
            val day = numericParts[0].toIntOrNull() ?: return null
            val month = numericParts[1].toIntOrNull() ?: return null
            return candidateDate(day = day, month = month, today = today)
        }

        val textParts = value.split(Regex("[\\s-]+"))
        if (textParts.size == 2) {
            val day = textParts[0].toIntOrNull() ?: return null
            val month = parseMonth(textParts[1]) ?: return null
            return candidateDate(day = day, month = month, today = today)
        }
        return null
    }

    private fun candidateDate(day: Int, month: Int, today: LocalDate): LocalDate? {
        val initial = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (initial.isBefore(today.minusDays(3))) initial.plusYears(1) else initial
    }

    private fun parseMonth(token: String): Int? {
        return when (token.lowercase(Locale.ENGLISH).take(3)) {
            "jan" -> 1
            "feb" -> 2
            "mar" -> 3
            "apr" -> 4
            "may" -> 5
            "jun" -> 6
            "jul" -> 7
            "aug" -> 8
            "sep" -> 9
            "oct" -> 10
            "nov" -> 11
            "dec" -> 12
            else -> null
        }
    }

    private fun isCreditCardBillPayment(normalized: String): Boolean {
        val hasCardSignal = listOf("credit card", "card ending", "card xx", "card xxxx", "card x").any(normalized::contains)
        val hasPaymentSignal = listOf(
            "credit card bill payment",
            "credit card payment",
            "card payment",
            "payment towards your credit card",
            "payment received towards your credit card",
            "payment received for your credit card",
            "paid towards your credit card",
        ).any(normalized::contains)
        val isStatementReminder = statementOnlyKeywords.any(normalized::contains) && actualPaymentKeywords.none(normalized::contains)
        return hasCardSignal && hasPaymentSignal && !isStatementReminder
    }

    private fun extractCardBillMerchant(sender: String, body: String): String {
        val issuer = senderBrand(sender) ?: "Card"
        val cardSuffix = Regex("(?i)(?:card|credit card)(?:\\s+ending|\\s+xx|\\s+xxxx)?[\\s:-]*([0-9]{4})")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
        return listOfNotNull(
            issuer,
            "card bill payment",
            cardSuffix?.let { "ending $it" },
        ).joinToString(" ").replaceFirstChar { it.uppercase() }
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
        val senderLabel = senderBrand(sender) ?: return null

        val typeLabel = when (kind) {
            AccountKind.BANK -> "Bank"
            AccountKind.CARD -> "Card"
            AccountKind.WALLET -> "Wallet"
            AccountKind.UPI -> "UPI"
            AccountKind.CASH -> "Cash"
        }

        return listOfNotNull(senderLabel, typeLabel, lastFour?.let { "ending $it" }).joinToString(" ")
    }

    private fun senderBrand(sender: String): String? {
        return when {
            sender.contains("hdfc", ignoreCase = true) -> "HDFC"
            sender.contains("icici", ignoreCase = true) -> "ICICI"
            sender.contains("sbi", ignoreCase = true) -> "SBI"
            sender.contains("axis", ignoreCase = true) -> "Axis"
            sender.contains("kotak", ignoreCase = true) -> "Kotak"
            sender.contains("paytm", ignoreCase = true) -> "Paytm"
            else -> sender.takeIf { it.isNotBlank() }?.uppercase()?.take(6)
        }
    }

    private fun fallbackScheduledMerchant(sender: String): String {
        return "${senderBrand(sender) ?: sender.uppercase()} mandate"
    }
}
