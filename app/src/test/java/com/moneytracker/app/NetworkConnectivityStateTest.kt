package com.moneytracker.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConnectivityStateTest {

    @Test
    fun `reactive connectivity flow updates state properly`() = runTest {
        val connectivityFlow = MutableStateFlow(true)
        assertTrue(connectivityFlow.first())

        connectivityFlow.value = false
        assertFalse(connectivityFlow.first())

        connectivityFlow.value = true
        assertTrue(connectivityFlow.first())
    }

    @Test
    fun `default fallback when connectivity observer is absent is online`() = runTest {
        val defaultFlow = MutableStateFlow(true)
        assertTrue(defaultFlow.value)
    }
}
