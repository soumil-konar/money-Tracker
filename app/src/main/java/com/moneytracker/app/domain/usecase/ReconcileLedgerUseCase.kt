package com.moneytracker.app.domain.usecase

import androidx.room.withTransaction
import com.moneytracker.app.data.db.AccountDao
import com.moneytracker.app.data.db.AccountEntity
import com.moneytracker.app.data.db.FinanceDatabase
import com.moneytracker.app.data.db.TransactionDao
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.db.balanceSnapshot
import com.moneytracker.app.data.db.initialBalance
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.TransactionDirection

class ReconcileLedgerUseCase(
    private val database: FinanceDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val onWidgetUpdate: (() -> Unit)? = null,
) {

    suspend operator fun invoke(accountId: Long): Double = database.withTransaction {
        val account = accountDao.findById(accountId) ?: return@withTransaction 0.0
        val anchor = transactionDao.getLatestBalanceAnchor(accountId)
        val anchorTime = anchor?.occurredAtMillis ?: 0L
        val deltaRecords = transactionDao.getPostedTransactionsSince(accountId, anchorTime)
        val baseBalance = anchor?.balanceSnapshot ?: account.initialBalance
        val calculatedBalance = deltaRecords
            .filter { it.id != anchor?.id }
            .fold(baseBalance) { current, tx ->
                when (tx.direction) {
                    TransactionDirection.CREDIT -> current + tx.amount
                    TransactionDirection.DEBIT -> current - tx.amount
                }
            }
            .coerceAtLeast(0.0)

        accountDao.updateBalance(accountId, calculatedBalance)
        onWidgetUpdate?.invoke()
        calculatedBalance
    }

    suspend fun reconcileSingleAccount(accountId: Long): Boolean {
        invoke(accountId)
        return true
    }

    suspend fun reconcileAccountsAndBalances(): Int {
        val accounts = accountDao.getAccounts()
        var reconciledCount = 0
        for (account in accounts) {
            if (account.kind == AccountKind.BANK || account.kind == AccountKind.CARD) {
                invoke(account.id)
                reconciledCount++
            }
        }
        onWidgetUpdate?.invoke()
        return reconciledCount
    }

    suspend fun trueUpAccountBalance(
        accountId: Long,
        newBalance: Double,
        reason: String? = null,
        effectiveTimestamp: Long = System.currentTimeMillis(),
    ): Boolean {
        val cleanBalance = newBalance.coerceAtLeast(0.0)
        val note = if (!reason.isNullOrBlank()) reason.trim() else "User verified baseline balance"
        accountDao.updateVerifiedBalance(
            accountId = accountId,
            balance = cleanBalance,
            updatedAt = effectiveTimestamp,
            proofSnippet = "Manual True-Up: $note",
            proofSource = "Manual True-Up",
            isVerified = true,
        )
        invoke(accountId)
        return true
    }
}
