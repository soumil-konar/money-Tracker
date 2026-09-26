package com.moneytracker.app.ai

import com.moneytracker.app.data.db.TransactionDao
import com.moneytracker.app.data.db.TransactionEmbeddingDao
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.sqrt

data class RetrievedContext(
    val transactions: List<TransactionRecord>,
    val macroSummary: String,
)

class FinanceRagEngine(
    private val transactionDao: TransactionDao,
    private val embeddingDao: TransactionEmbeddingDao,
    private val geminiApiClient: GeminiApiClient,
    private val onDeviceAiEngine: OnDeviceAiEngine,
) {

    suspend fun retrieveContext(
        query: String,
        apiKey: String,
        currentBudgetLimit: Double?,
        allPosted: List<TransactionRecord>,
    ): RetrievedContext {
        val lowerQuery = query.lowercase(Locale.getDefault())

        // 1. Extract Temporal Range
        val (timeStart, timeEnd) = extractTimeRange(lowerQuery)

        // 2. Candidate collection
        val candidates = LinkedHashMap<Long, TransactionRecord>()

        // 2a. Time window candidates
        if (timeStart != null && timeEnd != null) {
            val timeMatches = transactionDao.getTransactionsBetween(timeStart, timeEnd)
            timeMatches.forEach { candidates[it.id] = it }
        }

        // 2b. Full-Text Search (FTS) Keyword matches
        val ftsQuery = sanitizeForFts(lowerQuery)
        if (ftsQuery.isNotBlank()) {
            runCatching {
                val ftsMatches = transactionDao.searchTransactionsFts(ftsQuery, limit = 40)
                ftsMatches.forEach { candidates[it.id] = it }
            }
        }

        // 2c. Semantic Vector Search (Cloud or On-Device via Tensor G4)
        runCatching {
            val queryEmbedding: List<Float>? = if (apiKey.isNotBlank()) {
                geminiApiClient.generateEmbedding(
                    text = query,
                    apiKey = apiKey,
                    outputDimensionality = 256,
                ).getOrNull() ?: onDeviceAiEngine.generateEmbeddingOnDevice(query).getOrNull()
            } else {
                onDeviceAiEngine.generateEmbeddingOnDevice(query).getOrNull()
            }

            val queryVector = queryEmbedding?.let { list -> FloatArray(list.size) { list[it] } }
            if (queryVector != null && queryVector.isNotEmpty()) {
                val storedEmbeddings = embeddingDao.getAll()
                val scored = storedEmbeddings.mapNotNull { entity ->
                    val vector = entity.toFloatArray()
                    if (vector.isEmpty()) return@mapNotNull null
                    val score = cosineSimilarity(queryVector, vector)
                    entity.transactionId to score
                }.filter { it.second > 0.45 }
                    .sortedByDescending { it.second }
                    .take(25)

                if (scored.isNotEmpty()) {
                    val allMap = allPosted.associateBy { it.id }
                    scored.forEach { (txId, _) ->
                        allMap[txId]?.let { candidates[it.id] = it }
                    }
                }
            }
        }

        // 2d. Fallback if candidates are still empty (e.g. general questions like "How are my savings?")
        if (candidates.isEmpty()) {
            allPosted.take(30).forEach { candidates[it.id] = it }
        }

        // Sort candidates: prioritize time relevancy and amount
        val finalTransactions = candidates.values
            .sortedByDescending { it.occurredAtMillis }
            .take(30)

        // 3. Build Macro Financial Summary
        val currentMonth = YearMonth.now()
        val currentMonthTxs = allPosted.filter {
            YearMonth.from(it.toLocalDate()) == currentMonth
        }
        val monthSpent = currentMonthTxs
            .filter { it.direction == TransactionDirection.DEBIT && it.category != TransactionCategory.TRANSFER && it.countsTowardBudget }
            .sumOf(TransactionRecord::amount)
        val monthIncome = currentMonthTxs
            .filter { it.direction == TransactionDirection.CREDIT }
            .sumOf(TransactionRecord::amount)
        val totalTrackedBalance = allPosted.sumOf {
            if (it.direction == TransactionDirection.CREDIT) it.amount else -it.amount
        }

        val topCategories = currentMonthTxs
            .filter { it.direction == TransactionDirection.DEBIT && it.category != TransactionCategory.TRANSFER }
            .groupBy { it.category }
            .mapValues { it.value.sumOf(TransactionRecord::amount) }
            .entries
            .sortedByDescending { it.value }
            .take(4)
            .joinToString(", ") { "${it.key.label}: ₹${it.value.toInt()}" }

        val macroSummary = """
            - Current Month: ${currentMonth.month.name} ${currentMonth.year}
            - Total Spent This Month: ₹$monthSpent
            - Total Income This Month: ₹$monthIncome
            - Monthly Budget Limit: ${currentBudgetLimit?.let { "₹$it" } ?: "Not set"}
            - Overall Tracked Balance: ₹$totalTrackedBalance
            - Top Spend Categories This Month: ${topCategories.ifBlank { "None recorded yet" }}
        """.trimIndent()

        return RetrievedContext(
            transactions = finalTransactions,
            macroSummary = macroSummary,
        )
    }

    private fun extractTimeRange(query: String): Pair<Long?, Long?> {
        val now = LocalDate.now()
        val zone = ZoneId.systemDefault()

        return when {
            "this month" in query || "current month" in query -> {
                val start = now.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = System.currentTimeMillis()
                start to end
            }
            "last month" in query || "previous month" in query -> {
                val prev = now.minusMonths(1)
                val start = prev.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = prev.withDayOfMonth(prev.lengthOfMonth()).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                start to end
            }
            "today" in query -> {
                val start = now.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = System.currentTimeMillis()
                start to end
            }
            "yesterday" in query -> {
                val yest = now.minusDays(1)
                val start = yest.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = yest.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                start to end
            }
            "last 7 days" in query || "this week" in query || "past week" in query -> {
                val start = now.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = System.currentTimeMillis()
                start to end
            }
            "last 30 days" in query || "past 30 days" in query -> {
                val start = now.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = System.currentTimeMillis()
                start to end
            }
            else -> null to null
        }
    }

    private fun sanitizeForFts(query: String): String {
        val stopWords = setOf(
            "how", "much", "did", "i", "spend", "on", "what", "where", "my", "in", "the",
            "for", "to", "show", "me", "all", "expenses", "transactions", "money", "tracker",
            "at", "from", "was", "were", "a", "an", "and", "or", "of", "about", "with",
            "this", "that", "last", "month", "today", "yesterday", "week", "year",
        )

        val tokens = query.lowercase(Locale.getDefault())
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length > 2 && it !in stopWords }

        if (tokens.isEmpty()) return ""
        // Form SQLite FTS prefix match: e.g. "swiggy* OR bangalore* OR food*"
        return tokens.take(4).joinToString(" OR ") { "$it*" }
    }

    private fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in vecA.indices) {
            val a = vecA[i]
            val b = vecB[i]
            dot += a * b
            normA += a * a
            normB += b * b
        }
        if (normA <= 0f || normB <= 0f) return 0f
        return dot / (sqrt(normA) * sqrt(normB))
    }

    private fun TransactionRecord.toLocalDate(): LocalDate {
        return java.time.Instant.ofEpochMilli(occurredAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
}
