package com.moneytracker.app

import com.moneytracker.app.worker.DailyInsightsWorker
import com.moneytracker.app.worker.EmailSyncWorker
import com.moneytracker.app.worker.InsightsScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WorkerSchedulingTest {

    @Test
    fun `email sync worker has unique work identifier`() {
        assertEquals("periodic_email_sync", EmailSyncWorker.UNIQUE_WORK_NAME)
    }

    @Test
    fun `daily insights worker has unique work identifier and target evening hour`() {
        assertEquals("daily_insights", InsightsScheduler.UNIQUE_WORK_NAME)
        assertEquals(21, InsightsScheduler.TARGET_EVENING_HOUR)
    }

    @Test
    fun `calculateInitialDelayToNinePm computes correct delay before 9pm`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14) // 2:00 PM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay = InsightsScheduler.calculateInitialDelayToNinePm(cal.timeInMillis)
        assertEquals(TimeUnit.HOURS.toMillis(7), delay)
    }

    @Test
    fun `calculateInitialDelayToNinePm computes correct delay after 9pm advancing to next day`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22) // 10:00 PM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay = InsightsScheduler.calculateInitialDelayToNinePm(cal.timeInMillis)
        assertEquals(TimeUnit.HOURS.toMillis(23), delay)
    }
}
