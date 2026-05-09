package com.soumil.moneytracker

import com.soumil.moneytracker.data.model.AccountKind
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
        assertEquals(AccountKind.UPI, result.accountKind)
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
        assertEquals(AccountKind.CARD, result.accountKind)
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
}

