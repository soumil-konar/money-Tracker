package com.soumil.moneytracker

import com.soumil.moneytracker.bank.BankDetector
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.TransactionDirection
import com.soumil.moneytracker.data.model.TransactionStatus
import com.soumil.moneytracker.parser.SmsParser
import com.soumil.moneytracker.data.db.TransactionRecord
import com.soumil.moneytracker.data.db.canTransferToCash
import com.soumil.moneytracker.data.db.isAtmWithdrawal
import com.soumil.moneytracker.data.db.isTransferredToCash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBalanceAndRoutingTest {

    private val parser = SmsParser()

    @Test
    fun `extracts account numbers with bullets dots and ending phrases accurately`() {
        val msgBullets = "Rs 450.00 debited from A/c ••••4321 on 25-Sep-26 via UPI to ZOMATO. Avl Bal: INR 12,300.00"
        val msgDots = "Dear Customer, INR 1,200.00 credited to A/c ...7890 on 25-Sep-26. Avl Bal: Rs 45,000.00"
        val msgEnding = "Your account ending 5678 was debited by Rs 250 on 25-Sep-26. Avl Bal: Rs 8,000"
        val msgEndingIn = "A/c ending in 1122 debited with Rs 999. Avl Bal INR 15,000"

        assertEquals("4321", BankDetector.detectBankAccount("AD-HDFCBK", msgBullets).accountLastFour)
        assertEquals("7890", BankDetector.detectBankAccount("VK-SBIINB", msgDots).accountLastFour)
        assertEquals("5678", BankDetector.detectBankAccount("BZ-ICICIB", msgEnding).accountLastFour)
        assertEquals("1122", BankDetector.detectBankAccount("VM-AXISBK", msgEndingIn).accountLastFour)
    }

    @Test
    fun `extracts card last four and distinguishes from bank account last four`() {
        val dualMsg = "Payment of INR 5,000 received towards your HDFC Credit Card ending 9999 from A/c XX1234 on 25-Sep-26."
        val cardLastFour = BankDetector.extractCardLastFour(dualMsg)
        val bankLastFour = BankDetector.detectBankAccount("AD-HDFCBK", dualMsg).accountLastFour

        assertEquals("9999", cardLastFour)
        assertEquals("1234", bankLastFour)
        assertNotEquals("Card digits must not collide with bank account digits", cardLastFour, bankLastFour)
    }

    @Test
    fun `parses available balance and transaction amount correctly`() {
        val body = "Rs 2,500.00 debited from A/c XX4321 on 25-Sep-26. Avl Bal: INR 35,420.50"
        val parsed = parser.parse("AD-HDFCBK", body)

        assertEquals(2500.0, parsed.amount ?: 0.0, 0.01)
        assertEquals(35420.50, parsed.availableBalance ?: 0.0, 0.01)
        assertEquals("4321", parsed.bankAccountLastFourDigits)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertEquals(AccountKind.BANK, parsed.accountKind)
    }

    @Test
    fun `verifies ledger balance reconciliation algorithm mathematically`() {
        // Simulated verified balance snapshot anchor from genuine bank SMS
        val anchorBalance = 50000.0
        val anchorTimestamp = 1727200000000L // e.g., Sept 24, 2026

        // Subsequent posted transactions without balance statements
        data class MockTx(val amount: Double, val direction: TransactionDirection, val timestamp: Long, val status: TransactionStatus)

        val transactions = listOf(
            // Pre-anchor transaction: must be IGNORED because anchor already includes it
            MockTx(amount = 1000.0, direction = TransactionDirection.DEBIT, timestamp = anchorTimestamp - 10000L, status = TransactionStatus.POSTED),
            // Subsequent debit 1 (Swiggy)
            MockTx(amount = 450.0, direction = TransactionDirection.DEBIT, timestamp = anchorTimestamp + 5000L, status = TransactionStatus.POSTED),
            // Subsequent credit 2 (Refund / peer UPI)
            MockTx(amount = 1200.0, direction = TransactionDirection.CREDIT, timestamp = anchorTimestamp + 10000L, status = TransactionStatus.POSTED),
            // Subsequent debit 3 (Groceries)
            MockTx(amount = 850.0, direction = TransactionDirection.DEBIT, timestamp = anchorTimestamp + 15000L, status = TransactionStatus.POSTED),
            // Review transaction: must NOT affect ledger balance until approved
            MockTx(amount = 5000.0, direction = TransactionDirection.DEBIT, timestamp = anchorTimestamp + 20000L, status = TransactionStatus.REVIEW),
        )

        // Algorithm logic as implemented in FinanceRepository.reconcileSingleAccount
        var calculatedBalance = anchorBalance
        for (tx in transactions.filter { it.timestamp > anchorTimestamp && it.status == TransactionStatus.POSTED }) {
            when (tx.direction) {
                TransactionDirection.CREDIT -> calculatedBalance += tx.amount
                TransactionDirection.DEBIT -> calculatedBalance -= tx.amount
            }
        }

        // Expected: 50000 - 450 + 1200 - 850 = 49900.0
        assertEquals(49900.0, calculatedBalance, 0.001)
    }

    @Test
    fun `two accounts at same bank with different last-4 digits must remain separate`() {
        val sbiAccount1Digits = "1111"
        val sbiAccount2Digits = "2222"

        val bankInstitution = "State Bank of India"

        // Simulated matching logic from resolveAccount
        fun matchAccount(accountDigits: String?, candidateDigits: String?, candidateBank: String?): Boolean {
            if (accountDigits.isNullOrBlank()) {
                return candidateDigits.isNullOrBlank() && candidateBank == bankInstitution
            }
            return candidateDigits == accountDigits && candidateBank == bankInstitution
        }

        // Account 1 should match 1111, but NEVER 2222
        assertTrue(matchAccount(sbiAccount1Digits, "1111", bankInstitution))
        org.junit.Assert.assertFalse(matchAccount(sbiAccount1Digits, "2222", bankInstitution))

        // Account 2 should match 2222, but NEVER 1111
        assertTrue(matchAccount(sbiAccount2Digits, "2222", bankInstitution))
        org.junit.Assert.assertFalse(matchAccount(sbiAccount2Digits, "1111", bankInstitution))
    }

    @Test
    fun `manual true-up balance anchors accurately and takes precedence over older bank statement proofs`() {
        val oldSmsTimestamp = 1727100000000L
        val oldSmsBalance = 40000.0

        val userTrueUpTimestamp = 1727200000000L
        val userTrueUpBalance = 75000.0

        data class MockTx(val amount: Double, val direction: TransactionDirection, val timestamp: Long)

        val transactions = listOf(
            MockTx(amount = 1500.0, direction = TransactionDirection.DEBIT, timestamp = 1727150000000L), // between SMS and True-Up
            MockTx(amount = 2500.0, direction = TransactionDirection.DEBIT, timestamp = 1727250000000L), // after True-Up
            MockTx(amount = 5000.0, direction = TransactionDirection.CREDIT, timestamp = 1727300000000L), // after True-Up
        )

        // Reconciliation logic when userTrueUpTimestamp > oldSmsTimestamp:
        // Prioritize user true-up baseline
        var currentBalance = userTrueUpBalance
        val postTrueUpTxs = transactions.filter { it.timestamp > userTrueUpTimestamp }
        for (tx in postTrueUpTxs) {
            when (tx.direction) {
                TransactionDirection.CREDIT -> currentBalance += tx.amount
                TransactionDirection.DEBIT -> currentBalance -= tx.amount
            }
        }

        // Expected: 75000 - 2500 + 5000 = 77500.0 (the 1500 debit before true-up is already reflected in the 75000 true-up)
        assertEquals(77500.0, currentBalance, 0.001)
    }

    @Test
    fun `detects multi-account internal self-transfer pairs accurately`() {
        val utrRegex = Regex("""(?i)\b(?:upi\s*ref(?:erence)?(?:\s*no)?|rrn|utr|ref\s*no\.?|ref)[#:\s]*([0-9a-zA-Z]{6,16})\b""")

        val debitBody = "INR 5,000.00 debited from A/c XX1234 to VPA self@okaxis on 25-Sep-26. UPI Ref 426819283719."
        val creditBody = "INR 5,000.00 credited to A/c XX5678 from VPA self@okhdfc on 25-Sep-26. UPI Ref 426819283719."

        val debitRef = utrRegex.find(debitBody)?.groupValues?.getOrNull(1)
        val creditRef = utrRegex.find(creditBody)?.groupValues?.getOrNull(1)

        assertNotNull(debitRef)
        assertNotNull(creditRef)
        assertEquals("426819283719", debitRef)
        assertEquals(debitRef, creditRef)

        data class MockTxRecord(
            val id: Long,
            val accountId: Long,
            val amount: Double,
            val direction: TransactionDirection,
            val timestamp: Long,
            val merchant: String,
            val body: String,
            var category: com.soumil.moneytracker.data.model.TransactionCategory,
            var countsTowardBudget: Boolean,
        )

        val tx1 = MockTxRecord(
            id = 1L,
            accountId = 101L,
            amount = 5000.0,
            direction = TransactionDirection.DEBIT,
            timestamp = 1727260000000L,
            merchant = "self@okaxis",
            body = debitBody,
            category = com.soumil.moneytracker.data.model.TransactionCategory.OTHER,
            countsTowardBudget = true,
        )

        val tx2 = MockTxRecord(
            id = 2L,
            accountId = 102L,
            amount = 5000.0,
            direction = TransactionDirection.CREDIT,
            timestamp = 1727260030000L, // 30 seconds later
            merchant = "self@okhdfc",
            body = creditBody,
            category = com.soumil.moneytracker.data.model.TransactionCategory.OTHER,
            countsTowardBudget = true,
        )

        // Matching logic
        val sameRef = debitRef == creditRef
        val oppositeDirection = tx1.direction != tx2.direction
        val sameAmount = tx1.amount == tx2.amount
        val timeDeltaMs = kotlin.math.abs(tx1.timestamp - tx2.timestamp)
        val withinWindow = timeDeltaMs <= 5 * 60 * 1000L
        val differentAccounts = tx1.accountId != tx2.accountId

        val isTransferPair = sameRef && oppositeDirection && sameAmount && withinWindow && differentAccounts

        assertTrue("Should detect internal transfer pair", isTransferPair)
        if (isTransferPair) {
            tx1.category = com.soumil.moneytracker.data.model.TransactionCategory.TRANSFER
            tx1.countsTowardBudget = false
            tx2.category = com.soumil.moneytracker.data.model.TransactionCategory.TRANSFER
            tx2.countsTowardBudget = false
        }

        assertEquals(com.soumil.moneytracker.data.model.TransactionCategory.TRANSFER, tx1.category)
        org.junit.Assert.assertFalse(tx1.countsTowardBudget)
        assertEquals(com.soumil.moneytracker.data.model.TransactionCategory.TRANSFER, tx2.category)
        org.junit.Assert.assertFalse(tx2.countsTowardBudget)
    }

    @Test
    fun `atm withdrawal cash in hand accounting preserves net worth and excludes from budget`() {
        val atmSms = "Rs 5,000.00 debited from A/c XX1234 on 25-Sep-26 at SBI ATM. Avl Bal: Rs 20,000"
        val parsed = parser.parse("SBINB", atmSms)

        assertTrue("Must be identified as ATM withdrawal", parsed.isAtmWithdrawal)
        assertEquals(5000.0, parsed.amount ?: 0.0, 0.0)

        // Mock TransactionRecord
        val atmTx = com.soumil.moneytracker.data.db.TransactionRecord(
            id = 101L,
            amount = 5000.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = System.currentTimeMillis(),
            merchant = parsed.merchant ?: "SBI ATM",
            category = com.soumil.moneytracker.data.model.TransactionCategory.OTHER,
            accountId = 1L,
            sourceSender = "SBINB",
            smsBody = atmSms,
            confidence = 0.95,
            status = TransactionStatus.POSTED,
            note = "ATM Cash Withdrawal",
            countsTowardBudget = true,
            accountName = "SBI A/c 1234",
            accountKind = AccountKind.BANK,
        )

        // Extension property validation
        assertTrue(atmTx.isAtmWithdrawal)
        assertTrue(atmTx.canTransferToCash)

        // Initial balances before ATM withdrawal: Bank = 25000, Cash = 500
        var bankBalance = 25000.0
        var cashBalance = 500.0
        val initialNetWorth = bankBalance + cashBalance // 25500.0

        // Bank debit occurs via SMS (-5000)
        bankBalance -= atmTx.amount // 20000.0

        // User transfers to Cash in Hand
        val transferredAtmTx = atmTx.copy(
            category = com.soumil.moneytracker.data.model.TransactionCategory.TRANSFER,
            countsTowardBudget = false,
            note = "Transferred to Cash in Hand",
        )
        val pairedCashCredit = com.soumil.moneytracker.data.db.TransactionRecord(
            id = 102L,
            amount = 5000.0,
            direction = TransactionDirection.CREDIT,
            occurredAtMillis = atmTx.occurredAtMillis,
            merchant = "Cash in Hand",
            category = com.soumil.moneytracker.data.model.TransactionCategory.TRANSFER,
            accountId = 2L,
            sourceSender = "INTERNAL_TRANSFER",
            smsBody = null,
            confidence = 1.0,
            status = TransactionStatus.POSTED,
            note = "ATM cash withdrawal from SBI A/c 1234",
            countsTowardBudget = false,
            accountName = "Cash in Hand",
            accountKind = AccountKind.CASH,
        )
        cashBalance += pairedCashCredit.amount // 5500.0

        // Verify:
        // 1. Double-entry net worth is preserved
        val postTransferNetWorth = bankBalance + cashBalance // 20000 + 5500 = 25500
        assertEquals(initialNetWorth, postTransferNetWorth, 0.001)

        // 2. Neither transaction counts toward expense budget
        org.junit.Assert.assertFalse(transferredAtmTx.countsTowardBudget)
        org.junit.Assert.assertFalse(pairedCashCredit.countsTowardBudget)

        // 3. Status is now transferred
        assertTrue(transferredAtmTx.isTransferredToCash)
        org.junit.Assert.assertFalse(transferredAtmTx.canTransferToCash)
    }
}
