package com.moneytracker.app.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Update
import com.moneytracker.app.data.model.TransactionDirection
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        ORDER BY t.occurredAtMillis DESC
        """,
    )
    fun getPagedTransactions(): PagingSource<Int, TransactionRecord>

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE t.accountId = :accountId AND t.availableBalance IS NOT NULL
        ORDER BY t.occurredAtMillis DESC, t.id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestBalanceAnchor(accountId: Long): TransactionRecord?

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE t.accountId = :accountId AND t.status = 'POSTED' AND t.occurredAtMillis >= :anchorTimeMillis
        ORDER BY t.occurredAtMillis ASC
        """,
    )
    suspend fun getPostedTransactionsSince(accountId: Long, anchorTimeMillis: Long): List<TransactionRecord>

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        ORDER BY t.occurredAtMillis DESC, t.id DESC
        """,
    )
    fun observeTransactions(): Flow<List<TransactionRecord>>

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        ORDER BY t.occurredAtMillis DESC, t.id DESC
        """,
    )
    fun pagedTransactions(): PagingSource<Int, TransactionRecord>

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE (:accountId IS NULL OR t.accountId = :accountId)
          AND (:direction IS NULL OR t.direction = :direction)
          AND (:searchQuery = '' OR t.merchant LIKE '%' || :searchQuery || '%' OR t.note LIKE '%' || :searchQuery || '%')
        ORDER BY t.occurredAtMillis DESC, t.id DESC
        """,
    )
    fun pagedFilteredTransactions(
        accountId: Long?,
        direction: String?,
        searchQuery: String,
    ): PagingSource<Int, TransactionRecord>

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE t.status = 'POSTED'
        ORDER BY t.occurredAtMillis DESC, t.id DESC
        """,
    )
    fun observePostedTransactions(): Flow<List<TransactionRecord>>

    @Query("SELECT COUNT(*) > 0 FROM transactions WHERE fingerprint = :fingerprint")
    suspend fun fingerprintExists(fingerprint: String): Boolean

    @Query(
        """
        SELECT * FROM transactions
        WHERE amount = :amount
          AND direction = :direction
          AND ABS(occurredAtMillis - :occurredAtMillis) <= :timeToleranceMillis
        LIMIT 1
        """,
    )
    suspend fun findSimilarTransaction(
        amount: Double,
        direction: TransactionDirection,
        occurredAtMillis: Long,
        timeToleranceMillis: Long = 180_000L,
    ): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY occurredAtMillis DESC, id DESC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE occurredAtMillis >= :sinceMillis ORDER BY occurredAtMillis DESC")
    suspend fun getRecentTransactions(sinceMillis: Long): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("UPDATE transactions SET status = :status WHERE id = :transactionId")
    suspend fun updateStatus(transactionId: Long, status: String)

    @Query("UPDATE transactions SET countsTowardBudget = :countsTowardBudget WHERE id = :transactionId")
    suspend fun updateBudgetInclusion(transactionId: Long, countsTowardBudget: Boolean)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteById(transactionId: Long)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getById(transactionId: Long): TransactionEntity?

    @SkipQueryVerification
    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        JOIN transactions_fts f ON t.id = f.rowid
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE transactions_fts MATCH :matchQuery
        ORDER BY t.occurredAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun queryTransactionsFtsRaw(matchQuery: String, limit: Int = 50): List<TransactionRecord>

    suspend fun searchTransactionsFts(matchQuery: String, limit: Int = 50): List<TransactionRecord> {
        val sanitized = sanitizeFts5Query(matchQuery)
        if (sanitized.isBlank()) return emptyList()
        return try {
            queryTransactionsFtsRaw(sanitized, limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        fun sanitizeFts5Query(rawQuery: String): String {
            if (rawQuery.isBlank()) return ""
            if (rawQuery.contains(Regex("""\bOR\b""", RegexOption.IGNORE_CASE))) {
                val parts = rawQuery.split(Regex("""\bOR\b""", RegexOption.IGNORE_CASE))
                val sanitizedParts = parts.map { sanitizeClause(it) }.filter { it.isNotBlank() }
                if (sanitizedParts.isEmpty()) return ""
                return sanitizedParts.joinToString(" OR ")
            }
            return sanitizeClause(rawQuery)
        }

        private fun sanitizeClause(clause: String): String {
            val clean = clause.replace(Regex("""[^\p{L}\p{N}\s]"""), " ").trim()
            val tokens = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return ""
            return tokens.joinToString(" AND ") { "\"$it\"" }
        }
    }

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE t.occurredAtMillis BETWEEN :startMillis AND :endMillis
        ORDER BY t.occurredAtMillis DESC
        """,
    )
    suspend fun getTransactionsBetween(startMillis: Long, endMillis: Long): List<TransactionRecord>

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE t.status = 'POSTED'
        ORDER BY t.occurredAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentPostedTransactions(limit: Int = 50): List<TransactionRecord>

    @Query("SELECT occurredAtMillis FROM transactions WHERE status = 'POSTED'")
    suspend fun getAllPostedTransactionTimestamps(): List<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0)
        FROM transactions
        WHERE status = 'POSTED'
          AND direction = 'DEBIT'
          AND countsTowardBudget = 1
          AND category != 'TRANSFER'
          AND occurredAtMillis BETWEEN :startMillis AND :endMillis
        """,
    )
    fun observeMonthlySpent(startMillis: Long, endMillis: Long): Flow<Double>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0)
        FROM transactions
        WHERE status = 'POSTED'
          AND direction = 'CREDIT'
          AND countsTowardBudget = 1
          AND category != 'TRANSFER'
          AND occurredAtMillis BETWEEN :startMillis AND :endMillis
        """,
    )
    fun observeMonthlyIncome(startMillis: Long, endMillis: Long): Flow<Double>

    @Query(
        """
        SELECT category, COALESCE(SUM(amount), 0.0) AS totalAmount
        FROM transactions
        WHERE status = 'POSTED'
          AND direction = 'DEBIT'
          AND countsTowardBudget = 1
          AND category != 'TRANSFER'
          AND occurredAtMillis BETWEEN :startMillis AND :endMillis
        GROUP BY category
        ORDER BY totalAmount DESC
        """,
    )
    fun observeCategorySpendBreakdown(startMillis: Long, endMillis: Long): Flow<List<CategorySpendAggregate>>

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE t.status = 'POSTED'
        ORDER BY t.occurredAtMillis DESC, t.id DESC
        LIMIT :limit
        """,
    )
    fun observeRecentPostedTransactions(limit: Int): Flow<List<TransactionRecord>>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'REVIEW'")
    fun observeReviewCount(): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(t.amount), 0.0)
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE t.status = 'POSTED'
          AND t.direction = 'DEBIT'
          AND t.countsTowardBudget = 1
          AND t.category != 'TRANSFER'
          AND a.kind = 'CARD'
          AND t.occurredAtMillis BETWEEN :startMillis AND :endMillis
        """,
    )
    fun observeCardSpend(startMillis: Long, endMillis: Long): Flow<Double>
}
