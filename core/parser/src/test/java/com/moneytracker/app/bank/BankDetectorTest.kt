package com.moneytracker.app.bank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankDetectorTest {

    @Test
    fun resolveBankInstitution_resolvesKnownTraiSenderPrefixes() {
        assertEquals("HDFC Bank", BankDetector.resolveBankInstitution("AD-HDFCBK"))
        assertEquals("State Bank of India", BankDetector.resolveBankInstitution("VK-SBIINB"))
        assertEquals("ICICI Bank", BankDetector.resolveBankInstitution("VM-ICICIB"))
        assertEquals("Axis Bank", BankDetector.resolveBankInstitution("AX-AXISBK"))
        assertEquals("Kotak Bank", BankDetector.resolveBankInstitution("BZ-KOTAKB"))
    }

    @Test
    fun extractBankAccountLastFour_extractsMaskedAccountDigits() {
        val sms = "Rs 2,500.00 debited from A/c XX4128 on 15-Mar-25."
        val lastFour = BankDetector.extractBankAccountLastFour(sms)
        assertEquals("4128", lastFour)
    }

    @Test
    fun detectBankAccount_identifiesValidBankMessage() {
        val detection = BankDetector.detectBankAccount("AD-HDFCBK", "Debited Rs 120 from A/c XX1234")
        assertTrue(detection.isBankAccount)
        assertEquals("HDFC Bank", detection.institutionName)
        assertEquals("1234", detection.accountLastFour)
    }
}
