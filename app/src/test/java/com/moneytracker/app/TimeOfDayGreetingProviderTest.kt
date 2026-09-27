package com.moneytracker.app

import com.moneytracker.app.data.model.DashboardState
import com.moneytracker.app.ui.greeting.TimeOfDay
import com.moneytracker.app.ui.greeting.TimeOfDayGreetingProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimeOfDayGreetingProviderTest {

    @Test
    fun `getTimeOfDay partitions 24 hours into four distinct periods`() {
        // Morning: 05:00 - 11:59
        assertEquals(TimeOfDay.MORNING, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(5, 0)))
        assertEquals(TimeOfDay.MORNING, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(8, 30)))
        assertEquals(TimeOfDay.MORNING, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(11, 59)))

        // Afternoon: 12:00 - 16:59
        assertEquals(TimeOfDay.AFTERNOON, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(12, 0)))
        assertEquals(TimeOfDay.AFTERNOON, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(14, 15)))
        assertEquals(TimeOfDay.AFTERNOON, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(16, 59)))

        // Evening: 17:00 - 21:59
        assertEquals(TimeOfDay.EVENING, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(17, 0)))
        assertEquals(TimeOfDay.EVENING, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(19, 45)))
        assertEquals(TimeOfDay.EVENING, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(21, 59)))

        // Night: 22:00 - 04:59
        assertEquals(TimeOfDay.NIGHT, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(22, 0)))
        assertEquals(TimeOfDay.NIGHT, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(23, 30)))
        assertEquals(TimeOfDay.NIGHT, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(1, 0)))
        assertEquals(TimeOfDay.NIGHT, TimeOfDayGreetingProvider.getTimeOfDay(LocalTime.of(4, 59)))
    }

    @Test
    fun `getTopGreeting formats personal greeting with user name across time of day`() {
        val userName = "Soumil"

        val morningGreeting = TimeOfDayGreetingProvider.getTopGreeting(userName, LocalTime.of(9, 0))
        assertEquals("Good morning, Soumil", morningGreeting)

        val afternoonGreeting = TimeOfDayGreetingProvider.getTopGreeting(userName, LocalTime.of(13, 0))
        assertEquals("Good afternoon, Soumil", afternoonGreeting)

        val eveningGreeting = TimeOfDayGreetingProvider.getTopGreeting(userName, LocalTime.of(18, 0))
        assertEquals("Good evening, Soumil", eveningGreeting)

        val nightGreeting = TimeOfDayGreetingProvider.getTopGreeting(userName, LocalTime.of(23, 0))
        assertEquals("Welcome back, Soumil", nightGreeting)
    }

    @Test
    fun `getTopGreeting falls back cleanly when user name is blank`() {
        assertEquals("Good morning", TimeOfDayGreetingProvider.getTopGreeting("", LocalTime.of(8, 0)))
        assertEquals("Good afternoon", TimeOfDayGreetingProvider.getTopGreeting("   ", LocalTime.of(14, 0)))
        assertEquals("Good evening", TimeOfDayGreetingProvider.getTopGreeting("", LocalTime.of(20, 0)))
        assertEquals("Welcome back", TimeOfDayGreetingProvider.getTopGreeting("", LocalTime.of(23, 0)))
    }

    @Test
    fun `getContextualMessage provides tailored morning advice`() {
        // Over budget scenario
        val overBudgetDashboard = DashboardState(
            budgetLimit = 15000.0,
            monthSpent = 20000.0,
            safeDailySpend = 0.0,
        )
        val morningAlert = TimeOfDayGreetingProvider.getContextualMessage(
            dashboard = overBudgetDashboard,
            userName = "Soumil",
            time = LocalTime.of(8, 0),
        )
        assertTrue(morningAlert.message.contains("over target", ignoreCase = true))
        assertEquals("Morning Alert", morningAlert.tag)

        // Safe burn scenario
        val safeBurnDashboard = DashboardState(
            budgetLimit = 25000.0,
            monthSpent = 10000.0,
            safeDailySpend = 750.0,
        )
        val morningSafeBurn = TimeOfDayGreetingProvider.getContextualMessage(
            dashboard = safeBurnDashboard,
            userName = "Soumil",
            time = LocalTime.of(9, 0),
        )
        assertTrue(morningSafeBurn.message.contains("Safe daily burn is ₹750", ignoreCase = true))
        assertEquals("Daily Target", morningSafeBurn.tag)
    }

    @Test
    fun `getContextualMessage surfaces pending reviews in afternoon and evening`() {
        val pendingReviewDashboard = DashboardState(
            reviewCount = 3,
            safeDailySpend = 500.0,
        )

        val afternoonMsg = TimeOfDayGreetingProvider.getContextualMessage(
            dashboard = pendingReviewDashboard,
            userName = "Soumil",
            time = LocalTime.of(15, 0),
        )
        assertTrue(afternoonMsg.message.contains("3 transactions pending review", ignoreCase = true))
        assertEquals("Review Queue", afternoonMsg.tag)

        val eveningMsg = TimeOfDayGreetingProvider.getContextualMessage(
            dashboard = pendingReviewDashboard,
            userName = "Soumil",
            time = LocalTime.of(20, 0),
        )
        assertTrue(eveningMsg.message.contains("3 unverified items ready for your review", ignoreCase = true))
        assertEquals("Evening Review", eveningMsg.tag)
    }

    @Test
    fun `getContextualMessage surfaces calming peace of mind message at night`() {
        val peacefulDashboard = DashboardState(
            reviewCount = 0,
            monthNetCashflow = 5000.0,
        )

        val nightMsg = TimeOfDayGreetingProvider.getContextualMessage(
            dashboard = peacefulDashboard,
            userName = "Soumil",
            time = LocalTime.of(23, 30),
        )
        assertTrue(nightMsg.message.contains("Good night", ignoreCase = true) || nightMsg.message.contains("secured", ignoreCase = true))
        assertEquals("Peace of Mind", nightMsg.tag)
    }

    @Test
    fun `getContextualNudges generates prioritized dynamic nudges with actions`() {
        val aiEngine = com.moneytracker.app.ai.OnDeviceAiEngine()
        val dashboard = DashboardState(
            budgetLimit = 10000.0,
            monthSpent = 15000.0,
            reviewCount = 2,
            cardSpendThisMonth = 12000.0,
        )

        val nudges = TimeOfDayGreetingProvider.getContextualNudges(
            dashboard = dashboard,
            userName = "Soumil",
            aiEngine = aiEngine,
            time = LocalTime.of(23, 0),
            date = java.time.LocalDate.of(2026, 9, 28),
        )

        assertTrue("Expected at least 2 nudges", nudges.size >= 2)
        // High priority review queue should be present with NAVIGATE_REVIEW action
        val reviewNudge = nudges.firstOrNull { it.tag == "Review Queue" }
        org.junit.Assert.assertNotNull(reviewNudge)
        assertEquals(com.moneytracker.app.data.model.NudgeActionType.NAVIGATE_REVIEW, reviewNudge?.actionType)
        assertEquals("Review Items", reviewNudge?.actionLabel)

        // Burn velocity anomaly should have OPEN_BUDGET action
        val burnNudge = nudges.firstOrNull { it.tag == "Burn Velocity" }
        org.junit.Assert.assertNotNull(burnNudge)
        assertEquals(com.moneytracker.app.data.model.NudgeActionType.OPEN_BUDGET, burnNudge?.actionType)
        assertEquals("Adjust Budget", burnNudge?.actionLabel)
        org.junit.Assert.assertNotNull(burnNudge?.acceleratorBadge)

        // Card spend nudge should be present
        val cardNudge = nudges.firstOrNull { it.tag == "Card Spends" }
        org.junit.Assert.assertNotNull(cardNudge)

        // High priority nudges should appear first
        assertTrue(nudges.first().priority == com.moneytracker.app.data.model.NudgePriority.HIGH)
    }
}
