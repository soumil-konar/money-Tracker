package com.soumil.moneytracker.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.CardType
import com.soumil.moneytracker.data.model.ScheduledTransactionKind
import com.soumil.moneytracker.data.model.SubscriptionState
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import com.soumil.moneytracker.data.model.TransactionStatus

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: AccountKind,
    val institutionName: String? = null,
    val cardType: CardType? = null,
    val lastFourDigits: String? = null,
    val isRupayCreditCard: Boolean = false,
    val isSystemGenerated: Boolean = false,
    val currentBalance: Double = 0.0,
    val balanceUpdatedAtMillis: Long? = null,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["occurredAtMillis"]),
        Index(value = ["status"]),
        Index(value = ["accountId"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val direction: TransactionDirection,
    val occurredAtMillis: Long,
    val merchant: String,
    val category: TransactionCategory,
    val accountId: Long?,
    val sourceSender: String,
    val smsBody: String?,
    val confidence: Double,
    val fingerprint: String,
    val status: TransactionStatus,
    val note: String? = null,
    val countsTowardBudget: Boolean = true,
    val availableBalance: Double? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Fts4(contentEntity = TransactionEntity::class)
@Entity(tableName = "transactions_fts")
data class TransactionFtsEntity(
    val merchant: String,
    val note: String?,
    val sourceSender: String,
    val smsBody: String?,
)

@Entity(
    tableName = "transaction_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["transactionId"], unique = true),
    ],
)
data class TransactionEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val documentText: String,
    val embeddingCsv: String,
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun toFloatArray(): FloatArray {
        if (embeddingCsv.isBlank()) return FloatArray(0)
        val tokens = embeddingCsv.split(",")
        val result = FloatArray(tokens.size)
        for (i in tokens.indices) {
            result[i] = tokens[i].toFloatOrNull() ?: 0f
        }
        return result
    }

    companion object {
        fun fromFloatList(transactionId: Long, documentText: String, floats: List<Float>): TransactionEmbeddingEntity {
            return TransactionEmbeddingEntity(
                transactionId = transactionId,
                documentText = documentText,
                embeddingCsv = floats.joinToString(","),
            )
        }
    }
}

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["monthKey", "category"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val monthKey: String,
    val category: TransactionCategory?,
    val amountLimit: Double,
)

@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["merchant"], unique = true)],
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val amount: Double,
    val billingCycleDays: Int,
    val nextDueAtMillis: Long,
    val accountId: Long?,
    val state: SubscriptionState,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "scheduled_transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["scheduledForMillis"]),
        Index(value = ["accountId"]),
    ],
)
data class ScheduledTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val amount: Double,
    val scheduledForMillis: Long,
    val category: TransactionCategory,
    val accountId: Long?,
    val sourceSender: String,
    val smsBody: String?,
    val kind: ScheduledTransactionKind,
    val fingerprint: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

data class TransactionRecord(
    val id: Long,
    val amount: Double,
    val direction: TransactionDirection,
    val occurredAtMillis: Long,
    val merchant: String,
    val category: TransactionCategory,
    val accountId: Long?,
    val sourceSender: String,
    val smsBody: String?,
    val confidence: Double,
    val status: TransactionStatus,
    val note: String?,
    val countsTowardBudget: Boolean,
    val accountName: String?,
    val accountKind: AccountKind?,
    val availableBalance: Double? = null,
)

data class ScheduledTransactionRecord(
    val id: Long,
    val merchant: String,
    val amount: Double,
    val scheduledForMillis: Long,
    val category: TransactionCategory,
    val kind: ScheduledTransactionKind,
    val sourceSender: String,
    val accountName: String?,
    val accountKind: AccountKind?,
)

data class SubscriptionRecord(
    val id: Long,
    val merchant: String,
    val amount: Double,
    val billingCycleDays: Int,
    val nextDueAtMillis: Long,
    val state: SubscriptionState,
    val accountName: String?,
)

class FinanceTypeConverters {
    @TypeConverter
    fun fromAccountKind(value: AccountKind): String = value.name

    @TypeConverter
    fun toAccountKind(value: String): AccountKind = AccountKind.valueOf(value)

    @TypeConverter
    fun fromCardType(value: CardType?): String? = value?.name

    @TypeConverter
    fun toCardType(value: String?): CardType? = value?.let(CardType::valueOf)

    @TypeConverter
    fun fromTransactionDirection(value: TransactionDirection): String = value.name

    @TypeConverter
    fun toTransactionDirection(value: String): TransactionDirection = TransactionDirection.valueOf(value)

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus = TransactionStatus.valueOf(value)

    @TypeConverter
    fun fromTransactionCategory(value: TransactionCategory?): String? = value?.name

    @TypeConverter
    fun toTransactionCategory(value: String?): TransactionCategory? = value?.let(TransactionCategory::valueOf)

    @TypeConverter
    fun fromSubscriptionState(value: SubscriptionState): String = value.name

    @TypeConverter
    fun toSubscriptionState(value: String): SubscriptionState = SubscriptionState.valueOf(value)

    @TypeConverter
    fun fromScheduledTransactionKind(value: ScheduledTransactionKind): String = value.name

    @TypeConverter
    fun toScheduledTransactionKind(value: String): ScheduledTransactionKind = ScheduledTransactionKind.valueOf(value)
}

