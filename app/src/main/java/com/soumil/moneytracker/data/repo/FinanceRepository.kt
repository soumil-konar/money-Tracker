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
import com.soumil.moneytracker.data.model.MonthBudgetSummary
import com.soumil.moneytracker.data.model.ParsedScheduledTransaction
import com.soumil.moneytracker.data.model.SubscriptionDraft
import com.soumil.moneytracker.data.model.SubscriptionState
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import com.soumil.moneytracker.data.model.TransactionDraft
import com.soumil.moneytracker.data.model.TransactionStatus
import com.soumil.moneytracker.data.model.TrendPoint
import com.soumil.moneytracker.bank.BalanceProofVerifier
import com.soumil.moneytracker.bank.BankDetector
import com.soumil.moneytracker.parser.SmsParser
import com.soumil.moneytracker.sms.SmsImportManager
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.soumil.moneytracker.ai.AiParsedTransaction
import com.soumil.moneytracker.ai.FinanceRagEngine
import com.soumil.moneytracker.ai.GeminiApiClient
import com.soumil.moneytracker.ai.OnDeviceAiEngine
import com.soumil.moneytracker.ai.RagAnswerResponse
import com.soumil.moneytracker.data.db.TransactionEmbeddingDao
import com.soumil.moneytracker.data.db.TransactionEmbeddingEntity
import com.soumil.moneytracker.data.local.AiEngineMode
import com.soumil.moneytracker.data.local.AiPreferences
import com.soumil.moneytracker.data.local.EmailPreferences
import com.soumil.moneytracker.data.local.ExclusionPreferences
import com.soumil.moneytracker.data.local.HapticPreferences
import com.soumil.moneytracker.data.local.NotificationPreferences
import com.soumil.moneytracker.data.local.SecurityPreferences
import com.soumil.moneytracker.data.local.ThemePreferences
import com.soumil.moneytracker.email.EmailSyncManager
import com.soumil.moneytracker.data.model.AssistantMessage
import com.soumil.moneytracker.data.model.AssistantSender

