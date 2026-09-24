package com.soumil.moneytracker

import com.soumil.moneytracker.ai.OnDeviceAiEngine
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDescriptionEnrichmentTest {

    private val engine = OnDeviceAiEngine()

    @Test
    fun `detailed description is synthesized for food delivery transactions`() {
        val sms = "Rs 450.00 debited from A/C **1234 on 24-Sep-26 towards Swiggy order. Avl Bal: Rs 15,000."
        val result = engine.parseSmsOnDevice(sms, "HDFC").getOrNull()

        assertNotNull(result)
        assertEquals(TransactionCategory.FOOD, result!!.category)
        assertNotNull("Detailed description should be populated", result.detailedDescription)
        assertTrue(
            "Description should mention dining/food and merchant: ${result.detailedDescription}",
            result.detailedDescription!!.contains("Swiggy", ignoreCase = true),
        )
    }

    @Test
    fun `detailed description is synthesized for cab and travel rides`() {
        val sms = "Paid Rs 320 to Uber India via Paytm UPI on 24-Sep-26."
        val result = engine.parseSmsOnDevice(sms, "Paytm").getOrNull()

        assertNotNull(result)
        assertEquals(TransactionCategory.TRAVEL, result!!.category)
        assertNotNull(result.detailedDescription)
        assertTrue(
            "Description should mention Uber: ${result.detailedDescription}",
            result.detailedDescription!!.contains("Uber", ignoreCase = true),
        )
    }

    @Test
    fun `detailed description is synthesized for credit card bill clearance`() {
        val sms = "Thank you. Payment of Rs 12,500.00 received towards your SBI Credit Card ending 8888 on 24-Sep-26 via CRED."
        val result = engine.parseSmsOnDevice(sms, "SBI Card").getOrNull()

        assertNotNull(result)
        assertTrue(result!!.isCardBillPayment)
        assertEquals(TransactionCategory.TRANSFER, result.category)
        assertNotNull(result.detailedDescription)
        assertTrue(
            "Description should mention credit card bill payment: ${result.detailedDescription}",
            result.detailedDescription!!.contains("credit card", ignoreCase = true),
        )
    }

    @Test
    fun `detailed description preserves branch or location detail when present`() {
        val sms = "Debited INR 750.00 at Starbucks Indiranagar outlet using HDFC Bank Card ending 4321."
        val result = engine.parseSmsOnDevice(sms, "HDFC Bank").getOrNull()

        assertNotNull(result)
        assertNotNull(result!!.detailedDescription)
        assertTrue(
            "Description should include location detail: ${result.detailedDescription}",
            result.detailedDescription!!.contains("Indiranagar", ignoreCase = true),
        )
    }

    @Test
    fun `detailed description formats payment received credits`() {
        val sms = "Your A/C **5555 is credited with INR 75,000.00 on 24-Sep-26 by Infosys Technologies Ltd towards Salary."
        val result = engine.parseSmsOnDevice(sms, "ICICI Bank").getOrNull()

        assertNotNull(result)
        assertEquals(TransactionDirection.CREDIT, result!!.direction)
        assertNotNull(result.detailedDescription)
        assertTrue(
            "Description should indicate payment received: ${result.detailedDescription}",
            result.detailedDescription!!.contains("received", ignoreCase = true) ||
                result.detailedDescription!!.contains("Salary", ignoreCase = true) ||
                result.detailedDescription!!.contains("Infosys", ignoreCase = true),
        )
    }
}
