package com.moneytracker.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile generator for Money Tracker.
 *
 * Captures startup bytecode and critical user journey paths (navigating to
 * transactions and scrolling Paging 3 Compose list) into baseline profiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        baselineProfileRule.collect(
            packageName = "com.moneytracker.app",
        ) {
            // Cold app startup
            pressHome()
            startActivityAndWait()

            // Wait for the dashboard/home content to render
            device.wait(Until.hasObject(By.text("Transactions")), 5_000)

            // Navigate to the Transactions tab
            val transactionsTab = device.findObject(By.desc("Transactions"))
                ?: device.findObject(By.text("Transactions"))
            transactionsTab?.click()

            // Wait for transaction LazyColumn to appear and scroll to warm Paging 3 / Compose paths
            device.wait(Until.hasObject(By.scrollable(true)), 5_000)
            val transactionList = device.findObject(By.scrollable(true))
            if (transactionList != null) {
                transactionList.setGestureMargin(device.displayWidth / 5)
                transactionList.fling(Direction.DOWN)
                device.waitForIdle()
                transactionList.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }
}
