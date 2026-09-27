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

data class TransactionCategory(
    val name: String,
    val label: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransactionCategory) return false
        return name.equals(other.name, ignoreCase = true)
    }

    override fun hashCode(): Int {
        return name.uppercase().hashCode()
    }

    override fun toString(): String = label

    companion object {
        val FOOD = TransactionCategory("FOOD", "Food")
        val TRAVEL = TransactionCategory("TRAVEL", "Travel")
        val BILLS = TransactionCategory("BILLS", "Bills")
        val SHOPPING = TransactionCategory("SHOPPING", "Shopping")
        val TRANSFER = TransactionCategory("TRANSFER", "Transfer")
        val SALARY = TransactionCategory("SALARY", "Salary")
        val SUBSCRIPTION = TransactionCategory("SUBSCRIPTION", "Subscription")
        val HEALTH = TransactionCategory("HEALTH", "Health")
        val OTHER = TransactionCategory("OTHER", "Other")

        val defaultCategories = listOf(
            FOOD,
            TRAVEL,
            BILLS,
            SHOPPING,
            TRANSFER,
            SALARY,
            SUBSCRIPTION,
            HEALTH,
            OTHER,
        )

        val entries: List<TransactionCategory> get() = defaultCategories

        private val customCategoriesMap = java.util.concurrent.ConcurrentHashMap<String, TransactionCategory>()
        private val categoryPaletteIndices = java.util.concurrent.ConcurrentHashMap<String, Int>()

        fun register(category: TransactionCategory, paletteIndex: Int? = null) {
            val key = category.name.uppercase()
            if (defaultCategories.none { it.name.equals(category.name, ignoreCase = true) }) {
                customCategoriesMap[key] = category
            }
            if (paletteIndex != null) {
                categoryPaletteIndices[key] = paletteIndex
            }
        }

        fun registerAll(categories: Collection<TransactionCategory>) {
            categories.forEach { register(it) }
        }

        fun getRegisteredPaletteIndex(category: TransactionCategory): Int? {
            return categoryPaletteIndices[category.name.uppercase()]
        }

        fun getRegisteredCustomCategories(): List<TransactionCategory> {
            return customCategoriesMap.values.sortedBy { it.label }
        }

        fun allCategories(): List<TransactionCategory> {
            return defaultCategories + getRegisteredCustomCategories()
        }

        fun valueOf(raw: String): TransactionCategory {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return OTHER

            defaultCategories.firstOrNull {
                it.name.equals(trimmed, ignoreCase = true) || it.label.equals(trimmed, ignoreCase = true)
            }?.let { return it }

            customCategoriesMap[trimmed.uppercase()]?.let { return it }

            val customCategory = custom(trimmed)
            register(customCategory)
            return customCategory
        }

        fun custom(input: String): TransactionCategory {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return OTHER

            defaultCategories.firstOrNull {
                it.name.equals(trimmed, ignoreCase = true) || it.label.equals(trimmed, ignoreCase = true)
            }?.let { return it }

            val formattedLabel = formatCategoryLabel(trimmed)
            return TransactionCategory(name = formattedLabel, label = formattedLabel)
        }

        fun values(): Array<TransactionCategory> = defaultCategories.toTypedArray()

        private fun formatCategoryLabel(raw: String): String {
            val spaced = raw.replace('_', ' ').replace('-', ' ').trim()
            return spaced.split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { char -> char.uppercase() }
                }
        }
    }
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
    BILL_REMINDER("Bill Reminder"),
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


