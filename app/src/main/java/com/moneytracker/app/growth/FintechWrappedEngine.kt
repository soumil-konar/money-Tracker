package com.moneytracker.app.growth

import com.moneytracker.app.data.db.FinanceDatabase
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WrappedCategoryShare(
    val category: TransactionCategory,
    val totalAmount: Double,
    val percentageOfSpend: Double,
)

data class WrappedMerchantVolume(
    val merchant: String,
    val totalAmount: Double,
    val transactionCount: Int,
)

data class FintechPersona(
    val title: String,
    val emoji: String,
    val tagLine: String,
    val punchline: String,
)

data class FintechWrappedData(
    val periodLabel: String,
    val totalDebit: Double,
    val totalCredit: Double,
    val netSavingsRate: Double,
    val topCategories: List<WrappedCategoryShare>,
    val topMerchant: WrappedMerchantVolume?,
    val weekendDailyVelocity: Double,
    val weekdayDailyVelocity: Double,
    val weekendVelocityRatio: Double,
    val persona: FintechPersona,
    val isAIGeneratedPunchline: Boolean = false,
)

object FintechWrappedEngine {

    private val CARD_DIGITS_REGEX = Regex("""(?i)\b(?:card|a/c|acct|acc|xx|ending in|no\.?)\s*[:#]?\s*\d{3,16}\b""")
    private val STANDALONE_DIGITS_REGEX = Regex("""\b\d{4,16}\b""")

    /**
     * Sanitizes merchant names to strip out card numbers, last-4 digits, and account numbers.
     */
    fun sanitizeMerchantName(rawMerchant: String): String {
        if (rawMerchant.isBlank()) return "Merchant"
        val masked = rawMerchant
            .replace(CARD_DIGITS_REGEX, "••••")
            .replace(STANDALONE_DIGITS_REGEX, "••••")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return masked.ifBlank { "Merchant" }
    }

