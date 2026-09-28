package com.moneytracker.app.domain.usecase

import android.content.ContentResolver
import android.util.Log
import com.moneytracker.app.ai.GeminiApiClient
import com.moneytracker.app.ai.OnDeviceAiEngine
import com.moneytracker.app.bank.BalanceProofVerifier
import com.moneytracker.app.bank.BankDetector
import com.moneytracker.app.data.db.AccountDao
import com.moneytracker.app.data.db.AccountEntity
import com.moneytracker.app.data.db.ScheduledTransactionDao
import com.moneytracker.app.data.db.ScheduledTransactionEntity
import com.moneytracker.app.data.db.TransactionDao
import com.moneytracker.app.data.db.TransactionEntity
import com.moneytracker.app.data.local.AiEngineMode
import com.moneytracker.app.data.local.AiPreferences
import com.moneytracker.app.data.local.ExclusionPreferences
import com.moneytracker.app.data.local.NotificationPreferences
import com.moneytracker.app.data.model.AccountDraft
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.CardType
import com.moneytracker.app.data.model.ImportReport
import com.moneytracker.app.data.model.ParsedScheduledTransaction
import com.moneytracker.app.data.model.ScheduledTransactionKind
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import com.moneytracker.app.parser.PromotionalDetector
import com.moneytracker.app.parser.SmsParser
import com.moneytracker.app.sms.SmsImportManager
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

enum class SmsIngestionOutcome {
    IMPORTED,
    REVIEW,
    SCHEDULED,
    IGNORED,
    DUPLICATE,
}

/**
 * Handles multi-channel ingestion logic across SMS, status-bar alerts, and direct IMAP streams.
 * Coordinates deduplication against recent transactions, dynamic exclusion filters,
 * categorization, and account balance updates.
 */
