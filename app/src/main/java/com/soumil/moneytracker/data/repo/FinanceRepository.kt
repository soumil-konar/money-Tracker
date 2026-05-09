package com.soumil.moneytracker.data.repo

import android.content.ContentResolver
import com.soumil.moneytracker.data.db.AccountDao
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.db.BudgetDao
import com.soumil.moneytracker.data.db.BudgetEntity
import com.soumil.moneytracker.data.db.ScheduledTransactionDao
import com.soumil.moneytracker.data.db.ScheduledTransactionEntity
import com.soumil.moneytracker.data.db.ScheduledTransactionRecord
import com.soumil.moneytracker.data.db.SubscriptionDao
import com.soumil.moneytracker.data.db.SubscriptionEntity
import com.soumil.moneytracker.data.db.SubscriptionRecord
import com.soumil.moneytracker.data.db.TransactionDao
import com.soumil.moneytracker.data.db.TransactionEntity
import com.soumil.moneytracker.data.db.TransactionRecord
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.CategorySlice
import com.soumil.moneytracker.data.model.DashboardState
import com.soumil.moneytracker.data.model.ImportReport
import com.soumil.moneytracker.data.model.ParsedScheduledTransaction
import com.soumil.moneytracker.data.model.SubscriptionDraft
import com.soumil.moneytracker.data.model.SubscriptionState
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import com.soumil.moneytracker.data.model.TransactionDraft
import com.soumil.moneytracker.data.model.TransactionStatus
import com.soumil.moneytracker.data.model.TrendPoint
import com.soumil.moneytracker.parser.SmsParser
import com.soumil.moneytracker.sms.SmsImportManager
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class FinanceRepository(
    private val accountDao: AccountDao,
    private val budgetDao: BudgetDao,
    private val scheduledTransactionDao: ScheduledTransactionDao,
    private val subscriptionDao: SubscriptionDao,
    private val transactionDao: TransactionDao,
    private val parser: SmsParser,
) {

    val accounts: Flow<List<AccountEntity>> = accountDao.observeAccounts()
    val scheduledTransactions: Flow<List<ScheduledTransactionRecord>> = scheduledTransactionDao.observeScheduledTransactions()
    val transactions: Flow<List<TransactionRecord>> = transactionDao.observeTransactions()
    val postedTransactions: Flow<List<TransactionRecord>> = transactionDao.observePostedTransactions()
    val subscriptions: Flow<List<SubscriptionRecord>> = subscriptionDao.observeSubscriptions()
    val currentBudget: Flow<BudgetEntity?> = budgetDao.observeOverallBudget(currentMonthKey())

    val dashboard: Flow<DashboardState> = combine(
        postedTransactions,
        transactions,
        currentBudget,
        subscriptions,
    ) { posted, all, budget, subscriptions ->
        buildDashboardState(
            postedTransactions = posted,
            allTransactions = all,
            budget = budget?.amountLimit,
            subscriptions = subscriptions,
        )
    }

    suspend fun bootstrap() {
        ensureStarterAccounts()
        refreshRecurringSuggestions()
    }

    suspend fun setMonthlyBudget(amount: Double) {
        val monthKey = currentMonthKey()
        budgetDao.deleteOverallBudget(monthKey)
        budgetDao.insert(
            BudgetEntity(
                monthKey = monthKey,
                category = null,
                amountLimit = amount,
            ),
        )
    }

    suspend fun addManualTransaction(draft: TransactionDraft) {
        val fingerprint = hashFingerprint(
            sender = "MANUAL",
            body = listOf(
                draft.merchant,
                draft.amount,
                draft.direction,
                draft.occurredAtMillis,
            ).joinToString("|"),
            receivedAtMillis = draft.occurredAtMillis,
        )
        val transaction = TransactionEntity(
            amount = draft.amount,
            direction = draft.direction,
            occurredAtMillis = draft.occurredAtMillis,
            merchant = draft.merchant.trim(),
            category = draft.category,
            accountId = draft.accountId,
            sourceSender = "MANUAL",
            smsBody = null,
            confidence = 1.0,
            fingerprint = fingerprint,
            status = TransactionStatus.POSTED,
            note = draft.note?.takeIf { it.isNotBlank() },
        )
        transactionDao.insert(transaction)
    }

    suspend fun updateTransaction(transactionId: Long, draft: TransactionDraft) {
        val existing = transactionDao.getById(transactionId)
            ?: error("Transaction $transactionId not found")
        transactionDao.update(
            existing.copy(
                amount = draft.amount,
                direction = draft.direction,
                merchant = draft.merchant.trim(),
                category = draft.category,
                accountId = draft.accountId,
                confidence = 1.0,
                status = TransactionStatus.POSTED,
                note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    suspend fun addSubscription(draft: SubscriptionDraft) {
        subscriptionDao.insert(
            SubscriptionEntity(
                merchant = draft.merchant.trim(),
                amount = draft.amount,
                billingCycleDays = draft.billingCycleDays,
                nextDueAtMillis = draft.nextDueAtMillis,
                accountId = draft.accountId,
                state = SubscriptionState.ACTIVE,
            ),
        )
    }

    suspend fun promoteSubscription(subscriptionId: Long) {
        val subscription = subscriptionDao.getAll().firstOrNull { it.id == subscriptionId } ?: return
        subscriptionDao.update(subscription.copy(state = SubscriptionState.ACTIVE))
    }

    suspend fun dismissSubscription(subscriptionId: Long) {
        subscriptionDao.deleteById(subscriptionId)
    }

    suspend fun approveReview(transactionId: Long) {
        transactionDao.updateStatus(transactionId, TransactionStatus.POSTED.name)
    }

    suspend fun deleteTransaction(transactionId: Long) {
        transactionDao.deleteById(transactionId)
    }

    suspend fun processIncomingSms(
        sender: String,
        body: String,
        receivedAtMillis: Long,
    ): Boolean {
        return when (ingestMessage(sender = sender, body = body, receivedAtMillis = receivedAtMillis)) {
            SmsIngestionOutcome.IMPORTED,
            SmsIngestionOutcome.REVIEW,
            SmsIngestionOutcome.SCHEDULED -> true

            SmsIngestionOutcome.DUPLICATE,
            SmsIngestionOutcome.IGNORED -> false
        }
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
        val messages = importer.readRecentMessages(limit)
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
        return ImportReport(
            scanned = messages.size,
            imported = imported,
            sentToReview = review,
            scheduled = scheduled,
            ignored = ignored,
        )
    }

    suspend fun refreshRecurringSuggestions() {
        val existingMerchants = subscriptionDao.getAll().associateBy { normalizeMerchant(it.merchant) }
        val debitTransactions = postedTransactions.first().filter {
            it.direction == TransactionDirection.DEBIT && it.category != TransactionCategory.TRANSFER
        }
        val grouped = debitTransactions.groupBy { normalizeMerchant(it.merchant) }

        grouped.forEach { (merchantKey, transactions) ->
            if (merchantKey.isBlank() || merchantKey in existingMerchants) return@forEach
            if (transactions.size < 3) return@forEach

            val sorted = transactions.sortedBy { it.occurredAtMillis }
            val intervals = sorted.zipWithNext { first, second ->
                val days = (second.occurredAtMillis - first.occurredAtMillis) / (24 * 60 * 60 * 1000)
                days.toInt()
            }
            if (intervals.isEmpty()) return@forEach

            val averageInterval = intervals.average()
            val averageAmount = sorted.map { it.amount }.average()
            val consistentTiming = averageInterval in 25.0..35.0
            val consistentAmount = sorted.all { abs(it.amount - averageAmount) <= averageAmount * 0.15 }
            if (!consistentTiming || !consistentAmount) return@forEach

            val latest = sorted.last()
            subscriptionDao.insert(
                SubscriptionEntity(
                    merchant = latest.merchant,
                    amount = latest.amount,
                    billingCycleDays = averageInterval.toInt().coerceAtLeast(28),
                    nextDueAtMillis = latest.occurredAtMillis + (averageInterval.toLong() * 24 * 60 * 60 * 1000),
                    accountId = null,
                    state = SubscriptionState.SUGGESTED,
                ),
            )
        }
    }

    private suspend fun ensureStarterAccounts() {
        if (accountDao.countAccounts() > 0) return
        listOf(
            AccountEntity(name = "Primary Bank", kind = AccountKind.BANK, isSystemGenerated = true),
            AccountEntity(name = "UPI Wallet", kind = AccountKind.UPI, isSystemGenerated = true),
            AccountEntity(name = "Credit Card", kind = AccountKind.CARD, isSystemGenerated = true),
        ).forEach { accountDao.insert(it) }
    }

    private suspend fun resolveAccount(name: String, kind: AccountKind): Long {
        return accountDao.findByName(name)?.id
            ?: accountDao.insert(
                AccountEntity(
                    name = name,
                    kind = kind,
                    isSystemGenerated = true,
                ),
            )
    }

    private fun buildDashboardState(
        postedTransactions: List<TransactionRecord>,
        allTransactions: List<TransactionRecord>,
        budget: Double?,
        subscriptions: List<SubscriptionRecord>,
    ): DashboardState {
        val currentMonth = YearMonth.now()
        val currentMonthTransactions = postedTransactions.filter {
            YearMonth.from(it.toLocalDate()) == currentMonth
        }
        val spendTransactions = currentMonthTransactions.filter {
            it.direction == TransactionDirection.DEBIT && it.category != TransactionCategory.TRANSFER
        }
        val monthSpent = spendTransactions
            .sumOf(TransactionRecord::amount)
        val monthIncome = currentMonthTransactions
            .filter { it.direction == TransactionDirection.CREDIT }
            .sumOf(TransactionRecord::amount)
        val trackedBalance = postedTransactions.sumOf {
            if (it.direction == TransactionDirection.CREDIT) it.amount else -it.amount
        }
        val categoryBreakdown = spendTransactions
            .groupBy(TransactionRecord::category)
            .map { (category, items) -> CategorySlice(category, items.sumOf(TransactionRecord::amount)) }
            .sortedByDescending(CategorySlice::amount)

        val today = LocalDate.now()
        val trendPoints = (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val dayTransactions = currentMonthTransactions.filter { it.toLocalDate() == date }
            TrendPoint(
                date = date,
                income = dayTransactions.filter { it.direction == TransactionDirection.CREDIT }.sumOf(TransactionRecord::amount),
                expense = dayTransactions
                    .filter { it.direction == TransactionDirection.DEBIT && it.category != TransactionCategory.TRANSFER }
                    .sumOf(TransactionRecord::amount),
            )
        }

        return DashboardState(
            trackedBalance = trackedBalance,
            monthSpent = monthSpent,
            monthIncome = monthIncome,
            budgetLimit = budget,
            reviewCount = allTransactions.count { it.status == TransactionStatus.REVIEW },
            activeSubscriptionsCount = subscriptions.count { it.state == SubscriptionState.ACTIVE },
            categoryBreakdown = categoryBreakdown,
            trendPoints = trendPoints,
            recentTransactions = allTransactions.take(6),
        )
    }

    private fun currentMonthKey(): String = YearMonth.now().toString()

    private fun defaultAccountName(kind: AccountKind): String {
        return when (kind) {
            AccountKind.BANK -> "Primary Bank"
            AccountKind.CARD -> "Credit Card"
            AccountKind.WALLET -> "Wallet"
            AccountKind.UPI -> "UPI Wallet"
            AccountKind.CASH -> "Cash"
        }
    }

    private fun fallbackMerchant(sender: String, direction: TransactionDirection): String {
        return when (direction) {
            TransactionDirection.CREDIT -> "Incoming via ${sender.uppercase()}"
            TransactionDirection.DEBIT -> "Spent via ${sender.uppercase()}"
        }
    }

    private fun normalizeMerchant(merchant: String): String {
        return merchant.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private suspend fun ingestMessage(
        sender: String,
        body: String,
        receivedAtMillis: Long,
    ): SmsIngestionOutcome {
        val parsedMessage = parser.parseMessage(sender = sender, body = body)
        if (parsedMessage.shouldIgnore) {
            return SmsIngestionOutcome.IGNORED
        }

        parsedMessage.scheduledTransaction?.let { scheduled ->
            return storeScheduledTransaction(
                sender = sender,
                body = body,
                parsed = scheduled,
            )
        }

        val parsed = parsedMessage.transaction ?: return SmsIngestionOutcome.IGNORED
        if (parsed.amount == null || parsed.direction == null) {
            return SmsIngestionOutcome.IGNORED
        }

        val fingerprint = hashFingerprint(sender, body, receivedAtMillis)
        if (transactionDao.fingerprintExists(fingerprint)) {
            return SmsIngestionOutcome.DUPLICATE
        }

        val accountId = resolveAccount(
            parsed.accountLabel ?: defaultAccountName(parsed.accountKind),
            parsed.accountKind,
        )
        val status = if (parsed.confidence >= 0.7) TransactionStatus.POSTED else TransactionStatus.REVIEW
        val inserted = transactionDao.insert(
            TransactionEntity(
                amount = parsed.amount,
                direction = parsed.direction,
                occurredAtMillis = receivedAtMillis,
                merchant = parsed.merchant ?: fallbackMerchant(sender, parsed.direction),
                category = parsed.inferredCategory,
                accountId = accountId,
                sourceSender = sender,
                smsBody = body,
                confidence = parsed.confidence,
                fingerprint = fingerprint,
                status = status,
            ),
        )
        if (inserted == -1L) {
            return SmsIngestionOutcome.DUPLICATE
        }
        return if (status == TransactionStatus.POSTED) {
            SmsIngestionOutcome.IMPORTED
        } else {
            SmsIngestionOutcome.REVIEW
        }
    }

    private suspend fun storeScheduledTransaction(
        sender: String,
        body: String,
        parsed: ParsedScheduledTransaction,
    ): SmsIngestionOutcome {
        val amount = parsed.amount ?: return SmsIngestionOutcome.IGNORED
        val scheduledForMillis = parsed.scheduledForMillis ?: return SmsIngestionOutcome.IGNORED
        val accountId = resolveAccount(
            parsed.accountLabel ?: defaultAccountName(parsed.accountKind),
            parsed.accountKind,
        )
        val merchant = parsed.merchant ?: fallbackScheduledMerchant(sender)
        val fingerprint = hashScheduledFingerprint(
            sender = sender,
            merchant = merchant,
            amount = amount,
            scheduledForMillis = scheduledForMillis,
            accountLabel = parsed.accountLabel,
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

    private fun fallbackScheduledMerchant(sender: String): String {
        return "Scheduled via ${sender.uppercase()}"
    }

    private fun hashFingerprint(sender: String, body: String, receivedAtMillis: Long): String {
        return hashCompositeFingerprint(sender, body, receivedAtMillis)
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
            sender.uppercase(),
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

    private fun TransactionRecord.toLocalDate(): LocalDate {
        return Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private enum class SmsIngestionOutcome {
        IMPORTED,
        REVIEW,
        SCHEDULED,
        IGNORED,
        DUPLICATE,
    }
}
