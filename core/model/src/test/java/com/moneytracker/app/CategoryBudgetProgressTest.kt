package com.moneytracker.app

import com.moneytracker.app.data.model.CategoryBudgetProgress
import com.moneytracker.app.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryBudgetProgressTest {

    @Test
    fun testCategoryBudgetProgressCalculation() {
        val category = TransactionCategory.FOOD
        val limit = 8000.0
        val spent = 3200.0
        val remaining = (limit - spent).coerceAtLeast(0.0)
        val percent = (spent / limit).toFloat()

        val progress = CategoryBudgetProgress(
            category = category,
            budgetLimit = limit,
            currentSpent = spent,
            remainingAmount = remaining,
            progressPercent = percent,
        )

        assertEquals("Food", progress.category.label)
        assertEquals(8000.0, progress.budgetLimit, 0.001)
        assertEquals(3200.0, progress.currentSpent, 0.001)
        assertEquals(4800.0, progress.remainingAmount, 0.001)
        assertEquals(0.40f, progress.progressPercent, 0.001f)
    }

    @Test
    fun testCategoryBudgetOverspendProgress() {
        val category = TransactionCategory.SHOPPING
        val limit = 5000.0
        val spent = 6500.0
        val remaining = (limit - spent).coerceAtLeast(0.0)
        val percent = (spent / limit).toFloat()

        val progress = CategoryBudgetProgress(
            category = category,
            budgetLimit = limit,
            currentSpent = spent,
            remainingAmount = remaining,
            progressPercent = percent,
        )

        assertEquals(0.0, progress.remainingAmount, 0.001)
        assertEquals(1.30f, progress.progressPercent, 0.001f)
    }
}
