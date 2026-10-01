package com.moneytracker.app.data.repo

import android.content.ContentResolver
import com.moneytracker.app.data.db.AccountDao
import com.moneytracker.app.data.db.AccountEntity
import com.moneytracker.app.data.db.BudgetDao
import com.moneytracker.app.data.db.BudgetEntity
import com.moneytracker.app.data.db.ScheduledTransactionDao
import com.moneytracker.app.data.db.ScheduledTransactionEntity
import com.moneytracker.app.data.db.ScheduledTransactionRecord
import com.moneytracker.app.data.db.SubscriptionDao
import com.moneytracker.app.data.db.SubscriptionEntity
import com.moneytracker.app.data.db.SubscriptionRecord
import com.moneytracker.app.data.db.TransactionDao
import com.moneytracker.app.data.db.TransactionEntity
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.db.cleanNote
import com.moneytracker.app.data.db.parseEngine
import com.moneytracker.app.data.local.SetupPreferences
import com.moneytracker.app.data.model.AccountDraft
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.CardType
import com.moneytracker.app.data.model.CategoryBudgetProgress
import com.moneytracker.app.data.model.CategorySlice
import com.moneytracker.app.data.model.DashboardState
import com.moneytracker.app.data.model.ImportReport
import com.moneytracker.app.data.model.MonthBudgetSummary
import com.moneytracker.app.data.model.ParsedScheduledTransaction
import com.moneytracker.app.data.model.ScheduledTransactionKind
import com.moneytracker.app.data.model.SubscriptionDraft
import com.moneytracker.app.data.model.SubscriptionState
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionDraft
import com.moneytracker.app.data.model.TransactionStatus
import com.moneytracker.app.data.model.TrendPoint
import com.moneytracker.app.bank.BalanceProofVerifier
import com.moneytracker.app.bank.BankDetector
import com.moneytracker.app.backup.BackupManager
import com.moneytracker.app.backup.BackupRestoreResult
import com.moneytracker.app.parser.PromotionalDetector
import com.moneytracker.app.parser.SmsParser
import com.moneytracker.app.sms.SmsImportManager
import java.security.MessageDigest
import java.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import com.moneytracker.app.data.db.CategorySpendAggregate
import com.moneytracker.app.ai.AiParsedTransaction
import com.moneytracker.app.ai.FinanceRagEngine
import com.moneytracker.app.ai.GeminiApiClient
import com.moneytracker.app.ai.OnDeviceAiEngine
import com.moneytracker.app.ai.RagAnswerResponse
import com.moneytracker.app.ai.SpendingAssistantGuardrail
import com.moneytracker.app.data.db.TransactionEmbeddingDao
import com.moneytracker.app.data.db.TransactionEmbeddingEntity
import com.moneytracker.app.data.local.AiEngineMode
import com.moneytracker.app.data.local.AiPreferences
import com.moneytracker.app.data.local.EmailPreferences
import com.moneytracker.app.data.local.ExclusionPreferences
import com.moneytracker.app.data.local.HapticPreferences
import com.moneytracker.app.data.local.NotificationPreferences
import com.moneytracker.app.data.local.SecurityPreferences
import com.moneytracker.app.data.local.ThemePreferences
import com.moneytracker.app.email.EmailSyncManager
import com.moneytracker.app.data.model.AssistantMessage
import com.moneytracker.app.data.model.AssistantSender
import com.moneytracker.app.ui.greeting.TimeOfDayGreetingProvider
import kotlinx.coroutines.flow.asStateFlow

import com.moneytracker.app.domain.usecase.DetectTransferPairsUseCase
import com.moneytracker.app.domain.usecase.GenerateSpendingInsightsUseCase
import com.moneytracker.app.domain.usecase.IngestTransactionUseCase
import com.moneytracker.app.domain.usecase.ReconcileLedgerUseCase
import com.moneytracker.app.domain.usecase.SyncGmailAlertsUseCase
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import java.time.LocalTime