class FinanceRepository(
    private val accountDao: AccountDao,
    private val budgetDao: BudgetDao,
    private val scheduledTransactionDao: ScheduledTransactionDao,
    private val subscriptionDao: SubscriptionDao,
    private val transactionDao: TransactionDao,
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
) {

    val accounts: Flow<List<AccountEntity>> = accountDao.observeAccounts()
    val isInitialSetupComplete: Flow<Boolean> = setupPreferences.isInitialSetupComplete
    val scheduledTransactions: Flow<List<ScheduledTransactionRecord>> = scheduledTransactionDao.observeScheduledTransactions()
    val transactions: Flow<List<TransactionRecord>> = transactionDao.observeTransactions()
    val postedTransactions: Flow<List<TransactionRecord>> = transactionDao.observePostedTransactions()
    val subscriptions: Flow<List<SubscriptionRecord>> = subscriptionDao.observeSubscriptions()
    val currentBudget: Flow<BudgetEntity?> = budgetDao.observeOverallBudget(currentMonthKey())
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.observeOverallBudgets()

    private val _spendingInsights = MutableStateFlow<List<String>>(emptyList())
    val spendingInsights: StateFlow<List<String>> = _spendingInsights
    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading

    val budgetHistory: Flow<List<MonthBudgetSummary>> = combine(
        postedTransactions,
        allBudgets,
    ) { posted, budgets ->
        buildBudgetHistory(posted, budgets)
    }

    val dashboard: Flow<DashboardState> = combine(
        postedTransactions,
        transactions,
        currentBudget,
        subscriptions,
        accounts,
        _spendingInsights,
        _isAiLoading,
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val posted = args[0] as List<TransactionRecord>
        @Suppress("UNCHECKED_CAST")
        val all = args[1] as List<TransactionRecord>
        val budget = args[2] as? BudgetEntity
        @Suppress("UNCHECKED_CAST")
        val subs = args[3] as List<SubscriptionRecord>
        @Suppress("UNCHECKED_CAST")
        val accountsList = args[4] as List<AccountEntity>
        @Suppress("UNCHECKED_CAST")
        val insights = args[5] as List<String>
        val isLoading = args[6] as Boolean
        buildDashboardState(
            postedTransactions = posted,
            allTransactions = all,
            budget = budget?.amountLimit,
            subscriptions = subs,
            accountsList = accountsList,
            insights = insights,
            isAiLoading = isLoading,
        )
    }

    suspend fun bootstrap() {
        reconcileAccountsAndBalances()
        refreshRecurringSuggestions()
        deduplicateTransactions()
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
            countsTowardBudget = draft.countsTowardBudget,
        )
        val insertedId = transactionDao.insert(transaction)
        if (insertedId != -1L && draft.accountId != null) {
            val delta = if (draft.direction == TransactionDirection.CREDIT) draft.amount else -draft.amount
            accountDao.adjustBalance(draft.accountId, delta)
        }
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
                countsTowardBudget = draft.countsTowardBudget,
            ),
        )
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
        val fingerprint = hashFingerprint(
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

    suspend fun setTransactionBudgetInclusion(transactionId: Long, countsTowardBudget: Boolean) {
        transactionDao.updateBudgetInclusion(transactionId, countsTowardBudget)
    }

    suspend fun enrichTransactionWithAi(transactionId: Long): Result<TransactionRecord> {
        val existing = transactionDao.getById(transactionId)
            ?: return Result.failure(IllegalArgumentException("Transaction not found"))
        val body = existing.smsBody
            ?: return Result.failure(IllegalArgumentException("No SMS content available for this transaction"))
        val apiKey = aiPreferences.apiKey.value
        val engineMode = aiPreferences.engineMode.value

        val (updatedMerchant, updatedCategory, updatedNote) = when (engineMode) {
            AiEngineMode.ON_DEVICE_ONLY -> {
                val parsed = onDeviceAiEngine.parseSmsOnDevice(body, existing.sourceSender)
                    .getOrElse { return Result.failure(it) }
                Triple(
                    parsed.merchant?.takeIf { it.isNotBlank() } ?: existing.merchant,
                    parsed.category,
                    parsed.detailedDescription ?: parsed.placeDetail ?: existing.note,
                )
            }
            AiEngineMode.AUTO_PIXEL_FIRST -> {
                if (apiKey.isNotBlank()) {
                    val parsed = geminiApiClient.parseSms(
                        smsBody = body,
                        sender = existing.sourceSender,
                        apiKey = apiKey,
                        model = aiPreferences.selectedModel.value,
                    ).getOrElse {
                        // Fall back to On-Device Engine on Tensor G4 TPU
                        onDeviceAiEngine.parseSmsOnDevice(body, existing.sourceSender)
                            .getOrElse { return Result.failure(it) }
                    }
                    Triple(
                        parsed.merchant?.takeIf { it.isNotBlank() } ?: existing.merchant,
                        parsed.category,
                        parsed.detailedDescription ?: parsed.placeDetail ?: existing.note,
                    )
                } else {
                    val parsed = onDeviceAiEngine.parseSmsOnDevice(body, existing.sourceSender)
                        .getOrElse { return Result.failure(it) }
                    Triple(
                        parsed.merchant?.takeIf { it.isNotBlank() } ?: existing.merchant,
                        parsed.category,
                        parsed.detailedDescription ?: parsed.placeDetail ?: existing.note,
                    )
                }
            }
            AiEngineMode.CLOUD_ONLY -> {
                if (apiKey.isBlank()) {
                    return Result.failure(IllegalStateException("Google AI Studio API key is not configured in Settings."))
                }
                val parsed = geminiApiClient.parseSms(
                    smsBody = body,
                    sender = existing.sourceSender,
                    apiKey = apiKey,
                    model = aiPreferences.selectedModel.value,
                ).getOrElse { return Result.failure(it) }
                Triple(
                    parsed.merchant?.takeIf { it.isNotBlank() } ?: existing.merchant,
                    parsed.category,
                    parsed.detailedDescription ?: parsed.placeDetail ?: existing.note,
                )
            }
        }

        val updatedEntity = existing.copy(
            merchant = updatedMerchant,
            category = updatedCategory,
            note = updatedNote,
            confidence = 0.98,
            status = TransactionStatus.POSTED,
        )
        transactionDao.update(updatedEntity)
        val record = transactionDao.observeTransactions().first().firstOrNull { it.id == transactionId }
            ?: return Result.failure(IllegalStateException("Failed to load updated transaction"))
        return Result.success(record)
    }

    suspend fun refreshAiSpendingInsights(): Result<List<String>> {
        val apiKey = aiPreferences.apiKey.value
        val engineMode = aiPreferences.engineMode.value
        _isAiLoading.value = true
        try {
            val posted = postedTransactions.first()
            val budget = currentBudget.first()?.amountLimit
            val currentMonth = YearMonth.now()
            val currentMonthTransactions = posted.filter {
                YearMonth.from(it.toLocalDate()) == currentMonth
            }
            val monthSpent = currentMonthTransactions
                .filter {
                    it.direction == TransactionDirection.DEBIT &&
                        it.category != TransactionCategory.TRANSFER &&
                        it.countsTowardBudget &&
                        !isRepaymentOrTransfer(it)
                }
                .sumOf(TransactionRecord::amount)
            val monthIncome = currentMonthTransactions
                .filter {
                    it.direction == TransactionDirection.CREDIT &&
                        it.category != TransactionCategory.TRANSFER &&
                        it.countsTowardBudget &&
                        !isRepaymentOrTransfer(it)
                }
                .sumOf(TransactionRecord::amount)

            val insightsResult: Result<List<String>> = when (engineMode) {
                AiEngineMode.ON_DEVICE_ONLY -> {
                    Result.success(
                        onDeviceAiEngine.generateSpendingInsightsOnDevice(
                            transactions = currentMonthTransactions,
                            budgetLimit = budget,
                            monthSpent = monthSpent,
                            monthIncome = monthIncome,
                        ),
                    )
                }
                AiEngineMode.AUTO_PIXEL_FIRST -> {
                    if (apiKey.isNotBlank()) {
                        geminiApiClient.generateSpendingInsights(
                            transactions = currentMonthTransactions,
                            budgetLimit = budget,
                            monthSpent = monthSpent,
                            monthIncome = monthIncome,
                            apiKey = apiKey,
                            model = aiPreferences.selectedModel.value,
                        ).recoverCatching {
                            // Graceful fallback to on-device engine if cloud request fails
                            onDeviceAiEngine.generateSpendingInsightsOnDevice(
                                transactions = currentMonthTransactions,
                                budgetLimit = budget,
                                monthSpent = monthSpent,
                                monthIncome = monthIncome,
                            )
                        }
                    } else {
                        // Offline or Pixel 9 without cloud API key
                        Result.success(
                            onDeviceAiEngine.generateSpendingInsightsOnDevice(
                                transactions = currentMonthTransactions,
                                budgetLimit = budget,
                                monthSpent = monthSpent,
                                monthIncome = monthIncome,
                            ),
                        )
                    }
                }
                AiEngineMode.CLOUD_ONLY -> {
                    if (apiKey.isBlank()) {
                        Result.failure(IllegalStateException("Configure your Google AI Studio API key in Settings."))
                    } else {
                        geminiApiClient.generateSpendingInsights(
                            transactions = currentMonthTransactions,
                            budgetLimit = budget,
                            monthSpent = monthSpent,
                            monthIncome = monthIncome,
                            apiKey = apiKey,
                            model = aiPreferences.selectedModel.value,
                        )
                    }
                }
            }

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
        val apiKey = aiPreferences.apiKey.value
        val engineMode = aiPreferences.engineMode.value
        val allPosted = postedTransactions.first()
        val currentBudgetLimit = currentBudget.first()?.amountLimit

        val context = ragEngine.retrieveContext(
            query = userQuery,
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

        val embedding: List<Float>? = if (engineMode != AiEngineMode.ON_DEVICE_ONLY && apiKey.isNotBlank()) {
            geminiApiClient.generateEmbedding(
                text = documentText,
                apiKey = apiKey,
                outputDimensionality = 256,
            ).getOrNull() ?: onDeviceAiEngine.generateEmbeddingOnDevice(documentText).getOrNull()
        } else {
            onDeviceAiEngine.generateEmbeddingOnDevice(documentText).getOrNull()
        }

        if (embedding == null || embedding.isEmpty()) return

        val entity = TransactionEmbeddingEntity.fromFloatList(
            transactionId = transactionId,
            documentText = documentText,
            floats = embedding,
        )
        embeddingDao.insert(entity)
    }

    suspend fun indexUnembeddedTransactions(limit: Int = 15) {
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
    ): Boolean {
        return when (ingestMessage(sender = sender, body = body, receivedAtMillis = receivedAtMillis)) {
            SmsIngestionOutcome.IMPORTED,
            SmsIngestionOutcome.REVIEW,
            SmsIngestionOutcome.SCHEDULED -> true

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
            android.util.Log.i("FinanceRepository", "Notification from $packageName skipped: matched exclusion keyword '$matchedExclusion'")
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

        val combinedBody = when {
            title.isNotBlank() && text.isNotBlank() && !text.startsWith(title, ignoreCase = true) -> "$title: $text"
            text.isNotBlank() -> text
            else -> title
        }
        val lower = combinedBody.lowercase(java.util.Locale.getDefault())

        val hasMoneyIndicator = listOf("₹", "rs.", "inr", "rs ").any { it in lower }
        val hasTransactionVerb = listOf(
            "debited", "credited", "spent", "paid", "withdrawn", "received", "deducted",
            "sent", "transfer", "successful", "purchase", "bill payment", "alert", "vpa",
        ).any { it in lower }

        if (isGmail) {
            if (!hasMoneyIndicator || !hasTransactionVerb) {
                return false
            }
        } else if (!isPaymentApp && !isBankApp) {
            if (!hasMoneyIndicator || !hasTransactionVerb) {
                return false
            }
        }

        val resolvedSender = when {
            isGmail -> title.takeIf { it.isNotBlank() } ?: "Gmail"
            packageName == NotificationPreferences.PACKAGE_GPAY -> "Google Pay"
            packageName == NotificationPreferences.PACKAGE_PHONEPE -> "PhonePe"
            packageName == NotificationPreferences.PACKAGE_PAYTM -> "Paytm"
            packageName == NotificationPreferences.PACKAGE_CRED -> "CRED"
            packageName == NotificationPreferences.PACKAGE_BHIM -> "BHIM"
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
            SmsIngestionOutcome.SCHEDULED -> true
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
        reconcileAccountsAndBalances()
        return ImportReport(
            scanned = messages.size,
            imported = imported,
            sentToReview = review,
            scheduled = scheduled,
            ignored = ignored,
        )
    }

    suspend fun syncRecentEmails(maxMessages: Int = 30): Result<Int> {
        val email = emailPreferences.emailAddress.value
        val password = emailPreferences.appPassword.value
        if (email.isBlank() || password.isBlank()) {
            return Result.failure(IllegalStateException("Gmail address or App Password is not configured."))
        }

        val result = emailSyncManager.fetchRecentBankAlerts(email, password, maxMessages)
        return result.fold(
            onSuccess = { messages ->
                var importedCount = 0
                val sortedMessages = messages.sortedBy { it.timestampMillis }
                sortedMessages.forEach { msg ->
                    val outcome = ingestMessage(
                        sender = msg.sender,
                        body = msg.body,
                        receivedAtMillis = msg.timestampMillis,
                    )
                    if (outcome == SmsIngestionOutcome.IMPORTED || outcome == SmsIngestionOutcome.REVIEW) {
                        importedCount++
                    }
                }
                deduplicateTransactions()
                reconcileAccountsAndBalances()
                emailPreferences.updateSyncResult(
                    timestampMillis = System.currentTimeMillis(),
                    status = "Synced ${messages.size} alerts ($importedCount new)",
                )
                Result.success(importedCount)
            },
            onFailure = { error ->
                emailPreferences.updateSyncResult(
                    timestampMillis = System.currentTimeMillis(),
                    status = "Sync error: ${error.message ?: "Authentication failed"}",
                )
                Result.failure(error)
            },
        )
    }

    suspend fun testEmailCredentials(email: String, appPassword: String): Result<Boolean> {
        return emailSyncManager.testCredentials(email, appPassword)
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

    private fun buildDashboardState(
        postedTransactions: List<TransactionRecord>,
        allTransactions: List<TransactionRecord>,
        budget: Double?,
        subscriptions: List<SubscriptionRecord>,
        accountsList: List<AccountEntity> = emptyList(),
        insights: List<String> = emptyList(),
        isAiLoading: Boolean = false,
    ): DashboardState {
        val currentMonth = YearMonth.now()
        val currentMonthTransactions = postedTransactions.filter {
            YearMonth.from(it.toLocalDate()) == currentMonth
        }
        val spendTransactions = currentMonthTransactions.filter {
            it.direction == TransactionDirection.DEBIT &&
                it.category != TransactionCategory.TRANSFER &&
                it.countsTowardBudget &&
                !isRepaymentOrTransfer(it)
        }
        val monthSpent = spendTransactions.sumOf(TransactionRecord::amount)

        val incomeTransactions = currentMonthTransactions.filter {
            it.direction == TransactionDirection.CREDIT &&
                it.category != TransactionCategory.TRANSFER &&
                it.countsTowardBudget &&
                !isRepaymentOrTransfer(it)
        }
        val monthIncome = incomeTransactions.sumOf(TransactionRecord::amount)

        val cardSpendThisMonth = spendTransactions
            .filter { it.accountKind == AccountKind.CARD }
            .sumOf(TransactionRecord::amount)
        val bankSpendThisMonth = spendTransactions
            .filter { it.accountKind != AccountKind.CARD }
            .sumOf(TransactionRecord::amount)

        val monthNetCashflow = monthIncome - monthSpent
        val today = LocalDate.now()
        val daysInMonth = currentMonth.lengthOfMonth()
        val daysRemaining = (daysInMonth - today.dayOfMonth + 1).coerceAtLeast(1)
        val remainingBudget = if (budget != null) (budget - monthSpent).coerceAtLeast(0.0) else 0.0
        val safeDailySpend = if (budget != null && budget > 0.0) remainingBudget / daysRemaining else 0.0
        val budgetPercentUsed = if (budget != null && budget > 0.0) (monthSpent / budget).toFloat() else 0f

        val categoryBreakdown = spendTransactions
            .groupBy(TransactionRecord::category)
            .map { (category, items) -> CategorySlice(category, items.sumOf(TransactionRecord::amount)) }
            .sortedByDescending(CategorySlice::amount)

        val trendPoints = (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val dayTransactions = currentMonthTransactions.filter { it.toLocalDate() == date }
            TrendPoint(
                date = date,
                income = dayTransactions.filter {
                    it.direction == TransactionDirection.CREDIT &&
                        it.category != TransactionCategory.TRANSFER &&
                        it.countsTowardBudget &&
                        !isRepaymentOrTransfer(it)
                }.sumOf(TransactionRecord::amount),
                expense = dayTransactions
                    .filter {
                        it.direction == TransactionDirection.DEBIT &&
                            it.category != TransactionCategory.TRANSFER &&
                            it.countsTowardBudget &&
                            !isRepaymentOrTransfer(it)
                    }
                    .sumOf(TransactionRecord::amount),
            )
        }

        val totalBankBalance = accountsList.filter { it.kind != AccountKind.CARD }.sumOf { it.currentBalance }

        return DashboardState(
            trackedBalance = if (totalBankBalance != 0.0) totalBankBalance else monthNetCashflow,
            monthSpent = monthSpent,
            monthIncome = monthIncome,
            monthNetCashflow = monthNetCashflow,
            cardSpendThisMonth = cardSpendThisMonth,
            bankSpendThisMonth = bankSpendThisMonth,
            safeDailySpend = safeDailySpend,
            budgetPercentUsed = budgetPercentUsed,
            budgetLimit = budget,
            reviewCount = allTransactions.count { it.status == TransactionStatus.REVIEW },
            activeSubscriptionsCount = subscriptions.count { it.state == SubscriptionState.ACTIVE },
            categoryBreakdown = categoryBreakdown,
            trendPoints = trendPoints,
            recentTransactions = allTransactions.take(6),
            spendingInsights = insights,
            isAiLoading = isAiLoading,
            accounts = accountsList,
        )
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
        val earlyExclusion = exclusionPreferences.findMatchedKeyword(sender, body)
        if (earlyExclusion != null) {
            android.util.Log.i("FinanceRepository", "Ingestion skipped for sender '$sender': matched exclusion keyword '$earlyExclusion'")
            return SmsIngestionOutcome.IGNORED
        }

        // Strict promotional marketing check
        val isExplicitPromotional = listOf(
            "off on", "save up to", "save upto", "up to ₹", "upto ₹", "cashback up to",
            "pre-approved", "loan approved", "reward points", "deal of the day", "use code", "coupon",
            "on emi purchases", "convert to emi",
        ).any { body.contains(it, ignoreCase = true) }
        val hasStrongDebitSignal = listOf(
            "has been debited", "is debited", "was debited", "debited with", "debited by", "debited for", "a/c debited", "account debited",
        ).any { body.contains(it, ignoreCase = true) }

        if (isExplicitPromotional && !hasStrongDebitSignal) {
            android.util.Log.i("FinanceRepository", "Ingestion skipped for sender '$sender': detected promotional marketing offer.")
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

        // Hybrid / On-Device Intelligence: Extract rich merchant, clean note, place detail
        if (aiPreferences.isAiEnabled.value) {
            val engineMode = aiPreferences.engineMode.value
            val apiKey = aiPreferences.apiKey.value

            val onDeviceCandidate = if (engineMode == AiEngineMode.ON_DEVICE_ONLY || engineMode == AiEngineMode.AUTO_PIXEL_FIRST) {
                onDeviceAiEngine.parseSmsOnDevice(smsBody = body, sender = sender).getOrNull()
            } else {
                null
            }

            var aiParsed: AiParsedTransaction? = null

            if (onDeviceCandidate != null && onDeviceCandidate.isTransaction && onDeviceCandidate.amount != null && onDeviceCandidate.direction != null) {
                if (engineMode == AiEngineMode.ON_DEVICE_ONLY || onDeviceCandidate.confidence >= 0.90) {
                    aiParsed = onDeviceCandidate
                }
            }

            if (aiParsed == null && (engineMode == AiEngineMode.AUTO_PIXEL_FIRST || engineMode == AiEngineMode.CLOUD_ONLY) && apiKey.isNotBlank()) {
                val cloudResult = geminiApiClient.parseSms(
                    smsBody = body,
                    sender = sender,
                    apiKey = apiKey,
                    model = aiPreferences.selectedModel.value,
                ).getOrNull()

                if (cloudResult != null && cloudResult.isTransaction) {
                    aiParsed = cloudResult
                } else if (onDeviceCandidate != null && onDeviceCandidate.isTransaction) {
                    aiParsed = onDeviceCandidate
                }
            } else if (aiParsed == null && onDeviceCandidate != null && onDeviceCandidate.isTransaction) {
                aiParsed = onDeviceCandidate
            }

            aiParsed?.let { result ->
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
            android.util.Log.i("FinanceRepository", "Ingestion skipped: merchant '$resolvedMerchant' matched exclusion keyword '$merchantExclusion'")
            return SmsIngestionOutcome.IGNORED
        }

        if (note.isNullOrBlank()) {
            note = when (direction) {
                TransactionDirection.CREDIT -> "Payment from $resolvedMerchant"
                TransactionDirection.DEBIT -> "Payment to $resolvedMerchant"
            }
        }

        val fingerprint = hashFingerprint(sender, body, receivedAtMillis)
        if (transactionDao.fingerprintExists(fingerprint)) {
            return SmsIngestionOutcome.DUPLICATE
        }

        val resolvedOccurredAt = parsed?.occurredAtMillis ?: receivedAtMillis

        val existingSimilar = transactionDao.findSimilarTransaction(
            amount = amount,
            direction = direction,
            occurredAtMillis = resolvedOccurredAt,
            timeToleranceMillis = 300_000L,
        )
        if (existingSimilar != null) {
            val merchantMatch = existingSimilar.merchant.equals(resolvedMerchant, ignoreCase = true)
            val senderMatch = existingSimilar.sourceSender.equals(sender, ignoreCase = true)
            if (merchantMatch || senderMatch) {
                return SmsIngestionOutcome.DUPLICATE
            }
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
        val status = if (confidence >= 0.7) TransactionStatus.POSTED else TransactionStatus.REVIEW
        val countsTowardBudget = if (isCardBillPayment || category == TransactionCategory.TRANSFER) {
            false
        } else {
            parsed?.countsTowardBudget ?: true
        }

        val balanceProof = BalanceProofVerifier.verifyBalance(sender, body, resolvedOccurredAt)
        val verifiedAvailableBalance = if (balanceProof.isVerified) balanceProof.balance else availableBalance

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
            if (balanceProof.isVerified && balanceProof.balance != null) {
                accountDao.updateVerifiedBalance(
                    accountId = accountId,
                    balance = balanceProof.balance,
                    updatedAt = resolvedOccurredAt,
                    proofSnippet = balanceProof.proofSnippet,
                    proofSource = balanceProof.proofSource,
                    isVerified = true,
                )
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
            }
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
                name = accountLabel ?: defaultAccountName(effectiveKind),
                kind = effectiveKind,
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
        val canonical = BankDetector.normalizeToCanonicalBank(value)
        if (canonical != null) return canonical
        return value?.trim()?.takeIf { it.isNotBlank() && BankDetector.isLegitimateBank(it) }
    }

    private fun normalizeLastFourDigits(value: String?): String? {
        val digits = value?.filter(Char::isDigit)?.takeLast(4).orEmpty()
        return digits.takeIf { it.length == 4 }
    }

    private fun fallbackScheduledMerchant(sender: String): String {
        return "Scheduled via ${sender.uppercase()}"
    }

    suspend fun deduplicateTransactions(): Int {
        val all = transactionDao.getAllTransactions()
        val duplicatesToDelete = mutableListOf<Long>()
        val seen = mutableListOf<TransactionEntity>()

        for (tx in all) {
            val merchantLower = tx.merchant.trim().lowercase()
            val isBogus = merchantLower.startsWith("be recorded") ||
                merchantLower.contains("recorded by amc") ||
                (merchantLower == "merchant" && tx.note?.contains("Card purchase at Merchant", ignoreCase = true) == true)

            if (isBogus) {
                duplicatesToDelete.add(tx.id)
                continue
            }

            val isDuplicate = seen.any { existing ->
                existing.amount == tx.amount &&
                    existing.direction == tx.direction &&
                    kotlin.math.abs(existing.occurredAtMillis - tx.occurredAtMillis) <= 300_000L &&
                    (existing.merchant.equals(tx.merchant, ignoreCase = true) ||
                        (existing.accountId != null && existing.accountId == tx.accountId) ||
                        existing.sourceSender.equals(tx.sourceSender, ignoreCase = true))
            }
            if (isDuplicate) {
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

    suspend fun reconcileAccountsAndBalances(): Int {
        val existingAccounts = accountDao.getAccounts()
        val allTransactions = transactionDao.getAllTransactions()
        var reconciledCount = 0

        // 1. Identify and purge bogus accounts (system-generated non-banks, e.g. "UPI", "VM-MYNTR", generic merchants)
        for (account in existingAccounts) {
            val isLegitBank = BankDetector.isLegitimateBank(account.institutionName ?: account.name)
            val isBogus = (account.kind == AccountKind.BANK && account.isSystemGenerated && !isLegitBank) ||
                (account.name.equals("UPI", ignoreCase = true) && account.kind == AccountKind.UPI && account.isSystemGenerated)

            if (isBogus) {
                // Re-route transactions to a genuine bank account if possible
                val orphanTxs = allTransactions.filter { it.accountId == account.id }
                for (tx in orphanTxs) {
                    val matchingBank = existingAccounts.firstOrNull {
                        it.id != account.id && it.kind == AccountKind.BANK &&
                            BankDetector.isLegitimateBank(it.institutionName ?: it.name)
                    }
                    transactionDao.update(tx.copy(accountId = matchingBank?.id))
                }
                accountDao.deleteById(account.id)
                reconciledCount++
            }
        }

        // 2. Re-anchor genuine bank accounts with verified substantial proof
        val refreshedAccounts = accountDao.getAccounts()
        val updatedTransactions = transactionDao.getAllTransactions()

        for (account in refreshedAccounts) {
            if (account.kind != AccountKind.BANK && account.kind != AccountKind.CARD) continue

            // Find the most recent transaction with a verified available balance
            val txWithBalance = updatedTransactions
                .filter { it.accountId == account.id && it.status == TransactionStatus.POSTED }
                .sortedByDescending { it.occurredAtMillis }
                .firstOrNull { tx ->
                    if (tx.availableBalance != null && tx.availableBalance > 0.0) {
                        val proof = BalanceProofVerifier.verifyBalance(
                            sender = tx.sourceSender,
                            body = tx.smsBody ?: "",
                            occurredAtMillis = tx.occurredAtMillis,
                        )
                        proof.isVerified
                    } else false
                }

            if (txWithBalance != null) {
                val proof = BalanceProofVerifier.verifyBalance(
                    sender = txWithBalance.sourceSender,
                    body = txWithBalance.smsBody ?: "",
                    occurredAtMillis = txWithBalance.occurredAtMillis,
                )
                if (proof.isVerified && proof.balance != null) {
                    accountDao.updateVerifiedBalance(
                        accountId = account.id,
                        balance = proof.balance,
                        updatedAt = txWithBalance.occurredAtMillis,
                        proofSnippet = proof.proofSnippet,
                        proofSource = proof.proofSource,
                        isVerified = true,
                    )
                }
            } else if (account.isSystemGenerated && account.currentBalance != 0.0 && !account.isBalanceVerified) {
                // Clear unverified legacy balance if no bank statement verifies it
                if (account.balanceUpdatedAtMillis == null) {
                    accountDao.updateVerifiedBalance(
                        accountId = account.id,
                        balance = 0.0,
                        updatedAt = System.currentTimeMillis(),
                        proofSnippet = null,
                        proofSource = null,
                        isVerified = false,
                    )
                }
            }
        }

        return reconciledCount
    }

    private fun hashFingerprint(sender: String, body: String, receivedAtMillis: Long): String {
        val normalizedSender = sender.trim().uppercase()
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
