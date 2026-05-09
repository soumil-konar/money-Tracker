package com.soumil.moneytracker.data.model

import com.soumil.moneytracker.data.db.TransactionRecord
import java.time.LocalDate

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

enum class SubscriptionState {
    ACTIVE,
    SUGGESTED,
}

enum class ScheduledTransactionKind(val label: String) {
    MANDATE("Mandate"),
}

enum class TransactionFilter(val label: String) {
    ALL("All"),
    SPENT("Spent"),
    INCOME("Income"),
    UPI("UPI"),
    CARD("Card"),
    REVIEW("Needs Review"),
}

data class ParsedSmsTransaction(
    val amount: Double?,
    val direction: TransactionDirection?,
    val merchant: String?,
    val inferredCategory: TransactionCategory,
    val accountLabel: String?,
    val accountKind: AccountKind,
    val confidence: Double,
    val shouldIgnore: Boolean,
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
    val budgetLimit: Double? = null,
    val reviewCount: Int = 0,
    val activeSubscriptionsCount: Int = 0,
    val categoryBreakdown: List<CategorySlice> = emptyList(),
    val trendPoints: List<TrendPoint> = emptyList(),
    val recentTransactions: List<TransactionRecord> = emptyList(),
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

