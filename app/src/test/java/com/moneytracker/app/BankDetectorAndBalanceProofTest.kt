package com.moneytracker.app

import com.moneytracker.app.bank.BalanceProofVerifier
import com.moneytracker.app.bank.BankDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankDetectorAndBalanceProofTest {

    @Test
    fun `resolves bank institutions accurately from TRAI senders and body`() {
        assertEquals("HDFC Bank", BankDetector.resolveBankInstitution("AD-HDFCBK", "Rs 500 debited"))
        assertEquals("State Bank of India", BankDetector.resolveBankInstitution("VK-SBIINB", "Rs 1200 credited"))
        assertEquals("ICICI Bank", BankDetector.resolveBankInstitution("BZ-ICICIB", "Rs 800 paid"))
        assertEquals("Axis Bank", BankDetector.resolveBankInstitution("VM-AXISBK", "A/c debited"))
        assertEquals("Kotak Bank", BankDetector.resolveBankInstitution("JM-KOTAKB", "Transaction successful"))
        assertEquals("Bank of Baroda", BankDetector.resolveBankInstitution("AD-BOBTXN", "Rs 300 debited"))
        assertEquals("Punjab National Bank", BankDetector.resolveBankInstitution("VK-PNBSMS", "A/c 1234 credited"))
        assertEquals("Canara Bank", BankDetector.resolveBankInstitution("BW-CANBNK", "Txn done"))
        assertEquals("IndusInd Bank", BankDetector.resolveBankInstitution("CP-INDBNK", "Debited"))
        assertEquals("IDFC FIRST Bank", BankDetector.resolveBankInstitution("BP-IDFCFB", "Paid via UPI"))
    }

    @Test
    fun `rejects non-bank senders from creating bank accounts`() {
        assertNull(BankDetector.resolveBankInstitution("VM-MYNTRA", "Flat 50% off on shoes"))
        assertNull(BankDetector.resolveBankInstitution("JK-SWIGGY", "Your order has been delivered"))
        assertNull(BankDetector.resolveBankInstitution("BP-AMAZON", "Your package has arrived"))
        assertNull(BankDetector.resolveBankInstitution("NOTICE", "Electricity bill reminder"))
        assertFalse(BankDetector.isLegitimateBank("VM-MYNTR"))
        assertFalse(BankDetector.isLegitimateBank("Merchant"))
        assertFalse(BankDetector.isLegitimateBank("UPI"))
    }

    @Test
    fun `detects bank account with UPI debit correctly`() {
        val sender = "AD-HDFCBK"
        val body = "Rs 450.00 debited from A/c **1234 on 24-Sep-26 via UPI to SWIGGY. Avl Bal: INR 35,420.50"

        val detection = BankDetector.detectBankAccount(sender, body)
        assertTrue(detection.isBankAccount)
        assertEquals("HDFC Bank", detection.institutionName)
        assertEquals("1234", detection.accountLastFour)
    }

    @Test
    fun `distinguishes credit card transaction from bank account`() {
        val sender = "AD-HDFCCC"
        val body = "Spent Rs 2,500 on HDFC Bank Credit Card ending 9876 at Croma on 24-Sep. Available Credit Limit: Rs 1,45,000"

        val detection = BankDetector.detectBankAccount(sender, body)
        assertFalse("Standalone credit card without bank debit is not a bank account", detection.isBankAccount)
        assertEquals("HDFC Bank", detection.institutionName)
    }

    @Test
    fun `verifies genuine available balance with substantial proof`() {
        val sender = "AD-HDFCBK"
        val body = "Rs 1,250.00 debited from A/c XX4321 on 24-Sep-26 via UPI to Zomato. Avl Bal: INR 48,150.75."

        val proof = BalanceProofVerifier.verifyBalance(sender, body, 1727184000000L)
        assertTrue(proof.isVerified)
        assertEquals(48150.75, proof.balance ?: 0.0, 0.01)
        assertEquals("4321", proof.accountLastFour)
        assertEquals("HDFC Bank", proof.institutionName)
        assertNotNull(proof.proofSnippet)
        assertTrue(proof.proofSnippet!!.contains("Avl Bal: INR 48,150.75"))
        assertTrue(proof.proofSource!!.contains("HDFCBK"))
    }

    @Test
    fun `rejects credit card credit limit from being treated as available bank balance`() {
        val sender = "AD-HDFCBK"
        val body = "Transaction of Rs 5,000 on Card ending 1234 at Apple Store. Avl Bal: Rs 95,000. Total Credit Limit: Rs 1,00,000."

        val proof = BalanceProofVerifier.verifyBalance(sender, body)
        assertFalse("Credit card limit must be rejected", proof.isVerified)
        assertNull(proof.balance)
        assertTrue(proof.rejectionReason!!.contains("conflicting context"))
    }

    @Test
    fun `rejects total due or bill amount from being treated as available balance`() {
        val sender = "VK-SBIINB"
        val body = "Dear Customer, your total balance due is Rs 8,420 for Card ending 5555. Min due Rs 500."

        val proof = BalanceProofVerifier.verifyBalance(sender, body)
        assertFalse("Bill total due must be rejected", proof.isVerified)
        assertNull(proof.balance)
    }

    @Test
    fun `rejects reward points balance`() {
        val sender = "BZ-ICICIB"
        val body = "Congratulations! You earned 250 points on your last purchase. Points Bal: 1,450."

        val proof = BalanceProofVerifier.verifyBalance(sender, body)
        assertFalse("Reward points balance must be rejected", proof.isVerified)
        assertNull(proof.balance)
    }

    @Test
    fun `rejects promotional offers and loan pre-approvals`() {
        val sender = "VM-AXISBK"
        val body = "You have a pre-approved personal loan of Rs 5,00,000 with Axis Bank. Apply now!"

        val proof = BalanceProofVerifier.verifyBalance(sender, body)
        assertFalse("Loan offer must be rejected", proof.isVerified)
        assertNull(proof.balance)
    }
}
