package com.moneytracker.app.domain.usecase

import com.moneytracker.app.ai.GeminiApiClient
import com.moneytracker.app.ai.OnDeviceAiEngine
import com.moneytracker.app.data.db.BudgetDao
import com.moneytracker.app.data.db.ScheduledTransactionDao
import com.moneytracker.app.data.db.ScheduledTransactionEntity
import com.moneytracker.app.data.db.TransactionDao
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.local.AiEngineMode
import com.moneytracker.app.data.local.AiPreferences
import com.moneytracker.app.data.model.DailyRecapData
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

import kotlinx.coroutines.flow.first

/**
 * Coordinates anomaly detection, contextual nudges, spending velocity warnings,
 * and Gemini LLM synthesis.
 */
class GenerateSpendingInsightsUseCase(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val scheduledTransactionDao: ScheduledTransactionDao,
    private val aiPreferences: AiPreferences,
    private val onDeviceAiEngine: OnDeviceAiEngine,
    private val geminiApiClient: GeminiApiClient,
) {

    suspend fun generateSpendingInsights(): Result<List<String>> {
        val posted = transactionDao.observePostedTransactions().first()
        val currentMonth = YearMonth.now()
        val currentMonthTransactions = posted.filter {
            val localDate = Instant.ofEpochMilli(it.occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            YearMonth.from(localDate) == currentMonth
        }
        val budget = budgetDao.getOverallBudget(currentMonth.toString())?.amountLimit
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

        return generateSpendingInsights(
            currentMonthTransactions = currentMonthTransactions,
            budgetLimit = budget,
            monthSpent = monthSpent,
            monthIncome = monthIncome,
        )
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

    suspend fun generateSpendingInsights(
        currentMonthTransactions: List<TransactionRecord>,
        budgetLimit: Double?,
        monthSpent: Double,
        monthIncome: Double,
    ): Result<List<String>> {
        val apiKey = aiPreferences.apiKey.value
        val engineMode = aiPreferences.engineMode.value

        return when (engineMode) {
            AiEngineMode.ON_DEVICE_ONLY -> {
                Result.success(
                    onDeviceAiEngine.generateSpendingInsightsOnDevice(
                        transactions = currentMonthTransactions,
                        budgetLimit = budgetLimit,
                        monthSpent = monthSpent,
                        monthIncome = monthIncome,
                    ),
                )
            }
            AiEngineMode.AUTO_PIXEL_FIRST -> {
                if (apiKey.isNotBlank()) {
                    geminiApiClient.generateSpendingInsights(
                        transactions = currentMonthTransactions,
                        budgetLimit = budgetLimit,
                        monthSpent = monthSpent,
                        monthIncome = monthIncome,
                        apiKey = apiKey,
                        model = aiPreferences.selectedModel.value,
                    ).recoverCatching {
                        // Graceful fallback to on-device engine if cloud request fails
                        onDeviceAiEngine.generateSpendingInsightsOnDevice(
                            transactions = currentMonthTransactions,
                            budgetLimit = budgetLimit,
                            monthSpent = monthSpent,
                            monthIncome = monthIncome,
                        )
                    }
                } else {
                    // Offline or device without cloud API key
                    Result.success(
                        onDeviceAiEngine.generateSpendingInsightsOnDevice(
                            transactions = currentMonthTransactions,
                            budgetLimit = budgetLimit,
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
                        budgetLimit = budgetLimit,
                        monthSpent = monthSpent,
                        monthIncome = monthIncome,
                        apiKey = apiKey,
                        model = aiPreferences.selectedModel.value,
                    )
                }
            }
        }
    }

    suspend fun getDailyRecapData(referenceMillis: Long = System.currentTimeMillis()): DailyRecapData {
        val zone = ZoneId.systemDefault()
        val localDate = Instant.ofEpochMilli(referenceMillis).atZone(zone).toLocalDate()
        val startOfDay = localDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = localDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val todayTransactions = transactionDao.getTransactionsBetween(startOfDay, endOfDay)
        val todayDebits = todayTransactions.filter {
            it.direction == TransactionDirection.DEBIT && it.status == TransactionStatus.POSTED
        }
        val spentToday = todayDebits.sumOf { it.amount }
        val txCount = todayDebits.size

        val currentMonthKey = YearMonth.from(localDate).toString()
        val budget = budgetDao.getOverallBudget(currentMonthKey)
        val monthStart = YearMonth.from(localDate).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = YearMonth.from(localDate).atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        val monthTransactions = transactionDao.getTransactionsBetween(monthStart, monthEnd)
        val monthSpent = monthTransactions.filter {
            it.direction == TransactionDirection.DEBIT && it.countsTowardBudget && it.category != TransactionCategory.TRANSFER && it.status == TransactionStatus.POSTED
        }.sumOf { it.amount }

        val remainingBuffer = budget?.let { (it.amountLimit - monthSpent).coerceAtLeast(0.0) } ?: 0.0

        return DailyRecapData(
            spentToday = spentToday,
            txCount = txCount,
            remainingBuffer = remainingBuffer,
        )
    }

    suspend fun getUpcomingBillReminders(minDueMillis: Long, maxDueMillis: Long): List<ScheduledTransactionEntity> {
        return scheduledTransactionDao.getUpcomingUnpaidBillReminders(minDueMillis, maxDueMillis)
    }

    suspend fun getDistinctTransactionDaysCount(): Int {
        val timestamps = transactionDao.getAllPostedTransactionTimestamps()
        val zone = ZoneId.systemDefault()
        return timestamps.map {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
        }.distinct().size
    }
}
