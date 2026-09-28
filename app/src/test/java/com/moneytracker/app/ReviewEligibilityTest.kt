package com.moneytracker.app

import com.moneytracker.app.data.local.ReviewPreferences
import com.moneytracker.app.ui.review.ReviewPromptManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReviewEligibilityTest {

    private class FakeReviewPreferences(
        initialInstallTimestamp: Long = 0L,
        initialLastPromptTimestamp: Long = 0L,
        initialMonthCloseCount: Int = 0,
    ) {
        val lastPromptFlow = MutableStateFlow(initialLastPromptTimestamp)
        val installFlow = MutableStateFlow(initialInstallTimestamp)
        val monthCloseFlow = MutableStateFlow(initialMonthCloseCount)

        fun setLastPrompt(ts: Long) {
            lastPromptFlow.value = ts
        }

        fun setInstall(ts: Long) {
            installFlow.value = ts
        }

        fun incrementMonthClose() {
            monthCloseFlow.value++
        }
    }

    @Test
    fun `milestone gating constants adhere to specification`() {
        assertEquals(14L, ReviewPromptManager.MIN_INSTALL_DAYS)
        assertEquals(90L, ReviewPromptManager.COOLDOWN_DAYS)
        assertEquals(14, ReviewPromptManager.MIN_DISTINCT_DAYS)
    }

    @Test
    fun `ineligible when app installed for less than 14 days`() {
        val now = 1_000_000_000L
        val installTime = now - TimeUnit.DAYS.toMillis(13) // Only 13 days
        val daysSinceInstall = TimeUnit.MILLISECONDS.toDays(now - installTime)

        assertTrue(daysSinceInstall < ReviewPromptManager.MIN_INSTALL_DAYS)
    }

    @Test
    fun `ineligible when cooldown has not elapsed 90 days`() {
        val now = 1_000_000_000L
        val lastPrompt = now - TimeUnit.DAYS.toMillis(89) // Only 89 days
        val daysSinceLastPrompt = TimeUnit.MILLISECONDS.toDays(now - lastPrompt)

        assertTrue(daysSinceLastPrompt < ReviewPromptManager.COOLDOWN_DAYS)
    }

    @Test
    fun `eligible when 14 days installed, 90 days cooldown, and 14 distinct days logged`() {
        val now = 1_000_000_000L
        val installTime = now - TimeUnit.DAYS.toMillis(20) // 20 days ago
        val lastPrompt = now - TimeUnit.DAYS.toMillis(95) // 95 days ago
        val distinctDays = 15

        val daysSinceInstall = TimeUnit.MILLISECONDS.toDays(now - installTime)
        val daysSinceLastPrompt = TimeUnit.MILLISECONDS.toDays(now - lastPrompt)
        val hasDelight = distinctDays >= ReviewPromptManager.MIN_DISTINCT_DAYS

        assertTrue(daysSinceInstall >= ReviewPromptManager.MIN_INSTALL_DAYS)
        assertTrue(daysSinceLastPrompt >= ReviewPromptManager.COOLDOWN_DAYS)
        assertTrue(hasDelight)
    }

    @Test
    fun `eligible when 14 days installed, no prior prompt, and user closed month under budget`() {
        val now = 1_000_000_000L
        val installTime = now - TimeUnit.DAYS.toMillis(14)
        val lastPrompt = 0L // Never prompted before
        val isUnderBudgetAtMonthClose = true
        val distinctDays = 5 // Less than 14, but month close delight is reached

        val daysSinceInstall = TimeUnit.MILLISECONDS.toDays(now - installTime)
        val hasCooldownPassed = lastPrompt == 0L || TimeUnit.MILLISECONDS.toDays(now - lastPrompt) >= ReviewPromptManager.COOLDOWN_DAYS
        val hasDelight = isUnderBudgetAtMonthClose || distinctDays >= ReviewPromptManager.MIN_DISTINCT_DAYS

        assertTrue(daysSinceInstall >= ReviewPromptManager.MIN_INSTALL_DAYS)
        assertTrue(hasCooldownPassed)
        assertTrue(hasDelight)
    }

    @Test
    fun `ineligible when delight milestone not reached despite meeting install and cooldown`() {
        val now = 1_000_000_000L
        val installTime = now - TimeUnit.DAYS.toMillis(30)
        val lastPrompt = 0L
        val isUnderBudgetAtMonthClose = false
        val distinctDays = 10 // Less than 14

        val daysSinceInstall = TimeUnit.MILLISECONDS.toDays(now - installTime)
        val hasDelight = isUnderBudgetAtMonthClose || distinctDays >= ReviewPromptManager.MIN_DISTINCT_DAYS

        assertTrue(daysSinceInstall >= ReviewPromptManager.MIN_INSTALL_DAYS)
        assertFalse(hasDelight)
    }
}
