package com.moneytracker.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.CardType
import com.moneytracker.app.data.model.ScheduledTransactionKind
import com.moneytracker.app.data.model.SubscriptionState
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus

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
    val balanceProofSnippet: String? = null,
    val balanceProofSource: String? = null,
    val isBalanceVerified: Boolean = false,
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

val TransactionRecord.isAtmWithdrawal: Boolean
    get() = direction == TransactionDirection.DEBIT &&
        (merchant.contains("ATM", ignoreCase = true) ||
            note?.contains("ATM", ignoreCase = true) == true ||
            smsBody?.contains("ATM", ignoreCase = true) == true ||
            smsBody?.contains("WITHDRAWAL", ignoreCase = true) == true)

val TransactionRecord.isTransferredToCash: Boolean
    get() = category == TransactionCategory.TRANSFER &&
        (note?.contains("Cash in Hand", ignoreCase = true) == true ||
            merchant.contains("Cash in Hand", ignoreCase = true))

val TransactionRecord.canTransferToCash: Boolean
    get() = isAtmWithdrawal && !isTransferredToCash && category != TransactionCategory.TRANSFER

val TransactionRecord.parseEngine: String
    get() = when {
        note?.contains("[Engine: GEMINI_NANO]") == true -> "GEMINI_NANO"
        note?.contains("[Engine: GEMINI_CLOUD]") == true -> "GEMINI_CLOUD"
        note?.contains("[Engine: DEGRADED_MANUAL]") == true -> "DEGRADED_MANUAL"
        note?.contains("[Engine: LOCAL_REGEX]") == true -> "LOCAL_REGEX"
        else -> "LOCAL_REGEX"
    }

val TransactionRecord.cleanNote: String?
    get() = note?.replace(Regex("""\s*\[Engine:\s*[A-Z_]+\]"""), "")?.trim()?.takeIf { it.isNotEmpty() }

data class ScheduledTransactionRecord(
    val id: Long,
    val merchant: String,
    val amount: Double,
    val scheduledForMillis: Long,
    val category: TransactionCategory,
    val kind: ScheduledTransactionKind,
    val sourceSender: String,
    val smsBody: String?,
    val isPaid: Boolean,
    val paidAtMillis: Long?,
    val matchedTransactionId: Long?,
    val requiresConfirmation: Boolean,
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

data class CategorySpendAggregate(
    val category: String,
    val totalAmount: Double,
)
