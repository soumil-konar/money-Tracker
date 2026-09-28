package com.moneytracker.app.ai

import com.moneytracker.app.data.db.TransactionEntity
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.db.cleanNote
import com.moneytracker.app.data.db.parseEngine
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeoutException

class AiCoreNanoManagerTest {

    @Test
    fun testSuccessfulJsonExtraction() = runTest {
        val sampleJson = """
            {
              "amount": 450.50,
              "direction": "DEBIT",
              "merchant": "Swiggy",
              "accountLast4": "4321",
              "availableBalance": 15200.75,
              "category": "FOOD",
              "isTransaction": true
            }
        """.trimIndent()

        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { sampleJson },
        )

        assertTrue(manager.isAvailable())

        val result = manager.extractFinancialEntity(
            rawText = "Rs 450.50 debited at Swiggy from A/c 4321. Bal Rs 15200.75",
            sender = "HDFC-BANK",
        )

        assertTrue("Inference should succeed", result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        assertEquals(true, entity?.isTransaction)
        assertEquals(450.50, entity?.amount ?: 0.0, 0.001)
        assertEquals(TransactionDirection.DEBIT, entity?.direction)
        assertEquals("Swiggy", entity?.merchant)
        assertEquals("4321", entity?.accountLastFour)
        assertEquals(15200.75, entity?.availableBalance ?: 0.0, 0.001)
        assertEquals(TransactionCategory.FOOD, entity?.category)
        assertTrue(entity?.countsTowardBudget == true)
        assertEquals(0.98, entity?.confidence ?: 0.0, 0.001)
    }

    @Test
    fun testCreditSalaryExtraction() = runTest {
        val sampleJson = """
            {
              "amount": 75000.0,
              "direction": "CREDIT",
              "merchant": "Infosys Payroll",
              "accountLast4": "9876",
              "availableBalance": 120000.0,
              "category": "SALARY",
              "isTransaction": true
            }
        """.trimIndent()

        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { sampleJson },
        )

        val result = manager.extractFinancialEntity(
            rawText = "Salary of Rs 75,000 credited to your A/c 9876",
            sender = "ICICI-SAL",
        )

        assertTrue(result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        assertEquals(TransactionDirection.CREDIT, entity?.direction)
        assertEquals(75000.0, entity?.amount ?: 0.0, 0.001)
        assertEquals("Infosys Payroll", entity?.merchant)
        assertEquals(TransactionCategory.SALARY, entity?.category)
        assertEquals("9876", entity?.accountLastFour)
    }

    @Test
    fun testMalformedOutputCleanupWithMarkdownFences() = runTest {
        val wrappedJson = """
            ```json
            {
              "amount": 1299.00,
              "direction": "DEBIT",
              "merchant": "Amazon",
              "accountLast4": "5544",
              "availableBalance": 8500.00,
              "category": "SHOPPING",
              "isTransaction": true
            }
            ```
        """.trimIndent()

        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { wrappedJson },
        )

        val result = manager.extractFinancialEntity(
            rawText = "Paid Rs 1299.00 at Amazon using card ending 5544",
            sender = "SBI-CARD",
        )

        assertTrue(result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        assertEquals(1299.00, entity?.amount ?: 0.0, 0.001)
        assertEquals("Amazon", entity?.merchant)
        assertEquals(TransactionCategory.SHOPPING, entity?.category)
        assertEquals("5544", entity?.accountLastFour)
    }

    @Test
    fun testMalformedOutputCleanupWithProseCommentary() = runTest {
        val proseOutput = """
            Here is the extracted transaction details in JSON format as requested:
            {
              "amount": 180.00,
              "direction": "DEBIT",
              "merchant": "Uber",
              "accountLast4": null,
              "availableBalance": null,
              "category": "TRANSPORT",
              "isTransaction": true
            }
            Note: This spend has been categorized under travel.
        """.trimIndent()

        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { proseOutput },
        )

        val result = manager.extractFinancialEntity(
            rawText = "Paid Rs 180 to Uber for your ride",
            sender = "UBER",
        )

        assertTrue(result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        assertEquals(180.00, entity?.amount ?: 0.0, 0.001)
        assertEquals("Uber", entity?.merchant)
        assertEquals(TransactionCategory.TRAVEL, entity?.category)
    }

