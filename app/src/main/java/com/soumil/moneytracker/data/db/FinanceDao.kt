package com.soumil.moneytracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long
}

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        ORDER BY t.occurredAtMillis DESC
        """,
    )
    fun observeTransactions(): Flow<List<TransactionRecord>>

    @Query(
        """
        SELECT t.id, t.amount, t.direction, t.occurredAtMillis, t.merchant, t.category, t.accountId, t.sourceSender,
               t.smsBody, t.confidence, t.status, t.note, a.name AS accountName, a.kind AS accountKind
        FROM transactions t
        LEFT JOIN accounts a ON t.accountId = a.id
        WHERE t.status = 'POSTED'
        ORDER BY t.occurredAtMillis DESC
        """,
    )
    fun observePostedTransactions(): Flow<List<TransactionRecord>>

    @Query("SELECT COUNT(*) > 0 FROM transactions WHERE fingerprint = :fingerprint")
    suspend fun fingerprintExists(fingerprint: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("UPDATE transactions SET status = :status WHERE id = :transactionId")
    suspend fun updateStatus(transactionId: Long, status: String)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteById(transactionId: Long)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getById(transactionId: Long): TransactionEntity?
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey AND category IS NULL LIMIT 1")
    fun observeOverallBudget(monthKey: String): Flow<BudgetEntity?>

    @Query("DELETE FROM budgets WHERE monthKey = :monthKey AND category IS NULL")
    suspend fun deleteOverallBudget(monthKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity)
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
