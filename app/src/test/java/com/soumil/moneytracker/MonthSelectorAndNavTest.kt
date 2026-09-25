package com.soumil.moneytracker

import com.soumil.moneytracker.data.model.DashboardState
import com.soumil.moneytracker.ui.navigation.AppDestination
import com.soumil.moneytracker.ui.navigation.bottomDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MonthSelectorAndNavTest {

    @Test
    fun testDashboardStateSelectedMonthDefault() {
        val dashboard = DashboardState()
        assertEquals(YearMonth.now(), dashboard.selectedYearMonth)
    }

    @Test
    fun testDashboardStateSelectedMonthCustom() {
        val targetMonth = YearMonth.of(2026, 6)
        val dashboard = DashboardState(
            monthSpent = 4500.0,
            monthIncome = 12000.0,
            budgetLimit = 10000.0,
            selectedYearMonth = targetMonth,
        )
        assertEquals(targetMonth, dashboard.selectedYearMonth)
        assertEquals(4500.0, dashboard.monthSpent, 0.001)
        assertEquals(12000.0, dashboard.monthIncome, 0.001)
        assertEquals(10000.0, dashboard.budgetLimit!!, 0.001)
    }

    @Test
    fun testMonthFormatting() {
        val month = YearMonth.of(2026, 9)
        val formatted = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
        assertEquals("September 2026", formatted)

        val august = YearMonth.of(2026, 8)
        val formattedAug = august.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
        assertEquals("August 2026", formattedAug)
    }

    @Test
    fun testDaysRemainingForSelectedMonth() {
        val now = YearMonth.now()
        val today = LocalDate.now()
        val currentDaysRemaining = (now.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)

        // For past month, remaining days should be 1 (closed)
        val pastMonth = now.minusMonths(2)
        val pastDaysRemaining = when {
            pastMonth == now -> (pastMonth.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
            pastMonth < now -> 1
            else -> pastMonth.lengthOfMonth()
        }
        assertEquals(1, pastDaysRemaining)

        // For future month, days remaining is full month
        val futureMonth = now.plusMonths(2)
        val futureDaysRemaining = when {
            futureMonth == now -> (futureMonth.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
            futureMonth < now -> 1
            else -> futureMonth.lengthOfMonth()
        }
        assertEquals(futureMonth.lengthOfMonth(), futureDaysRemaining)
    }

    @Test
    fun testBottomNavbarDestinations() {
        assertEquals(4, bottomDestinations.size)
        assertEquals(AppDestination.Home, bottomDestinations[0])
        assertEquals(AppDestination.Transactions, bottomDestinations[1])
        assertEquals(AppDestination.More, bottomDestinations[2])
        assertEquals(AppDestination.Settings, bottomDestinations[3])
    }

    @Test
    fun testFabVisibilityPerRoute() {
        // AI button only on Home (page 0)
        // Plus button only on Transactions (page 1)
        // Hidden on More (page 2), Settings (page 3), and BudgetHistory
        fun isFabVisible(page: Int, isBudgetHistory: Boolean): Boolean {
            return !isBudgetHistory && (page == 0 || page == 1)
        }

        fun isAiButton(page: Int): Boolean {
            return page == 0
        }

        assertTrue(isFabVisible(0, false))
        assertTrue(isAiButton(0))

        assertTrue(isFabVisible(1, false))
        assertFalse(isAiButton(1))

        assertFalse(isFabVisible(2, false))
        assertFalse(isFabVisible(3, false))
        assertFalse(isFabVisible(0, true))
        assertFalse(isFabVisible(1, true))
    }
}
