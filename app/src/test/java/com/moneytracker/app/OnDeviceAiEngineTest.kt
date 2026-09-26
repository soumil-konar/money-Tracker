package com.moneytracker.app

import com.moneytracker.app.ai.OnDeviceAiEngine
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.CardType
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class OnDeviceAiEngineTest {

    private val engine = OnDeviceAiEngine()

    private fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in vecA.indices) {
            val a = vecA[i]
            val b = vecB[i]
            dot += a * b
            normA += a * a
            normB += b * b
        }
        if (normA <= 0f || normB <= 0f) return 0f
        return dot / (sqrt(normA) * sqrt(normB))
    }

    @Test
    fun `parseSmsOnDevice correctly extracts debit transaction with place and merchant`() {
        val sms = "Rs 450.00 debited from HDFC Bank A/c xx1234 at Starbucks, Indiranagar on 12-Oct-24. Avl Bal: Rs 12,000"
        val sender = "HDFCBK"

        val result = engine.parseSmsOnDevice(sms, sender)
        assertTrue(result.isSuccess)
        val parsed = result.getOrNull()
        assertNotNull(parsed)
        assertTrue(parsed!!.isTransaction)
        assertEquals(450.0, parsed.amount ?: 0.0, 0.01)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertTrue(parsed.merchant?.contains("Starbucks", ignoreCase = true) == true)
        assertEquals(TransactionCategory.FOOD, parsed.category)
        assertEquals("HDFC Bank", parsed.institutionName)
        assertEquals("1234", parsed.accountLastFour)
        assertEquals("Indiranagar", parsed.placeDetail)
    }

    @Test
    fun `parseSmsOnDevice correctly extracts credit card spend with location`() {
        val sms = "Spent INR 1,899.00 on your ICICI Bank Credit Card ending 5678 at Zomato Bangalore. Info: P2A."
        val sender = "ICICIB"

        val result = engine.parseSmsOnDevice(sms, sender)
        assertTrue(result.isSuccess)
        val parsed = result.getOrNull()
        assertNotNull(parsed)
        assertTrue(parsed!!.isTransaction)
        assertEquals(1899.0, parsed.amount ?: 0.0, 0.01)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertEquals(AccountKind.CARD, parsed.accountKind)
        assertEquals(CardType.CREDIT, parsed.cardType)
        assertEquals("5678", parsed.accountLastFour)
        assertEquals("Bangalore", parsed.placeDetail)
        assertEquals(TransactionCategory.FOOD, parsed.category)
    }

    @Test
    fun `parseSmsOnDevice correctly extracts credit deposit`() {
        val sms = "INR 85,000.00 credited to your SBI A/c xx9876 on 01-Nov-24 by Infosys Payroll."
        val sender = "SBIBNK"

        val result = engine.parseSmsOnDevice(sms, sender)
        assertTrue(result.isSuccess)
        val parsed = result.getOrNull()
        assertNotNull(parsed)
        assertTrue(parsed!!.isTransaction)
        assertEquals(85000.0, parsed.amount ?: 0.0, 0.01)
        assertEquals(TransactionDirection.CREDIT, parsed.direction)
        assertEquals("State Bank of India", parsed.institutionName)
        assertEquals("9876", parsed.accountLastFour)
    }

    @Test
    fun `parseSmsOnDevice rejects OTP messages`() {
        val sms = "Your OTP for netbanking login is 492018. Do not share this secret code with anyone."
        val sender = "HDFCBK"

        val result = engine.parseSmsOnDevice(sms, sender)
        assertTrue(result.isSuccess)
        val parsed = result.getOrNull()
        assertNotNull(parsed)
        assertFalse(parsed!!.isTransaction)
    }

    @Test
    fun `parseSmsOnDevice detects credit card bill payment and excludes from budget`() {
        val sms = "Payment of INR 15,000 received towards your HDFC Bank credit card ending 4321 from A/c xx9999 on 15-Nov-24."
        val sender = "HDFCBK"

        val result = engine.parseSmsOnDevice(sms, sender)
        assertTrue(result.isSuccess)
        val parsed = result.getOrNull()
        assertNotNull(parsed)
        assertTrue(parsed!!.isTransaction)
        assertEquals(15000.0, parsed.amount ?: 0.0, 0.01)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertEquals(TransactionCategory.TRANSFER, parsed.category)
        assertTrue(parsed.isCardBillPayment)
        assertFalse(parsed.countsTowardBudget)
    }

    @Test
    fun `parseSmsOnDevice detects self transfer and excludes from budget`() {
        val sms = "Rs 10,000 debited from A/c xx1234 on transfer to self account xx5678."
        val sender = "ICICIB"

        val result = engine.parseSmsOnDevice(sms, sender)
        assertTrue(result.isSuccess)
        val parsed = result.getOrNull()
        assertNotNull(parsed)
        assertTrue(parsed!!.isTransaction)
        assertEquals(10000.0, parsed.amount ?: 0.0, 0.01)
        assertEquals(TransactionCategory.TRANSFER, parsed.category)
        assertFalse(parsed.countsTowardBudget)
    }

    @Test
    fun `queryAssistantOnDevice calculates food spending accurately`() {
        val tx1 = TransactionRecord(
            id = 1L,
            amount = 450.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = System.currentTimeMillis(),
            merchant = "Starbucks Indiranagar",
            category = TransactionCategory.FOOD,
            accountId = 1L,
            sourceSender = "HDFCBK",
            smsBody = null,
            confidence = 0.98,
            status = TransactionStatus.POSTED,
            note = "Indiranagar",
            countsTowardBudget = true,
            accountName = "HDFC Bank",
            accountKind = AccountKind.BANK,
        )
        val tx2 = TransactionRecord(
            id = 2L,
            amount = 3200.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = System.currentTimeMillis(),
            merchant = "Zara Brigade Road",
            category = TransactionCategory.SHOPPING,
            accountId = 1L,
            sourceSender = "HDFCBK",
            smsBody = null,
            confidence = 0.98,
            status = TransactionStatus.POSTED,
            note = "Brigade Road",
            countsTowardBudget = true,
            accountName = "HDFC Bank",
            accountKind = AccountKind.BANK,
        )
        val tx3 = TransactionRecord(
            id = 3L,
            amount = 350.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = System.currentTimeMillis(),
            merchant = "Third Wave Coffee",
            category = TransactionCategory.FOOD,
            accountId = 1L,
            sourceSender = "HDFCBK",
            smsBody = null,
            confidence = 0.98,
            status = TransactionStatus.POSTED,
            note = "Koramangala",
            countsTowardBudget = true,
            accountName = "HDFC Bank",
            accountKind = AccountKind.BANK,
        )

        val result = engine.queryAssistantOnDevice(
            userQuery = "How much did I spend on dining and coffee?",
            retrievedTransactions = listOf(tx1, tx2, tx3),
            macroContext = "Monthly budget limit: ₹50,000 | Spent so far: ₹4,000",
        )

        assertTrue(result.isSuccess)
        val answer = result.getOrNull()
        assertNotNull(answer)
        assertTrue(answer!!.answer.contains("₹800.00") || answer.answer.contains("800"))
        assertTrue(answer.citedTransactionIds.contains(1L))
        assertTrue(answer.citedTransactionIds.contains(3L))
    }

    @Test
    fun `queryAssistantOnDevice locates expenses by place name`() {
        val tx = TransactionRecord(
            id = 10L,
            amount = 2500.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = System.currentTimeMillis(),
            merchant = "Toit Brewpub",
            category = TransactionCategory.FOOD,
            accountId = 1L,
            sourceSender = "HDFCBK",
            smsBody = null,
            confidence = 0.98,
            status = TransactionStatus.POSTED,
            note = "Indiranagar",
            countsTowardBudget = true,
            accountName = "HDFC Bank",
            accountKind = AccountKind.BANK,
        )

        val result = engine.queryAssistantOnDevice(
            userQuery = "Where did I spend money in Indiranagar?",
            retrievedTransactions = listOf(tx),
            macroContext = "",
        )

        assertTrue(result.isSuccess)
        val answer = result.getOrNull()
        assertNotNull(answer)
        assertTrue(answer!!.answer.contains("Toit Brewpub"))
        assertTrue(answer.answer.contains("Indiranagar"))
        assertTrue(answer.citedTransactionIds.contains(10L))
    }

    @Test
    fun `generateEmbeddingOnDevice produces 256-dimensional normalized vector`() {
        val text1 = "DEBIT ₹450 at Starbucks (Food) on 12-Oct-2024. Note: Indiranagar"
        val text2 = "DEBIT ₹450 at Starbucks (Food) on 12-Oct-2024. Note: Indiranagar"
        val text3 = "CREDIT ₹85000 at Infosys Payroll (Salary) on 01-Nov-2024."

        val emb1 = engine.generateEmbeddingOnDevice(text1).getOrNull()!!.let { list -> FloatArray(list.size) { list[it] } }
        val emb2 = engine.generateEmbeddingOnDevice(text2).getOrNull()!!.let { list -> FloatArray(list.size) { list[it] } }
        val emb3 = engine.generateEmbeddingOnDevice(text3).getOrNull()!!.let { list -> FloatArray(list.size) { list[it] } }

        assertEquals(256, emb1.size)
        assertEquals(256, emb2.size)
        assertEquals(256, emb3.size)

        val simIdentical = cosineSimilarity(emb1, emb2)
        assertEquals(1.0f, simIdentical, 0.001f)

        val simDifferent = cosineSimilarity(emb1, emb3)
        assertTrue(simDifferent < 0.95f)
    }

    @Test
    fun `generateSpendingInsightsOnDevice produces structured insights with pacing and spend drivers`() {
        val tx1 = TransactionRecord(
            id = 1L,
            amount = 1450.0,
            direction = TransactionDirection.DEBIT,
            merchant = "Swiggy",
            category = TransactionCategory.FOOD,
            accountId = 1L,
            accountName = "HDFC Bank",
            sourceSender = "HDFCBK",
            smsBody = null,
            confidence = 0.95,
            note = null,
            occurredAtMillis = System.currentTimeMillis(),
            status = TransactionStatus.POSTED,
            countsTowardBudget = true,
            accountKind = AccountKind.BANK,
        )
        val tx2 = TransactionRecord(
            id = 2L,
            amount = 3500.0,
            direction = TransactionDirection.DEBIT,
            merchant = "Uber",
            category = TransactionCategory.TRAVEL,
            accountId = 2L,
            accountName = "ICICI Card",
            sourceSender = "ICICIB",
            smsBody = null,
            confidence = 0.95,
            note = null,
            occurredAtMillis = System.currentTimeMillis(),
            status = TransactionStatus.POSTED,
            countsTowardBudget = true,
            accountKind = AccountKind.CARD,
        )
        val tx3 = TransactionRecord(
            id = 3L,
            amount = 50000.0,
            direction = TransactionDirection.CREDIT,
            merchant = "Employer Payroll",
            category = TransactionCategory.SALARY,
            accountId = 1L,
            accountName = "HDFC Bank",
            sourceSender = "HDFCBK",
            smsBody = null,
            confidence = 0.95,
            note = null,
            occurredAtMillis = System.currentTimeMillis(),
            status = TransactionStatus.POSTED,
            countsTowardBudget = true,
            accountKind = AccountKind.BANK,
        )

        val insights = engine.generateSpendingInsightsOnDevice(
            transactions = listOf(tx1, tx2, tx3),
            budgetLimit = 20000.0,
            monthSpent = 4950.0,
            monthIncome = 50000.0,
        )

        assertFalse(insights.isEmpty())
        assertTrue(insights.size in 3..4)
        // Verify budget pacing check
        assertTrue(insights.any { it.contains("budget", ignoreCase = true) || it.contains("used", ignoreCase = true) })
        // Verify spend driver check
        assertTrue(insights.any { it.contains("spend driver", ignoreCase = true) || it.contains("Travel", ignoreCase = true) })
        // Verify cashflow / savings check
        assertTrue(insights.any { it.contains("cashflow", ignoreCase = true) || it.contains("savings", ignoreCase = true) })
    }

    @Test
    fun `generateSpendingInsightsOnDevice handles empty transactions gracefully`() {
        val insights = engine.generateSpendingInsightsOnDevice(
            transactions = emptyList(),
            budgetLimit = 15000.0,
            monthSpent = 0.0,
            monthIncome = 0.0,
        )

        assertFalse(insights.isEmpty())
        assertTrue(insights.any { it.contains("No debits", ignoreCase = true) })
    }
}
