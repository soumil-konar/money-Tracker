package com.soumil.moneytracker.data.repo

import android.content.ContentResolver
import com.soumil.moneytracker.data.db.AccountDao
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.db.BudgetDao
import com.soumil.moneytracker.data.db.BudgetEntity
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
    private val subscriptionDao: SubscriptionDao,
    private val transactionDao: TransactionDao,
    private val parser: SmsParser,
) {

    val accounts: Flow<List<AccountEntity>> = accountDao.observeAccounts()
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

    suspend fun dismissTransaction(transactionId: Long) {
        transactionDao.deleteById(transactionId)
    }

    suspend fun processIncomingSms(
        sender: String,
        body: String,
        receivedAtMillis: Long,
    ): Boolean {
        val fingerprint = hashFingerprint(sender, body, receivedAtMillis)
        if (transactionDao.fingerprintExists(fingerprint)) {
            return false
        }

        val parsed = parser.parse(sender = sender, body = body)
        if (parsed.shouldIgnore || parsed.amount == null || parsed.direction == null) {
            return false
        }

        val accountId = resolveAccount(
            parsed.accountLabel ?: defaultAccountName(parsed.accountKind),
            parsed.accountKind,
        )

        val status = if (parsed.confidence >= 0.7) TransactionStatus.POSTED else TransactionStatus.REVIEW
        val merchantLabel = parsed.merchant ?: fallbackMerchant(sender, parsed.direction)

        val entity = TransactionEntity(
            amount = parsed.amount,
            direction = parsed.direction,
            occurredAtMillis = receivedAtMillis,
            merchant = merchantLabel,
            category = parsed.inferredCategory,
            accountId = accountId,
            sourceSender = sender,
            smsBody = body,
            confidence = parsed.confidence,
            fingerprint = fingerprint,
            status = status,
        )
        return transactionDao.insert(entity) != -1L
    }

    suspend fun importRecentSms(
        contentResolver: ContentResolver,
        limit: Int = 200,
    ): ImportReport {
        val importer = SmsImportManager(contentResolver)
        var imported = 0
        var review = 0
        var ignored = 0
        val messages = importer.readRecentMessages(limit)
        messages.forEach { message ->
            val fingerprint = hashFingerprint(message.sender, message.body, message.timestampMillis)
            if (transactionDao.fingerprintExists(fingerprint)) return@forEach
            val parsed = parser.parse(message.sender, message.body)
            if (parsed.shouldIgnore || parsed.amount == null || parsed.direction == null) {
                ignored += 1
                return@forEach
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
                    occurredAtMillis = message.timestampMillis,
                    merchant = parsed.merchant ?: fallbackMerchant(message.sender, parsed.direction),
                    category = parsed.inferredCategory,
                    accountId = accountId,
                    sourceSender = message.sender,
                    smsBody = message.body,
                    confidence = parsed.confidence,
                    fingerprint = fingerprint,
                    status = status,
                ),
            )

            if (inserted != -1L) {
                if (status == TransactionStatus.POSTED) imported += 1 else review += 1
            }
        }
        return ImportReport(
            scanned = messages.size,
            imported = imported,
            sentToReview = review,
            ignored = ignored,
        )
    }

    suspend fun refreshRecurringSuggestions() {
        val existingMerchants = subscriptionDao.getAll().associateBy { normalizeMerchant(it.merchant) }
        val debitTransactions = postedTransactions.first().filter { it.direction == TransactionDirection.DEBIT }
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
        val monthSpent = currentMonthTransactions
            .filter { it.direction == TransactionDirection.DEBIT }
            .sumOf(TransactionRecord::amount)
        val monthIncome = currentMonthTransactions
            .filter { it.direction == TransactionDirection.CREDIT }
            .sumOf(TransactionRecord::amount)
        val trackedBalance = postedTransactions.sumOf {
            if (it.direction == TransactionDirection.CREDIT) it.amount else -it.amount
        }
        val categoryBreakdown = currentMonthTransactions
            .filter { it.direction == TransactionDirection.DEBIT }
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
                expense = dayTransactions.filter { it.direction == TransactionDirection.DEBIT }.sumOf(TransactionRecord::amount),
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

    private fun hashFingerprint(sender: String, body: String, receivedAtMillis: Long): String {
        val input = "$sender|$body|$receivedAtMillis"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun TransactionRecord.toLocalDate(): LocalDate {
        return Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
