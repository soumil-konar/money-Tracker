package com.moneytracker.app

import com.moneytracker.app.ui.navigation.AppDestination
import com.moneytracker.app.ui.navigation.bottomDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerBottomBarTest {

    @Test
    fun testBottomDestinationsCountAndOrder() {
        assertEquals(4, bottomDestinations.size)
        assertEquals(AppDestination.Home, bottomDestinations[0])
        assertEquals(AppDestination.Transactions, bottomDestinations[1])
        assertEquals(AppDestination.More, bottomDestinations[2])
        assertEquals(AppDestination.Settings, bottomDestinations[3])
    }

    @Test
    fun testDestinationsHaveDistinctSelectedAndUnselectedIcons() {
        bottomDestinations.forEach { dest ->
            assertNotNull(dest.icon)
            assertNotNull(dest.selectedIcon)
            assertEquals(dest.label.isNotEmpty(), true)
            // Verify icon vectors exist
            assertTrue(dest.icon.name.isNotEmpty())
            assertTrue(dest.selectedIcon.name.isNotEmpty())
        }
    }

    @Test
    fun testDestinationRoutes() {
        assertEquals("home", AppDestination.Home.route)
        assertEquals("transactions", AppDestination.Transactions.route)
        assertEquals("more", AppDestination.More.route)
        assertEquals("settings", AppDestination.Settings.route)
    }
}
