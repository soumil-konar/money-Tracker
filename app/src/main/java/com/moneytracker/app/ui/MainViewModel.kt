package com.moneytracker.app.ui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.time.YearMonth
import com.moneytracker.app.backup.BackupRestoreResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moneytracker.app.data.db.AccountEntity
import com.moneytracker.app.data.db.BudgetEntity
import com.moneytracker.app.data.db.ScheduledTransactionRecord
import com.moneytracker.app.data.db.SubscriptionRecord
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.AccountDraft
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.DashboardState
import com.moneytracker.app.data.model.MonthBudgetSummary
import com.moneytracker.app.data.model.SubscriptionDraft
import com.moneytracker.app.data.model.SubscriptionState
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.AssistantMessage
import com.moneytracker.app.data.model.AssistantSender
import com.moneytracker.app.data.local.AiEngineMode
import com.moneytracker.app.data.local.BiometricLockTimeout
import com.moneytracker.app.data.local.HapticIntensity
import com.moneytracker.app.data.model.TransactionDraft
import com.moneytracker.app.data.model.TransactionFilter
import com.moneytracker.app.data.model.TransactionStatus
import com.moneytracker.app.data.repo.FinanceRepository
import com.moneytracker.app.data.db.canTransferToCash
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val messageEvents = MutableSharedFlow<String>()
    private val selectedFilter = MutableStateFlow(TransactionFilter.ALL)
    private val currentSearchQuery = MutableStateFlow("")
    private val _isAiAnalyzing = MutableStateFlow(false)
    private val _aiTestStatus = MutableStateFlow<String?>(null)

    val messages = messageEvents.asSharedFlow()
    val filter: StateFlow<TransactionFilter> = selectedFilter
    val searchQuery: StateFlow<String> = currentSearchQuery
    val isAiAnalyzing: StateFlow<Boolean> = _isAiAnalyzing
    val aiTestStatus: StateFlow<String?> = _aiTestStatus
    private val _emailTestStatus = MutableStateFlow<String?>(null)
    val emailTestStatus: StateFlow<String?> = _emailTestStatus

    private val _assistantMessages = MutableStateFlow<List<AssistantMessage>>(
        listOf(
            AssistantMessage(
                sender = AssistantSender.ASSISTANT,
                text = "Hi! I'm your Gemini Spending Assistant. Ask me anything about your expenses, categories, places you've spent money, or how to pace your budget this month.",
            ),
        ),
    )
    val assistantMessages: StateFlow<List<AssistantMessage>> = _assistantMessages

    private val _isAssistantThinking = MutableStateFlow(false)
    val isAssistantThinking: StateFlow<Boolean> = _isAssistantThinking

    val aiApiKey: StateFlow<String> = repository.aiPreferences.apiKey
    val isAiEnabled: StateFlow<Boolean> = repository.aiPreferences.isAiEnabled
    val selectedModel: StateFlow<String> = repository.aiPreferences.selectedModel
    val engineMode: StateFlow<AiEngineMode> = repository.aiPreferences.engineMode
    val deviceStatus: String = repository.onDeviceAiEngine.getDeviceStatus()
    val isPixelDevice: Boolean = repository.onDeviceAiEngine.isPixelDevice()
    val isTensorG4Ready: Boolean = repository.onDeviceAiEngine.isTensorSoc()

    val isEmailSyncEnabled: StateFlow<Boolean> = repository.emailPreferences.isEmailSyncEnabled
    val emailAddress: StateFlow<String> = repository.emailPreferences.emailAddress
    val emailAppPassword: StateFlow<String> = repository.emailPreferences.appPassword
    val emailLastSyncTimestamp: StateFlow<Long> = repository.emailPreferences.lastSyncTimestamp
    val emailLastSyncStatus: StateFlow<String?> = repository.emailPreferences.lastSyncStatus
    private val _isEmailSyncing = MutableStateFlow(false)
    val isEmailSyncing: StateFlow<Boolean> = _isEmailSyncing

    val isNotificationListenerEnabled: StateFlow<Boolean> = repository.notificationPreferences.isNotificationListenerEnabled
    val isGmailMonitoringEnabled: StateFlow<Boolean> = repository.notificationPreferences.isGmailMonitoringEnabled
    val isPaymentAppsMonitoringEnabled: StateFlow<Boolean> = repository.notificationPreferences.isPaymentAppsMonitoringEnabled
    val isBankAppsMonitoringEnabled: StateFlow<Boolean> = repository.notificationPreferences.isBankAppsMonitoringEnabled
    val notificationLastCapturedTimestamp: StateFlow<Long> = repository.notificationPreferences.lastCapturedTimestamp
    val notificationLastCapturedPackage: StateFlow<String?> = repository.notificationPreferences.lastCapturedPackage
    val notificationCapturedCount: StateFlow<Int> = repository.notificationPreferences.capturedCount

    val isExclusionFilterEnabled: StateFlow<Boolean> = repository.exclusionPreferences.isExclusionFilterEnabled
    val excludedKeywords: StateFlow<Set<String>> = repository.exclusionPreferences.excludedKeywords

    val dashboard: StateFlow<DashboardState> = repository.dashboard.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardState(),
    )

    val selectedYearMonth: StateFlow<YearMonth> = repository.selectedYearMonth

    fun setSelectedYearMonth(yearMonth: YearMonth) {
        repository.setSelectedYearMonth(yearMonth)
    }

    val accounts: StateFlow<List<AccountEntity>> = repository.accounts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val currentBudget: StateFlow<BudgetEntity?> = repository.currentBudget.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val budgetHistory: StateFlow<List<MonthBudgetSummary>> = repository.budgetHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val isInitialSetupComplete: StateFlow<Boolean> = repository.isInitialSetupComplete.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    val subscriptions: StateFlow<List<SubscriptionRecord>> = repository.subscriptions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val scheduledTransactions: StateFlow<List<ScheduledTransactionRecord>> = repository.scheduledTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val filteredTransactions: StateFlow<List<TransactionRecord>> = combine(
        repository.transactions,
        selectedFilter,
        currentSearchQuery,
    ) { transactions, filter, query ->
        val filterMatched = when (filter) {
            TransactionFilter.ALL -> transactions
            TransactionFilter.BUDGET -> transactions.filter {
                it.status == TransactionStatus.POSTED &&
                    it.direction == TransactionDirection.DEBIT &&
                    it.category != TransactionCategory.TRANSFER &&
                    it.countsTowardBudget
            }
            TransactionFilter.SPENT -> transactions.filter {
                it.direction == TransactionDirection.DEBIT && it.status == TransactionStatus.POSTED
            }
            TransactionFilter.INCOME -> transactions.filter {
                it.direction == TransactionDirection.CREDIT && it.status == TransactionStatus.POSTED
            }
            TransactionFilter.UPI -> transactions.filter {
                it.direction == TransactionDirection.DEBIT &&
                    it.status == TransactionStatus.POSTED &&
                    it.matchesUpiFilter()
            }
            TransactionFilter.CARD -> transactions.filter {
                it.direction == TransactionDirection.DEBIT &&
                    it.status == TransactionStatus.POSTED &&
                    it.matchesCardFilter()
            }
            TransactionFilter.REVIEW -> transactions.filter { it.status == TransactionStatus.REVIEW }
        }

        if (query.isBlank()) {
            filterMatched
        } else {
            val q = query.trim().lowercase()
            filterMatched.filter {
                it.merchant.lowercase().contains(q) ||
                    it.note?.lowercase()?.contains(q) == true ||
                    it.category.label.lowercase().contains(q) ||
                    it.amount.toString().contains(q) ||
                    it.accountName?.lowercase()?.contains(q) == true
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val activeSubscriptions: StateFlow<List<SubscriptionRecord>> = subscriptions
        .map { list -> list.filter { it.state == SubscriptionState.ACTIVE } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val suggestedSubscriptions: StateFlow<List<SubscriptionRecord>> = subscriptions
        .map { list -> list.filter { it.state == SubscriptionState.SUGGESTED } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        viewModelScope.launch {
            repository.deduplicateTransactions()
        }
    }

    fun setFilter(filter: TransactionFilter) {
        selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        currentSearchQuery.value = query
    }

    fun updateAiApiKey(key: String) {
        repository.aiPreferences.setApiKey(key)
        emitMessage("AI API key saved.")
    }

    fun setAiEnabled(enabled: Boolean) {
        repository.aiPreferences.setAiEnabled(enabled)
        emitMessage(if (enabled) "AI SMS enrichment enabled." else "AI SMS enrichment disabled.")
    }

    fun setSelectedModel(model: String) {
        repository.aiPreferences.setSelectedModel(model)
        emitMessage("AI model updated to $model.")
    }

    fun setAiEngineMode(mode: AiEngineMode) {
        repository.aiPreferences.setEngineMode(mode)
        emitMessage("AI Engine switched to: ${mode.label}")
    }

    val isHapticEnabled: StateFlow<Boolean> = repository.hapticPreferences.isHapticEnabled
    val hapticIntensity: StateFlow<HapticIntensity> = repository.hapticPreferences.hapticIntensity

    fun setHapticEnabled(enabled: Boolean) {
        repository.hapticPreferences.setHapticEnabled(enabled)
    }

    fun setHapticIntensity(intensity: HapticIntensity) {
        repository.hapticPreferences.setHapticIntensity(intensity)
    }

    val themeMode: StateFlow<com.moneytracker.app.data.local.ThemeMode> = repository.themePreferences.themeMode
    val themeAccent: StateFlow<com.moneytracker.app.data.local.ThemeAccent> = repository.themePreferences.themeAccent

    fun setThemeMode(mode: com.moneytracker.app.data.local.ThemeMode) {
        repository.themePreferences.setThemeMode(mode)
    }

    fun setThemeAccent(accent: com.moneytracker.app.data.local.ThemeAccent) {
        repository.themePreferences.setThemeAccent(accent)
        if (accent.isExpressive) {
            emitMessage("Material 3 Expressive theming activated.")
        } else {
            emitMessage("${accent.label} accent applied (Material 3 Expressive disabled).")
        }
    }

    val isBiometricEnabled: StateFlow<Boolean> = repository.securityPreferences.isBiometricEnabled
    val biometricTimeout: StateFlow<BiometricLockTimeout> = repository.securityPreferences.biometricTimeout

    fun setBiometricEnabled(enabled: Boolean) {
        repository.securityPreferences.setBiometricEnabled(enabled)
    }

    fun setBiometricTimeout(timeout: BiometricLockTimeout) {
        repository.securityPreferences.setBiometricTimeout(timeout)
    }

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        return repository.securityPreferences.isBiometricHardwareAvailable(context)
    }

    private val _dismissedAtmPromptIds = MutableStateFlow<Set<Long>>(emptySet())
    val dismissedAtmPromptIds: StateFlow<Set<Long>> = _dismissedAtmPromptIds.asStateFlow()

    val untransferredAtmTransactions: StateFlow<List<TransactionRecord>> = combine(
        repository.transactions,
        _dismissedAtmPromptIds,
    ) { txList: List<TransactionRecord>, dismissedIds: Set<Long> ->
        val oneWeekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        txList.filter { tx ->
            tx.canTransferToCash &&
                tx.id !in dismissedIds &&
                tx.occurredAtMillis >= oneWeekAgo
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun dismissAtmPrompt(transactionId: Long) {
        _dismissedAtmPromptIds.update { it + transactionId }
    }

    fun transferToCashWallet(transactionId: Long) {
        viewModelScope.launch {
            repository.transferToCashWallet(transactionId)
                .onSuccess {
                    emitMessage("Transferred to Cash in Hand wallet")
                }
                .onFailure {
                    emitMessage("Failed to transfer: ${it.message ?: "Unknown error"}")
                }
        }
    }

    fun testAiConnection() {
        viewModelScope.launch {
            _aiTestStatus.value = "Testing connection..."
            val result = repository.testAiConnection(
                apiKey = repository.aiPreferences.apiKey.value,
                model = repository.aiPreferences.selectedModel.value,
            )
            result.onSuccess {
                _aiTestStatus.value = it
                emitMessage("Connected to Gemini successfully!")
            }.onFailure {
                _aiTestStatus.value = "Failed: ${it.message}"
                emitMessage("Connection failed: ${it.message}")
            }
        }
    }

    fun enrichTransactionWithAi(transactionId: Long) {
        viewModelScope.launch {
            _isAiAnalyzing.value = true
            runCatching {
                repository.enrichTransactionWithAi(transactionId)
            }.onSuccess {
                emitMessage("Transaction enriched and confirmed by AI.")
            }.onFailure {
                emitMessage("AI analysis failed: ${it.message}")
            }
            _isAiAnalyzing.value = false
        }
    }

    fun refreshAiSpendingInsights() {
        viewModelScope.launch {
            runCatching {
                repository.refreshAiSpendingInsights()
            }.onSuccess {
                emitMessage("AI financial insights updated.")
            }.onFailure {
                emitMessage("Could not generate AI insights: ${it.message}")
            }
        }
    }

    fun askAssistant(userQuery: String) {
        val query = userQuery.trim()
        if (query.isBlank()) return

        val userMessage = AssistantMessage(
            sender = AssistantSender.USER,
            text = query,
        )
        _assistantMessages.value = _assistantMessages.value + userMessage
        _isAssistantThinking.value = true

        viewModelScope.launch {
            val result = repository.askSpendingAssistant(query)
            _isAssistantThinking.value = false
            result.onSuccess { assistantMessage ->
                _assistantMessages.value = _assistantMessages.value + assistantMessage
            }.onFailure { error ->
                val errorMsg = error.message ?: "Failed to get response from assistant."
                _assistantMessages.value = _assistantMessages.value + AssistantMessage(
                    sender = AssistantSender.ASSISTANT,
                    text = "Error: $errorMsg",
                )
            }
        }
    }

    fun clearAssistantChat() {
        _assistantMessages.value = listOf(
            AssistantMessage(
                sender = AssistantSender.ASSISTANT,
                text = "Chat cleared. What else can I help you analyze about your finances?",
            ),
        )
    }

    fun setMonthlyBudget(amountText: String, yearMonth: YearMonth = repository.selectedYearMonth.value) {
        val amount = amountText.replace(",", "").toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            emitMessage("Enter a valid monthly budget.")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.setMonthlyBudget(amount, yearMonth.toString())
            }.onSuccess {
                emitMessage("Monthly budget updated.")
            }.onFailure {
                emitMessage("Could not update the monthly budget.")
            }
        }
    }

    fun addTransaction(draft: TransactionDraft) {
        viewModelScope.launch {
            runCatching {
                repository.addManualTransaction(draft)
            }.onSuccess {
                emitMessage("Transaction added.")
            }.onFailure {
                emitMessage("Could not add the transaction.")
            }
        }
    }

    fun configurePrimaryBank(
        institutionName: String,
        accountName: String,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.configurePrimaryBank(
                    institutionName = institutionName,
                    accountName = accountName,
                )
            }.onSuccess {
                emitMessage("Primary bank updated.")
            }.onFailure {
                emitMessage("Could not save the bank setup.")
            }
        }
    }

    fun addAccount(draft: AccountDraft) {
        viewModelScope.launch {
            runCatching {
                repository.addAccount(draft)
            }.onSuccess {
                emitMessage("Account added.")
            }.onFailure {
                emitMessage("Could not add the account.")
            }
        }
    }

    fun updateAccount(
        accountId: Long,
        draft: AccountDraft,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.updateAccount(accountId = accountId, draft = draft)
            }.onSuccess {
                emitMessage("Account updated.")
            }.onFailure {
                emitMessage("Could not update the account.")
            }
        }
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteAccount(accountId)
            }.onSuccess {
                emitMessage("Account removed.")
            }.onFailure {
                emitMessage("Could not remove the account.")
            }
        }
    }

    fun trueUpAccountBalance(accountId: Long, newBalance: Double, reason: String? = null) {
        viewModelScope.launch {
            runCatching {
                repository.trueUpAccountBalance(accountId, newBalance, reason)
            }.onSuccess {
                emitMessage("Account balance adjusted and reconciled.")
            }.onFailure {
                emitMessage("Could not adjust account balance.")
            }
        }
    }

    fun exportBackup(context: Context, destinationUri: Uri, passphrase: String) {
        viewModelScope.launch {
            runCatching {
                val backupBytes = repository.exportEncryptedBackup(passphrase)
                context.contentResolver.openOutputStream(destinationUri)?.use { stream ->
                    stream.write(backupBytes)
                    stream.flush()
                } ?: error("Unable to open output stream.")
            }.onSuccess {
                emitMessage("Encrypted backup exported successfully.")
            }.onFailure { e ->
                emitMessage("Export failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun restoreBackup(context: Context, sourceUri: Uri, passphrase: String) {
        viewModelScope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    stream.readBytes()
                } ?: error("Unable to open backup file.")
                repository.restoreEncryptedBackup(bytes, passphrase)
            }.onSuccess { result ->
                when (result) {
                    is BackupRestoreResult.Success -> {
                        emitMessage("Restored ${result.accountsCount} accounts and ${result.transactionsCount} transactions.")
                    }
                    is BackupRestoreResult.Error -> {
                        emitMessage(result.message)
                    }
                }
            }.onFailure { e ->
                emitMessage("Restore failed: ${e.message ?: "Decryption error"}")
            }
        }
    }

    fun markInitialSetupComplete() {
        repository.markInitialSetupComplete()
        emitMessage("Bank and card setup saved.")
    }

    fun updateTransaction(transactionId: Long, draft: TransactionDraft) {
        viewModelScope.launch {
            runCatching {
                repository.updateTransaction(transactionId, draft)
            }.onSuccess {
                emitMessage("Transaction updated.")
            }.onFailure {
                emitMessage("Could not update the transaction.")
            }
        }
    }

    fun addSubscription(draft: SubscriptionDraft) {
        viewModelScope.launch {
            runCatching {
                repository.addSubscription(draft)
            }.onSuccess {
                emitMessage("Subscription added.")
            }.onFailure {
                emitMessage("Could not add the subscription.")
            }
        }
    }

    fun approveReview(transactionId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.approveReview(transactionId)
            }.onSuccess {
                emitMessage("Review item approved.")
            }.onFailure {
                emitMessage("Could not approve the review item.")
            }
        }
    }

    fun setTransactionBudgetInclusion(transactionId: Long, countsTowardBudget: Boolean) {
        viewModelScope.launch {
            runCatching {
                repository.setTransactionBudgetInclusion(transactionId, countsTowardBudget)
            }.onSuccess {
                emitMessage(
                    if (countsTowardBudget) "Transaction added to budget."
                    else "Transaction excluded from budget.",
                )
            }.onFailure {
                emitMessage("Could not update budget inclusion.")
            }
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteTransaction(transactionId)
            }.onSuccess {
                emitMessage("Transaction removed.")
            }.onFailure {
                emitMessage("Could not remove the transaction.")
            }
        }
    }

    fun acceptSuggestedSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.promoteSubscription(subscriptionId)
            }.onSuccess {
                emitMessage("Subscription accepted.")
            }.onFailure {
                emitMessage("Could not accept the suggestion.")
            }
        }
    }

    fun dismissSuggestedSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.dismissSubscription(subscriptionId)
            }.onSuccess {
                emitMessage("Suggestion dismissed.")
            }.onFailure {
                emitMessage("Could not dismiss the suggestion.")
            }
        }
    }

    fun importRecentSms(contentResolver: ContentResolver) {
        viewModelScope.launch {
            runCatching {
                repository.importRecentSms(contentResolver)
            }.onSuccess { report ->
                emitMessage(
                    "Scanned ${report.scanned} SMS. Imported ${report.imported}, review ${report.sentToReview}, scheduled ${report.scheduled}, ignored ${report.ignored}.",
                )
            }.onFailure {
                emitMessage("Could not import SMS. Check permissions and try again.")
            }
        }
    }

    fun setEmailSyncEnabled(enabled: Boolean) {
        repository.emailPreferences.setEmailSyncEnabled(enabled)
    }

    fun updateEmailCredentials(email: String, appPassword: String) {
        repository.emailPreferences.setCredentials(email, appPassword)
    }

    fun testEmailConnection(email: String, appPassword: String) {
        viewModelScope.launch {
            _emailTestStatus.value = "Testing Gmail connection..."
            repository.testEmailCredentials(email, appPassword)
                .onSuccess {
                    repository.emailPreferences.setCredentials(email, appPassword)
                    _emailTestStatus.value = "Success: Connected and authenticated with Gmail IMAP."
                    emitMessage("Gmail IMAP connected successfully!")
                }
                .onFailure { error ->
                    _emailTestStatus.value = error.message ?: "Authentication failed"
                    emitMessage("Gmail connection test failed.")
                }
        }
    }

    fun clearEmailCredentials() {
        repository.emailPreferences.clearCredentials()
    }

    fun syncRecentEmails() {
        viewModelScope.launch {
            _isEmailSyncing.value = true
            try {
                repository.syncRecentEmails()
                    .onSuccess { count ->
                        emitMessage("Email sync complete. Ingested $count new transaction alerts.")
                    }
                    .onFailure { error ->
                        emitMessage("Email sync failed: ${error.message ?: "Authentication error"}")
                    }
            } finally {
                _isEmailSyncing.value = false
            }
        }
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return repository.notificationPreferences.isSystemPermissionGranted(context)
    }

    fun buildNotificationSettingsIntent(context: Context) =
        repository.notificationPreferences.buildSystemSettingsIntent(context)

    fun setNotificationListenerEnabled(enabled: Boolean) {
        repository.notificationPreferences.setNotificationListenerEnabled(enabled)
        emitMessage(if (enabled) "Notification listener active." else "Notification listener paused.")
    }

    fun setGmailMonitoringEnabled(enabled: Boolean) {
        repository.notificationPreferences.setGmailMonitoringEnabled(enabled)
    }

    fun setPaymentAppsMonitoringEnabled(enabled: Boolean) {
        repository.notificationPreferences.setPaymentAppsMonitoringEnabled(enabled)
    }

    fun setBankAppsMonitoringEnabled(enabled: Boolean) {
        repository.notificationPreferences.setBankAppsMonitoringEnabled(enabled)
    }

    fun setExclusionFilterEnabled(enabled: Boolean) {
        repository.exclusionPreferences.setExclusionFilterEnabled(enabled)
        emitMessage(if (enabled) "Exclusion keyword filter enabled." else "Exclusion filter disabled.")
    }

    fun addExclusionKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) {
            emitMessage("Exclusion keyword cannot be blank.")
            return
        }
        val added = repository.exclusionPreferences.addKeyword(trimmed)
        if (added) {
            emitMessage("Added exclusion rule for '$trimmed'.")
        } else {
            emitMessage("'$trimmed' is already in your exclusion list.")
        }
    }

    fun removeExclusionKeyword(keyword: String) {
        repository.exclusionPreferences.removeKeyword(keyword)
        emitMessage("Removed '$keyword' from exclusion rules.")
    }

    fun resetExclusionKeywords() {
        repository.exclusionPreferences.resetToDefaults()
        emitMessage("Reset exclusion rules to default recommendations.")
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            messageEvents.emit(message)
        }
    }

    companion object {
        fun provideFactory(repository: FinanceRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(repository) as T
                }
            }
    }
}

private fun TransactionRecord.matchesUpiFilter(): Boolean {
    val body = smsBody?.lowercase().orEmpty()
    return accountKind == AccountKind.UPI ||
        body.contains("upi") ||
        body.contains("vpa") ||
        sourceSender.contains("paytm", ignoreCase = true) ||
        sourceSender.contains("gpay", ignoreCase = true) ||
        sourceSender.contains("phonepe", ignoreCase = true)
}

private fun TransactionRecord.matchesCardFilter(): Boolean {
    val body = smsBody?.lowercase().orEmpty()
    val isCardBillPayment = category == TransactionCategory.TRANSFER &&
        (
            body.contains("credit card bill payment") ||
                body.contains("payment received towards your credit card") ||
                merchant.lowercase().contains("bill payment")
            )
    if (isCardBillPayment) return false
    if (accountKind == AccountKind.CARD) return true
    if (category == TransactionCategory.TRANSFER) return false
    return body.contains("credit card") ||
        body.contains("debit card") ||
        body.contains("card ending") ||
        body.contains("card xx") ||
        body.contains("card xxxx")
}