    /**
     * Pure calculation of Fintech Wrapped metrics from a given list of transactions.
     * Ensures deterministic offline computation and strict privacy masking.
     */
    fun computeMetrics(
        transactions: List<TransactionRecord>,
        yearMonth: YearMonth = YearMonth.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        customPunchline: String? = null,
    ): FintechWrappedData {
        val startOfMonthMillis = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfMonthMillis = yearMonth.atEndOfMonth().atTime(23, 59, 59, 999_999_999).atZone(zoneId).toInstant().toEpochMilli()

        val periodLabel = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))

        // Filter valid month transactions: POSTED, counts toward budget, non-transfer
        val monthTransactions = transactions.filter { tx ->
            tx.occurredAtMillis in startOfMonthMillis..endOfMonthMillis &&
                tx.status == TransactionStatus.POSTED &&
                tx.countsTowardBudget &&
                tx.category != TransactionCategory.TRANSFER
        }

        val debitTxs = monthTransactions.filter { it.direction == TransactionDirection.DEBIT }
        val creditTxs = monthTransactions.filter { it.direction == TransactionDirection.CREDIT }

        val totalDebit = debitTxs.sumOf { it.amount }
        val totalCredit = creditTxs.sumOf { it.amount }

        // Net Savings Rate % = (Credit - Debit) / Credit * 100
        val netSavingsRate = when {
            totalCredit > 0.0 -> ((totalCredit - totalDebit) / totalCredit * 100.0)
            else -> 0.0
        }

        // Top 3 spending categories
        val topCategories = debitTxs.groupBy { it.category }
            .map { (cat, txList) ->
                val amount = txList.sumOf { it.amount }
                val pct = if (totalDebit > 0.0) (amount / totalDebit * 100.0) else 0.0
                WrappedCategoryShare(
                    category = cat,
                    totalAmount = amount,
                    percentageOfSpend = pct,
                )
            }
            .sortedByDescending { it.totalAmount }
            .take(3)

        // Top merchant by volume
        val topMerchant = debitTxs
            .filter { it.merchant.isNotBlank() }
            .groupBy { sanitizeMerchantName(it.merchant) }
            .map { (sanitizedName, txList) ->
                WrappedMerchantVolume(
                    merchant = sanitizedName,
                    totalAmount = txList.sumOf { it.amount },
                    transactionCount = txList.size,
                )
            }
            .maxByOrNull { it.totalAmount }

        // Weekend vs Weekday daily spend velocity
        var weekendDays = 0
        var weekdayDays = 0
        val daysInMonth = yearMonth.lengthOfMonth()
        for (day in 1..daysInMonth) {
            val dow = yearMonth.atDay(day).dayOfWeek
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                weekendDays++
            } else {
                weekdayDays++
            }
        }

        var weekendSpend = 0.0
        var weekdaySpend = 0.0
        for (tx in debitTxs) {
            val dow = Instant.ofEpochMilli(tx.occurredAtMillis).atZone(zoneId).dayOfWeek
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                weekendSpend += tx.amount
            } else {
                weekdaySpend += tx.amount
            }
        }

        val weekendDailyVelocity = if (weekendDays > 0) weekendSpend / weekendDays else 0.0
        val weekdayDailyVelocity = if (weekdayDays > 0) weekdaySpend / weekdayDays else 0.0
        val weekendVelocityRatio = when {
            weekdayDailyVelocity > 0.0 -> weekendDailyVelocity / weekdayDailyVelocity
            weekendDailyVelocity > 0.0 -> 2.0
            else -> 1.0
        }

        // Persona assignment via deterministic heuristic matrix
        val persona = assignPersona(
            netSavingsRate = netSavingsRate,
            topCategories = topCategories,
            weekendVelocityRatio = weekendVelocityRatio,
            customPunchline = customPunchline,
        )

        return FintechWrappedData(
            periodLabel = periodLabel,
            totalDebit = totalDebit,
            totalCredit = totalCredit,
            netSavingsRate = netSavingsRate,
            topCategories = topCategories,
            topMerchant = topMerchant,
            weekendDailyVelocity = weekendDailyVelocity,
            weekdayDailyVelocity = weekdayDailyVelocity,
            weekendVelocityRatio = weekendVelocityRatio,
            persona = persona,
            isAIGeneratedPunchline = !customPunchline.isNullOrBlank(),
        )
    }

    /**
     * Deterministic persona heuristic matrix.
     */
    fun assignPersona(
        netSavingsRate: Double,
        topCategories: List<WrappedCategoryShare>,
        weekendVelocityRatio: Double,
        customPunchline: String? = null,
    ): FintechPersona {
        val topCategory = topCategories.firstOrNull()

        val basePersona = when {
            netSavingsRate >= 40.0 -> FintechPersona(
                title = "The Fortress Builder",
                emoji = "🛡️",
                tagLine = "Unshakable Capital Resilience",
                punchline = "Your future self is already throwing you a thank-you party.",
            )
            topCategory != null && topCategory.category == TransactionCategory.FOOD && topCategory.percentageOfSpend >= 30.0 -> FintechPersona(
                title = "The Weekend Epicurean",
                emoji = "🍽️",
                tagLine = "Culinary Explorer & Taste Connoisseur",
                punchline = "Life is too short for boring meals—and your statement proves it.",
            )
            topCategory != null && topCategory.category == TransactionCategory.SHOPPING && topCategory.percentageOfSpend >= 30.0 -> FintechPersona(
                title = "The Retail Visionary",
                emoji = "🛍️",
                tagLine = "High-Volume Lifestyle Curator",
                punchline = "Packages at the door: modern day dopamine delivered.",
            )
            weekendVelocityRatio >= 2.0 -> FintechPersona(
                title = "The Sunday Sprinter",
                emoji = "⚡",
                tagLine = "Weekend Capital Accelerator",
                punchline = "Work hard, unwind harder. The weekend economy salutes you.",
            )
            topCategory != null && topCategory.category == TransactionCategory.SUBSCRIPTION && topCategory.percentageOfSpend >= 20.0 -> FintechPersona(
                title = "The Streamlined Subscriber",
                emoji = "🔄",
                tagLine = "Recurring Service Architect",
                punchline = "Every recurring subscription fine-tuned for high life satisfaction.",
            )
            topCategory != null && topCategory.category == TransactionCategory.BILLS && topCategory.percentageOfSpend >= 35.0 -> FintechPersona(
                title = "The Systems Optimizer",
                emoji = "⚙️",
                tagLine = "Precision Utility Maestro",
                punchline = "Every subscription accounted for, zero operational friction.",
            )
            topCategory != null && topCategory.category == TransactionCategory.TRAVEL && topCategory.percentageOfSpend >= 20.0 -> FintechPersona(
                title = "The Frontier Explorer",
                emoji = "✈️",
                tagLine = "High-Velocity Urban Nomad",
                punchline = "Your transit passes and odometer are getting quite the workout.",
            )
            netSavingsRate >= 20.0 -> FintechPersona(
                title = "The Disciplined Accumulator",
                emoji = "🌱",
                tagLine = "Steady Wealth Compounder",
                punchline = "Consistently building your net worth month over month.",
            )
            else -> FintechPersona(
                title = "The Steady Navigator",
                emoji = "🧭",
                tagLine = "Balanced Financial Explorer",
                punchline = "Smooth sailing with a balanced, mindful financial compass.",
            )
        }

        return if (!customPunchline.isNullOrBlank()) {
            basePersona.copy(punchline = customPunchline.trim())
        } else {
            basePersona
        }
    }

    /**
     * Executes queries directly against FinanceDatabase and computes the month's FintechWrappedData.
     */
    suspend fun queryMonthlyWrapped(
        database: FinanceDatabase,
        yearMonth: YearMonth = YearMonth.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        nanoPunchlineProvider: (suspend (persona: String, savingsRate: Double, topCategory: String) -> String?)? = null,
    ): FintechWrappedData = withContext(Dispatchers.IO) {
        val startOfMonthMillis = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfMonthMillis = yearMonth.atEndOfMonth().atTime(23, 59, 59, 999_999_999).atZone(zoneId).toInstant().toEpochMilli()

        val transactions = database.transactionDao().getTransactionsBetween(startOfMonthMillis, endOfMonthMillis)

        // Compute baseline without AI punchline first
        val baseline = computeMetrics(transactions, yearMonth, zoneId)

        val topCategoryName = baseline.topCategories.firstOrNull()?.category?.name ?: "General"
        val aiPunchline = try {
            nanoPunchlineProvider?.invoke(
                baseline.persona.title,
                baseline.netSavingsRate,
                topCategoryName,
            )
        } catch (_: Exception) {
            null
        }

        if (!aiPunchline.isNullOrBlank()) {
            baseline.copy(
                persona = baseline.persona.copy(punchline = aiPunchline),
                isAIGeneratedPunchline = true,
            )
        } else {
            baseline
        }
    }
}
