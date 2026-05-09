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
import com.soumil.moneytracker.data.local.SetupPreferences
import com.soumil.moneytracker.data.model.AccountDraft
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.CardType
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
    private val setupPreferences: SetupPreferences,
) {

    val accounts: Flow<List<AccountEntity>> = accountDao.observeAccounts()
    val isInitialSetupComplete: Flow<Boolean> = setupPreferences.isInitialSetupComplete
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
        refreshRecurringSuggestions()
    }

    fun markInitialSetupComplete() {
        setupPreferences.markInitialSetupComplete(true)
    }

    suspend fun configurePrimaryBank(
        institutionName: String,
        accountName: String,
    ) {
        val normalizedInstitution = normalizeInstitutionName(institutionName)
            ?: institutionName.trim().takeIf { it.isNotBlank() }
            ?: error("Institution name is required")
        val resolvedName = accountName.trim().ifBlank { normalizedInstitution }
        val existingAccounts = accountDao.getAccounts()
        val target = findPrimaryBankAccount(existingAccounts, normalizedInstitution)
            ?: existingAccounts.firstOrNull { account ->
                account.kind == AccountKind.BANK &&
                    (account.isSystemGenerated || account.institutionName == null)
            }

        val entity = if (target == null) {
            AccountEntity(
                name = resolvedName,
                kind = AccountKind.BANK,
                institutionName = normalizedInstitution,
                isSystemGenerated = false,
            )
        } else {
            target.copy(
                name = resolvedName,
                kind = AccountKind.BANK,
                institutionName = normalizedInstitution,
                cardType = null,
                lastFourDigits = null,
                isRupayCreditCard = false,
                isSystemGenerated = false,
            )
        }

        if (target == null) {
            accountDao.insert(entity)
        } else {
            accountDao.update(entity)
        }
    }

    suspend fun addAccount(draft: AccountDraft) {
        val sanitizedDraft = sanitizeAccountDraft(draft)
        val existingAccounts = accountDao.getAccounts()
        val existing = when (sanitizedDraft.kind) {
            AccountKind.CARD -> findConfiguredCardAccount(
                accounts = existingAccounts,
                lastFourDigits = sanitizedDraft.lastFourDigits,
                institutionName = sanitizedDraft.institutionName,
                cardType = sanitizedDraft.cardType,
            )

            else -> null
        }

        if (existing != null) {
            accountDao.update(
                existing.copy(
                    name = sanitizedDraft.name,
                    institutionName = sanitizedDraft.institutionName,
                    cardType = sanitizedDraft.cardType,
                    lastFourDigits = sanitizedDraft.lastFourDigits,
                    isRupayCreditCard = sanitizedDraft.isRupayCreditCard,
                    isSystemGenerated = false,
                ),
            )
            return
        }

        accountDao.insert(
            AccountEntity(
                name = sanitizedDraft.name,
                kind = sanitizedDraft.kind,
                institutionName = sanitizedDraft.institutionName,
                cardType = sanitizedDraft.cardType,
                lastFourDigits = sanitizedDraft.lastFourDigits,
                isRupayCreditCard = sanitizedDraft.isRupayCreditCard,
                isSystemGenerated = false,
            ),
        )
    }

    suspend fun updateAccount(
        accountId: Long,
        draft: AccountDraft,
    ) {
        val existing = accountDao.findById(accountId) ?: error("Account $accountId not found")
        val sanitizedDraft = sanitizeAccountDraft(draft.copy(kind = existing.kind))
        accountDao.update(
            existing.copy(
                name = sanitizedDraft.name,
                institutionName = sanitizedDraft.institutionName,
                cardType = sanitizedDraft.cardType,
                lastFourDigits = sanitizedDraft.lastFourDigits,
                isRupayCreditCard = sanitizedDraft.isRupayCreditCard,
                isSystemGenerated = false,
            ),
        )
    }

    suspend fun deleteAccount(accountId: Long) {
        accountDao.findById(accountId) ?: return
        accountDao.deleteById(accountId)
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
        val monthSpent = spendTransactions.sumOf(TransactionRecord::amount)
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

    private fun defaultAccountName(
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

        val accountId = resolveParsedAccount(
            accountLabel = parsed.accountLabel,
            accountKind = parsed.accountKind,
            institutionName = parsed.institutionName,
            bankAccountLastFourDigits = parsed.bankAccountLastFourDigits,
            cardLastFourDigits = parsed.cardLastFourDigits,
            cardType = parsed.cardType,
            isCardBillPayment = parsed.isCardBillPayment,
        )
        val status = if (parsed.confidence >= 0.7) TransactionStatus.POSTED else TransactionStatus.REVIEW
        val inserted = transactionDao.insert(
            TransactionEntity(
                amount = parsed.amount,
                direction = parsed.direction,
                occurredAtMillis = parsed.occurredAtMillis ?: receivedAtMillis,
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

    private suspend fun resolveParsedAccount(
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

        if (isCardBillPayment) {
            findConfiguredBankAccount(
                accounts = existingAccounts,
                lastFourDigits = normalizedBankLastFour,
                institutionName = normalizedInstitution,
            )?.let { return it.id }
            findPrimaryBankAccount(existingAccounts, normalizedInstitution)?.let { return it.id }
        }

        if (accountKind == AccountKind.BANK) {
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

        return when (accountKind) {
            AccountKind.BANK -> {
                val bankAccount = findConfiguredBankAccount(
                    accounts = existingAccounts,
                    lastFourDigits = normalizedBankLastFour,
                    institutionName = normalizedInstitution,
                ) ?: findPrimaryBankAccount(existingAccounts, normalizedInstitution)
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
                name = accountLabel ?: defaultAccountName(accountKind),
                kind = accountKind,
            )
        }
    }

    private suspend fun resolveAccount(
        name: String,
        kind: AccountKind,
        institutionName: String? = null,
        cardType: CardType? = null,
        lastFourDigits: String? = null,
    ): Long {
        val normalizedInstitution = normalizeInstitutionName(institutionName)
        val normalizedLastFour = normalizeLastFourDigits(lastFourDigits)
        val existingAccounts = accountDao.getAccounts()
        val existing = when (kind) {
            AccountKind.BANK -> findPrimaryBankAccount(existingAccounts, normalizedInstitution)
                ?.takeIf { normalizedInstitution != null }
                ?: existingAccounts.firstOrNull { account ->
                    account.kind == AccountKind.BANK &&
                        account.name.equals(name, ignoreCase = true)
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

    private fun findPrimaryBankAccount(
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

    private fun findConfiguredBankAccount(
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

    private fun findConfiguredCardAccount(
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

    private fun sanitizeAccountDraft(draft: AccountDraft): AccountDraft {
        val normalizedInstitution = normalizeInstitutionName(draft.institutionName)
        val normalizedLastFour = normalizeLastFourDigits(draft.lastFourDigits)
        val normalizedCardType = if (draft.kind == AccountKind.CARD) draft.cardType else null
        if (draft.kind == AccountKind.CARD) {
            require(normalizedCardType != null) { "Card type is required for card accounts." }
            require(normalizedLastFour != null) { "Card last four digits are required for card accounts." }
        }
        val resolvedName = draft.name.trim().ifBlank {
            defaultAccountName(
                kind = draft.kind,
                institutionName = normalizedInstitution,
                cardType = normalizedCardType,
                lastFourDigits = normalizedLastFour,
            )
        }
        return draft.copy(
            name = resolvedName,
            institutionName = when (draft.kind) {
                AccountKind.BANK,
                AccountKind.CARD -> normalizedInstitution

                else -> null
            },
            cardType = normalizedCardType,
            lastFourDigits = when (draft.kind) {
                AccountKind.BANK,
                AccountKind.CARD -> normalizedLastFour

                else -> null
            },
            isRupayCreditCard = draft.kind == AccountKind.CARD &&
                normalizedCardType == CardType.CREDIT &&
                draft.isRupayCreditCard,
        )
    }

    private fun normalizeInstitutionName(value: String?): String? {
        val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            "axis" in normalized -> "Axis Bank"
            normalized.contains("state bank") || normalized == "sbi" -> "State Bank of India"
            "hdfc" in normalized -> "HDFC Bank"
            "icici" in normalized -> "ICICI Bank"
            "kotak" in normalized -> "Kotak Bank"
            else -> value.trim()
        }
    }

    private fun normalizeLastFourDigits(value: String?): String? {
        val digits = value?.filter(Char::isDigit)?.takeLast(4).orEmpty()
        return digits.takeIf { it.length == 4 }
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
