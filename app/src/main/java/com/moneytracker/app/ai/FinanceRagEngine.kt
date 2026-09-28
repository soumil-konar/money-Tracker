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
        val candidates = LinkedHashMap<Long, TransactionRecord>()

        // -------------------------------------------------------------
        // Tier 1: Temporal & Deterministic SQL
        // -------------------------------------------------------------
        val (timeStart, timeEnd) = extractTimeRange(lowerQuery)
        if (timeStart != null && timeEnd != null) {
            runCatching {
                val timeMatches = transactionDao.getTransactionsBetween(timeStart, timeEnd)
                timeMatches.forEach { candidates[it.id] = it }
            }
        }

        // -------------------------------------------------------------
        // Tier 2: FTS5 Substring Search (Trigram Tokenizer)
        // -------------------------------------------------------------
        val ftsQuery = sanitizeForFts5(lowerQuery)
        if (ftsQuery.isNotBlank()) {
            runCatching {
                val ftsMatches = transactionDao.searchTransactionsFts(ftsQuery, limit = 30)
                ftsMatches.forEach { candidates[it.id] = it }
            }
        }

        // -------------------------------------------------------------
        // Tier 3: Semantic Vector Search (Conditional on Cloud API Key)
        // Never fall back to SHA-256 byte mapping. If cloud embedding is
        // unavailable or key is absent, rely on Tier 1 + Tier 2.
        // -------------------------------------------------------------
        if (apiKey.isNotBlank()) {
            runCatching {
                val embeddingResult = geminiApiClient.generateEmbedding(
                    text = query,
                    apiKey = apiKey,
                    outputDimensionality = 256,
                )
                val queryEmbedding = embeddingResult.getOrNull()
                if (queryEmbedding != null && queryEmbedding.isNotEmpty()) {
                    val queryVector = FloatArray(queryEmbedding.size) { queryEmbedding[it] }
                    val storedEmbeddings = embeddingDao.getAll()
                    val scored = storedEmbeddings.mapNotNull { entity ->
                        val vector = entity.toFloatArray()
                        if (vector.isEmpty()) return@mapNotNull null
                        val score = cosineSimilarity(queryVector, vector)
                        entity.transactionId to score
                    }.filter { it.second > 0.45f }
                        .sortedByDescending { it.second }
                        .take(15)

                    if (scored.isNotEmpty()) {
                        val allMap = allPosted.associateBy { it.id }
                        scored.forEach { (txId, _) ->
                            allMap[txId]?.let { candidates[it.id] = it }
                        }
                    }
                }
            }
        }

        // Fallback for general non-targeted inquiries (e.g. "How are my savings?")
        if (candidates.isEmpty()) {
            allPosted.take(15).forEach { candidates[it.id] = it }
        }

        // -------------------------------------------------------------
        // Context Synthesis:
        // Combine unique records from Tier 1, Tier 2, and Tier 3.
        // Compact to top 10-15 records to maintain prompt compactness
        // and eliminate context dilution.
        // -------------------------------------------------------------
        val finalTransactions = candidates.values
            .sortedByDescending { it.occurredAtMillis }
            .take(15)

        // Build Macro Financial Summary
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

    companion object {
        fun sanitizeForFts5(query: String): String {
            val stopWords = setOf(
                "how", "much", "did", "i", "spend", "on", "what", "where", "my", "in", "the",
                "for", "to", "show", "me", "all", "expenses", "transactions", "money", "tracker",
                "at", "from", "was", "were", "a", "an", "and", "or", "of", "about", "with",
                "this", "that", "last", "month", "today", "yesterday", "week", "year",
            )

            val tokens = query.lowercase(Locale.getDefault())
                .split(Regex("[^a-z0-9]+"))
                .map { it.trim() }
                .filter { it.length >= 2 && it !in stopWords }

            if (tokens.isEmpty()) return ""
            // Wrap alphanumeric search terms inside escaped double quotes for FTS5 trigram
            return tokens.take(5).joinToString(" OR ") { "\"$it\"" }
        }
    }
}
