package com.soumil.moneytracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.soumil.moneytracker.data.model.TransactionDirection
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    suspend fun getAccounts(): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun countAccounts(): Int

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    suspend fun findById(accountId: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("""
        UPDATE accounts 
        SET currentBalance = :balance, 
            balanceUpdatedAtMillis = :updatedAt,
            balanceProofSnippet = :proofSnippet,
            balanceProofSource = :proofSource,
            isBalanceVerified = :isVerified
        WHERE id = :accountId 
          AND (:updatedAt >= balanceUpdatedAtMillis OR balanceUpdatedAtMillis IS NULL)
    """)
    suspend fun updateVerifiedBalance(
        accountId: Long,
        balance: Double,
        updatedAt: Long,
        proofSnippet: String?,
        proofSource: String?,
        isVerified: Boolean = true,
    ): Int

    @Query("""
        UPDATE accounts 
        SET currentBalance = :balance, 
            balanceUpdatedAtMillis = :updatedAt 
        WHERE id = :accountId 
          AND (:updatedAt >= balanceUpdatedAtMillis OR balanceUpdatedAtMillis IS NULL)
    """)
    suspend fun updateBalance(accountId: Long, balance: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE accounts SET currentBalance = currentBalance + :delta WHERE id = :accountId")
    suspend fun adjustBalance(accountId: Long, delta: Double)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteById(accountId: Long)
}

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
    fun observeTransactions(): Flow<List<TransactionRecord>>

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE t.status = 'POSTED'
        ORDER BY t.occurredAtMillis DESC
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

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, t.countsTowardBudget, t.availableBalance,
               a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        JOIN transactions_fts f ON t.id = f.docid
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE transactions_fts MATCH :matchQuery
        ORDER BY t.occurredAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun searchTransactionsFts(matchQuery: String, limit: Int = 50): List<TransactionRecord>

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
}

@Dao
interface TransactionEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: TransactionEmbeddingEntity): Long

    @Query("SELECT * FROM transaction_embeddings WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getByTransactionId(transactionId: Long): TransactionEmbeddingEntity?

    @Query("SELECT * FROM transaction_embeddings")
    suspend fun getAll(): List<TransactionEmbeddingEntity>

    @Query("SELECT COUNT(*) FROM transaction_embeddings")
    suspend fun count(): Int

    @Query(
        """
        SELECT t.id FROM transactions t
        LEFT JOIN transaction_embeddings e ON t.id = e.transactionId
        WHERE e.id IS NULL
        ORDER BY t.occurredAtMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getUnembeddedTransactionIds(limit: Int = 50): List<Long>
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey AND category IS NULL LIMIT 1")
    fun observeOverallBudget(monthKey: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey AND category IS NULL LIMIT 1")
    suspend fun getOverallBudget(monthKey: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE category IS NULL ORDER BY monthKey DESC")
    fun observeOverallBudgets(): Flow<List<BudgetEntity>>

    @Query("DELETE FROM budgets WHERE monthKey = :monthKey AND category IS NULL")
    suspend fun deleteOverallBudget(monthKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity)

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>
}

@Dao
interface SubscriptionDao {
    @Query(
        """
        SELECT s.id, s.merchant, s.amount, s.billingCycleDays, s.nextDueAtMillis, s.state,
               a.name AS accountName
        FROM subscriptions s
        LEFT JOIN accounts a ON s.accountId = a.id
        ORDER BY s.state ASC, s.nextDueAtMillis ASC
        """,
    )
    fun observeSubscriptions(): Flow<List<SubscriptionRecord>>

    @Query("SELECT * FROM subscriptions WHERE merchant = :merchant LIMIT 1")
    suspend fun findByMerchant(merchant: String): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :subscriptionId")
    suspend fun deleteById(subscriptionId: Long)

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<SubscriptionEntity>
}

@Dao
interface ScheduledTransactionDao {
    @Query(
        """
        SELECT s.id, s.merchant, s.amount, s.scheduledForMillis, s.category, s.kind, s.sourceSender,
               a.name AS accountName, a.kind AS accountKind
        FROM scheduled_transactions s
        LEFT JOIN accounts a ON s.accountId = a.id
        ORDER BY s.scheduledForMillis ASC, s.createdAtMillis DESC
        """,
    )
    fun observeScheduledTransactions(): Flow<List<ScheduledTransactionRecord>>

    @Query("SELECT COUNT(*) > 0 FROM scheduled_transactions WHERE fingerprint = :fingerprint")
    suspend fun fingerprintExists(fingerprint: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: ScheduledTransactionEntity): Long

    @Query("SELECT * FROM scheduled_transactions")
    suspend fun getAll(): List<ScheduledTransactionEntity>
}
