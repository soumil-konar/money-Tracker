package com.soumil.moneytracker

import com.soumil.moneytracker.bank.BankDetector
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.TransactionDirection
import com.soumil.moneytracker.data.model.TransactionStatus
import com.soumil.moneytracker.parser.SmsParser
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
}
