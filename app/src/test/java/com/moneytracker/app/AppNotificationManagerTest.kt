package com.moneytracker.app

import com.moneytracker.app.notification.AppNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNotificationManagerTest {

    @Test
    fun `notification channel identifiers match specification`() {
        assertEquals("channel_daily_insights", AppNotificationManager.CHANNEL_DAILY_INSIGHTS)
        assertEquals("channel_budget_alerts", AppNotificationManager.CHANNEL_BUDGET_ALERTS)
        assertEquals("channel_transactions", AppNotificationManager.CHANNEL_TRANSACTIONS)
    }

    @Test
    fun `navigation extras match specification`() {
        assertEquals("extra_nav_destination", AppNotificationManager.EXTRA_DESTINATION)
        assertEquals("extra_nav_category", AppNotificationManager.EXTRA_CATEGORY)
        assertEquals("extra_nav_merchant", AppNotificationManager.EXTRA_MERCHANT)
    }

    @Test
    fun `notification base ids are distinct`() {
        assertEquals(1001, AppNotificationManager.NOTIFICATION_ID_DAILY_RECAP)
        assertEquals(2000, AppNotificationManager.NOTIFICATION_ID_BUDGET_BASE)
        assertEquals(3000, AppNotificationManager.NOTIFICATION_ID_BILL_BASE)
    }
}
