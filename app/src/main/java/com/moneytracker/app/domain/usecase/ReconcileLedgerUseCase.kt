package com.moneytracker.app.domain.usecase

import com.moneytracker.app.bank.BalanceProofVerifier
import com.moneytracker.app.bank.BankDetector
import com.moneytracker.app.data.db.AccountDao
import com.moneytracker.app.data.db.TransactionDao
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus

/**
 * Encapsulates balance proof verification, forward running delta math,
 * and user-anchored baseline true-ups.
 */
class ReconcileLedgerUseCase(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val onWidgetUpdate: (() -> Unit)? = null,
) {

    suspend fun trueUpAccountBalance(
        accountId: Long,
        newBalance: Double,
        reason: String? = null,
        effectiveTimestamp: Long = System.currentTimeMillis(),
    ): Boolean {
        val account = accountDao.findById(accountId) ?: return false
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

        return reconcileSingleAccount(accountId)
    }

    suspend fun reconcileSingleAccount(accountId: Long): Boolean {
        val account = accountDao.findById(accountId) ?: return false
        val anchorTime = account.balanceUpdatedAtMillis ?: 0L
        val allAccountTxs = transactionDao.getPostedTransactionsSince(accountId, anchorTime)
            .sortedWith(compareBy({ it.occurredAtMillis }, { it.id }))

        // 1. Check for the most recent verified bank SMS transaction
        val verifiedAnchorTx = allAccountTxs.lastOrNull { tx ->
            val availBal = tx.availableBalance
            if (availBal != null && availBal > 0.0) {
                val proof = BalanceProofVerifier.verifyBalance(
                    sender = tx.sourceSender,
                    body = tx.smsBody ?: "",
                    occurredAtMillis = tx.occurredAtMillis,
                )
                proof.isVerified
            } else false
        }

        val smsAnchorTime = verifiedAnchorTx?.occurredAtMillis ?: 0L
        val isManualTrueUp = account.balanceProofSource == "Manual True-Up"
        val userTrueUpTime = if (isManualTrueUp) (account.balanceUpdatedAtMillis ?: 0L) else 0L

        // Prioritize manual true-up anchor if user performed it after the latest bank SMS proof
        if (isManualTrueUp && userTrueUpTime > smsAnchorTime) {
            val subsequentTxs = allAccountTxs.filter { it.occurredAtMillis > userTrueUpTime }
            val subsequentDelta = subsequentTxs.sumOf { tx ->
                if (tx.direction == TransactionDirection.CREDIT) tx.amount else -tx.amount
            }
            val calculatedBalance = (account.currentBalance + subsequentDelta).coerceAtLeast(0.0)
            val latestTime = maxOf(userTrueUpTime, subsequentTxs.lastOrNull()?.occurredAtMillis ?: userTrueUpTime)

            accountDao.updateVerifiedBalance(
                accountId = accountId,
                balance = calculatedBalance,
                updatedAt = latestTime,
                proofSnippet = account.balanceProofSnippet ?: "Manual True-Up",
                proofSource = "Manual True-Up",
                isVerified = true,
            )
            onWidgetUpdate?.invoke()
            return true
        }

        if (verifiedAnchorTx != null) {
            val proof = BalanceProofVerifier.verifyBalance(
                sender = verifiedAnchorTx.sourceSender,
                body = verifiedAnchorTx.smsBody ?: "",
                occurredAtMillis = verifiedAnchorTx.occurredAtMillis,
            )
            val anchorBalance = proof.balance ?: verifiedAnchorTx.availableBalance ?: 0.0
            val txAnchorTime = verifiedAnchorTx.occurredAtMillis

            // Sum all subsequent transactions strictly after the anchor snapshot
            val subsequentTxs = allAccountTxs.filter {
                it.occurredAtMillis > txAnchorTime || (it.occurredAtMillis == txAnchorTime && it.id > verifiedAnchorTx.id)
            }
            val subsequentDelta = subsequentTxs.sumOf { tx ->
                if (tx.direction == TransactionDirection.CREDIT) tx.amount else -tx.amount
            }
            val calculatedBalance = (anchorBalance + subsequentDelta).coerceAtLeast(0.0)
            val latestTime = maxOf(txAnchorTime, subsequentTxs.lastOrNull()?.occurredAtMillis ?: txAnchorTime)

            accountDao.updateVerifiedBalance(
                accountId = accountId,
                balance = calculatedBalance,
                updatedAt = latestTime,
                proofSnippet = proof.proofSnippet,
                proofSource = proof.proofSource,
                isVerified = true,
            )
            onWidgetUpdate?.invoke()
            return true
        }

        if (account.isBalanceVerified) {
            val subsequentTxs = allAccountTxs.filter { it.occurredAtMillis > anchorTime }
            if (subsequentTxs.isNotEmpty()) {
                val subsequentDelta = subsequentTxs.sumOf { tx ->
                    if (tx.direction == TransactionDirection.CREDIT) tx.amount else -tx.amount
                }
                val calculatedBalance = (account.currentBalance + subsequentDelta).coerceAtLeast(0.0)
                val latestTime = maxOf(anchorTime, subsequentTxs.lastOrNull()?.occurredAtMillis ?: anchorTime)

                accountDao.updateVerifiedBalance(
                    accountId = accountId,
                    balance = calculatedBalance,
                    updatedAt = latestTime,
                    proofSnippet = account.balanceProofSnippet,
                    proofSource = account.balanceProofSource,
                    isVerified = true,
                )
                onWidgetUpdate?.invoke()
                return true
            }
        }

        // 2. If no bank statement proof exists, check if user manually configured/edited a baseline balance
        if (!account.isBalanceVerified && account.balanceProofSource == "User Entry") {
            val userSetTime = account.balanceUpdatedAtMillis ?: 0L
            val subsequentTxs = allAccountTxs.filter { it.occurredAtMillis > userSetTime }
            val subsequentDelta = subsequentTxs.sumOf { tx ->
                if (tx.direction == TransactionDirection.CREDIT) tx.amount else -tx.amount
            }
            val calculated = (account.currentBalance + subsequentDelta).coerceAtLeast(0.0)
            val latestTime = maxOf(userSetTime, subsequentTxs.lastOrNull()?.occurredAtMillis ?: userSetTime)
            accountDao.updateBalance(account.id, calculated, latestTime)
            onWidgetUpdate?.invoke()
            return true
        }

        // 3. For system-generated accounts without any balance statement proof, calculate net cash flow
        if (account.isSystemGenerated) {
            val netFlow = allAccountTxs.sumOf { tx ->
                if (tx.direction == TransactionDirection.CREDIT) tx.amount else -tx.amount
            }.coerceAtLeast(0.0)
            val latestTime = allAccountTxs.lastOrNull()?.occurredAtMillis ?: System.currentTimeMillis()
            accountDao.updateVerifiedBalance(
                accountId = accountId,
                balance = netFlow,
                updatedAt = latestTime,
                proofSnippet = null,
                proofSource = null,
                isVerified = false,
            )
            onWidgetUpdate?.invoke()
            return true
        }

        return false
    }

    suspend fun reconcileAccountsAndBalances(): Int {
        val existingAccounts = accountDao.getAccounts()
        val allTransactions = transactionDao.getAllTransactions()
        var reconciledCount = 0

        // 1. Identify and purge bogus accounts (system-generated non-banks, e.g. "UPI", "VM-MYNTR", generic merchants)
        for (account in existingAccounts) {
            val isLegitBank = BankDetector.isLegitimateBank(account.institutionName ?: account.name)
            val isBogus = (account.kind == AccountKind.BANK && account.isSystemGenerated && !isLegitBank) ||
                (account.name.equals("UPI", ignoreCase = true) && account.kind == AccountKind.UPI && account.isSystemGenerated)

            if (isBogus) {
                // Re-route transactions to a genuine bank account if possible
                val orphanTxs = allTransactions.filter { it.accountId == account.id }
                for (tx in orphanTxs) {
                    val matchingBank = existingAccounts.firstOrNull {
                        it.id != account.id && it.kind == AccountKind.BANK &&
                            BankDetector.isLegitimateBank(it.institutionName ?: it.name)
                    }
                    transactionDao.update(tx.copy(accountId = matchingBank?.id))
                }
                accountDao.deleteById(account.id)
                reconciledCount++
            }
        }

        // 2. Re-anchor genuine bank accounts with verified substantial proof and calculate subsequent transaction deltas
        val refreshedAccounts = accountDao.getAccounts()
        for (account in refreshedAccounts) {
            if (account.kind == AccountKind.BANK || account.kind == AccountKind.CARD) {
                if (reconcileSingleAccount(account.id)) {
                    reconciledCount++
                }
            }
        }

        onWidgetUpdate?.invoke()
        return reconciledCount
    }
}
