package com.moneytracker.app.parser

import com.moneytracker.app.data.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmsParserTest {

    private lateinit var parser: SmsParser

    @Before
    fun setUp() {
        parser = SmsParser()
    }

    @Test
    fun parseHdfcBankDebitSms_extractsAmountMerchantAndAccount() {
        val sms = "Rs 1,250.00 debited from A/c XX4128 at Swiggy on 12-Mar-25. Avl bal Rs 45,200.00"
        val parsed = parser.parseMessage("AD-HDFCBK", sms)

        assertNotNull(parsed.transaction)
        val tx = parsed.transaction!!
        assertEquals(1250.00, tx.amount!!, 0.01)
        assertEquals(TransactionDirection.DEBIT, tx.direction)
        assertEquals("HDFC Bank", tx.institutionName)
        assertEquals("4128", tx.bankAccountLastFourDigits)
        assertEquals(45200.00, tx.availableBalance!!, 0.01)
    }

    @Test
    fun parseSbiBankCreditSms_extractsCreditDetails() {
        val sms = "Your A/c XX9012 has been credited by Rs 25,000.00 on 01-Mar-25 by SALARY. Avl bal: Rs 82,100.00."
        val parsed = parser.parseMessage("VK-SBIINB", sms)

        assertNotNull(parsed.transaction)
        val tx = parsed.transaction!!
        assertEquals(25000.00, tx.amount!!, 0.01)
        assertEquals(TransactionDirection.CREDIT, tx.direction)
        assertEquals("State Bank of India", tx.institutionName)
        assertEquals("9012", tx.bankAccountLastFourDigits)
    }

    @Test
    fun parseUpiPaymentSms_identifiesUpiAndMerchant() {
        val sms = "Paid Rs 350.00 to Zomato via UPI Ref 401234567890. A/c debited: XX1234."
        val parsed = parser.parseMessage("AX-AXISBK", sms)

        assertNotNull(parsed.transaction)
        val tx = parsed.transaction!!
        assertEquals(350.00, tx.amount!!, 0.01)
        assertEquals(TransactionDirection.DEBIT, tx.direction)
        assertTrue(tx.isUpiPayment)
    }

    @Test
    fun parseOtpSms_shouldIgnore() {
        val sms = "Your OTP for transaction of Rs 5,000.00 at Amazon is 482190. Do not share with anyone."
        val parsed = parser.parseMessage("AD-HDFCBK", sms)

        assertTrue(parsed.shouldIgnore)
    }
}