    @Test
    fun testNonTransactionDetectionForOtp() = runTest {
        val nonTxJson = """
            {
              "isTransaction": false
            }
        """.trimIndent()

        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { nonTxJson },
        )

        val result = manager.extractFinancialEntity(
            rawText = "Your OTP for login is 849201. Do not share this with anyone.",
            sender = "HDFC-OTP",
        )

        assertTrue(result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        assertFalse("Should be marked as non-transaction", entity!!.isTransaction)
        assertNull(entity.amount)
        assertNull(entity.merchant)
    }

    @Test
    fun testTimeoutHandOff() = runTest {
        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = {
                // Simulate an execution taking longer than the 2500ms safeguard
                delay(3000L)
                """{"amount": 100.0, "direction": "DEBIT", "isTransaction": true}"""
            },
        )

        val result = manager.extractFinancialEntity(
            rawText = "Paid Rs 100 at Chai Point",
            sender = "PAYTM",
        )

        assertTrue("Inference taking >2500ms must fail with timeout", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(
            "Exception should indicate timeout",
            exception is TimeoutException || exception?.message?.contains("timed out") == true,
        )
    }

    @Test
    fun testAvailabilityStates() {
        val readyManager = AiCoreNanoManager(initialStatus = NanoAvailabilityStatus.Ready)
        assertTrue(readyManager.isAvailable())
        assertEquals(NanoAvailabilityStatus.Ready, readyManager.availabilityStatus.value)

        val downloadingManager = AiCoreNanoManager(initialStatus = NanoAvailabilityStatus.DownloadingModel)
        assertFalse(downloadingManager.isAvailable())

        val unsupportedManager = AiCoreNanoManager(initialStatus = NanoAvailabilityStatus.UnsupportedDevice)
        assertFalse(unsupportedManager.isAvailable())

        val errorManager = AiCoreNanoManager(initialStatus = NanoAvailabilityStatus.Error("Driver crash"))
        assertFalse(errorManager.isAvailable())
    }

    @Test
    fun testUnifiedPipelineTier1NanoSuccess() = runTest {
        val nanoManager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = {
                """
                {
                  "amount": 620.0,
                  "direction": "DEBIT",
                  "merchant": "Zomato",
                  "category": "FOOD",
                  "isTransaction": true
                }
                """.trimIndent()
            },
        )

        val engine = OnDeviceAiEngine(aiCoreNanoManager = nanoManager)
        val parseResult = engine.parseIncomingMessage(
            body = "Rs 620 debited for Zomato order",
            sender = "HDFC-BANK",
        )

