package com.moneytracker.app.parser

import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.CardType
import com.moneytracker.app.data.model.ParsedScheduledTransaction
import com.moneytracker.app.data.model.ParsedSmsMessage
import com.moneytracker.app.data.model.ParsedSmsTransaction
import com.moneytracker.app.data.model.ScheduledTransactionKind
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import com.moneytracker.app.bank.BalanceProofVerifier
import com.moneytracker.app.bank.BankDetector

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
    )

    private val creditKeywords = listOf(
        "credited",
        "received",
        "refund",
        "deposited",
        "salary",
        "reversal",
        "reversed",
    )

    private val debitAbbreviationRegex = Regex("(?i)\\bdr\\b")
    private val creditAbbreviationRegex = Regex("(?i)\\bcr\\b")

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
        "statement",
    )

    private val cardRepaymentKeywords = listOf(
        "credit card bill payment",
        "card bill payment",
        "credit card bill",
        "statement payment",
        "payment received towards your credit card",
        "payment received towards credit card",
        "payment received towards",
        "payment received for credit card",
        "payment received for your card",
        "payment made towards credit card",
        "payment made towards",
        "received towards your credit card",
        "received towards your card",
        "received towards your",
        "received towards",
        "credited towards credit card",
        "credited to your credit card",
        "credited to credit card",
        "credited to your card",
        "credited to card",
        "credited to your sbi card",
        "credited to sbi card",
        "paid towards your credit card",
        "paid towards your card",
        "paid towards your",
        "paid towards",
        "payment towards credit card",
        "payment towards card",
        "payment towards",
        "towards credit card",
        "towards your credit card",
        "towards your card",
        "towards card",
        "thank you for paying",
        "thank you for payment",
        "via cred",
        "on cred",
        "through cred",
        "via billdesk",
        "through billdesk",
        "via cheq",
    )

    private val selfTransferKeywords = listOf(
        "to self",
        "to own account",
        "from own account",
        "self transfer",
        "wallet topup",
        "wallet top-up",
        "added to wallet",
        "loaded to wallet",
        "funds transfer to self",
        "transfer to own",
    )

    private val billIgnoreKeywords = listOf(
        "total amount due",
        "minimum amount due",
        "payment due",
        "statement generated",
        "outstanding amount",
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
        Regex("(?i)(?:debited|credited|spent|withdrawn|paid)\\s+(?:by|for|of)?\\s*([0-9,]+(?:\\.\\d{1,2})?)"),
    )

    private val merchantRegexes = listOf(
        Regex("(?i)(?:to|at|towards|for|in favour of|in favor of)\\s+([a-z0-9@ &._-]{3,80})"),
        Regex("(?i)(?:from)\\s+([a-z0-9@ &._-]{3,80})"),
        Regex("(?i)(?:via upi to)\\s+([a-z0-9@ &._-]{3,80})"),
        Regex("(?i)(?:merchant|biller)\\s*[:.-]?\\s*([a-z0-9@ &._-]{3,80})"),
    )

    private val scheduledDateRegexes = listOf(
        Regex("(?i)(?:scheduled on|scheduled for|presented on|auto[ -]?debit on|autopay on|due on|due date|debit on|will be debited on|to be debited on)\\s*[:.-]?\\s*([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4})"),
        Regex("(?i)(?:scheduled on|scheduled for|presented on|auto[ -]?debit on|autopay on|due on|due date|debit on|will be debited on|to be debited on)\\s*[:.-]?\\s*([0-9]{1,2}[\\s-][A-Za-z]{3,9}[\\s-][0-9]{2,4})"),
        Regex("(?i)(?:scheduled on|scheduled for|presented on|auto[ -]?debit on|autopay on|due on|due date|debit on|will be debited on|to be debited on)\\s*[:.-]?\\s*([0-9]{1,2}[A-Za-z]{3,9}[0-9]{2,4})"),
        Regex("(?i)(?:scheduled on|scheduled for|presented on|auto[ -]?debit on|autopay on|due on|due date|debit on|will be debited on|to be debited on)\\s*[:.-]?\\s*([0-9]{1,2}[\\s-][A-Za-z]{3,9})"),
    )

    private val transactionDateRegexes = listOf(
        Regex("(?im)^([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4})(?:,\\s*[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)?$"),
        Regex("(?i)(?:on\\s+date|on|dated)\\s*[:.-]?\\s*([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4})"),
        Regex("(?i)(?:on\\s+date|on|dated)\\s*[:.-]?\\s*([0-9]{1,2}[/-][0-9]{1,2})\\b"),
        Regex("(?i)(?:on\\s+date|on|dated)\\s*[:.-]?\\s*([0-9]{1,2}[\\s-][A-Za-z]{3,9}[\\s-][0-9]{2,4})"),
        Regex("(?i)(?:on\\s+date|on|dated)\\s*[:.-]?\\s*([0-9]{1,2}[A-Za-z]{3,9}[0-9]{2,4})"),
        Regex("(?i)(?:on\\s+date|on|dated)\\s*[:.-]?\\s*([0-9]{1,2}[\\s-][A-Za-z]{3,9})"),
    )

    private val bankAccountLastFourRegexes = listOf(
        Regex("(?i)(?:a/c|acct|account)(?:\\s*(?:no\\.?|number))?\\s*[xX*]*\\s*([0-9]{4})"),
        Regex("(?i)(?:a/c|acct|account)(?:\\s*(?:no\\.?|number))?[\\s:-]*[xX*]{1,4}([0-9]{4})"),
    )

    private val cardLastFourRegexes = listOf(
        Regex("(?i)(?:credit|debit|rupay credit)?\\s*card(?:\\s+no\\.?|\\s+number|\\s+ending|\\s+ending with|\\s+xx|\\s+xxxx)?[\\s:.-]*[*xX]*([0-9]{4})"),
        Regex("(?i)(?:on|using|for)\\s+(?:[a-z ]+)?card[\\s:.-]*[*xX]*([0-9]{4})"),
        Regex("(?i)(?:visa|mastercard|rupay)[\\s:.-]*[*xX]*([0-9]{4})"),
    )

    private val balanceRegexes = listOf(
        Regex("(?i)(?:avl(?:\\.|\\s+)?bal(?:ance)?|available\\s+balance|avail(?:\\.|\\s+)?bal(?:ance)?|total\\s+balance|acct\\s+bal(?:ance)?)\\s*(?:is|:|-)?\\s*(?:rs\\.?|inr)?\\s*([0-9,]+(?:\\.\\d{1,2})?)"),
        Regex("(?i)(?:rs\\.?|inr)\\s*([0-9,]+(?:\\.\\d{1,2})?)\\s*(?:is\\s+)?(?:avl(?:\\.|\\s+)?bal(?:ance)?|available\\s+balance|avail(?:\\.|\\s+)?bal)"),
        Regex("(?i)\\bbal(?:ance)?\\s*[:=]\\s*(?:rs\\.?|inr)?\\s*([0-9,]+(?:\\.\\d{1,2})?)"),
    )

    private fun extractAvailableBalance(body: String, sender: String = ""): Double? {
        val verified = BalanceProofVerifier.verifyBalance(sender, body).balance
        if (verified != null) return verified

        return balanceRegexes.firstNotNullOfOrNull { regex ->
            val match = regex.find(body) ?: return@firstNotNullOfOrNull null
            val raw = match.groupValues.getOrNull(1)?.replace(",", "") ?: return@firstNotNullOfOrNull null
            val start = (match.range.first - 40).coerceAtLeast(0)
            val end = (match.range.last + 40).coerceAtMost(body.length)
            val ctx = body.substring(start, end).lowercase(Locale.ENGLISH)
            if (listOf("limit", "due", "points", "maintain", "reward", "loan").any { ctx.contains(it) }) {
                null
            } else {
                raw.toDoubleOrNull()
            }
        }
    }

    private val fullDatePatterns = listOf(
        "d/M/yyyy",
        "d-M-yyyy",
        "d/M/yy",
        "d-M-yy",
        "d MMM yyyy",
        "d MMM yy",
        "d MMMM yyyy",
        "d MMMM yy",
        "d-MMM-yyyy",
        "d-MMM-yy",
        "d-MMMM-yyyy",
        "dMMMyyyy",
        "dMMMyy",
        "ddMMMyyyy",
        "ddMMMyy",
    )

    fun parse(sender: String, body: String): ParsedSmsTransaction {
        val parsed = parseMessage(sender = sender, body = body)
        return parsed.transaction ?: ParsedSmsTransaction(
            amount = null,
            direction = null,
            merchant = null,
            inferredCategory = TransactionCategory.OTHER,
            accountLabel = null,
            accountKind = AccountKind.BANK,
            confidence = 0.0,
            shouldIgnore = true,
        )
    }

    fun parseMessage(sender: String, body: String): ParsedSmsMessage {
        val normalized = body.lowercase()
        val institutionName = inferInstitutionName(sender = sender, body = body)
        val bankAccountLastFourDigits = extractBankAccountLastFour(body)
        val cardLastFourDigits = extractCardLastFour(body)
        val cardType = inferCardType(normalized)
        val isUpiPayment = isUpiMessage(normalized)
        val hasCardSignal = hasCardSignal(normalized, cardLastFourDigits, cardType)
        val isCardBillPayment = isCreditCardBillPayment(normalized, hasCardSignal)
        val accountKind = inferAccountKind(
            sender = sender,
            body = body,
            hasCardSignal = hasCardSignal,
            isCardBillPayment = isCardBillPayment,
        )

        if (promotionalKeywords.any(normalized::contains)) {
            return ParsedSmsMessage(shouldIgnore = true)
        }

        parseScheduledMandate(
            sender = sender,
            body = body,
            normalized = normalized,
            institutionName = institutionName,
            bankAccountLastFourDigits = bankAccountLastFourDigits,
            cardLastFourDigits = cardLastFourDigits,
            cardType = cardType,
            accountKind = accountKind,
            isUpiPayment = isUpiPayment,
            hasCardSignal = hasCardSignal,
        )?.let {
            return ParsedSmsMessage(scheduledTransaction = it)
        }

        if (shouldIgnoreBillOrDueMessage(normalized)) {
            return ParsedSmsMessage(shouldIgnore = true)
        }

        if (isCardStatementOnly(normalized, hasCardSignal, isCardBillPayment)) {
            return ParsedSmsMessage(shouldIgnore = true)
        }

        if (requestOnlyKeywords.any(normalized::contains) && actualPaymentKeywords.none(normalized::contains)) {
            return ParsedSmsMessage(shouldIgnore = true)
        }

        if (!looksTransactional(normalized) && !isCardBillPayment) {
            return ParsedSmsMessage(shouldIgnore = true)
        }

        val amount = extractAmount(body)
        val direction = when {
            isCardBillPayment -> TransactionDirection.DEBIT
            else -> extractDirection(normalized) ?: inferCardTransactionDirection(normalized, hasCardSignal)
        }
        val occurredAtMillis = extractTransactionDate(body)

        if (amount == null || direction == null) {
            return ParsedSmsMessage(shouldIgnore = true)
        }

        val merchant = when {
            isCardBillPayment -> extractCardBillMerchant(
                institutionName = institutionName,
                body = body,
                sender = sender,
            )

            else -> extractMerchant(body)
        }
        val isCardPayment = hasCardSignal && !isCardBillPayment
        val isAtm = direction == TransactionDirection.DEBIT && isAtmWithdrawal(body = body, merchant = merchant)
        val resolvedMerchant = when {
            isCardBillPayment -> extractCardBillMerchant(
                institutionName = institutionName,
                body = body,
                sender = sender,
            )

            isAtm && (merchant.isNullOrBlank() || merchant.equals("atm", ignoreCase = true) || merchant.contains("atm", ignoreCase = true)) -> {
                if (!merchant.isNullOrBlank() && merchant.length > 3) merchant else listOfNotNull(institutionName, "ATM Cash Withdrawal").joinToString(" ")
            }

            else -> merchant
        }
        val accountLabel = inferAccountLabel(
            institutionName = institutionName,
            bankAccountLastFourDigits = bankAccountLastFourDigits,
            accountKind = accountKind,
            cardType = cardType,
            cardLastFourDigits = cardLastFourDigits,
        )
        val isSelfTransfer = selfTransferKeywords.any(normalized::contains)
        val inferredCategory = when {
            isCardBillPayment -> TransactionCategory.TRANSFER
            isSelfTransfer -> TransactionCategory.TRANSFER
            else -> inferCategory(
                merchant = resolvedMerchant,
                sender = sender,
                body = normalized,
                direction = direction,
            )
        }
        val countsTowardBudget = !isCardBillPayment && !isSelfTransfer && inferredCategory != TransactionCategory.TRANSFER

        var confidence = 0.2
        confidence += 0.35
        confidence += 0.2
        if (!resolvedMerchant.isNullOrBlank()) confidence += 0.15
        if (accountLabel != null) confidence += 0.1
        if (isUpiPayment || isCardPayment || normalized.contains("a/c") || normalized.contains("payment") || isAtm) {
            confidence += 0.1
        }
        if (occurredAtMillis != null) confidence += 0.05
        if (isCardBillPayment) confidence += 0.08

        val availableBalance = extractAvailableBalance(body, sender)

        return ParsedSmsMessage(
            transaction = ParsedSmsTransaction(
                amount = amount,
                direction = direction,
                merchant = resolvedMerchant,
                inferredCategory = inferredCategory,
                accountLabel = accountLabel,
                accountKind = accountKind,
                confidence = min(confidence, 0.98),
                shouldIgnore = false,
                institutionName = institutionName,
                occurredAtMillis = occurredAtMillis,
                bankAccountLastFourDigits = bankAccountLastFourDigits,
                cardLastFourDigits = cardLastFourDigits,
                cardType = cardType,
                isUpiPayment = isUpiPayment,
                isCardPayment = isCardPayment,
                isCardBillPayment = isCardBillPayment,
                countsTowardBudget = countsTowardBudget,
                availableBalance = availableBalance,
                isAtmWithdrawal = isAtm,
            ),
        )
    }

    private fun parseScheduledMandate(
        sender: String,
        body: String,
        normalized: String,
        institutionName: String?,
        bankAccountLastFourDigits: String?,
        cardLastFourDigits: String?,
        cardType: CardType?,
        accountKind: AccountKind,
        isUpiPayment: Boolean,
        hasCardSignal: Boolean,
    ): ParsedScheduledTransaction? {
        val hasMandateSignal = mandateKeywords.any(normalized::contains)
        val hasFutureSignal = mandateFutureKeywords.any(normalized::contains)
        val looksExecuted = mandateExecutedKeywords.any(normalized::contains)
        if (!hasMandateSignal || !hasFutureSignal || looksExecuted) {
            return null
        }

        val amount = extractAmount(body) ?: return null
        val scheduledForMillis = extractScheduledDate(body) ?: return null
        val merchant = extractMerchant(body) ?: fallbackScheduledMerchant(
            sender = sender,
            institutionName = institutionName,
        )
        val accountLabel = inferAccountLabel(
            institutionName = institutionName,
            bankAccountLastFourDigits = bankAccountLastFourDigits,
            accountKind = accountKind,
            cardType = cardType,
            cardLastFourDigits = cardLastFourDigits,
        )
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
            institutionName = institutionName,
            bankAccountLastFourDigits = bankAccountLastFourDigits,
            cardLastFourDigits = cardLastFourDigits,
            cardType = cardType,
            isUpiPayment = isUpiPayment,
            isCardPayment = hasCardSignal,
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
            "card",
        ).any(body::contains)
    }

    private fun extractAmount(body: String): Double? {
        return amountRegexes.firstNotNullOfOrNull { regex ->
            regex.find(body)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
        }
    }

    private fun extractDirection(normalized: String): TransactionDirection? {
        if (hasCreditDirectionKeyword(normalized)) return TransactionDirection.CREDIT
        if (hasDebitDirectionKeyword(normalized)) return TransactionDirection.DEBIT
        return null
    }

    private fun inferCardTransactionDirection(
        normalized: String,
        hasCardSignal: Boolean,
    ): TransactionDirection? {
        if (!hasCardSignal) return null
        if (hasCreditDirectionKeyword(normalized)) return TransactionDirection.CREDIT
        return when {
            normalized.contains("txn rs") -> TransactionDirection.DEBIT
            normalized.contains("txn of") -> TransactionDirection.DEBIT
            normalized.contains("by upi") -> TransactionDirection.DEBIT
            normalized.contains("via upi") -> TransactionDirection.DEBIT
            normalized.contains(" at ") -> TransactionDirection.DEBIT
            normalized.contains("purchase") -> TransactionDirection.DEBIT
            normalized.contains("spent") -> TransactionDirection.DEBIT
            else -> null
        }
    }

    private fun hasCreditDirectionKeyword(normalized: String): Boolean {
        return creditKeywords.any(normalized::contains) || creditAbbreviationRegex.containsMatchIn(normalized)
    }

    private fun hasDebitDirectionKeyword(normalized: String): Boolean {
        return debitKeywords.any(normalized::contains) || debitAbbreviationRegex.containsMatchIn(normalized)
    }

    private fun extractMerchant(body: String): String? {
        extractStructuredMerchant(body)?.let { return it }
        val match = merchantRegexes.firstNotNullOfOrNull { regex ->
            regex.find(body)?.groupValues?.getOrNull(1)
        } ?: return null

        return sanitizeMerchant(match)
    }

    private fun extractStructuredMerchant(body: String): String? {
        return body.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .firstNotNullOfOrNull { line ->
                extractUpiLedgerMerchant(line)
            }
    }

    private fun extractUpiLedgerMerchant(line: String): String? {
        if (!line.startsWith("UPI/", ignoreCase = true)) return null
        val parts = line.split("/")
            .map(String::trim)
            .filter(String::isNotEmpty)
        val candidate = parts.getOrNull(3) ?: return null
        if (!candidate.any(Char::isLetter)) return null
        return sanitizeMerchant(candidate)
    }

    private fun sanitizeMerchant(rawValue: String): String? {
        return rawValue
            .replace(SANITIZE_MERCHANT_REGEX, "")
            .replace(REPLACE_BY_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim(' ', '.', ',', '-', ':')
            .takeIf { it.length >= 3 }
    }

    private fun extractScheduledDate(body: String): Long? {
        val token = scheduledDateRegexes.firstNotNullOfOrNull { regex ->
            regex.find(body)?.groupValues?.getOrNull(1)
        } ?: return null
        return parseDateToken(token, preferFuture = true)
    }

    private fun extractTransactionDate(body: String): Long? {
        val token = transactionDateRegexes.firstNotNullOfOrNull { regex ->
            regex.find(body)?.groupValues?.getOrNull(1)
        } ?: return null
        return parseDateToken(token, preferFuture = false)
    }

    private fun parseDateToken(
        token: String,
        preferFuture: Boolean,
    ): Long? {
        val cleaned = token
            .replace(",", " ")
            .replace(ORDINAL_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

        fullDatePatterns.forEach { pattern ->
            val parsedDate = runCatching {
                LocalDate.parse(cleaned, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
            }.getOrNull()
            if (parsedDate != null) {
                return parsedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }

        parseDateWithoutYear(cleaned, preferFuture)?.let { date ->
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        return null
    }

    private fun parseDateWithoutYear(
        value: String,
        preferFuture: Boolean,
    ): LocalDate? {
        val today = LocalDate.now()
        val compactAlpha = COMPACT_ALPHA_DATE_REGEX
            .matchEntire(value)
        if (compactAlpha != null) {
            val day = compactAlpha.groupValues[1].toIntOrNull() ?: return null
            val month = parseMonth(compactAlpha.groupValues[2]) ?: return null
            return candidateDate(day = day, month = month, today = today, preferFuture = preferFuture)
        }

        val numericParts = value.split(DATE_SPLIT_SLASH_HYPHEN)
        if (numericParts.size == 2 && numericParts.all { part -> part.all(Char::isDigit) }) {
            val day = numericParts[0].toIntOrNull() ?: return null
            val month = numericParts[1].toIntOrNull() ?: return null
            return candidateDate(day = day, month = month, today = today, preferFuture = preferFuture)
        }

        val textParts = value.split(DATE_SPLIT_WHITESPACE_HYPHEN)
        if (textParts.size == 2) {
            val day = textParts[0].toIntOrNull() ?: return null
            val month = parseMonth(textParts[1]) ?: return null
            return candidateDate(day = day, month = month, today = today, preferFuture = preferFuture)
        }
        return null
    }

    private fun candidateDate(
        day: Int,
        month: Int,
        today: LocalDate,
        preferFuture: Boolean,
    ): LocalDate? {
        val initial = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (preferFuture) {
            if (initial.isBefore(today.minusDays(3))) initial.plusYears(1) else initial
        } else {
            if (initial.isAfter(today.plusDays(3))) initial.minusYears(1) else initial
        }
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

    private fun extractBankAccountLastFour(body: String): String? {
        return BankDetector.extractBankAccountLastFour(body)
            ?: bankAccountLastFourRegexes.firstNotNullOfOrNull { regex ->
                regex.find(body)?.groupValues?.getOrNull(1)
            }?.filter(Char::isDigit)
                ?.takeLast(4)
                ?.takeIf { it.length == 4 }
    }

    private fun hasCardSignal(
        normalized: String,
        cardLastFourDigits: String?,
        cardType: CardType?,
    ): Boolean {
        return cardLastFourDigits != null ||
            cardType != null ||
            listOf(
                "credit card",
                "debit card",
                "bank card",
                "card ending",
                "card xx",
                "card xxxx",
                "rupay credit card",
                "visa card",
                "mastercard",
            ).any(normalized::contains)
    }

    private fun inferCardType(normalized: String): CardType? {
        return when {
            normalized.contains("credit card") || normalized.contains("rupay credit") -> CardType.CREDIT
            normalized.contains("debit card") -> CardType.DEBIT
            else -> null
        }
    }

    private fun extractCardLastFour(body: String): String? {
        return BankDetector.extractCardLastFour(body)
            ?: cardLastFourRegexes.firstNotNullOfOrNull { regex ->
                regex.find(body)?.groupValues?.getOrNull(1)
            }?.filter(Char::isDigit)
                ?.takeLast(4)
                ?.takeIf { it.length == 4 }
    }

    private fun isUpiMessage(normalized: String): Boolean {
        return listOf(
            "upi",
            "vpa",
            "@oksbi",
            "@okhdfcbank",
            "@ibl",
            "@ybl",
            "@axl",
        ).any(normalized::contains)
    }

    private fun isCreditCardBillPayment(
        normalized: String,
        hasCardSignal: Boolean,
    ): Boolean {
        val explicitCardRepayment = listOf(
            "credit card bill",
            "card bill",
            "towards credit card",
            "towards your credit card",
            "towards your card",
            "towards card",
            "credited to your credit card",
            "credited to credit card",
            "credited to your card",
            "credited to card",
            "credited towards credit card",
            "payment received towards your credit card",
            "payment received towards credit card",
            "payment received for credit card",
            "payment received for your card",
            "payment received towards",
            "paid towards your credit card",
            "paid towards credit card",
            "received towards your credit card",
            "received towards your card",
            "via cred",
            "on cred",
            "through cred",
            "via cheq",
            "via billdesk",
            "through billdesk",
        ).any(normalized::contains)

        val hasPaymentSignal = explicitCardRepayment || (hasCardSignal && cardRepaymentKeywords.any(normalized::contains))
        val isStatementReminder = statementOnlyKeywords.any(normalized::contains) && actualPaymentKeywords.none(normalized::contains)
        return hasPaymentSignal && !isStatementReminder
    }

    private fun isCardStatementOnly(
        normalized: String,
        hasCardSignal: Boolean,
        isCardBillPayment: Boolean,
    ): Boolean {
        if (!hasCardSignal || isCardBillPayment) return false
        return statementOnlyKeywords.any(normalized::contains) &&
            actualPaymentKeywords.none(normalized::contains)
    }

    private fun shouldIgnoreBillOrDueMessage(normalized: String): Boolean {
        val looksExecuted = actualPaymentKeywords.any(normalized::contains) ||
            listOf("paid", "debited", "successful", "processed").any(normalized::contains)

        if (looksExecuted) {
            return false
        }

        if (billIgnoreKeywords.any(normalized::contains)) return true
        return (normalized.contains("due date") || normalized.contains("due on")) &&
            (normalized.contains("bill") || normalized.contains("statement") || normalized.contains("credit card"))
    }

    private fun extractCardBillMerchant(
        institutionName: String?,
        body: String,
        sender: String,
    ): String {
        val issuer = institutionName ?: inferInstitutionName(sender = sender, body = body) ?: "Card"
        val cardSuffix = extractCardLastFour(body)
        return listOfNotNull(
            issuer,
            "credit card bill payment",
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
            source.contains("upi") || source.contains("transfer") || source.contains("neft") || source.contains("imps") || source.contains("trf") -> TransactionCategory.TRANSFER
            else -> TransactionCategory.OTHER
        }
    }

    private fun inferAccountKind(
        sender: String,
        body: String,
        hasCardSignal: Boolean,
        isCardBillPayment: Boolean,
    ): AccountKind {
        val source = "${sender.lowercase()} ${body.lowercase()}"
        return when {
            hasCardSignal && !isCardBillPayment -> AccountKind.CARD
            source.contains("wallet") -> AccountKind.WALLET
            else -> AccountKind.BANK
        }
    }

    private fun inferAccountLabel(
        institutionName: String?,
        bankAccountLastFourDigits: String?,
        accountKind: AccountKind,
        cardType: CardType?,
        cardLastFourDigits: String?,
    ): String? {
        return when (accountKind) {
            AccountKind.BANK -> listOfNotNull(
                institutionName,
                bankAccountLastFourDigits?.let { "A/C $it" },
            ).joinToString(" ").ifBlank { null }

            AccountKind.CARD -> listOfNotNull(
                institutionName,
                cardType?.label,
                cardLastFourDigits?.let { "ending $it" },
            ).joinToString(" ").ifBlank { null }

            AccountKind.WALLET -> institutionName ?: "Wallet"
            AccountKind.UPI -> institutionName ?: "UPI"
            AccountKind.CASH -> "Cash"
        }
    }

    private fun inferInstitutionName(sender: String, body: String): String? {
        return BankDetector.resolveBankInstitution(sender, body)
            ?: BankDetector.normalizeToCanonicalBank(sender)
    }

    private fun fallbackScheduledMerchant(
        sender: String,
        institutionName: String?,
    ): String {
        return "${institutionName ?: sender.uppercase()} mandate"
    }

    companion object {
        private val SANITIZE_MERCHANT_REGEX = Regex("(?i)(?:\\bby\\s+upi\\b|\\bvia\\s+upi\\b|\\bupi\\b|\\bref(?:no)?\\b|\\binfo\\b|\\bavl\\b|\\bbal\\b|\\bavailable balance\\b|\\bdue date\\b|\\bscheduled on\\b|\\bscheduled for\\b|\\bpresented on\\b|\\bwill be debited\\b|\\bwill be presented\\b|\\bfrom a/c\\b|\\bfrom acct\\b|\\bfrom account\\b|\\bon date\\b|\\bon\\s+[0-9]{1,2}[/-][0-9]{1,2}(?:[/-][0-9]{2,4})?\\b|\\bon\\s+[0-9]{1,2}[A-Za-z]{3,9}[0-9]{2,4}\\b|\\bon\\s+[0-9]{1,2}[\\s-][A-Za-z]{3,9}(?:[\\s-][0-9]{2,4})?\\b).*")
        private val REPLACE_BY_REGEX = Regex("(?i)\\bby\\b$")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val ORDINAL_REGEX = Regex("(?i)(st|nd|rd|th)")
        private val COMPACT_ALPHA_DATE_REGEX = Regex("(?i)^([0-9]{1,2})([A-Za-z]{3,9})$")
        private val DATE_SPLIT_SLASH_HYPHEN = Regex("[/-]")
        private val DATE_SPLIT_WHITESPACE_HYPHEN = Regex("[\\s-]+")
        private val ATM_BODY_REGEX = Regex("(?i)\\b(atm|cash wdl|cash withdrawal|atm wdl)\\b")
        private val ATM_MERCHANT_REGEX = Regex("(?i)\\b(atm|cash withdrawal)\\b")

        fun isAtmWithdrawal(body: String, merchant: String? = null): Boolean {
            val norm = body.lowercase()
            val merchantNorm = merchant?.lowercase().orEmpty()
            val hasAtmWord = ATM_BODY_REGEX.containsMatchIn(norm) ||
                ATM_MERCHANT_REGEX.containsMatchIn(merchantNorm)
            val hasWithdrawalSignal = listOf("withdrawn", "withdrawal", "debited", "paid", "spent", "cash", "txn").any { norm.contains(it) } ||
                merchantNorm.contains("atm")
            return hasAtmWord && hasWithdrawalSignal
        }
    }
}
