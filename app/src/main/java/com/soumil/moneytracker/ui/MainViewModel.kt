package com.soumil.moneytracker.ui

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.db.BudgetEntity
import com.soumil.moneytracker.data.db.SubscriptionRecord
import com.soumil.moneytracker.data.db.TransactionRecord
import com.soumil.moneytracker.data.model.DashboardState
import com.soumil.moneytracker.data.model.SubscriptionDraft
import com.soumil.moneytracker.data.model.SubscriptionState
import com.soumil.moneytracker.data.model.TransactionDraft
import com.soumil.moneytracker.data.model.TransactionFilter
import com.soumil.moneytracker.data.model.TransactionStatus
import com.soumil.moneytracker.data.repo.FinanceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val messageEvents = MutableSharedFlow<String>()
    private val selectedFilter = MutableStateFlow(TransactionFilter.ALL)

    val messages = messageEvents.asSharedFlow()
    val filter: StateFlow<TransactionFilter> = selectedFilter

    val dashboard: StateFlow<DashboardState> = repository.dashboard.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardState(),
    )

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

    val subscriptions: StateFlow<List<SubscriptionRecord>> = repository.subscriptions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val filteredTransactions: StateFlow<List<TransactionRecord>> = combine(
        repository.transactions,
        selectedFilter,
    ) { transactions, filter ->
        when (filter) {
            TransactionFilter.ALL -> transactions
            TransactionFilter.SPENT -> transactions.filter { it.direction == com.soumil.moneytracker.data.model.TransactionDirection.DEBIT && it.status == TransactionStatus.POSTED }
            TransactionFilter.INCOME -> transactions.filter { it.direction == com.soumil.moneytracker.data.model.TransactionDirection.CREDIT && it.status == TransactionStatus.POSTED }
            TransactionFilter.REVIEW -> transactions.filter { it.status == TransactionStatus.REVIEW }
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

    fun setFilter(filter: TransactionFilter) {
        selectedFilter.value = filter
    }

    fun setMonthlyBudget(amountText: String) {
        val amount = amountText.replace(",", "").toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            emitMessage("Enter a valid monthly budget.")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.setMonthlyBudget(amount)
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

    fun dismissTransaction(transactionId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.dismissTransaction(transactionId)
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
                    "Scanned ${report.scanned} SMS. Imported ${report.imported}, review ${report.sentToReview}, ignored ${report.ignored}.",
                )
            }.onFailure {
                emitMessage("Could not import SMS. Check permissions and try again.")
            }
        }
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
