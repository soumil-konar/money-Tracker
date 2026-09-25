package com.soumil.moneytracker

import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.CardType
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import com.soumil.moneytracker.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private val parser = SmsParser()

    @Test
    fun `parses debit upi message with merchant and amount`() {
        val result = parser.parse(
            sender = "HDFCBK",
            body = "Rs.1,250 debited from A/c XX1234 on 05-05-2026 via UPI to SWIGGY. Avl bal Rs.40,000",
        )

        assertFalse(result.shouldIgnore)
        assertEquals(1250.0, result.amount ?: 0.0, 0.0)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(TransactionCategory.FOOD, result.inferredCategory)
        assertEquals(AccountKind.BANK, result.accountKind)
        assertTrue(result.isUpiPayment)
        assertTrue(result.confidence >= 0.7)
    }

    @Test
    fun `parses salary credit message`() {
        val result = parser.parse(
            sender = "ICICIB",
            body = "INR 85000 credited to your salary account on 05-05-2026 from ACME PAYROLL.",
        )

        assertFalse(result.shouldIgnore)
        assertEquals(TransactionDirection.CREDIT, result.direction)
        assertEquals(TransactionCategory.SALARY, result.inferredCategory)
        assertEquals(85000.0, result.amount ?: 0.0, 0.0)
        assertNotNull(result.occurredAtMillis)
    }

    @Test
    fun `treats credit card bill payment as debit transfer instead of income`() {
        val result = parser.parse(
            sender = "HDFCBK",
            body = "Payment of INR 12450 received towards your HDFC Bank Credit Card ending 1234 from A/c XX8899 on 09-05-2026.",
        )

        assertFalse(result.shouldIgnore)
        assertEquals(12450.0, result.amount ?: 0.0, 0.0)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(TransactionCategory.TRANSFER, result.inferredCategory)
        assertEquals(AccountKind.BANK, result.accountKind)
        assertEquals(CardType.CREDIT, result.cardType)
        assertEquals("1234", result.cardLastFourDigits)
        assertTrue(result.isCardBillPayment)
        assertFalse(result.countsTowardBudget)
    }

    @Test
    fun `treats bill payment via CRED as transfer excluded from budget`() {
        val result = parser.parse(
            sender = "HDFCBK",
            body = "Rs.25,000 paid towards credit card bill via CRED from A/c XX5566 on 10-05-2026.",
        )

        assertFalse(result.shouldIgnore)
        assertEquals(25000.0, result.amount ?: 0.0, 0.0)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(TransactionCategory.TRANSFER, result.inferredCategory)
        assertTrue(result.isCardBillPayment)
        assertFalse(result.countsTowardBudget)
    }

    @Test
    fun `extracts future mandate into scheduled transaction`() {
        val result = parser.parseMessage(
            sender = "ICICIB",
            body = "Your eMandate of Rs.499 towards NETFLIX will be presented on 15/06/2026 from A/c XX1234.",
        )

        assertFalse(result.shouldIgnore)
        assertNull(result.transaction)
        assertNotNull(result.scheduledTransaction)
        assertEquals(499.0, result.scheduledTransaction?.amount ?: 0.0, 0.0)
        assertEquals(TransactionCategory.SUBSCRIPTION, result.scheduledTransaction?.inferredCategory)
        assertEquals("NETFLIX", result.scheduledTransaction?.merchant)
    }

    @Test
    fun `ignores collect requests before payment happens`() {
        val result = parser.parseMessage(
            sender = "PAYTM",
            body = "UPI collect request for Rs.500 from ABC STORES. Approve in app to pay.",
        )

        assertTrue(result.shouldIgnore)
        assertNull(result.transaction)
        assertNull(result.scheduledTransaction)
    }

    @Test
    fun `ignores promotional sms`() {
        val result = parser.parse(
            sender = "SHOPPR",
            body = "Flash sale today. Use coupon WIN100 and get cashback offer on every order.",
        )

        assertTrue(result.shouldIgnore)
        assertNull(result.amount)
        assertNull(result.direction)
    }

    @Test
    fun `extracts debit card last four for card spends`() {
        val result = parser.parse(
            sender = "HDFCBK",
            body = "INR 2400 spent on your debit card ending 4321 at DMART on 09-05-2026.",
        )

        assertFalse(result.shouldIgnore)
        assertEquals(AccountKind.CARD, result.accountKind)
        assertEquals(CardType.DEBIT, result.cardType)
        assertEquals("4321", result.cardLastFourDigits)
        assertTrue(result.isCardPayment)
        assertNotNull(result.occurredAtMillis)
    }

    @Test
    fun `marks rupay credit card upi spends as both card and upi traffic`() {
        val result = parser.parse(
            sender = "SBIUPI",
            body = "Rs.850 paid via UPI using your RuPay Credit Card ending 8765 to SWIGGY on 09-05-2026.",
        )

        assertFalse(result.shouldIgnore)
        assertEquals(AccountKind.CARD, result.accountKind)
        assertEquals(CardType.CREDIT, result.cardType)
        assertEquals("8765", result.cardLastFourDigits)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertTrue(result.isUpiPayment)
        assertTrue(result.isCardPayment)
        assertEquals(TransactionCategory.FOOD, result.inferredCategory)
    }

    @Test
    fun `parses card transaction format with card digits and inline date`() {
        val result = parser.parse(
            sender = "HDFCBK",
            body = "Txn Rs.20.00 On HDFC Bank Card 2159 At Vyapar.169705261244@hdfcb by UPI 153652450137 On 08-05",
        )

        assertFalse(result.shouldIgnore)
        assertEquals(20.0, result.amount ?: 0.0, 0.0)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(AccountKind.CARD, result.accountKind)
        assertEquals("2159", result.cardLastFourDigits)
        assertTrue(result.isUpiPayment)
        assertTrue(result.isCardPayment)
        assertEquals("Vyapar.169705261244@hdfcb", result.merchant)
        assertNotNull(result.occurredAtMillis)
    }

    @Test
    fun `parses bank upi transfer with normalized account digits and body date`() {
        val result = parser.parse(
            sender = "SBIUPI",
            body = "Dear UPI user A/C X6009 debited by 158.00 on date 07May26 trf to ZERODHA BROKING Refno 134847343423",
        )

        assertFalse(result.shouldIgnore)
        assertEquals(158.0, result.amount ?: 0.0, 0.0)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(AccountKind.BANK, result.accountKind)
        assertEquals("6009", result.bankAccountLastFourDigits)
        assertEquals("ZERODHA BROKING", result.merchant)
        assertNotNull(result.occurredAtMillis)
    }

    @Test
    fun `parses axis upi ledger format and uses beneficiary name as merchant`() {
        val result = parser.parse(
            sender = "AXISBK",
            body = """
                INR 33500.00 debited
                A/c no. XX6942
                02-05-26, 10:38:46
                UPI/P2A/103846333789/SHILPA SHRIPAD NAIK
                Not you? SMS BLOCKUPI Cust ID to 919951860002
                Axis Bank
            """.trimIndent(),
        )

        assertFalse(result.shouldIgnore)
        assertEquals(33500.0, result.amount ?: 0.0, 0.0)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(AccountKind.BANK, result.accountKind)
        assertEquals("6942", result.bankAccountLastFourDigits)
        assertEquals("SHILPA SHRIPAD NAIK", result.merchant)
        assertEquals(TransactionCategory.TRANSFER, result.inferredCategory)
        assertNotNull(result.occurredAtMillis)
    }

    @Test
    fun `treats generic card repayment with last four digits as bank transfer`() {
        val result = parser.parse(
            sender = "HDFCBK",
            body = "Payment of INR 12450 received towards your HDFC Bank Card 2159 from A/c XX8899 on 09-05-2026.",
        )

        assertFalse(result.shouldIgnore)
        assertEquals(12450.0, result.amount ?: 0.0, 0.0)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(AccountKind.BANK, result.accountKind)
        assertEquals(TransactionCategory.TRANSFER, result.inferredCategory)
        assertEquals("2159", result.cardLastFourDigits)
        assertTrue(result.isCardBillPayment)
    }

    @Test
    fun `ignores statement reminders for tracked cards`() {
        val result = parser.parseMessage(
            sender = "HDFCBK",
            body = "Your HDFC Bank Card 2159 statement generated. Total amount due Rs.12450 due date 18-05-2026.",
        )

        assertTrue(result.shouldIgnore)
        assertNull(result.transaction)
        assertNull(result.scheduledTransaction)
    }

    @Test
    fun `parses executed utility bill payment as debit under bills category`() {
        val result = parser.parseMessage(
            sender = "AXISBK",
            body = "Bill payment of INR 1899 paid for ELECTRICITY on 02-05-2026 from A/c XX6942.",
        )

        assertFalse(result.shouldIgnore)
        assertNotNull(result.transaction)
        assertEquals(1899.0, result.transaction?.amount ?: 0.0, 0.0)
        assertEquals(TransactionDirection.DEBIT, result.transaction?.direction)
        assertEquals(TransactionCategory.BILLS, result.transaction?.inferredCategory)
        assertEquals("6942", result.transaction?.bankAccountLastFourDigits)
    }

    @Test
    fun `still ignores pending bill due notices`() {
        val result = parser.parseMessage(
            sender = "BESCOM",
            body = "Your electricity bill for CA 10293847 is Rs 1,450. Due date is 25-05-2026. Pay now to avoid late fee.",
        )

        assertTrue(result.shouldIgnore)
        assertNull(result.transaction)
    }

    @Test
    fun `extracts available balance from banking sms accurately`() {
        val result1 = parser.parse(
            sender = "HDFCBK",
            body = "Rs.1,250 debited from A/c XX1234 on 05-05-2026 via UPI to SWIGGY. Avl bal Rs.40,000",
        )
        assertEquals(40000.0, result1.availableBalance ?: 0.0, 0.0)

        val result2 = parser.parse(
            sender = "ICICIB",
            body = "INR 2,500.00 debited from A/c XX5678 on 12-05-2026. Available balance is INR 1,25,400.75.",
        )
        assertEquals(125400.75, result2.availableBalance ?: 0.0, 0.0)
    }

    @Test
    fun `credit card bill payment receipt is treated as transfer without affecting budget`() {
        val result = parser.parse(
            sender = "SBICRD",
            body = "Payment of INR 15,000 received towards your SBI Card ending 9876 from A/c XX4321 on 15-05-2026.",
        )

        assertFalse(result.shouldIgnore)
        assertEquals(15000.0, result.amount ?: 0.0, 0.0)
        assertEquals(TransactionDirection.DEBIT, result.direction)
        assertEquals(TransactionCategory.TRANSFER, result.inferredCategory)
        assertTrue(result.isCardBillPayment)
        assertFalse(result.countsTowardBudget)
    }

    @Test
    fun `detects atm cash withdrawal correctly`() {
        val result1 = parser.parse(
            sender = "SBINB",
            body = "Rs.5,000 debited from A/c XX1234 on 05-05-2026 at SBI ATM. Avl bal Rs.20,000",
        )

        assertFalse(result1.shouldIgnore)
        assertEquals(5000.0, result1.amount ?: 0.0, 0.0)
        assertEquals(TransactionDirection.DEBIT, result1.direction)
        assertTrue(result1.isAtmWithdrawal)
        assertTrue(result1.merchant?.contains("ATM", ignoreCase = true) == true)

        val result2 = parser.parse(
            sender = "HDFCBK",
            body = "Your a/c no. XX1234 is debited for Rs.2,000.00 on 24-09-26 by cash withdrawal at ATM. Bal: Rs 15000",
        )
        assertFalse(result2.shouldIgnore)
        assertEquals(2000.0, result2.amount ?: 0.0, 0.0)
        assertEquals(TransactionDirection.DEBIT, result2.direction)
        assertTrue(result2.isAtmWithdrawal)
    }
}

