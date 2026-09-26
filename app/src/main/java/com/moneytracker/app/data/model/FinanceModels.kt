package com.moneytracker.app.data.model

import com.moneytracker.app.data.db.AccountEntity
import com.moneytracker.app.data.db.TransactionRecord
import java.time.LocalDate
import java.time.YearMonth

enum class TransactionDirection {
    CREDIT,
    DEBIT,
}

enum class TransactionStatus {
    POSTED,
    REVIEW,
}

enum class TransactionCategory(val label: String) {
    FOOD("Food"),
    TRAVEL("Travel"),
    BILLS("Bills"),
    SHOPPING("Shopping"),
    TRANSFER("Transfer"),
    SALARY("Salary"),
    SUBSCRIPTION("Subscription"),
    HEALTH("Health"),
    OTHER("Other"),
}

enum class AccountKind {
    BANK,
    CARD,
    WALLET,
    UPI,
    CASH,
}

enum class CardType(val label: String) {
    CREDIT("Credit card"),
    DEBIT("Debit card"),
}

enum class SubscriptionState {
    ACTIVE,
    SUGGESTED,
}

enum class ScheduledTransactionKind(val label: String) {
    MANDATE("Mandate"),
}

enum class TransactionFilter(val label: String) {
    ALL("All"),
    BUDGET("Budget"),
    SPENT("Spent"),
    INCOME("Income"),
    UPI("UPI"),
    CARD("Card"),
    REVIEW("Needs Review"),
}

data class MonthBudgetSummary(
    val yearMonthKey: String,
    val monthLabel: String,
    val budgetLimit: Double?,
    val spent: Double,
    val transactions: List<com.moneytracker.app.data.db.TransactionRecord>,
)

data class ParsedSmsTransaction(
    val amount: Double?,
    val direction: TransactionDirection?,
    val merchant: String?,
    val inferredCategory: TransactionCategory,
    val accountLabel: String?,
    val accountKind: AccountKind,
    val confidence: Double,
    val shouldIgnore: Boolean,
    val institutionName: String? = null,
    val occurredAtMillis: Long? = null,
    val bankAccountLastFourDigits: String? = null,
    val cardLastFourDigits: String? = null,
    val cardType: CardType? = null,
    val isUpiPayment: Boolean = false,
    val isCardPayment: Boolean = false,
    val isCardBillPayment: Boolean = false,
    val placeDetail: String? = null,
    val aiEnriched: Boolean = false,
    val countsTowardBudget: Boolean = true,
    val availableBalance: Double? = null,
    val isAtmWithdrawal: Boolean = false,
)

data class ParsedScheduledTransaction(
    val amount: Double?,
    val merchant: String?,
    val scheduledForMillis: Long?,
    val inferredCategory: TransactionCategory,
    val accountLabel: String?,
    val accountKind: AccountKind,
    val kind: ScheduledTransactionKind,
    val confidence: Double,
    val institutionName: String? = null,
    val bankAccountLastFourDigits: String? = null,
    val cardLastFourDigits: String? = null,
    val cardType: CardType? = null,
    val isUpiPayment: Boolean = false,
    val isCardPayment: Boolean = false,
)

data class ParsedSmsMessage(
    val transaction: ParsedSmsTransaction? = null,
    val scheduledTransaction: ParsedScheduledTransaction? = null,
    val shouldIgnore: Boolean = false,
)

data class DashboardState(
    val trackedBalance: Double = 0.0,
    val monthSpent: Double = 0.0,
    val monthIncome: Double = 0.0,
    val monthNetCashflow: Double = 0.0,
    val cardSpendThisMonth: Double = 0.0,
    val bankSpendThisMonth: Double = 0.0,
    val safeDailySpend: Double = 0.0,
    val budgetPercentUsed: Float = 0f,
    val budgetLimit: Double? = null,
    val reviewCount: Int = 0,
    val activeSubscriptionsCount: Int = 0,
    val categoryBreakdown: List<CategorySlice> = emptyList(),
    val trendPoints: List<TrendPoint> = emptyList(),
    val recentTransactions: List<TransactionRecord> = emptyList(),
    val spendingInsights: List<String> = emptyList(),
    val isAiLoading: Boolean = false,
    val accounts: List<AccountEntity> = emptyList(),
    val selectedYearMonth: YearMonth = YearMonth.now(),
)

data class CategorySlice(
    val category: TransactionCategory,
    val amount: Double,
)

data class TrendPoint(
    val date: LocalDate,
    val income: Double,
    val expense: Double,
)

data class TransactionDraft(
    val amount: Double,
    val direction: TransactionDirection,
    val merchant: String,
    val category: TransactionCategory,
    val accountId: Long?,
    val note: String? = null,
    val occurredAtMillis: Long = System.currentTimeMillis(),
    val countsTowardBudget: Boolean = true,
)

data class AccountDraft(
    val name: String,
    val kind: AccountKind,
    val institutionName: String? = null,
    val cardType: CardType? = null,
    val lastFourDigits: String? = null,
    val isRupayCreditCard: Boolean = false,
    val currentBalance: Double = 0.0,
    val balanceProofSnippet: String? = null,
    val balanceProofSource: String? = null,
    val isBalanceVerified: Boolean = false,
)

data class SubscriptionDraft(
    val merchant: String,
    val amount: Double,
    val billingCycleDays: Int,
    val nextDueAtMillis: Long,
    val accountId: Long?,
)

data class ImportReport(
    val scanned: Int,
    val imported: Int,
    val sentToReview: Int,
    val scheduled: Int,
    val ignored: Int,
)

enum class AssistantSender {
    USER,
    ASSISTANT,
}

data class AssistantMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: AssistantSender,
    val text: String,
    val citedTransactions: List<TransactionRecord> = emptyList(),
    val timestampMillis: Long = System.currentTimeMillis(),
)