class FinanceRepository(
    private val accountDao: AccountDao,
    private val budgetDao: BudgetDao,
    private val scheduledTransactionDao: ScheduledTransactionDao,
    private val subscriptionDao: SubscriptionDao,
    val transactionDao: TransactionDao,
    private val embeddingDao: TransactionEmbeddingDao,
    private val ragEngine: FinanceRagEngine,
    private val parser: SmsParser,
    private val setupPreferences: SetupPreferences,
    val aiPreferences: AiPreferences,
    val hapticPreferences: HapticPreferences,
    val securityPreferences: SecurityPreferences,
    val emailPreferences: EmailPreferences,
    val exclusionPreferences: ExclusionPreferences,
    val notificationPreferences: NotificationPreferences,
    val themePreferences: ThemePreferences,
    val emailSyncManager: EmailSyncManager,
    val geminiApiClient: GeminiApiClient,
    val onDeviceAiEngine: OnDeviceAiEngine,
    private val context: android.content.Context? = null,
    val categoryPreferences: com.moneytracker.app.data.local.CategoryPreferences? = null,
    val userPreferences: com.moneytracker.app.data.local.UserPreferences? = null,
    val reconcileLedgerUseCase: ReconcileLedgerUseCase,
    val detectTransferPairsUseCase: DetectTransferPairsUseCase = DetectTransferPairsUseCase(
        transactionDao = transactionDao,
        accountDao = accountDao,
    ),
    val ingestTransactionUseCase: IngestTransactionUseCase = IngestTransactionUseCase(
        transactionDao = transactionDao,
        accountDao = accountDao,
        scheduledTransactionDao = scheduledTransactionDao,
        parser = parser,
        exclusionPreferences = exclusionPreferences,
        notificationPreferences = notificationPreferences,
        aiPreferences = aiPreferences,
        onDeviceAiEngine = onDeviceAiEngine,
        geminiApiClient = geminiApiClient,
        reconcileLedgerUseCase = reconcileLedgerUseCase,
        detectTransferPairsUseCase = detectTransferPairsUseCase,
    ),
    val generateSpendingInsightsUseCase: GenerateSpendingInsightsUseCase = GenerateSpendingInsightsUseCase(
        transactionDao = transactionDao,
        budgetDao = budgetDao,
        scheduledTransactionDao = scheduledTransactionDao,
        aiPreferences = aiPreferences,
        onDeviceAiEngine = onDeviceAiEngine,
        geminiApiClient = geminiApiClient,
    ),
    val syncGmailAlertsUseCase: SyncGmailAlertsUseCase = SyncGmailAlertsUseCase(
        emailPreferences = emailPreferences,
        emailSyncManager = emailSyncManager,
        ingestTransactionUseCase = ingestTransactionUseCase,
        reconcileLedgerUseCase = reconcileLedgerUseCase,
    ),
) {

    private fun notifyWidgetUpdate() {
        context?.let {
            com.moneytracker.app.widget.SafeSpendGlanceWidget.updateAllWidgets(it)
            com.moneytracker.app.widget.BalanceWidgetProvider.updateAllWidgets(it)
            com.moneytracker.app.widget.BudgetWidgetProvider.updateAllWidgets(it)
        }
    }

    val userName: StateFlow<String> = userPreferences?.userName ?: MutableStateFlow("")
    val hasPromptedForName: StateFlow<Boolean> = userPreferences?.hasPromptedForName ?: MutableStateFlow(true)

    fun setUserName(name: String) {
        userPreferences?.setUserName(name)
    }

    fun markPromptedForName() {
        userPreferences?.markPromptedForName()
    }

    val accounts: Flow<List<AccountEntity>> = accountDao.observeAccounts()
    val isInitialSetupComplete: Flow<Boolean> = setupPreferences.isInitialSetupComplete
    val scheduledTransactions: Flow<List<ScheduledTransactionRecord>> = scheduledTransactionDao.observeScheduledTransactions()
    val transactions: Flow<List<TransactionRecord>> = transactionDao.observeTransactions()
    val pagedTransactions: Flow<PagingData<TransactionRecord>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            prefetchDistance = 10,
            enablePlaceholders = false,
        ),
    ) {
        transactionDao.pagedTransactions()
    }.flow

    fun pagedFilteredTransactions(
        accountId: Long? = null,
        direction: String? = null,
        searchQuery: String = "",
    ): Flow<PagingData<TransactionRecord>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            prefetchDistance = 10,
            enablePlaceholders = false,
        ),
    ) {
        transactionDao.pagedFilteredTransactions(
            accountId = accountId,
            direction = direction,
            searchQuery = searchQuery,
        )
    }.flow
    val postedTransactions: Flow<List<TransactionRecord>> = transactionDao.observePostedTransactions()
    val subscriptions: Flow<List<SubscriptionRecord>> = subscriptionDao.observeSubscriptions()
    val currentBudget: Flow<BudgetEntity?> = budgetDao.observeOverallBudget(currentMonthKey())
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.observeOverallBudgets()

    private val _spendingInsights = MutableStateFlow<List<String>>(emptyList())
    val spendingInsights: StateFlow<List<String>> = _spendingInsights
    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading

    private val _selectedYearMonth = MutableStateFlow<YearMonth>(YearMonth.now())
    val selectedYearMonth: StateFlow<YearMonth> = _selectedYearMonth.asStateFlow()

    fun setSelectedYearMonth(yearMonth: YearMonth) {
        _selectedYearMonth.value = yearMonth
    }

    val budgetHistory: Flow<List<MonthBudgetSummary>> = combine(
        postedTransactions,
        allBudgets,
    ) { posted, budgets ->
        buildBudgetHistory(posted, budgets)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboard: Flow<DashboardState> = _selectedYearMonth.flatMapLatest { targetMonth ->
        val startLocalDate = targetMonth.atDay(1)
        val endLocalDate = targetMonth.atEndOfMonth()
        val startMillis = startLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endLocalDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val monthKey = targetMonth.toString()

        combine(
            transactionDao.observeMonthlySpent(startMillis, endMillis),
            transactionDao.observeMonthlyIncome(startMillis, endMillis),
            transactionDao.observeCategorySpendBreakdown(startMillis, endMillis),
            transactionDao.observeRecentPostedTransactions(6),
            transactionDao.observeReviewCount(),
            transactionDao.observeCardSpend(startMillis, endMillis),
            budgetDao.observeOverallBudget(monthKey),
            subscriptionDao.observeSubscriptions(),
            accountDao.observeAccounts(),
            _spendingInsights,
            _isAiLoading,
        ) { args: Array<Any?> ->
            val monthSpent = args[0] as Double
            val monthIncome = args[1] as Double
            @Suppress("UNCHECKED_CAST")
            val categoryAggregates = args[2] as List<CategorySpendAggregate>
            @Suppress("UNCHECKED_CAST")
            val recentTxs = args[3] as List<TransactionRecord>
            val reviewCount = args[4] as Int
            val cardSpend = args[5] as Double
            val budgetEntity = args[6] as BudgetEntity?
            @Suppress("UNCHECKED_CAST")
            val subs = args[7] as List<SubscriptionRecord>
            @Suppress("UNCHECKED_CAST")
            val accountsList = args[8] as List<AccountEntity>
            @Suppress("UNCHECKED_CAST")
            val insights = args[9] as List<String>
            val isLoading = args[10] as Boolean

            buildDashboardStateFromAggregates(
                monthSpent = monthSpent,
                monthIncome = monthIncome,
                categoryAggregates = categoryAggregates,
                recentTransactions = recentTxs,
                reviewCount = reviewCount,
                cardSpend = cardSpend,
                budget = budgetEntity?.amountLimit,
                subscriptions = subs,
                accountsList = accountsList,
                insights = insights,
                isAiLoading = isLoading,
                targetMonth = targetMonth,
            )
        }
    }

    suspend fun bootstrap() {
        purgeBogusRecoveredTransactions()
        reconcileAccountsAndBalances()
        refreshRecurringSuggestions()
        deduplicateTransactions(windowDays = 14)
    }

    suspend fun purgeBogusRecoveredTransactions(): Int {
        val reviewTransactions = transactionDao.getAllTransactions().filter {
            it.status == TransactionStatus.REVIEW || it.note?.contains("Recovered Transaction") == true
        }
        var purged = 0
        for (tx in reviewTransactions) {
            val body = tx.smsBody.orEmpty()
            val sender = tx.sourceSender.orEmpty()
            val merchant = tx.merchant.trim()
            val isPromo = PromotionalDetector.isPromotional(sender, body, merchant)
            val isCurrencyMerchant = merchant.matches(Regex("""^(?:rs\.?|inr|re\.?|₹|\$)\s*[0-9,]+.*""", RegexOption.IGNORE_CASE)) ||
                merchant.matches(Regex("""^[0-9,.\s]+$"""))
            val isMarketingBody = listOf("loan", "pre-approved", "credit limit", "limit enhanced", "limit increase", "avail now", "apply now")
                .any { body.contains(it, ignoreCase = true) } && !PromotionalDetector.hasConfirmedTransactionSignal(body)

            if (isPromo || isCurrencyMerchant || isMarketingBody) {
                transactionDao.deleteById(tx.id)
                purged++
            }
        }
        return purged
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
        val target = ingestTransactionUseCase.findPrimaryBankAccount(existingAccounts, normalizedInstitution)
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
            AccountKind.CARD -> ingestTransactionUseCase.findConfiguredCardAccount(
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
                currentBalance = sanitizedDraft.currentBalance,
                balanceUpdatedAtMillis = System.currentTimeMillis(),
                balanceProofSnippet = sanitizedDraft.balanceProofSnippet ?: if (sanitizedDraft.currentBalance != 0.0) "Manually entered by user" else null,
                balanceProofSource = sanitizedDraft.balanceProofSource ?: if (sanitizedDraft.currentBalance != 0.0) "User Entry" else null,
                isBalanceVerified = sanitizedDraft.isBalanceVerified,
            ),
        )
    }

    suspend fun updateAccount(
        accountId: Long,
        draft: AccountDraft,
    ) {
        val existing = accountDao.findById(accountId) ?: error("Account $accountId not found")
        val sanitizedDraft = sanitizeAccountDraft(draft.copy(kind = existing.kind))
        val isBalanceChanged = sanitizedDraft.currentBalance != existing.currentBalance

        accountDao.update(
            existing.copy(
                name = sanitizedDraft.name,
                institutionName = sanitizedDraft.institutionName,
                cardType = sanitizedDraft.cardType,
                lastFourDigits = sanitizedDraft.lastFourDigits,
                isRupayCreditCard = sanitizedDraft.isRupayCreditCard,
                isSystemGenerated = false,
                currentBalance = sanitizedDraft.currentBalance,
                balanceUpdatedAtMillis = if (isBalanceChanged) System.currentTimeMillis() else existing.balanceUpdatedAtMillis,
                balanceProofSnippet = if (isBalanceChanged) "Manually updated by user" else existing.balanceProofSnippet,
                balanceProofSource = if (isBalanceChanged) "User Entry" else existing.balanceProofSource,
                isBalanceVerified = if (isBalanceChanged) false else existing.isBalanceVerified,
            ),
        )
    }

    suspend fun deleteAccount(accountId: Long) {
        accountDao.findById(accountId) ?: return
        accountDao.deleteById(accountId)
    }

    suspend fun exportEncryptedBackup(passphrase: String): ByteArray {
        return BackupManager.createEncryptedBackup(
            accountDao = accountDao,
            transactionDao = transactionDao,
            budgetDao = budgetDao,
            subscriptionDao = subscriptionDao,
            passphrase = passphrase,
        )
    }

    suspend fun restoreEncryptedBackup(backupBytes: ByteArray, passphrase: String): BackupRestoreResult {
        val result = BackupManager.restoreEncryptedBackup(
            backupBytes = backupBytes,
            passphrase = passphrase,
            accountDao = accountDao,
            transactionDao = transactionDao,
            budgetDao = budgetDao,
            subscriptionDao = subscriptionDao,
        )
        if (result is BackupRestoreResult.Success) {
            reconcileAccountsAndBalances()
        }
        return result
    }

    suspend fun setMonthlyBudget(amount: Double, monthKey: String = _selectedYearMonth.value.toString()) {
        budgetDao.deleteOverallBudget(monthKey)
        budgetDao.insert(
            BudgetEntity(
                monthKey = monthKey,
                category = null,
                amountLimit = amount,
            ),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCategoryBudgetProgress(yearMonth: YearMonth = _selectedYearMonth.value): Flow<List<CategoryBudgetProgress>> {
        val startLocalDate = yearMonth.atDay(1)
        val endLocalDate = yearMonth.atEndOfMonth()
        val startMillis = startLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endLocalDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val monthKey = yearMonth.toString()

        return combine(
            budgetDao.observeCategoryBudgets(monthKey),
            transactionDao.observeCategorySpendBreakdown(startMillis, endMillis),
        ) { catBudgets, spendAggs ->
            val spendMap = spendAggs.associate { it.category.uppercase() to it.totalAmount }
            catBudgets.mapNotNull { budgetEntity ->
                val cat = budgetEntity.category ?: return@mapNotNull null
                val spent = spendMap[cat.name.uppercase()] ?: 0.0
                val limit = budgetEntity.amountLimit
                val remaining = (limit - spent).coerceAtLeast(0.0)
                val percent = if (limit > 0.0) (spent / limit).toFloat() else 0f
                CategoryBudgetProgress(
                    category = cat,
                    budgetLimit = limit,
                    currentSpent = spent,
                    remainingAmount = remaining,
                    progressPercent = percent,
                )
            }.sortedByDescending { it.currentSpent }
        }
    }

    suspend fun setCategoryBudget(
        yearMonth: YearMonth = _selectedYearMonth.value,
        category: TransactionCategory,
        limit: Double,
    ) {
        val entity = BudgetEntity(
            monthKey = yearMonth.toString(),
            category = category,
            amountLimit = limit,
        )
        budgetDao.insert(entity)
    }

    suspend fun removeCategoryBudget(
        yearMonth: YearMonth = _selectedYearMonth.value,
        category: TransactionCategory,
    ) {
        budgetDao.deleteCategoryBudget(yearMonth.toString(), category)
    }

    suspend fun addManualTransaction(draft: TransactionDraft) {
        val fingerprint = ingestTransactionUseCase.hashFingerprint(
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
            countsTowardBudget = draft.countsTowardBudget,
        )
        val insertedId = transactionDao.insert(transaction)
        val accId = draft.accountId
        if (insertedId != -1L && accId != null) {
            val delta = if (draft.direction == TransactionDirection.CREDIT) draft.amount else -draft.amount
            accountDao.adjustBalance(accId, delta)
        }
    }

    suspend fun updateTransaction(transactionId: Long, draft: TransactionDraft) {
        val existing = transactionDao.getById(transactionId)
            ?: error("Transaction $transactionId not found")
        val oldAccountId = existing.accountId
        transactionDao.update(
            existing.copy(
                amount = draft.amount,
                direction = draft.direction,
                occurredAtMillis = draft.occurredAtMillis,
                merchant = draft.merchant.trim(),
                category = draft.category,
                accountId = draft.accountId,
                confidence = 1.0,
                status = TransactionStatus.POSTED,
                note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
                countsTowardBudget = draft.countsTowardBudget,
            ),
        )
        oldAccountId?.let { reconcileSingleAccount(it) }
        draft.accountId?.takeIf { it != oldAccountId }?.let { reconcileSingleAccount(it) }
    }

    suspend fun addSubscription(draft: SubscriptionDraft) {
        val merchantName = draft.merchant.trim()
        subscriptionDao.insert(
            SubscriptionEntity(
                merchant = merchantName,
                amount = draft.amount,
                billingCycleDays = draft.billingCycleDays,
                nextDueAtMillis = draft.nextDueAtMillis,
                accountId = draft.accountId,
                state = SubscriptionState.ACTIVE,
            ),
        )
        val fingerprint = ingestTransactionUseCase.hashFingerprint(
            sender = "SUBSCRIPTION",
            body = listOf(
                merchantName,
                draft.amount,
                draft.nextDueAtMillis,
                draft.accountId ?: "noaccount",
            ).joinToString("|"),
            receivedAtMillis = draft.nextDueAtMillis,
        )
        transactionDao.insert(
            TransactionEntity(
                amount = draft.amount,
                direction = TransactionDirection.DEBIT,
                occurredAtMillis = draft.nextDueAtMillis,
                merchant = merchantName,
                category = TransactionCategory.SUBSCRIPTION,
                accountId = draft.accountId,
                sourceSender = "SUBSCRIPTION",
                smsBody = null,
                confidence = 1.0,
                fingerprint = fingerprint,
                status = TransactionStatus.POSTED,
            ),
        )
        draft.accountId?.let { reconcileSingleAccount(it) }
    }

    suspend fun promoteSubscription(subscriptionId: Long) {
        val subscription = subscriptionDao.getAll().firstOrNull { it.id == subscriptionId } ?: return
        subscriptionDao.update(subscription.copy(state = SubscriptionState.ACTIVE))
    }

    suspend fun dismissSubscription(subscriptionId: Long) {
        subscriptionDao.deleteById(subscriptionId)
    }

    suspend fun approveReview(transactionId: Long) {
        val existing = transactionDao.getById(transactionId)
        transactionDao.updateStatus(transactionId, TransactionStatus.POSTED.name)
        existing?.accountId?.let { reconcileSingleAccount(it) }
    }

    suspend fun deleteTransaction(transactionId: Long) {
        val existing = transactionDao.getById(transactionId)
        val targetAccountId = existing?.accountId
        transactionDao.deleteById(transactionId)
        targetAccountId?.let { reconcileSingleAccount(it) }
    }

    suspend fun transferToCashWallet(transactionId: Long): Result<Unit> = runCatching {
        val existing = transactionDao.getById(transactionId)
            ?: error("Transaction $transactionId not found")

        val accounts = accountDao.getAccounts()
        val cashAccount = accounts.firstOrNull { it.kind == AccountKind.CASH }
            ?: run {
                val newCashId = accountDao.insert(
                    AccountEntity(
                        name = "Cash in Hand",
                        kind = AccountKind.CASH,
                        currentBalance = 0.0,
                        balanceUpdatedAtMillis = System.currentTimeMillis(),
                    ),
                )
                accountDao.findById(newCashId) ?: error("Failed to create Cash in Hand wallet")
            }

        val updatedNote = listOfNotNull(
            existing.note?.takeIf { !it.contains("Cash in Hand", ignoreCase = true) },
            "Transferred to Cash in Hand",
        ).joinToString(" • ")

        val updatedExisting = existing.copy(
            category = TransactionCategory.TRANSFER,
            countsTowardBudget = false,
            note = updatedNote,
        )
        transactionDao.update(updatedExisting)

        val sourceAccount = existing.accountId?.let { accountDao.findById(it) }
        val creditTx = TransactionEntity(
            amount = existing.amount,
            direction = TransactionDirection.CREDIT,
            occurredAtMillis = existing.occurredAtMillis,
            merchant = "Cash in Hand",
            category = TransactionCategory.TRANSFER,
            accountId = cashAccount.id,
            sourceSender = "INTERNAL_TRANSFER",
            smsBody = null,
            confidence = 1.0,
            fingerprint = "cash_transfer_${existing.id}_${existing.occurredAtMillis}",
            status = TransactionStatus.POSTED,
            note = "ATM cash withdrawal from ${sourceAccount?.name ?: "Bank"}",
            countsTowardBudget = false,
        )
        transactionDao.insert(creditTx)

        accountDao.adjustBalance(cashAccount.id, existing.amount)
        notifyWidgetUpdate()
    }

    suspend fun setTransactionBudgetInclusion(transactionId: Long, countsTowardBudget: Boolean) {
        transactionDao.updateBudgetInclusion(transactionId, countsTowardBudget)
    }

    suspend fun enrichTransactionWithAi(transactionId: Long): Result<TransactionEntity> {
        val existing = transactionDao.getById(transactionId)
            ?: return Result.failure(IllegalArgumentException("Transaction not found"))
        val body = existing.smsBody
            ?: return Result.failure(IllegalArgumentException("No SMS content available for this transaction"))
        val apiKey = aiPreferences.apiKey.value
        val engineMode = aiPreferences.engineMode.value

        val parseResult = onDeviceAiEngine.parseIncomingMessage(
            body = body,
            sender = existing.sourceSender,
            apiKey = if (engineMode != AiEngineMode.ON_DEVICE_ONLY) apiKey else "",
            cloudClient = geminiApiClient,
            parser = parser,
        )
        val parsed = parseResult.transaction
            ?: return Result.failure(IllegalStateException("AI could not extract transaction details"))

        val updatedMerchant = parsed.merchant?.takeIf { it.isNotBlank() } ?: existing.merchant
        val updatedCategory = parsed.category
        val baseNote = parsed.detailedDescription ?: parsed.placeDetail ?: existing.cleanNote ?: existing.note
        val updatedNote = if (baseNote?.contains("[Engine:") == true) baseNote else "$baseNote [Engine: ${parseResult.engine}]"

        val updatedEntity = existing.copy(
            merchant = updatedMerchant,
            category = updatedCategory,
            note = updatedNote,
            confidence = 0.98,
            status = TransactionStatus.POSTED,
        )
        transactionDao.update(updatedEntity)
        val record = transactionDao.getById(transactionId)
            ?: return Result.failure(IllegalStateException("Failed to load updated transaction"))
        return Result.success(record)
    }

    suspend fun refreshAiSpendingInsights(): Result<List<String>> {
        _isAiLoading.value = true
        try {
            val insightsResult = generateSpendingInsightsUseCase.generateSpendingInsights()
            insightsResult.onSuccess {
                _spendingInsights.value = it
            }
            return insightsResult
        } finally {
            _isAiLoading.value = false
        }
    }


    suspend fun testAiConnection(apiKey: String, model: String): Result<String> {
        return geminiApiClient.testConnection(apiKey = apiKey, model = model)
    }

    suspend fun askSpendingAssistant(userQuery: String): Result<AssistantMessage> {
        val trimmedQuery = userQuery.trim()
        if (trimmedQuery.isBlank()) {
            return Result.failure(IllegalArgumentException("Query cannot be blank"))
        }

        // 1. Guardrail: Friendly greeting check
        if (SpendingAssistantGuardrail.isGreeting(trimmedQuery)) {
            return Result.success(
                AssistantMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = SpendingAssistantGuardrail.getGreetingResponse(),
                    citedTransactions = emptyList(),
                ),
            )
        }

        val allPosted = postedTransactions.first()
        val allAccounts = accounts.first()
        val knownMerchants = allPosted.map { it.merchant }.toSet()
        val knownAccounts = allAccounts.map { it.name }.toSet()

        // 2. Guardrail: Off-topic detection returning a cute response
        if (SpendingAssistantGuardrail.isOffTopic(trimmedQuery, knownMerchants, knownAccounts)) {
            return Result.success(
                AssistantMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = SpendingAssistantGuardrail.getCuteOffTopicResponse(trimmedQuery),
                    citedTransactions = emptyList(),
                ),
            )
        }

        val apiKey = aiPreferences.apiKey.value
        val engineMode = aiPreferences.engineMode.value
        val currentBudgetLimit = currentBudget.first()?.amountLimit

        val context = ragEngine.retrieveContext(
            query = trimmedQuery,
            apiKey = apiKey,
            currentBudgetLimit = currentBudgetLimit,
            allPosted = allPosted,
        )

        val ragResult: RagAnswerResponse = when (engineMode) {
            AiEngineMode.ON_DEVICE_ONLY -> {
                onDeviceAiEngine.queryAssistantOnDevice(
                    userQuery = userQuery,
                    retrievedTransactions = context.transactions,
                    macroContext = context.macroSummary,
                ).getOrElse { return Result.failure(it) }
            }
            AiEngineMode.AUTO_PIXEL_FIRST -> {
                if (apiKey.isNotBlank()) {
                    geminiApiClient.queryRagSpendingAssistant(
                        userQuery = userQuery,
                        retrievedTransactions = context.transactions,
                        macroContext = context.macroSummary,
                        apiKey = apiKey,
                        model = aiPreferences.selectedModel.value,
                    ).getOrElse {
                        // Fall back to On-Device Engine on Pixel 9 seamlessly
                        onDeviceAiEngine.queryAssistantOnDevice(
                            userQuery = userQuery,
                            retrievedTransactions = context.transactions,
                            macroContext = context.macroSummary,
                        ).getOrElse { fallbackError -> return Result.failure(fallbackError) }
                    }
                } else {
                    onDeviceAiEngine.queryAssistantOnDevice(
                        userQuery = userQuery,
                        retrievedTransactions = context.transactions,
                        macroContext = context.macroSummary,
                    ).getOrElse { return Result.failure(it) }
                }
            }
            AiEngineMode.CLOUD_ONLY -> {
                if (apiKey.isBlank()) {
                    return Result.failure(IllegalStateException("Configure your Google AI Studio API key in Settings for Cloud Mode."))
                }
                geminiApiClient.queryRagSpendingAssistant(
                    userQuery = userQuery,
                    retrievedTransactions = context.transactions,
                    macroContext = context.macroSummary,
                    apiKey = apiKey,
                    model = aiPreferences.selectedModel.value,
                ).getOrElse { return Result.failure(it) }
            }
        }

        val citedTxs = if (ragResult.citedTransactionIds.isNotEmpty()) {
            val citedSet = ragResult.citedTransactionIds.toSet()
            context.transactions.filter { it.id in citedSet }
        } else {
            context.transactions.take(3)
        }

        return Result.success(
            AssistantMessage(
                sender = AssistantSender.ASSISTANT,
                text = ragResult.answer,
                citedTransactions = citedTxs,
            ),
        )
    }

    suspend fun indexTransactionEmbedding(transactionId: Long, documentText: String) {
        val apiKey = aiPreferences.apiKey.value
        val engineMode = aiPreferences.engineMode.value

        if (apiKey.isBlank() || engineMode == AiEngineMode.ON_DEVICE_ONLY) {
            return
        }

        val embedding: List<Float>? = geminiApiClient.generateEmbedding(
            text = documentText,
            apiKey = apiKey,
            outputDimensionality = 256,
        ).getOrNull()

        if (embedding.isNullOrEmpty()) return

        val entity = TransactionEmbeddingEntity.fromFloatList(
            transactionId = transactionId,
            documentText = documentText,
            floats = embedding,
        )
        embeddingDao.insert(entity)
    }

    suspend fun indexUnembeddedTransactions(limit: Int = 15) {
        val apiKey = aiPreferences.apiKey.value
        val engineMode = aiPreferences.engineMode.value
        if (apiKey.isBlank() || engineMode == AiEngineMode.ON_DEVICE_ONLY) {
            return
        }

        val unembeddedIds = embeddingDao.getUnembeddedTransactionIds(limit)
        if (unembeddedIds.isEmpty()) return

        for (id in unembeddedIds) {
            val entity = transactionDao.getById(id) ?: continue
            val docText = "${entity.direction} ₹${entity.amount} at ${entity.merchant} (${entity.category.label}) on ${Instant.ofEpochMilli(entity.occurredAtMillis)}. Note: ${entity.note.orEmpty()}"
            indexTransactionEmbedding(id, docText)
        }
    }

    suspend fun processIncomingSms(
        sender: String,
        body: String,
        receivedAtMillis: Long,
    ): Boolean = ingestTransactionUseCase.processIncomingSms(sender, body, receivedAtMillis)

    suspend fun processIncomingNotification(
        packageName: String,
        title: String,
        text: String,
        subText: String,
        postTimeMillis: Long,
    ): Boolean = ingestTransactionUseCase.processIncomingNotification(
        packageName = packageName,
        title = title,
        text = text,
        subText = subText,
        postTimeMillis = postTimeMillis,
    )

    suspend fun importRecentSms(
        contentResolver: ContentResolver,
        limit: Int = 200,
    ): ImportReport = ingestTransactionUseCase.importRecentSms(contentResolver, limit)

    suspend fun syncRecentEmails(maxMessages: Int = 30): Result<Int> =
        syncGmailAlertsUseCase.syncRecentEmails(maxMessages)

    suspend fun testEmailCredentials(email: String, appPassword: String): Result<Boolean> =
        syncGmailAlertsUseCase.testEmailCredentials(email, appPassword)

    private fun normalizeMerchant(merchant: String): String {
        return merchant.lowercase().replace(Regex("[^a-z0-9]"), "")
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

    private fun isRepaymentOrTransfer(record: TransactionRecord): Boolean {
        if (record.category == TransactionCategory.TRANSFER) return true
        val text = "${record.merchant} ${record.smsBody.orEmpty()} ${record.note.orEmpty()}".lowercase(java.util.Locale.getDefault())
        return listOf(
            "credit card bill",
            "card bill payment",
            "payment received towards",
            "payment received for credit card",
            "received towards your credit card",
            "received towards your card",
            "credited towards credit card",
            "credited to your credit card",
            "credited to credit card",
            "credited to your card",
            "credited to card",
            "towards credit card",
            "towards your credit card",
            "towards your card",
            "paid towards your credit card",
            "paid towards credit card",
            "to self",
            "to own account",
            "from own account",
            "self transfer",
            "cred",
            "cheq",
            "billdesk",
        ).any { text.contains(it) }
    }

    private fun buildDashboardStateFromAggregates(
        monthSpent: Double,
        monthIncome: Double,
        categoryAggregates: List<CategorySpendAggregate>,
        recentTransactions: List<TransactionRecord>,
        reviewCount: Int,
        cardSpend: Double,
        budget: Double?,
        subscriptions: List<SubscriptionRecord>,
        accountsList: List<AccountEntity> = emptyList(),
        insights: List<String> = emptyList(),
        isAiLoading: Boolean = false,
        targetMonth: YearMonth = YearMonth.now(),
    ): DashboardState {
        val currentMonth = targetMonth
        val bankSpendThisMonth = (monthSpent - cardSpend).coerceAtLeast(0.0)
        val monthNetCashflow = monthIncome - monthSpent
        val now = YearMonth.now()
        val today = LocalDate.now()
        val daysInMonth = currentMonth.lengthOfMonth()
        val daysRemaining = when {
            currentMonth == now -> (daysInMonth - today.dayOfMonth + 1).coerceAtLeast(1)
            currentMonth < now -> 1
            else -> daysInMonth
        }
        val remainingBudget = if (budget != null) (budget - monthSpent).coerceAtLeast(0.0) else 0.0
        val safeDailySpend = if (budget != null && budget > 0.0 && currentMonth >= now) remainingBudget / daysRemaining else 0.0
        val budgetPercentUsed = if (budget != null && budget > 0.0) (monthSpent / budget).toFloat() else 0f

        val categoryBreakdown = categoryAggregates.map { agg ->
            CategorySlice(
                category = TransactionCategory.valueOf(agg.category),
                amount = agg.totalAmount,
            )
        }.sortedByDescending(CategorySlice::amount)

        val totalBankBalance = accountsList.filter { it.kind != AccountKind.CARD }.sumOf { it.currentBalance }

        val resolvedInsights = if (insights.isEmpty() && recentTransactions.isNotEmpty()) {
            onDeviceAiEngine.generateSpendingInsightsOnDevice(
                transactions = recentTransactions,
                budgetLimit = budget,
                monthSpent = monthSpent,
                monthIncome = monthIncome,
            )
        } else {
            insights
        }

        val baseDashboard = DashboardState(
            trackedBalance = if (totalBankBalance != 0.0) totalBankBalance else monthNetCashflow,
            monthSpent = monthSpent,
            monthIncome = monthIncome,
            monthNetCashflow = monthNetCashflow,
            cardSpendThisMonth = cardSpend,
            bankSpendThisMonth = bankSpendThisMonth,
            safeDailySpend = safeDailySpend,
            budgetPercentUsed = budgetPercentUsed,
            budgetLimit = budget,
            reviewCount = reviewCount,
            activeSubscriptionsCount = subscriptions.count { it.state == SubscriptionState.ACTIVE },
            categoryBreakdown = categoryBreakdown.toImmutableList(),
            trendPoints = persistentListOf(),
            recentTransactions = recentTransactions.toImmutableList(),
            spendingInsights = resolvedInsights,
            isAiLoading = isAiLoading,
            accounts = accountsList,
            selectedYearMonth = currentMonth,
        )

        val nudges = TimeOfDayGreetingProvider.getContextualNudges(
            dashboard = baseDashboard,
            userName = userPreferences?.userName?.value.orEmpty(),
            aiEngine = onDeviceAiEngine,
        )

        return baseDashboard.copy(contextualNudges = nudges)
    }

    private fun currentMonthKey(): String = YearMonth.now().toString()

    private fun buildBudgetHistory(
        postedTransactions: List<TransactionRecord>,
        budgets: List<BudgetEntity>,
    ): List<MonthBudgetSummary> {
        val groupedTransactions = postedTransactions.groupBy { record ->
            YearMonth.from(
                Instant.ofEpochMilli(record.occurredAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate(),
            )
        }
        val budgetByMonth = budgets.associateBy { it.monthKey }
        val months = (groupedTransactions.keys + budgetByMonth.keys.mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() })
            .toSortedSet(compareByDescending { it })

        val monthFormatter = java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy")
        return months.map { yearMonth ->
            val key = yearMonth.toString()
            val monthTransactions = groupedTransactions[yearMonth].orEmpty()
            val spent = monthTransactions
                .filter {
                    it.direction == TransactionDirection.DEBIT &&
                        it.category != TransactionCategory.TRANSFER &&
                        it.countsTowardBudget
                }
                .sumOf(TransactionRecord::amount)
            MonthBudgetSummary(
                yearMonthKey = key,
                monthLabel = yearMonth.format(monthFormatter),
                budgetLimit = budgetByMonth[key]?.amountLimit,
                spent = spent,
                transactions = monthTransactions.sortedByDescending(TransactionRecord::occurredAtMillis),
            )
        }
    }

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

    suspend fun markBillAsPaid(reminderId: Long) {
        scheduledTransactionDao.markAsPaid(reminderId, System.currentTimeMillis())
    }

    suspend fun confirmBillPayment(reminderId: Long, isPaid: Boolean) {
        scheduledTransactionDao.confirmPayment(reminderId, isPaid)
    }

    suspend fun deleteScheduledTransaction(id: Long) {
        scheduledTransactionDao.deleteById(id)
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
        val canonical = BankDetector.normalizeToCanonicalBank(value)
        if (canonical != null) return canonical
        return value?.trim()?.takeIf { it.isNotBlank() && BankDetector.isLegitimateBank(it) }
    }

    private fun normalizeLastFourDigits(value: String?): String? {
        val digits = value?.filter(Char::isDigit)?.takeLast(4).orEmpty()
        return digits.takeIf { it.length == 4 }
    }

    suspend fun deduplicateTransactions(windowDays: Int = 14): Int =
        ingestTransactionUseCase.deduplicateTransactions(windowDays)

    suspend fun trueUpAccountBalance(
        accountId: Long,
        newBalance: Double,
        reason: String? = null,
        effectiveTimestamp: Long = System.currentTimeMillis(),
    ): Boolean = reconcileLedgerUseCase.trueUpAccountBalance(accountId, newBalance, reason, effectiveTimestamp)

    suspend fun reconcileSingleAccount(accountId: Long): Boolean =
        reconcileLedgerUseCase.reconcileSingleAccount(accountId)

    suspend fun reconcileAccountsAndBalances(): Int =
        reconcileLedgerUseCase.reconcileAccountsAndBalances()

    suspend fun detectAndLinkTransferPair(targetTxId: Long): Boolean =
        detectTransferPairsUseCase.detectAndLinkTransferPair(targetTxId)

    suspend fun reconcileTransferPairs(): Int =
        detectTransferPairsUseCase.reconcileTransferPairs()

    suspend fun getDailyRecapData(referenceMillis: Long = System.currentTimeMillis()): com.moneytracker.app.data.model.DailyRecapData =
        generateSpendingInsightsUseCase.getDailyRecapData(referenceMillis)

    suspend fun getUpcomingBillReminders(minDueMillis: Long, maxDueMillis: Long): List<ScheduledTransactionEntity> =
        generateSpendingInsightsUseCase.getUpcomingBillReminders(minDueMillis, maxDueMillis)

    suspend fun getDistinctTransactionDaysCount(): Int =
        generateSpendingInsightsUseCase.getDistinctTransactionDaysCount()
}

