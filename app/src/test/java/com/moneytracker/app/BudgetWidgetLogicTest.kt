package com.moneytracker.app

import com.moneytracker.app.ui.asCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetWidgetLogicTest {

    @Test
    fun `test safe daily spend calculation with normal budget`() {
        val budgetLimit = 30000.0
        val monthSpent = 12000.0
        val daysRemaining = 15

        val remainingBudget = (budgetLimit - monthSpent).coerceAtLeast(0.0)
        val safeDailySpend = if (budgetLimit > 0) remainingBudget / daysRemaining else 0.0
        val percentUsed = if (budgetLimit > 0) ((monthSpent / budgetLimit) * 100).toInt().coerceIn(0, 100) else 0

        assertEquals(18000.0, remainingBudget, 0.001)
        assertEquals(1200.0, safeDailySpend, 0.001)
        assertEquals(40, percentUsed)
    }

    @Test
    fun `test safe daily spend calculation when budget is exceeded`() {
        val budgetLimit = 25000.0
        val monthSpent = 28000.0
        val daysRemaining = 10

        val remainingBudget = (budgetLimit - monthSpent).coerceAtLeast(0.0)
        val safeDailySpend = if (budgetLimit > 0) remainingBudget / daysRemaining else 0.0
        val percentUsed = if (budgetLimit > 0) ((monthSpent / budgetLimit) * 100).toInt().coerceIn(0, 100) else 0

        assertEquals(0.0, remainingBudget, 0.001)
        assertEquals(0.0, safeDailySpend, 0.001)
        assertEquals(100, percentUsed)
    }

    @Test
    fun `test days remaining calculation on last day of month`() {
        val daysInMonth = 31
        val currentDay = 31
        val daysRemaining = (daysInMonth - currentDay + 1).coerceAtLeast(1)

        assertEquals(1, daysRemaining)
    }

    @Test
    fun `test widget display text formatting with currency`() {
        val safeDailySpend = 850.0
        val monthSpent = 14500.0
        val budgetLimit = 30000.0
        val percentUsed = 48

        val spentText = "${monthSpent.asCurrency()} of ${budgetLimit.asCurrency()}"
        val percentText = "$percentUsed%"

        assertTrue(spentText.contains("14,500"))
        assertTrue(spentText.contains("30,000"))
        assertEquals("48%", percentText)
    }

    @Test
    fun `test fallback when no budget is configured`() {
        val budgetLimit = 0.0
        val monthSpent = 7500.0
        val daysRemaining = 20

        val remainingBudget = if (budgetLimit > 0) (budgetLimit - monthSpent).coerceAtLeast(0.0) else 0.0
        val safeDailySpend = if (budgetLimit > 0) remainingBudget / daysRemaining else 0.0
        val percentUsed = if (budgetLimit > 0) ((monthSpent / budgetLimit) * 100).toInt().coerceIn(0, 100) else 0

        assertEquals(0.0, remainingBudget, 0.001)
        assertEquals(0.0, safeDailySpend, 0.001)
        assertEquals(0, percentUsed)
    }
}
