package com.soumil.moneytracker

import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import com.soumil.moneytracker.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

