package com.moneytracker.app.growth

import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class FintechWrappedEngineTest {

    private val zoneId = ZoneId.of("UTC")
    // September 2026: starts on Tuesday, 30 days total (8 weekend days: 5,6, 12,13, 19,20, 26,27; 22 weekdays)
    private val testMonth = YearMonth.of(2026, 9)

    private fun dateToMillis(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        return LocalDate.of(year, month, day)
            .atTime(hour, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private fun createTx(
        id: Long,
        amount: Double,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        occurredAtMillis: Long = dateToMillis(2026, 9, 15),
        merchant: String = "Test Merchant",
        category: TransactionCategory = TransactionCategory.FOOD,
        status: TransactionStatus = TransactionStatus.POSTED,
        countsTowardBudget: Boolean = true,
        accountName: String? = "Savings Account",
        note: String? = null,
    ): TransactionRecord = TransactionRecord(
        id = id,
        amount = amount,
        direction = direction,
        occurredAtMillis = occurredAtMillis,
        merchant = merchant,
        category = category,
        accountId = 1L,
        sourceSender = "HDFC-BANK",
        smsBody = null,
        confidence = 1.0,
        status = status,
        note = note,
        countsTowardBudget = countsTowardBudget,
        accountName = accountName,
        accountKind = AccountKind.BANK,
        availableBalance = null,
    )

    @Test
    fun `test net savings rate calculation`() {
        val txs = listOf(
            createTx(id = 1, amount = 100000.0, direction = TransactionDirection.CREDIT, category = TransactionCategory.SALARY),
            createTx(id = 2, amount = 35000.0, direction = TransactionDirection.DEBIT, category = TransactionCategory.FOOD),
            createTx(id = 3, amount = 15000.0, direction = TransactionDirection.DEBIT, category = TransactionCategory.BILLS),
        )

        val wrapped = FintechWrappedEngine.computeMetrics(txs, testMonth, zoneId)

        assertEquals(100000.0, wrapped.totalCredit, 0.001)
        assertEquals(50000.0, wrapped.totalDebit, 0.001)
        // (100000 - 50000) / 100000 * 100 = 50.0%
        assertEquals(50.0, wrapped.netSavingsRate, 0.001)
    }

    @Test
    fun `test top 3 spending categories computation`() {
        val txs = listOf(
            createTx(id = 1, amount = 20000.0, category = TransactionCategory.FOOD),
            createTx(id = 2, amount = 15000.0, category = TransactionCategory.SHOPPING),
            createTx(id = 3, amount = 10000.0, category = TransactionCategory.TRAVEL),
            createTx(id = 4, amount = 5000.0, category = TransactionCategory.BILLS),
            createTx(id = 5, amount = 1000.0, category = TransactionCategory.HEALTH),
        )

        val wrapped = FintechWrappedEngine.computeMetrics(txs, testMonth, zoneId)

        assertEquals(3, wrapped.topCategories.size)
        assertEquals(TransactionCategory.FOOD, wrapped.topCategories[0].category)
        assertEquals(20000.0, wrapped.topCategories[0].totalAmount, 0.001)
        assertEquals(TransactionCategory.SHOPPING, wrapped.topCategories[1].category)
        assertEquals(15000.0, wrapped.topCategories[1].totalAmount, 0.001)
        assertEquals(TransactionCategory.TRAVEL, wrapped.topCategories[2].category)
        assertEquals(10000.0, wrapped.topCategories[2].totalAmount, 0.001)
    }

    @Test
    fun `test top merchant aggregation by volume`() {
        val txs = listOf(
            createTx(id = 1, amount = 300.0, merchant = "Swiggy"),
            createTx(id = 2, amount = 450.0, merchant = "Swiggy"),
            createTx(id = 3, amount = 600.0, merchant = "Uber"),
            createTx(id = 4, amount = 150.0, merchant = "Swiggy"),
        )

        val wrapped = FintechWrappedEngine.computeMetrics(txs, testMonth, zoneId)

        assertNotNull(wrapped.topMerchant)
        assertEquals("Swiggy", wrapped.topMerchant?.merchant)
        assertEquals(900.0, wrapped.topMerchant?.totalAmount ?: 0.0, 0.001)
        assertEquals(3, wrapped.topMerchant?.transactionCount)
    }

    @Test
    fun `test weekend spend velocity vs weekday velocity`() {
        // Sep 5, 2026 is Saturday (weekend)
        // Sep 6, 2026 is Sunday (weekend)
        // Sep 7, 2026 is Monday (weekday)
        // Sep 8, 2026 is Tuesday (weekday)
        val weekendTx1 = createTx(id = 1, amount = 4000.0, occurredAtMillis = dateToMillis(2026, 9, 5))
        val weekendTx2 = createTx(id = 2, amount = 4000.0, occurredAtMillis = dateToMillis(2026, 9, 6))
        val weekdayTx1 = createTx(id = 3, amount = 1100.0, occurredAtMillis = dateToMillis(2026, 9, 7))
        val weekdayTx2 = createTx(id = 4, amount = 1100.0, occurredAtMillis = dateToMillis(2026, 9, 8))

        val wrapped = FintechWrappedEngine.computeMetrics(
            listOf(weekendTx1, weekendTx2, weekdayTx1, weekdayTx2),
            testMonth,
            zoneId,
        )

        // September 2026 has 8 weekend days and 22 weekday days.
        // weekendSpend = 8000.0 / 8 = 1000.0 / day
        // weekdaySpend = 2200.0 / 22 = 100.0 / day
        assertEquals(1000.0, wrapped.weekendDailyVelocity, 0.001)
        assertEquals(100.0, wrapped.weekdayDailyVelocity, 0.001)
        assertEquals(10.0, wrapped.weekendVelocityRatio, 0.001)
    }

    @Test
    fun `test persona assignment - Fortress Builder for high savings rate`() {
        val txs = listOf(
            createTx(id = 1, amount = 100000.0, direction = TransactionDirection.CREDIT, category = TransactionCategory.SALARY),
            createTx(id = 2, amount = 30000.0, direction = TransactionDirection.DEBIT, category = TransactionCategory.FOOD),
        )

        val wrapped = FintechWrappedEngine.computeMetrics(txs, testMonth, zoneId)

        // Savings rate = 70% >= 40% -> The Fortress Builder
        assertEquals("The Fortress Builder", wrapped.persona.title)
        assertEquals("🛡️", wrapped.persona.emoji)
    }

    @Test
    fun `test persona assignment - Weekend Epicurean for high dining spend`() {
        val txs = listOf(
            createTx(id = 1, amount = 50000.0, direction = TransactionDirection.CREDIT, category = TransactionCategory.SALARY),
            createTx(id = 2, amount = 20000.0, direction = TransactionDirection.DEBIT, category = TransactionCategory.FOOD),
            createTx(id = 3, amount = 15000.0, direction = TransactionDirection.DEBIT, category = TransactionCategory.BILLS),
        )

        val wrapped = FintechWrappedEngine.computeMetrics(txs, testMonth, zoneId)

        // Total debit = 35000. Food = 20000 (57.1% >= 30%)
        // Savings rate = 30% (< 40%)
        assertEquals("The Weekend Epicurean", wrapped.persona.title)
        assertEquals("🍽️", wrapped.persona.emoji)
    }

    @Test
    fun `test persona assignment - Sunday Sprinter for high weekend velocity`() {
        // High weekend spend pace, moderate savings
        val weekendTx = createTx(id = 1, amount = 8000.0, occurredAtMillis = dateToMillis(2026, 9, 5), category = TransactionCategory.OTHER)
        val weekdayTx = createTx(id = 2, amount = 2200.0, occurredAtMillis = dateToMillis(2026, 9, 7), category = TransactionCategory.OTHER)
        val creditTx = createTx(id = 3, amount = 12000.0, direction = TransactionDirection.CREDIT, category = TransactionCategory.SALARY)

        val wrapped = FintechWrappedEngine.computeMetrics(
            listOf(weekendTx, weekdayTx, creditTx),
            testMonth,
            zoneId,
        )

        // Savings rate = (12000 - 10200) / 12000 = 15% (< 40%)
        // Weekend velocity = 8000 / 8 = 1000 / day
        // Weekday velocity = 2200 / 22 = 100 / day
        // Ratio = 10.0 >= 2.0 -> The Sunday Sprinter
        assertEquals("The Sunday Sprinter", wrapped.persona.title)
        assertEquals("⚡", wrapped.persona.emoji)
    }

    @Test
    fun `test AI punchline injection overrides default punchline`() {
        val txs = listOf(
            createTx(id = 1, amount = 50000.0, direction = TransactionDirection.CREDIT, category = TransactionCategory.SALARY),
            createTx(id = 2, amount = 20000.0, direction = TransactionDirection.DEBIT, category = TransactionCategory.FOOD),
        )

        val customAiPunchline = "Your culinary ventures would make Gordon Ramsay smile."
        val wrapped = FintechWrappedEngine.computeMetrics(
            transactions = txs,
            yearMonth = testMonth,
            zoneId = zoneId,
            customPunchline = customAiPunchline,
        )

        assertTrue(wrapped.isAIGeneratedPunchline)
        assertEquals(customAiPunchline, wrapped.persona.punchline)
    }

    @Test
    fun `test privacy guard - merchant sanitization masks card digits and account numbers`() {
        // Raw SMS/bank strings often leak card last-4 or account identifiers
        val rawMerchant1 = "Uber India Card 4321"
        val rawMerchant2 = "Swiggy UPI A/C 9876543210"
        val rawMerchant3 = "Amazon Retail 8842"
        val rawMerchant4 = "Starbucks Coffee"

        val sanitized1 = FintechWrappedEngine.sanitizeMerchantName(rawMerchant1)
        val sanitized2 = FintechWrappedEngine.sanitizeMerchantName(rawMerchant2)
        val sanitized3 = FintechWrappedEngine.sanitizeMerchantName(rawMerchant3)
        val sanitized4 = FintechWrappedEngine.sanitizeMerchantName(rawMerchant4)

        assertFalse("Should not contain 4321", sanitized1.contains("4321"))
        assertTrue("Should be masked with dots", sanitized1.contains("••••"))

        assertFalse("Should not contain account number", sanitized2.contains("9876543210"))
        assertTrue("Should be masked with dots", sanitized2.contains("••••"))

        assertFalse("Should not contain 8842", sanitized3.contains("8842"))
        assertTrue("Should be masked with dots", sanitized3.contains("••••"))

        assertEquals("Starbucks Coffee", sanitized4)
    }

    @Test
    fun `test privacy guard - wrapped data model contains zero raw bank balances`() {
        val tx = createTx(
            id = 10,
            amount = 1200.0,
            accountName = "HDFC Bank •••• 9999",
        )

        val wrapped = FintechWrappedEngine.computeMetrics(listOf(tx), testMonth, zoneId)

        // Ensure no raw balance properties exist in FintechWrappedData
        val properties = FintechWrappedData::class.java.declaredFields.map { it.name }
        assertFalse(properties.contains("bankBalance"))
        assertFalse(properties.contains("accountBalance"))
        assertFalse(properties.contains("currentBalance"))
        assertFalse(properties.contains("rawBalance"))
        assertFalse(properties.contains("accountNumber"))
    }
}
