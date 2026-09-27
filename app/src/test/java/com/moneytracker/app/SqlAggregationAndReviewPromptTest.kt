package com.moneytracker.app

import com.moneytracker.app.data.db.CategorySpendAggregate
import com.moneytracker.app.data.model.CategorySlice
import com.moneytracker.app.data.model.DashboardState
import com.moneytracker.app.data.model.TransactionCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class SqlAggregationAndReviewPromptTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCategorySpendAggregateMapping() {
        val aggregates = listOf(
            CategorySpendAggregate(category = "FOOD", totalAmount = 1250.50),
            CategorySpendAggregate(category = "TRAVEL", totalAmount = 800.00),
            CategorySpendAggregate(category = "SHOPPING", totalAmount = 3400.75),
        )

        val slices = aggregates.map { agg ->
            CategorySlice(
                category = TransactionCategory.valueOf(agg.category),
                amount = agg.totalAmount,
            )
        }.sortedByDescending { it.amount }

        assertEquals(3, slices.size)
        assertEquals(TransactionCategory.SHOPPING, slices[0].category)
        assertEquals(3400.75, slices[0].amount, 0.001)

        assertEquals(TransactionCategory.FOOD, slices[1].category)
        assertEquals(1250.50, slices[1].amount, 0.001)

        assertEquals(TransactionCategory.TRAVEL, slices[2].category)
        assertEquals(800.00, slices[2].amount, 0.001)
    }

    @Test
    fun testDashboardCalculationsFromAggregates() {
        val monthSpent = 5000.0
        val monthIncome = 15000.0
        val cardSpend = 2000.0
        val bankSpend = (monthSpent - cardSpend).coerceAtLeast(0.0)
        val monthNetCashflow = monthIncome - monthSpent
        val budgetLimit = 10000.0

        val targetMonth = YearMonth.now()
        val today = LocalDate.now()
        val daysInMonth = targetMonth.lengthOfMonth()
        val daysRemaining = (daysInMonth - today.dayOfMonth + 1).coerceAtLeast(1)
        val remainingBudget = (budgetLimit - monthSpent).coerceAtLeast(0.0)
        val safeDailySpend = remainingBudget / daysRemaining
        val budgetPercentUsed = (monthSpent / budgetLimit).toFloat()

        val dashboard = DashboardState(
            monthSpent = monthSpent,
            monthIncome = monthIncome,
            monthNetCashflow = monthNetCashflow,
            cardSpendThisMonth = cardSpend,
            bankSpendThisMonth = bankSpend,
            safeDailySpend = safeDailySpend,
            budgetPercentUsed = budgetPercentUsed,
            budgetLimit = budgetLimit,
            reviewCount = 2,
            selectedYearMonth = targetMonth,
        )

        assertEquals(5000.0, dashboard.monthSpent, 0.001)
        assertEquals(15000.0, dashboard.monthIncome, 0.001)
        assertEquals(10000.0, dashboard.monthNetCashflow, 0.001)
        assertEquals(2000.0, dashboard.cardSpendThisMonth, 0.001)
        assertEquals(3000.0, dashboard.bankSpendThisMonth, 0.001)
        assertEquals(0.5f, dashboard.budgetPercentUsed, 0.001f)
        assertEquals(2, dashboard.reviewCount)
    }
}