        assertEquals(OnDeviceAiEngine.ENGINE_GEMINI_NANO, parseResult.engine)
        assertEquals(TransactionStatus.POSTED, parseResult.status)
        assertEquals("Zomato", parseResult.transaction?.merchant)
        assertEquals(620.0, parseResult.transaction?.amount ?: 0.0, 0.001)
    }

    @Test
    fun testUnifiedPipelineTier1RegexFallbackWhenNanoFails() = runTest {
        // Nano throws an error
        val failingNano = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { throw RuntimeException("NPU Busy") },
        )

        val engine = OnDeviceAiEngine(aiCoreNanoManager = failingNano)
        val parseResult = engine.parseIncomingMessage(
            body = "Rs 450.00 debited from HDFC Bank A/c xx1234 at Starbucks on 12-Oct-24. Avl Bal: Rs 12,000",
            sender = "HDFCBK",
        )

        // Tier 1 Local Regex handles it with high confidence (>= 0.85)
        assertEquals(OnDeviceAiEngine.ENGINE_LOCAL_REGEX, parseResult.engine)
        assertEquals(TransactionStatus.POSTED, parseResult.status)
        assertEquals("Starbucks", parseResult.transaction?.merchant)
        assertEquals(450.0, parseResult.transaction?.amount ?: 0.0, 0.001)
    }

    @Test
    fun testUnifiedPipelineTier3DegradedRecoveryFallback() = runTest {
        val unsupportedNano = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.UnsupportedDevice,
        )

        val engine = OnDeviceAiEngine(aiCoreNanoManager = unsupportedNano)
        // Obscure banking SMS format that fails standard regex confidence
        val weirdSms = "Alert: Acct 1122 had a debit charge totaling INR 850.50 on terminal 44"
        val parseResult = engine.parseIncomingMessage(
            body = weirdSms,
            sender = "XYZ-BNK",
            apiKey = "", // No cloud API key configured
        )

        // Falls back to Tier 3 Local Recovery flagged with REVIEW
        assertEquals(OnDeviceAiEngine.ENGINE_DEGRADED_MANUAL, parseResult.engine)
        assertEquals(TransactionStatus.REVIEW, parseResult.status)
        assertNotNull(parseResult.transaction)
        assertEquals(850.50, parseResult.transaction?.amount ?: 0.0, 0.001)
    }

    @Test
    fun testProvenanceTrackingExtensions() {
        val nanoRecord = TransactionRecord(
            id = 1L,
            amount = 450.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = System.currentTimeMillis(),
            merchant = "Swiggy",
            category = TransactionCategory.FOOD,
            accountId = 1L,
            sourceSender = "HDFC",
            smsBody = "Swiggy debit Rs 450",
            confidence = 0.98,
            status = TransactionStatus.POSTED,
            note = "Dining / Food order [Engine: GEMINI_NANO]",
            countsTowardBudget = true,
            accountName = "HDFC Bank",
            accountKind = AccountKind.BANK,
        )

        assertEquals("GEMINI_NANO", nanoRecord.parseEngine)
        assertEquals("Dining / Food order", nanoRecord.cleanNote)

        val regexRecord = nanoRecord.copy(note = "Payment to Uber [Engine: LOCAL_REGEX]")
        assertEquals("LOCAL_REGEX", regexRecord.parseEngine)
        assertEquals("Payment to Uber", regexRecord.cleanNote)

        val cloudRecord = nanoRecord.copy(note = "Shopping order [Engine: GEMINI_CLOUD]")
        assertEquals("GEMINI_CLOUD", cloudRecord.parseEngine)
        assertEquals("Shopping order", cloudRecord.cleanNote)

        val degradedRecord = nanoRecord.copy(note = "Recovered Transaction [Engine: DEGRADED_MANUAL]")
        assertEquals("DEGRADED_MANUAL", degradedRecord.parseEngine)
        assertEquals("Recovered Transaction", degradedRecord.cleanNote)

        val entity = TransactionEntity(
            id = 2L,
            amount = 300.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = System.currentTimeMillis(),
            merchant = "Zomato",
            category = TransactionCategory.FOOD,
            accountId = null,
            sourceSender = "ICICI",
            smsBody = null,
            confidence = 0.95,
            fingerprint = "fp123",
            status = TransactionStatus.POSTED,
            note = "Zomato order [Engine: GEMINI_NANO]",
        )
        assertEquals("GEMINI_NANO", entity.parseEngine)
        assertEquals("Zomato order", entity.cleanNote)
    }

    @Test
    fun testHonestDeviceDiagnostics() {
        val readyNano = AiCoreNanoManager(initialStatus = NanoAvailabilityStatus.Ready)
        val readyEngine = OnDeviceAiEngine(aiCoreNanoManager = readyNano)
        assertEquals("Gemini Nano", readyEngine.getActiveParserName())
        assertTrue(readyEngine.getDeviceStatus().contains("Gemini Nano Active"))
        assertTrue(readyEngine.getHardwareAcceleratorName().contains("Gemini Nano Active"))

        val unsupportedNano = AiCoreNanoManager(initialStatus = NanoAvailabilityStatus.UnsupportedDevice)
        val fallbackEngine = OnDeviceAiEngine(aiCoreNanoManager = unsupportedNano)
        assertEquals("Local Regex Engine (CPU)", fallbackEngine.getActiveParserName())
        assertTrue(fallbackEngine.getDeviceStatus().contains("Local Deterministic Regex Engine (Ultra-Fast CPU)"))
        assertTrue(fallbackEngine.getHardwareAcceleratorName().contains("Local Deterministic Regex Engine (Ultra-Fast CPU)"))
    }
}
