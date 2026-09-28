package com.moneytracker.app.domain.usecase

import com.moneytracker.app.data.db.AccountDao
import com.moneytracker.app.data.db.TransactionDao
import com.moneytracker.app.data.db.TransactionEntity
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import kotlin.math.abs

/**
 * Scans and correlates simultaneous debit/credit transaction pairs within temporal windows,
 * updating countsTowardBudget = false to eliminate budget distortion.
 */
class DetectTransferPairsUseCase(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
) {

    suspend fun detectAndLinkTransferPair(targetTxId: Long): Boolean {
        val targetTx = transactionDao.getById(targetTxId) ?: return false
        val targetAccountId = targetTx.accountId ?: return false
        val targetAmount = targetTx.amount

        if (targetAmount <= 0.0) return false

        val accounts = accountDao.getAccounts().associateBy { it.id }
        if (!accounts.containsKey(targetAccountId)) return false

        val oppositeDirection = if (targetTx.direction == TransactionDirection.DEBIT) {
            TransactionDirection.CREDIT
        } else {
            TransactionDirection.DEBIT
        }

        val windowMillis = 5 * 60 * 1000L // 5 minutes window
        val minTime = targetTx.occurredAtMillis - windowMillis
        val maxTime = targetTx.occurredAtMillis + windowMillis

        val candidates = transactionDao.getAllTransactions().filter { candidate ->
            candidate.id != targetTx.id &&
                candidate.accountId != null &&
                candidate.accountId != targetAccountId &&
                candidate.direction == oppositeDirection &&
                abs(candidate.amount - targetAmount) < 0.01 &&
                candidate.occurredAtMillis in minTime..maxTime &&
                candidate.status == TransactionStatus.POSTED
        }

        if (candidates.isEmpty()) return false

        val targetRef = extractTransactionReference(targetTx.smsBody)

        val bestCandidate = candidates.firstOrNull { candidate ->
            val candidateRef = extractTransactionReference(candidate.smsBody)
            if (targetRef != null && candidateRef != null && targetRef.equals(candidateRef, ignoreCase = true)) {
                true
            } else {
                isInternalTransferSignal(targetTx, candidate)
            }
        } ?: if (candidates.size == 1 && isGenericTransferContext(targetTx, candidates.first())) candidates.first() else null

        if (bestCandidate != null) {
            val (debitTx, creditTx) = if (targetTx.direction == TransactionDirection.DEBIT) {
                targetTx to bestCandidate
            } else {
                bestCandidate to targetTx
            }
            val debitAccountName = accounts[debitTx.accountId]?.name ?: "Account"
            val creditAccountName = accounts[creditTx.accountId]?.name ?: "Account"

            transactionDao.update(
                debitTx.copy(
                    category = TransactionCategory.TRANSFER,
                    countsTowardBudget = false,
                    note = debitTx.note ?: "Internal transfer to $creditAccountName",
                ),
            )

            transactionDao.update(
                creditTx.copy(
                    category = TransactionCategory.TRANSFER,
                    countsTowardBudget = false,
                    note = creditTx.note ?: "Internal transfer from $debitAccountName",
                ),
            )

            return true
        }

        return false
    }

    suspend fun reconcileTransferPairs(): Int {
        val allTxs = transactionDao.getAllTransactions()
            .filter { it.accountId != null && it.status == TransactionStatus.POSTED }
            .sortedBy { it.occurredAtMillis }

        var pairedCount = 0
        for (tx in allTxs) {
            if (tx.category != TransactionCategory.TRANSFER) {
                if (detectAndLinkTransferPair(tx.id)) {
                    pairedCount++
                }
            }
        }
        return pairedCount
    }

    fun extractTransactionReference(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val regex = Regex("""(?i)\b(?:upi\s*ref(?:erence)?(?:\s*no)?|rrn|utr|ref\s*no\.?|ref)[#:\s]*([0-9a-zA-Z]{6,16})\b""")
        return regex.find(body)?.groupValues?.getOrNull(1)
    }

    private fun isInternalTransferSignal(tx1: TransactionEntity, tx2: TransactionEntity): Boolean {
        val s1 = tx1.smsBody?.lowercase() ?: ""
        val s2 = tx2.smsBody?.lowercase() ?: ""
        val transferKeywords = listOf("transfer", "upi", "imps", "neft", "sent to", "received from", "self", "trf")
        val hasTransferSignal = transferKeywords.any { s1.contains(it) } || transferKeywords.any { s2.contains(it) }
        val isNonMerchant = !isObviousMerchant(tx1.merchant) && !isObviousMerchant(tx2.merchant)
        return hasTransferSignal && isNonMerchant
    }

    private fun isGenericTransferContext(tx1: TransactionEntity, tx2: TransactionEntity): Boolean {
        return !isObviousMerchant(tx1.merchant) && !isObviousMerchant(tx2.merchant)
    }

    private fun isObviousMerchant(merchant: String): Boolean {
        val lower = merchant.lowercase()
        val commercialMerchants = listOf(
            "swiggy", "zomato", "uber", "ola", "amazon", "flipkart", "myntra",
            "netflix", "spotify", "google", "apple", "starbucks", "mcdonald",
            "blinkit", "zepto", "instamart", "bigbasket", "bbnow", "irctc",
            "makemytrip", "bookmyshow", "steam", "playstation", "epic games",
        )
        return commercialMerchants.any { lower.contains(it) }
    }
}