class IngestTransactionUseCase(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val scheduledTransactionDao: ScheduledTransactionDao,
    private val parser: SmsParser,
    private val exclusionPreferences: ExclusionPreferences,
    private val notificationPreferences: NotificationPreferences,
    private val aiPreferences: AiPreferences,
    private val onDeviceAiEngine: OnDeviceAiEngine,
    private val geminiApiClient: GeminiApiClient,
    private val reconcileLedgerUseCase: ReconcileLedgerUseCase,
    private val detectTransferPairsUseCase: DetectTransferPairsUseCase,
) {

    suspend fun processIncomingSms(
        sender: String,
        body: String,
        receivedAtMillis: Long,
    ): Boolean {
        val outcome = ingestMessage(sender = sender, body = body, receivedAtMillis = receivedAtMillis)
        return when (outcome) {
            SmsIngestionOutcome.IMPORTED,
            SmsIngestionOutcome.REVIEW,
            SmsIngestionOutcome.SCHEDULED -> {
                deduplicateTransactions()
                true
            }
            SmsIngestionOutcome.DUPLICATE,
            SmsIngestionOutcome.IGNORED -> false
        }
    }

    suspend fun processIncomingNotification(
        packageName: String,
        title: String,
        text: String,
        subText: String,
        postTimeMillis: Long,
    ): Boolean {
        if (!notificationPreferences.isNotificationListenerEnabled.value) {
            return false
        }

        // Check user-configured dynamic exclusion filter immediately
        val matchedExclusion = exclusionPreferences.findMatchedKeyword(title, text, subText)
        if (matchedExclusion != null) {
            Log.i("IngestTxUseCase", "Notification from $packageName skipped: matched exclusion keyword '$matchedExclusion'")
            return false
        }

        // Check dynamic promotional & marketing suppression filter
        val promotionalReason = PromotionalDetector.findPromotionalReason(title, text, subText)
        if (promotionalReason != null) {
            Log.i("IngestTxUseCase", "Notification from $packageName skipped: detected promotional/marketing content ($promotionalReason)")
            return false
        }

        val isGmail = packageName == NotificationPreferences.PACKAGE_GMAIL
        val isPaymentApp = NotificationPreferences.PAYMENT_APP_PACKAGES.contains(packageName)
        val isBankApp = NotificationPreferences.BANK_APP_PACKAGES.contains(packageName)

        if (isGmail && !notificationPreferences.isGmailMonitoringEnabled.value) {
            return false
        }
        if (isPaymentApp && !notificationPreferences.isPaymentAppsMonitoringEnabled.value) {
            return false
        }
        if (isBankApp && !notificationPreferences.isBankAppsMonitoringEnabled.value) {
            return false
        }

        val baseBody = when {
            title.isNotBlank() && text.isNotBlank() && !text.startsWith(title, ignoreCase = true) -> "$title: $text"
            text.isNotBlank() -> text
            else -> title
        }
        val combinedBody = if (subText.isNotBlank() && !baseBody.contains(subText, ignoreCase = true)) {
            "$baseBody ($subText)"
        } else {
            baseBody
        }
        val lower = combinedBody.lowercase(Locale.getDefault())

        val hasMoneyIndicator = listOf("₹", "rs.", "inr", "rs ").any { it in lower }
        val hasTransactionVerb = listOf(
            "debited", "credited", "spent", "paid", "withdrawn", "received", "deducted",
            "sent", "transfer", "successful", "purchase", "bill payment", "alert", "vpa",
        ).any { it in lower }

        if (!hasMoneyIndicator || !hasTransactionVerb) {
            return false
        }

        val resolvedSender = when {
            isGmail -> title.takeIf { it.isNotBlank() } ?: "Gmail"
            packageName == NotificationPreferences.PACKAGE_GPAY -> "Google Pay"
            packageName == NotificationPreferences.PACKAGE_PHONEPE -> "PhonePe"
            packageName == NotificationPreferences.PACKAGE_PAYTM -> "Paytm"
            packageName == NotificationPreferences.PACKAGE_CRED -> "CRED"
            packageName == NotificationPreferences.PACKAGE_BHIM -> "BHIM"
            packageName == NotificationPreferences.PACKAGE_TATA_NEU -> "Tata Neu"
            packageName == NotificationPreferences.PACKAGE_AMAZON_PAY -> "Amazon Pay"
            packageName == NotificationPreferences.PACKAGE_NAVI -> "Navi"
            packageName == NotificationPreferences.PACKAGE_SUPER_MONEY -> "super.money"
            packageName == NotificationPreferences.PACKAGE_MOBIKWIK -> "MobiKwik"
            packageName == NotificationPreferences.PACKAGE_FREECHARGE -> "Freecharge"
            isBankApp -> title.takeIf { it.isNotBlank() } ?: "Bank Alert"
            else -> title.takeIf { it.isNotBlank() } ?: packageName
        }

        val outcome = ingestMessage(
            sender = resolvedSender,
            body = combinedBody,
            receivedAtMillis = postTimeMillis,
        )

        val succeeded = when (outcome) {
            SmsIngestionOutcome.IMPORTED,
            SmsIngestionOutcome.REVIEW,
            SmsIngestionOutcome.SCHEDULED -> {
                deduplicateTransactions()
                true
            }
            SmsIngestionOutcome.DUPLICATE,
            SmsIngestionOutcome.IGNORED -> false
        }

        if (succeeded) {
            notificationPreferences.recordCapturedNotification(packageName, postTimeMillis)
        }
        return succeeded
    }

    suspend fun importRecentSms(
        contentResolver: ContentResolver,
        limit: Int = 200,
    ): ImportReport {
        val importer = SmsImportManager(contentResolver)
        var imported = 0
        var review = 0
        var scheduled = 0
        var ignored = 0
        val messages = importer.readRecentMessages(limit).sortedBy { it.timestampMillis }
        messages.forEach { message ->
            when (
                ingestMessage(
                    sender = message.sender,
                    body = message.body,
                    receivedAtMillis = message.timestampMillis,
                )
            ) {
                SmsIngestionOutcome.IMPORTED -> imported += 1
                SmsIngestionOutcome.REVIEW -> review += 1
                SmsIngestionOutcome.SCHEDULED -> scheduled += 1
                SmsIngestionOutcome.IGNORED -> ignored += 1
                SmsIngestionOutcome.DUPLICATE -> Unit
            }
        }
        deduplicateTransactions()
        reconcileLedgerUseCase.reconcileAccountsAndBalances()
        return ImportReport(
            scanned = messages.size,
            imported = imported,
            sentToReview = review,
            scheduled = scheduled,
            ignored = ignored,
        )
    }

    suspend fun ingestMessage(
        sender: String,
        body: String,
        receivedAtMillis: Long,
    ): SmsIngestionOutcome {
        val earlyExclusion = exclusionPreferences.findMatchedKeyword(sender, body)
        if (earlyExclusion != null) {
            Log.i("IngestTxUseCase", "Ingestion skipped for sender '$sender': matched exclusion keyword '$earlyExclusion'")
            return SmsIngestionOutcome.IGNORED
        }

        // Strict promotional marketing check
        val promotionalReason = PromotionalDetector.findPromotionalReason(sender, body)
        if (promotionalReason != null) {
            Log.i("IngestTxUseCase", "Ingestion skipped for sender '$sender': detected promotional marketing ($promotionalReason)")
            return SmsIngestionOutcome.IGNORED
        }

        val parsedMessage = parser.parseMessage(sender = sender, body = body)
        if (parsedMessage.shouldIgnore && !aiPreferences.isAiEnabled.value) {
            return SmsIngestionOutcome.IGNORED
        }

        parsedMessage.scheduledTransaction?.let { scheduled ->
            return storeScheduledTransaction(
                sender = sender,
                body = body,
                parsed = scheduled,
            )
        }

        val parsed = parsedMessage.transaction
        var amount = parsed?.amount
        var direction = parsed?.direction
        var merchant = parsed?.merchant
        var category = parsed?.inferredCategory ?: TransactionCategory.OTHER
        var accountKind = parsed?.accountKind ?: AccountKind.BANK
        var institutionName = parsed?.institutionName
        var bankLastFour = parsed?.bankAccountLastFourDigits
        var cardLastFour = parsed?.cardLastFourDigits
        var cardType = parsed?.cardType
        var isCardBillPayment = parsed?.isCardBillPayment ?: false
        var availableBalance = parsed?.availableBalance
        var confidence = parsed?.confidence ?: 0.5
        var note: String? = null

        // Unified 4-tier Ingestion Router (Gemini Nano -> Local Regex -> Cloud Gemini -> Local Recovery)
        var detectedEngine = OnDeviceAiEngine.ENGINE_LOCAL_REGEX
        if (aiPreferences.isAiEnabled.value) {
            val engineMode = aiPreferences.engineMode.value
            val apiKey = if (engineMode != AiEngineMode.ON_DEVICE_ONLY) aiPreferences.apiKey.value else ""

            val parseResult = onDeviceAiEngine.parseIncomingMessage(
                body = body,
                sender = sender,
                apiKey = apiKey,
                cloudClient = geminiApiClient,
                parser = parser,
            )
            detectedEngine = parseResult.engine

            parseResult.transaction?.let { result ->
                if (result.isTransaction && result.amount != null && result.direction != null) {
                    amount = result.amount
                    direction = result.direction
                    if (!result.merchant.isNullOrBlank()) {
                        merchant = result.merchant
                    }
                    category = result.category
                    accountKind = result.accountKind
                    if (!result.institutionName.isNullOrBlank()) {
                        institutionName = result.institutionName
                    }
                    if (!result.accountLastFour.isNullOrBlank()) {
                        if (result.accountKind == AccountKind.CARD) {
                            cardLastFour = result.accountLastFour
                        } else {
                            bankLastFour = result.accountLastFour
                        }
                    }
                    if (result.cardType != null) {
                        cardType = result.cardType
                    }
                    isCardBillPayment = result.isCardBillPayment
                    if (result.availableBalance != null) {
                        availableBalance = result.availableBalance
                    }
                    note = result.detailedDescription?.takeIf { it.isNotBlank() } ?: result.placeDetail
                    confidence = result.confidence
                }
            }
        }

        if (amount == null || direction == null) {
            return SmsIngestionOutcome.IGNORED
        }

        val resolvedMerchant = merchant ?: fallbackMerchant(sender, direction)
        val merchantExclusion = exclusionPreferences.findMatchedKeyword(resolvedMerchant)
        if (merchantExclusion != null) {
            Log.i("IngestTxUseCase", "Ingestion skipped: merchant '$resolvedMerchant' matched exclusion keyword '$merchantExclusion'")
            return SmsIngestionOutcome.IGNORED
        }

        val baseNote = note ?: when (direction) {
            TransactionDirection.CREDIT -> "Payment from $resolvedMerchant"
            TransactionDirection.DEBIT -> "Payment to $resolvedMerchant"
        }
        note = if (baseNote.contains("[Engine:")) baseNote else "$baseNote [Engine: $detectedEngine]"

        val resolvedOccurredAt = when {
            receivedAtMillis > 0L -> {
                val parsedTime = parsed?.occurredAtMillis
                if (parsedTime != null) {
                    val parsedDay = Instant.ofEpochMilli(parsedTime)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    val receivedDay = Instant.ofEpochMilli(receivedAtMillis)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    if (parsedDay == receivedDay) {
                        receivedAtMillis
                    } else {
                        val receivedTime = Instant.ofEpochMilli(receivedAtMillis)
                            .atZone(ZoneId.systemDefault()).toLocalTime()
                        parsedDay.atTime(receivedTime)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                } else {
                    receivedAtMillis
                }
            }
            else -> parsed?.occurredAtMillis ?: System.currentTimeMillis()
        }

        val fingerprint = hashFingerprint(sender, body, resolvedOccurredAt)
        if (transactionDao.fingerprintExists(fingerprint)) {
            return SmsIngestionOutcome.DUPLICATE
        }

        val accountId = resolveParsedAccount(
            accountLabel = parsed?.accountLabel,
            accountKind = accountKind,
            institutionName = institutionName,
            bankAccountLastFourDigits = bankLastFour,
            cardLastFourDigits = cardLastFour,
            cardType = cardType,
            isCardBillPayment = isCardBillPayment,
        )

        val balanceProof = BalanceProofVerifier.verifyBalance(sender, body, resolvedOccurredAt)
        val verifiedAvailableBalance = if (balanceProof.isVerified) balanceProof.balance else availableBalance

        val existingSimilar = transactionDao.findSimilarTransaction(
            amount = amount,
            direction = direction,
            occurredAtMillis = resolvedOccurredAt,
            timeToleranceMillis = 900_000L,
        ) ?: run {
            val startOfDay = Instant.ofEpochMilli(resolvedOccurredAt)
                .atZone(ZoneId.systemDefault()).toLocalDate()
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = startOfDay + 86_400_000L
            val dayCandidates = transactionDao.getAllTransactions().filter {
                it.amount == amount && it.direction == direction &&
                    it.occurredAtMillis in startOfDay..endOfDay
            }
            dayCandidates.firstOrNull { candidate ->
                candidate.merchant.equals(resolvedMerchant, ignoreCase = true) ||
                    candidate.merchant.contains(resolvedMerchant, ignoreCase = true) ||
                    resolvedMerchant.contains(candidate.merchant, ignoreCase = true)
            }
        }

        if (existingSimilar != null) {
            val existingRef = detectTransferPairsUseCase.extractTransactionReference(existingSimilar.smsBody)
            val incomingRef = detectTransferPairsUseCase.extractTransactionReference(body)
            val hasDistinctRefs = !existingRef.isNullOrBlank() && !incomingRef.isNullOrBlank() && !existingRef.equals(incomingRef, ignoreCase = true)

            val sameRefMatch = !existingRef.isNullOrBlank() && existingRef.equals(incomingRef, ignoreCase = true)
            val merchantMatch = existingSimilar.merchant.equals(resolvedMerchant, ignoreCase = true) ||
                existingSimilar.merchant.contains(resolvedMerchant, ignoreCase = true) ||
                resolvedMerchant.contains(existingSimilar.merchant, ignoreCase = true)
            val senderMatch = existingSimilar.sourceSender.equals(sender, ignoreCase = true)
            val sameAccount = accountId != null && existingSimilar.accountId != null && accountId == existingSimilar.accountId
            val isExistingGeneric = isGenericMerchant(existingSimilar.merchant)
            val isIncomingGeneric = isGenericMerchant(resolvedMerchant)
            val hasGenericPlaceholder = isExistingGeneric || isIncomingGeneric

            val isDuplicate = !hasDistinctRefs && (
                sameRefMatch ||
                merchantMatch ||
                (hasGenericPlaceholder && (sameAccount || accountId == null || existingSimilar.accountId == null || senderMatch))
            )

            if (isDuplicate) {
                val targetAccountId = accountId ?: existingSimilar.accountId
                val newAvailableBal = if (balanceProof.isVerified) balanceProof.balance else (availableBalance ?: existingSimilar.availableBalance)
                val targetMerchant = if (isExistingGeneric && !isIncomingGeneric) resolvedMerchant else existingSimilar.merchant
                val targetCategory = if (isExistingGeneric && !isIncomingGeneric) category else existingSimilar.category
                val targetNote = if (isExistingGeneric && !isIncomingGeneric) (note ?: existingSimilar.note) else existingSimilar.note
                val targetCountsTowardBudget = if (isExistingGeneric && !isIncomingGeneric) {
                    if (isCardBillPayment || targetCategory == TransactionCategory.TRANSFER) false else (parsed?.countsTowardBudget ?: true)
                } else {
                    existingSimilar.countsTowardBudget
                }
                val targetBody = if (isExistingGeneric && !isIncomingGeneric) body else existingSimilar.smsBody
                val targetSender = if (isExistingGeneric && !isIncomingGeneric) sender else existingSimilar.sourceSender

                val updated = existingSimilar.copy(
                    merchant = targetMerchant,
                    category = targetCategory,
                    note = targetNote,
                    countsTowardBudget = targetCountsTowardBudget,
                    smsBody = targetBody,
                    sourceSender = targetSender,
                    accountId = targetAccountId,
                    availableBalance = newAvailableBal,
                    confidence = maxOf(existingSimilar.confidence, confidence),
                )
                transactionDao.update(updated)

                val proofBal = balanceProof.balance
                if (targetAccountId != null && balanceProof.isVerified && proofBal != null) {
                    accountDao.updateVerifiedBalance(
                        accountId = targetAccountId,
                        balance = proofBal,
                        updatedAt = resolvedOccurredAt,
                        proofSnippet = balanceProof.proofSnippet,
                        proofSource = "SMS (${sender.substringAfter("-")})",
                        isVerified = true,
                    )
                    reconcileLedgerUseCase.reconcileSingleAccount(targetAccountId)
                }
                return SmsIngestionOutcome.DUPLICATE
            }
        }

        val status = if (confidence >= 0.7 && detectedEngine != OnDeviceAiEngine.ENGINE_DEGRADED_MANUAL) {
            TransactionStatus.POSTED
        } else {
            TransactionStatus.REVIEW
        }
        val countsTowardBudget = if (isCardBillPayment || category == TransactionCategory.TRANSFER) {
            false
        } else {
            parsed?.countsTowardBudget ?: true
        }

        val inserted = transactionDao.insert(
            TransactionEntity(
                amount = amount,
                direction = direction,
                occurredAtMillis = resolvedOccurredAt,
                merchant = resolvedMerchant,
                category = category,
                accountId = accountId,
                sourceSender = sender,
                smsBody = body,
                confidence = confidence,
                fingerprint = fingerprint,
                status = status,
                note = note,
                countsTowardBudget = countsTowardBudget,
                availableBalance = verifiedAvailableBalance,
            ),
        )
        if (inserted == -1L) {
            return SmsIngestionOutcome.DUPLICATE
        }

        if (accountId != null) {
            val proofBal = balanceProof.balance
            if (balanceProof.isVerified && proofBal != null) {
                accountDao.updateVerifiedBalance(
                    accountId = accountId,
                    balance = proofBal,
                    updatedAt = resolvedOccurredAt,
                    proofSnippet = balanceProof.proofSnippet,
                    proofSource = balanceProof.proofSource,
                    isVerified = true,
                )
                reconcileLedgerUseCase.reconcileSingleAccount(accountId)
            } else if (verifiedAvailableBalance != null) {
                val snippet = BalanceProofVerifier.extractProofSnippet(sender, body, occurredAtMillis = resolvedOccurredAt)
                accountDao.updateVerifiedBalance(
                    accountId = accountId,
                    balance = verifiedAvailableBalance,
                    updatedAt = resolvedOccurredAt,
                    proofSnippet = snippet,
                    proofSource = "SMS (${sender.substringAfter("-")})",
                    isVerified = true,
                )
                reconcileLedgerUseCase.reconcileSingleAccount(accountId)
            } else {
                reconcileLedgerUseCase.reconcileSingleAccount(accountId)
            }
            detectTransferPairsUseCase.detectAndLinkTransferPair(inserted)
        }

        matchAndLinkBillPayment(
            transactionId = inserted,
            amount = amount,
            direction = direction,
            merchant = resolvedMerchant,
            occurredAtMillis = resolvedOccurredAt,
        )

        return if (status == TransactionStatus.POSTED) {
            SmsIngestionOutcome.IMPORTED
        } else {
            SmsIngestionOutcome.REVIEW
        }
    }

    private suspend fun matchAndLinkBillPayment(
        transactionId: Long,
        amount: Double,
        direction: TransactionDirection,
        merchant: String,
        occurredAtMillis: Long,
    ) {
        if (direction != TransactionDirection.DEBIT) return
        val unpaidReminders = scheduledTransactionDao.getUnpaidBillReminders()
        for (reminder in unpaidReminders) {
            val amountMatches = abs(reminder.amount - amount) < 1.0
            val timeMatches = abs(reminder.scheduledForMillis - occurredAtMillis) <= 15 * 86_400_000L
            val remMerchant = reminder.merchant.lowercase(Locale.getDefault())
            val txMerchant = merchant.lowercase(Locale.getDefault())
            val merchantMatches = remMerchant.contains(txMerchant) ||
                txMerchant.contains(remMerchant) ||
                listOf("credit card", "card bill", "billdesk", "cred", "axis", "hdfc", "icici", "sbi", "kotak").any {
                    remMerchant.contains(it) && txMerchant.contains(it)
                }
            if (amountMatches && timeMatches && merchantMatches) {
                scheduledTransactionDao.linkMatchedTransaction(reminder.id, transactionId)
                break
            }
        }
    }

    private suspend fun storeScheduledTransaction(
        sender: String,
        body: String,
        parsed: ParsedScheduledTransaction,
    ): SmsIngestionOutcome {
        val amount = parsed.amount ?: return SmsIngestionOutcome.IGNORED
        val scheduledForMillis = parsed.scheduledForMillis ?: return SmsIngestionOutcome.IGNORED
        val accountId = resolveParsedAccount(
            accountLabel = parsed.accountLabel,
            accountKind = parsed.accountKind,
            institutionName = parsed.institutionName,
            bankAccountLastFourDigits = parsed.bankAccountLastFourDigits,
            cardLastFourDigits = parsed.cardLastFourDigits,
            cardType = parsed.cardType,
            isCardBillPayment = false,
        )
        val merchant = parsed.merchant ?: fallbackScheduledMerchant(sender)

        // For BILL_REMINDER, perform cross-channel deduplication across SMS, Gmail, and App Push
        if (parsed.kind == ScheduledTransactionKind.BILL_REMINDER) {
            val toleranceMillis = 4 * 86_400_000L // 4 days tolerance
            val similarReminders = scheduledTransactionDao.findSimilarBillReminders(
                amount = amount,
                minDueDate = scheduledForMillis - toleranceMillis,
                maxDueDate = scheduledForMillis + toleranceMillis,
            )
            val cardDigits = parsed.cardLastFourDigits
            val instName = parsed.institutionName
            val matchedExisting = similarReminders.firstOrNull { existing ->
                val m1 = existing.merchant.lowercase(Locale.getDefault())
                val m2 = merchant.lowercase(Locale.getDefault())
                m1 == m2 || m1.contains(m2) || m2.contains(m1) ||
                    (cardDigits != null && existing.smsBody?.contains(cardDigits) == true) ||
                    (instName != null && existing.merchant.contains(instName, ignoreCase = true))
            }
            if (matchedExisting != null) {
                // If the new one has a longer or richer body, enrich existing without creating duplicate
                if (body.length > (matchedExisting.smsBody?.length ?: 0)) {
                    scheduledTransactionDao.update(
                        matchedExisting.copy(
                            smsBody = body,
                            accountId = accountId ?: matchedExisting.accountId,
                        ),
                    )
                }
                return SmsIngestionOutcome.DUPLICATE
            }
        }

        val senderKey = if (parsed.kind == ScheduledTransactionKind.BILL_REMINDER) "CROSS_CHANNEL" else sender
        val dateKey = if (parsed.kind == ScheduledTransactionKind.BILL_REMINDER) scheduledForMillis / (86_400_000L) else scheduledForMillis
        val fingerprint = hashScheduledFingerprint(
            sender = senderKey,
            merchant = merchant,
            amount = amount,
            scheduledForMillis = dateKey,
            accountLabel = parsed.cardLastFourDigits ?: parsed.accountLabel,
            kind = parsed.kind.name,
        )
        if (scheduledTransactionDao.fingerprintExists(fingerprint)) {
            return SmsIngestionOutcome.DUPLICATE
        }
        val inserted = scheduledTransactionDao.insert(
            ScheduledTransactionEntity(
                merchant = merchant,
                amount = amount,
                scheduledForMillis = scheduledForMillis,
                category = parsed.inferredCategory,
                accountId = accountId,
                sourceSender = sender,
                smsBody = body,
                kind = parsed.kind,
                fingerprint = fingerprint,
            ),
        )
        return if (inserted == -1L) {
            SmsIngestionOutcome.DUPLICATE
        } else {
            SmsIngestionOutcome.SCHEDULED
        }
    }

    suspend fun resolveParsedAccount(
        accountLabel: String?,
        accountKind: AccountKind,
        institutionName: String?,
        bankAccountLastFourDigits: String?,
        cardLastFourDigits: String?,
        cardType: CardType?,
        isCardBillPayment: Boolean,
    ): Long? {
        val existingAccounts = accountDao.getAccounts()
        val normalizedInstitution = normalizeInstitutionName(institutionName)
        val normalizedBankLastFour = normalizeLastFourDigits(bankAccountLastFourDigits)
        val normalizedLastFour = normalizeLastFourDigits(cardLastFourDigits)

        val effectiveKind = if (accountKind == AccountKind.UPI && (normalizedInstitution != null || normalizedBankLastFour != null)) {
            AccountKind.BANK
        } else {
            accountKind
        }

        if (isCardBillPayment) {
            findConfiguredBankAccount(
                accounts = existingAccounts,
                lastFourDigits = normalizedBankLastFour,
                institutionName = normalizedInstitution,
            )?.let { return it.id }
            findPrimaryBankAccount(existingAccounts, normalizedInstitution)?.let { return it.id }
        }

        if (effectiveKind == AccountKind.BANK) {
            findConfiguredBankAccount(
                accounts = existingAccounts,
                lastFourDigits = normalizedBankLastFour,
                institutionName = normalizedInstitution,
            )?.let { return it.id }
        }

        if (normalizedLastFour != null) {
            findConfiguredCardAccount(
                accounts = existingAccounts,
                lastFourDigits = normalizedLastFour,
                institutionName = normalizedInstitution,
                cardType = cardType,
            )?.let { return it.id }
        }

        return when (effectiveKind) {
            AccountKind.BANK -> {
                val bankAccount = if (normalizedBankLastFour != null) {
                    findConfiguredBankAccount(
                        accounts = existingAccounts,
                        lastFourDigits = normalizedBankLastFour,
                        institutionName = normalizedInstitution,
                    )
                } else {
                    findPrimaryBankAccount(existingAccounts, normalizedInstitution)
                }
                bankAccount?.id ?: resolveAccount(
                    name = accountLabel ?: defaultAccountName(
                        kind = AccountKind.BANK,
                        institutionName = normalizedInstitution,
                        lastFourDigits = normalizedBankLastFour,
                    ),
                    kind = AccountKind.BANK,
                    institutionName = normalizedInstitution,
                    lastFourDigits = normalizedBankLastFour,
                )
            }

            AccountKind.CARD -> resolveAccount(
                name = accountLabel ?: defaultAccountName(
                    kind = AccountKind.CARD,
                    institutionName = normalizedInstitution,
                    cardType = cardType,
                    lastFourDigits = normalizedLastFour,
                ),
                kind = AccountKind.CARD,
                institutionName = normalizedInstitution,
                cardType = cardType,
                lastFourDigits = normalizedLastFour,
            )

            AccountKind.WALLET,
            AccountKind.UPI,
            AccountKind.CASH -> resolveAccount(
                name = accountLabel ?: defaultAccountName(effectiveKind),
                kind = effectiveKind,
            )
        }
    }

    suspend fun resolveAccount(
        name: String,
        kind: AccountKind,
        institutionName: String? = null,
        cardType: CardType? = null,
        lastFourDigits: String? = null,
    ): Long {
        val normalizedInstitution = normalizeInstitutionName(institutionName)
        val normalizedLastFour = normalizeLastFourDigits(lastFourDigits)
        val existingAccounts = accountDao.getAccounts()

        if (kind == AccountKind.BANK) {
            val isLegitBank = normalizedInstitution != null ||
                BankDetector.isLegitimateBank(name) ||
                existingAccounts.any { it.kind == AccountKind.BANK && it.name.equals(name, ignoreCase = true) }

            if (!isLegitBank) {
                val primary = findPrimaryBankAccount(existingAccounts, null)
                if (primary != null) return primary.id
            }
        }
        val existing = when (kind) {
            AccountKind.BANK -> {
                if (normalizedLastFour != null) {
                    findConfiguredBankAccount(existingAccounts, normalizedLastFour, normalizedInstitution)
                        ?: existingAccounts.firstOrNull { account ->
                            account.kind == AccountKind.BANK &&
                                account.lastFourDigits == null &&
                                (normalizedInstitution == null || normalizeInstitutionName(account.institutionName) == normalizedInstitution)
                        }
                } else {
                    findPrimaryBankAccount(existingAccounts, normalizedInstitution)
                        ?.takeIf { normalizedInstitution != null }
                        ?: existingAccounts.firstOrNull { account ->
                            account.kind == AccountKind.BANK &&
                                account.name.equals(name, ignoreCase = true)
                        }
                }
            }

            AccountKind.CARD -> findConfiguredCardAccount(
                accounts = existingAccounts,
                lastFourDigits = normalizedLastFour,
                institutionName = normalizedInstitution,
                cardType = cardType,
            ) ?: existingAccounts.firstOrNull { account ->
                account.kind == AccountKind.CARD &&
                    account.name.equals(name, ignoreCase = true)
            }

            else -> existingAccounts.firstOrNull { account ->
                account.kind == kind && account.name.equals(name, ignoreCase = true)
            }
        }

        if (existing != null) {
            val hydrated = existing.copy(
                institutionName = existing.institutionName ?: normalizedInstitution,
                cardType = existing.cardType ?: cardType,
                lastFourDigits = existing.lastFourDigits ?: normalizedLastFour,
            )
            if (hydrated != existing) {
                accountDao.update(hydrated)
            }
            return existing.id
        }

        return accountDao.insert(
            AccountEntity(
                name = name,
                kind = kind,
                institutionName = normalizedInstitution,
                cardType = cardType,
                lastFourDigits = normalizedLastFour,
                isSystemGenerated = true,
            ),
        )
    }

    fun findPrimaryBankAccount(
        accounts: List<AccountEntity>,
        institutionName: String?,
    ): AccountEntity? {
        val normalizedInstitution = normalizeInstitutionName(institutionName)
        if (normalizedInstitution != null) {
            return accounts.firstOrNull { account ->
                account.kind == AccountKind.BANK &&
                    normalizeInstitutionName(account.institutionName) == normalizedInstitution
            } ?: accounts.firstOrNull { account ->
                account.kind == AccountKind.BANK &&
                    account.name.contains(normalizedInstitution.removeSuffix(" Bank"), ignoreCase = true)
            }
        }
        return accounts.firstOrNull { account ->
            account.kind == AccountKind.BANK && account.institutionName != null
        } ?: accounts.firstOrNull { account ->
            account.kind == AccountKind.BANK
        }
    }

    fun findConfiguredBankAccount(
        accounts: List<AccountEntity>,
        lastFourDigits: String?,
        institutionName: String?,
    ): AccountEntity? {
        val normalizedLastFour = normalizeLastFourDigits(lastFourDigits) ?: return null
        val normalizedInstitution = normalizeInstitutionName(institutionName)
        return accounts.firstOrNull { account ->
            account.kind == AccountKind.BANK &&
                (account.lastFourDigits == normalizedLastFour || account.name.contains(normalizedLastFour)) &&
                (normalizedInstitution == null ||
                    account.institutionName == null ||
                    normalizeInstitutionName(account.institutionName) == normalizedInstitution)
        } ?: accounts.firstOrNull { account ->
            account.kind == AccountKind.BANK &&
                (account.lastFourDigits == normalizedLastFour || account.name.contains(normalizedLastFour))
        }
    }

    fun findConfiguredCardAccount(
        accounts: List<AccountEntity>,
        lastFourDigits: String?,
        institutionName: String?,
        cardType: CardType?,
    ): AccountEntity? {
        val normalizedLastFour = normalizeLastFourDigits(lastFourDigits) ?: return null
        val normalizedInstitution = normalizeInstitutionName(institutionName)
        return accounts.firstOrNull { account ->
            account.kind == AccountKind.CARD &&
                account.lastFourDigits == normalizedLastFour &&
                (cardType == null || account.cardType == null || account.cardType == cardType) &&
                (normalizedInstitution == null ||
                    account.institutionName == null ||
                    normalizeInstitutionName(account.institutionName) == normalizedInstitution)
        } ?: accounts.firstOrNull { account ->
            account.kind == AccountKind.CARD &&
                account.lastFourDigits == normalizedLastFour
        } ?: accounts.firstOrNull { account ->
            account.kind == AccountKind.CARD &&
                account.lastFourDigits == null &&
                account.name.contains(normalizedLastFour) &&
                (normalizedInstitution == null ||
                    account.name.contains(normalizedInstitution.removeSuffix(" Bank"), ignoreCase = true))
        }
    }

    fun defaultAccountName(
        kind: AccountKind,
        institutionName: String? = null,
        cardType: CardType? = null,
        lastFourDigits: String? = null,
    ): String {
        return when (kind) {
            AccountKind.BANK -> listOfNotNull(
                institutionName,
                lastFourDigits?.let { "A/C $it" },
            ).joinToString(" ").ifBlank { "Bank Account" }
            AccountKind.CARD -> listOfNotNull(
                institutionName,
                cardType?.label,
                lastFourDigits?.let { "ending $it" },
            ).joinToString(" ").ifBlank { "Card" }

            AccountKind.WALLET -> "Wallet"
            AccountKind.UPI -> "UPI"
            AccountKind.CASH -> "Cash"
        }
    }

    fun normalizeInstitutionName(value: String?): String? {
        val canonical = BankDetector.normalizeToCanonicalBank(value)
        if (canonical != null) return canonical
        return value?.trim()?.takeIf { it.isNotBlank() && BankDetector.isLegitimateBank(it) }
    }

    fun normalizeLastFourDigits(value: String?): String? {
        val digits = value?.filter(Char::isDigit)?.takeLast(4).orEmpty()
        return digits.takeIf { it.length == 4 }
    }

    fun fallbackMerchant(sender: String, direction: TransactionDirection): String {
        return when (direction) {
            TransactionDirection.CREDIT -> "Incoming via ${sender.uppercase(Locale.getDefault())}"
            TransactionDirection.DEBIT -> "Spent via ${sender.uppercase(Locale.getDefault())}"
        }
    }

    private fun fallbackScheduledMerchant(sender: String): String {
        return "Scheduled via ${sender.uppercase(Locale.getDefault())}"
    }

    fun isGenericMerchant(merchant: String): Boolean {
        val lower = merchant.lowercase(Locale.getDefault()).trim()
        return lower.startsWith("spent via") ||
            lower.startsWith("incoming via") ||
            lower == "bank alert" ||
            lower == "payment" ||
            lower.matches(Regex("""^(?:upi transfer|transfer)\s*\(?\.\.[0-9]{4}\)?$""")) ||
            lower.matches(Regex("""^(?:\+?91)?[0-9]{10,12}$"""))
    }

    private fun normalizeMerchant(merchant: String): String {
        return merchant.trim().replace(Regex("\\s+"), " ")
    }

    fun hashFingerprint(sender: String, body: String, receivedAtMillis: Long): String {
        val normalizedSender = sender.trim().uppercase(Locale.getDefault())
        val normalizedBody = body.trim().replace(Regex("\\s+"), " ")
        val minuteBucket = if (receivedAtMillis > 0L) receivedAtMillis / 60_000L else 0L
        return hashCompositeFingerprint(normalizedSender, normalizedBody, minuteBucket)
    }

    private fun hashScheduledFingerprint(
        sender: String,
        merchant: String,
        amount: Double,
        scheduledForMillis: Long,
        accountLabel: String?,
        kind: String,
    ): String {
        return hashCompositeFingerprint(
            "scheduled",
            sender.uppercase(Locale.getDefault()),
            normalizeMerchant(merchant),
            amount,
            scheduledForMillis,
            accountLabel ?: "",
            kind,
        )
    }

    private fun hashCompositeFingerprint(vararg parts: Any): String {
        val input = parts.joinToString(separator = "|")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    suspend fun deduplicateTransactions(windowDays: Int = 14): Int {
        val cutoff = System.currentTimeMillis() - windowDays * 24 * 60 * 60 * 1000L
        val candidates = if (windowDays > 0) {
            transactionDao.getRecentTransactions(cutoff)
        } else {
            transactionDao.getAllTransactions()
        }
        val duplicatesToDelete = mutableListOf<Long>()
        val seen = mutableListOf<TransactionEntity>()

        for (tx in candidates) {
            val merchantLower = tx.merchant.trim().lowercase(Locale.getDefault())
            val isBogus = merchantLower.startsWith("be recorded") ||
                merchantLower.contains("recorded by amc") ||
                (merchantLower == "merchant" && tx.note?.contains("Card purchase at Merchant", ignoreCase = true) == true)

            if (isBogus) {
                duplicatesToDelete.add(tx.id)
                continue
            }

            val matchingExisting = seen.firstOrNull { existing ->
                if (existing.amount != tx.amount || existing.direction != tx.direction) return@firstOrNull false
                if (abs(existing.occurredAtMillis - tx.occurredAtMillis) > 900_000L) return@firstOrNull false

                val existingRef = detectTransferPairsUseCase.extractTransactionReference(existing.smsBody)
                val txRef = detectTransferPairsUseCase.extractTransactionReference(tx.smsBody)
                val hasDistinctRefs = !existingRef.isNullOrBlank() && !txRef.isNullOrBlank() && !existingRef.equals(txRef, ignoreCase = true)
                if (hasDistinctRefs) return@firstOrNull false

                val sameRefMatch = !existingRef.isNullOrBlank() && existingRef.equals(txRef, ignoreCase = true)
                val merchantMatch = existing.merchant.equals(tx.merchant, ignoreCase = true) ||
                    existing.merchant.contains(tx.merchant, ignoreCase = true) ||
                    tx.merchant.contains(existing.merchant, ignoreCase = true)
                val senderMatch = existing.sourceSender.equals(tx.sourceSender, ignoreCase = true)
                val sameAccount = existing.accountId != null && tx.accountId != null && existing.accountId == tx.accountId
                val isExistingGeneric = isGenericMerchant(existing.merchant)
                val isTxGeneric = isGenericMerchant(tx.merchant)
                val hasGenericPlaceholder = isExistingGeneric || isTxGeneric

                sameRefMatch ||
                    merchantMatch ||
                    (hasGenericPlaceholder && (sameAccount || existing.accountId == null || tx.accountId == null || senderMatch))
            }

            if (matchingExisting != null) {
                // If existing has a generic merchant name and tx has a specific one, upgrade existing!
                if (isGenericMerchant(matchingExisting.merchant) && !isGenericMerchant(tx.merchant)) {
                    val upgraded = matchingExisting.copy(
                        merchant = tx.merchant,
                        category = tx.category,
                        note = tx.note ?: matchingExisting.note,
                        countsTowardBudget = tx.countsTowardBudget,
                        accountId = tx.accountId ?: matchingExisting.accountId,
                        availableBalance = tx.availableBalance ?: matchingExisting.availableBalance,
                        confidence = maxOf(matchingExisting.confidence, tx.confidence),
                        smsBody = tx.smsBody ?: matchingExisting.smsBody,
                        sourceSender = if (tx.sourceSender.contains("BK", ignoreCase = true) || tx.sourceSender.contains("BANK", ignoreCase = true)) tx.sourceSender else matchingExisting.sourceSender,
                    )
                    transactionDao.update(upgraded)
                    seen.remove(matchingExisting)
                    seen.add(upgraded)
                }
                duplicatesToDelete.add(tx.id)
            } else {
                seen.add(tx)
            }
        }

        duplicatesToDelete.forEach { id ->
            transactionDao.deleteById(id)
        }
        return duplicatesToDelete.size
    }
}
