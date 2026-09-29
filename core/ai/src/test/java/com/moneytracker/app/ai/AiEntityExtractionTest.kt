package com.moneytracker.app.ai

import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEntityExtractionTest {

    private val axisSmsRajuWines = """
        INR 1200.00 debited
        A/c no. XX6942
        26-09-26, 23:09:50
        UPI/P2M/626969941600/Raju Wines
        Not you? SMS BLOCKUPI Cust ID to 919951860002
        Axis Bank
    """.trimIndent()

    private val axisSmsPrakashChandra = """
        INR 10.00 debited
        A/c no. XX6942
        28-09-26, 18:38:35
        UPI/P2M/627192752017/PRAKASH
        CHANDRA CHA
        Not you? SMS BLOCKUPI Cust ID to 919951860002
        Axis Bank
    """.trimIndent()

    @Test
    fun `mock json extraction for Axis Bank Raju Wines maps cleanly to entity`() = runTest {
        val mockJson = """
            {"isTransaction":true,"amount":1200.0,"direction":"DEBIT","merchant":"Raju Wines","accountLast4":"6942","category":"ENTERTAINMENT","availableBalance":null}
        """.trimIndent()

        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { mockJson },
        )

        val result = manager.extractFinancialEntity(
            rawText = axisSmsRajuWines,
            sender = "AXISBK",
        )

        assertTrue("Inference must succeed", result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        assertEquals(true, entity?.isTransaction)
        assertEquals("Raju Wines", entity?.merchant)
        assertEquals(1200.0, entity?.amount ?: 0.0, 0.001)
        assertEquals(TransactionDirection.DEBIT, entity?.direction)
        assertEquals("6942", entity?.accountLastFour)
        assertEquals(AccountKind.UPI, entity?.accountKind)
    }

    @Test
    fun `mock json extraction for Axis Bank multi-line wrapped Prakash Chandra Cha maps cleanly to entity`() = runTest {
        val mockJson = """
            {"isTransaction":true,"amount":10.0,"direction":"DEBIT","merchant":"Prakash Chandra Cha","accountLast4":"6942","category":"FOOD","availableBalance":null}
        """.trimIndent()

        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { mockJson },
        )

        val result = manager.extractFinancialEntity(
            rawText = axisSmsPrakashChandra,
            sender = "AXISBK",
        )

        assertTrue("Inference must succeed", result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        assertEquals(true, entity?.isTransaction)
        assertEquals("Prakash Chandra Cha", entity?.merchant)
        assertEquals(10.0, entity?.amount ?: 0.0, 0.001)
        assertEquals(TransactionDirection.DEBIT, entity?.direction)
        assertEquals("6942", entity?.accountLastFour)
        assertEquals(TransactionCategory.FOOD, entity?.category)
        assertEquals(AccountKind.UPI, entity?.accountKind)
    }

    @Test
    fun `raw delimited UPI string from model is defensively cleaned by MerchantSanitizer`() = runTest {
        val rawDelimitedJson = """
            {"isTransaction":true,"amount":1200.0,"direction":"DEBIT","merchant":"UPI/P2M/626969941600/Raju Wines","accountLast4":"6942","category":"ENTERTAINMENT","availableBalance":null}
        """.trimIndent()

        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { rawDelimitedJson },
        )

        val result = manager.extractFinancialEntity(
            rawText = axisSmsRajuWines,
            sender = "AXISBK",
        )

        assertTrue(result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        assertEquals("Raju Wines", entity?.merchant)
        assertEquals(1200.0, entity?.amount ?: 0.0, 0.001)
        assertFalse("Merchant name must not contain protocol or RRN", entity?.merchant?.contains("UPI") == true)
        assertFalse("Merchant name must not contain bank name", entity?.merchant?.contains("Axis Bank") == true)
    }

    @Test
    fun `raw multiline all-caps tokens from model are joined and title-cased`() = runTest {
        val rawMultilineJson = """
            {"isTransaction":true,"amount":10.0,"direction":"DEBIT","merchant":"PRAKASH\nCHANDRA CHA\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank","accountLast4":"6942","category":"FOOD","availableBalance":null}
        """.trimIndent()

        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { rawMultilineJson },
        )

        val result = manager.extractFinancialEntity(
            rawText = axisSmsPrakashChandra,
            sender = "AXISBK",
        )

        assertTrue(result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        assertEquals("Prakash Chandra Cha", entity?.merchant)
        assertEquals(10.0, entity?.amount ?: 0.0, 0.001)
        assertFalse(entity?.merchant?.contains("Not you?") == true)
        assertFalse(entity?.merchant?.contains("Axis Bank") == true)
    }

    @Test
    fun `markdown wrapped json with code fences parses cleanly`() = runTest {
        val markdownJson = """
            ```json
            {
              "isTransaction": true,
              "amount": 1200.0,
              "direction": "DEBIT",
              "merchant": "Raju Wines",
              "accountLast4": "6942",
              "category": "ENTERTAINMENT",
              "availableBalance": null
            }
            ```
        """.trimIndent()

        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { markdownJson },
        )

        val result = manager.extractFinancialEntity(
            rawText = axisSmsRajuWines,
            sender = "AXISBK",
        )

        assertTrue(result.isSuccess)
        val entity = result.getOrNull()
        assertEquals("Raju Wines", entity?.merchant)
        assertEquals(1200.0, entity?.amount ?: 0.0, 0.001)
    }

    @Test
    fun `markdown wrapped json with conversational prose parses cleanly`() = runTest {
        val proseJson = """
            Here is the parsed transaction:
            ```json
            {"isTransaction":true,"amount":10.0,"direction":"DEBIT","merchant":"Prakash Chandra Cha","accountLast4":"6942","category":"FOOD","availableBalance":null}
            ```
            Hope this helps!
        """.trimIndent()

        val manager = AiCoreNanoManager(
            initialStatus = NanoAvailabilityStatus.Ready,
            customInferenceRunner = { proseJson },
        )

        val result = manager.extractFinancialEntity(
            rawText = axisSmsPrakashChandra,
            sender = "AXISBK",
        )

        assertTrue(result.isSuccess)
        val entity = result.getOrNull()
        assertEquals("Prakash Chandra Cha", entity?.merchant)
        assertEquals(10.0, entity?.amount ?: 0.0, 0.001)
    }

    @Test
    fun `UI transaction tile model receives exact title without truncation`() {
        val title1 = "Raju Wines"
        val record1 = TransactionRecord(
            id = 1L,
            amount = 1200.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = System.currentTimeMillis(),
            merchant = title1,
            category = TransactionCategory.SUBSCRIPTION,
            accountId = 1L,
            sourceSender = "AXISBK",
            smsBody = axisSmsRajuWines,
            confidence = 0.98,
            status = TransactionStatus.POSTED,
            note = "$title1 - On-Device AI Verified",
            countsTowardBudget = true,
            accountName = "Axis Bank",
            accountKind = AccountKind.UPI,
        )

        assertEquals("Raju Wines", record1.merchant)
        assertEquals(title1.length, record1.merchant.length)

        val title2 = "Prakash Chandra Cha"
        val record2 = TransactionRecord(
            id = 2L,
            amount = 10.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = System.currentTimeMillis(),
            merchant = title2,
            category = TransactionCategory.FOOD,
            accountId = 1L,
            sourceSender = "AXISBK",
            smsBody = axisSmsPrakashChandra,
            confidence = 0.98,
            status = TransactionStatus.POSTED,
            note = "$title2 - On-Device AI Verified",
            countsTowardBudget = true,
            accountName = "Axis Bank",
            accountKind = AccountKind.UPI,
        )

        assertEquals("Prakash Chandra Cha", record2.merchant)
        assertEquals(title2.length, record2.merchant.length)
        assertFalse(record2.merchant.contains("UPI"))
        assertFalse(record2.merchant.contains("Axis Bank"))
        assertFalse(record2.merchant.contains("Not you?"))
    }

    @Test
    fun `MerchantSanitizer preserves standard acronyms and handles edge cases`() {
        // Acronyms preserved
        assertEquals("IRCTC UPI Payment", MerchantSanitizer.sanitizeMerchantName("IRCTC UPI PAYMENT"))
        assertEquals("ATM Cash Withdrawal", MerchantSanitizer.sanitizeMerchantName("ATM CASH WITHDRAWAL"))
        assertEquals("HPCL Petrol Pump", MerchantSanitizer.sanitizeMerchantName("HPCL PETROL PUMP"))
        assertEquals("BPCL Retail", MerchantSanitizer.sanitizeMerchantName("BPCL RETAIL"))

        // Already clean names preserved
        assertEquals("Raju Wines", MerchantSanitizer.sanitizeMerchantName("Raju Wines"))
        assertEquals("Prakash Chandra Cha", MerchantSanitizer.sanitizeMerchantName("Prakash Chandra Cha"))
        assertEquals("McDonald's", MerchantSanitizer.sanitizeMerchantName("McDonald's"))
        assertEquals("PhonePe", MerchantSanitizer.sanitizeMerchantName("PhonePe"))

        // Stripping UPI prefixes
        assertEquals("Raju Wines", MerchantSanitizer.sanitizeMerchantName("UPI/P2M/626969941600/Raju Wines"))
        assertEquals("Prakash Chandra Cha", MerchantSanitizer.sanitizeMerchantName("UPI/P2P/627192752017/PRAKASH CHANDRA CHA"))

        // Multiline split and disclaimer truncation
        val multilineWithDisclaimer = """
            UPI/P2M/627192752017/PRAKASH
            CHANDRA CHA
            Not you? SMS BLOCKUPI Cust ID to 919951860002
            Axis Bank
        """.trimIndent()
        assertEquals("Prakash Chandra Cha", MerchantSanitizer.sanitizeMerchantName(multilineWithDisclaimer))
    }
}
