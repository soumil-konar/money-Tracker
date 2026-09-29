package com.moneytracker.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeSpendGlanceWidgetLogicTest {

    private fun calculatePacing(
        budgetLimit: Double,
        monthSpent: Double,
        maxDays: Int,
        currentDay: Int,
    ): SafeSpendState {
        val daysRemaining = (maxDays - currentDay + 1).coerceAtLeast(1)
        val remainingBudget = if (budgetLimit > 0) (budgetLimit - monthSpent).coerceAtLeast(0.0) else 0.0
        val safeDailySpend = if (budgetLimit > 0) remainingBudget / daysRemaining else 0.0
        val percentUsed = if (budgetLimit > 0) ((monthSpent / budgetLimit) * 100).toInt().coerceIn(0, 100) else 0
        val progressFloat = if (budgetLimit > 0) (monthSpent / budgetLimit).toFloat().coerceIn(0f, 1f) else 0f

        return SafeSpendState(
            monthName = "September 2026",
            monthSpent = monthSpent,
            budgetLimit = budgetLimit,
            safeDailySpend = safeDailySpend,
            percentUsed = percentUsed,
            progressFloat = progressFloat,
            daysRemaining = daysRemaining,
        )
    }

    @Test
    fun `test pacing calculation with healthy budget`() {
        val state = calculatePacing(
            budgetLimit = 40000.0,
            monthSpent = 16000.0,
            maxDays = 30,
            currentDay = 11, // 20 days remaining
        )

        assertEquals(20, state.daysRemaining)
        assertEquals(40, state.percentUsed)
        assertEquals(0.4f, state.progressFloat, 0.001f)
        // remaining = 24000.0 / 20 = 1200.0 / day
        assertEquals(1200.0, state.safeDailySpend, 0.001)
    }

    @Test
    fun `test pacing calculation when budget exceeded`() {
        val state = calculatePacing(
            budgetLimit = 20000.0,
            monthSpent = 25000.0,
            maxDays = 30,
            currentDay = 25,
        )

        assertEquals(6, state.daysRemaining)
        assertEquals(100, state.percentUsed)
        assertEquals(1.0f, state.progressFloat, 0.001f)
        assertEquals(0.0, state.safeDailySpend, 0.001)
    }

    @Test
    fun `test pacing calculation when no budget set`() {
        val state = calculatePacing(
            budgetLimit = 0.0,
            monthSpent = 8500.0,
            maxDays = 30,
            currentDay = 15,
        )

        assertEquals(0.0, state.safeDailySpend, 0.001)
        assertEquals(0, state.percentUsed)
        assertEquals(0.0f, state.progressFloat, 0.001f)
    }
}
