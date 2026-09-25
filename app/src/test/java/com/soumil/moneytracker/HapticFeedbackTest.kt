package com.soumil.moneytracker

import com.soumil.moneytracker.data.local.HapticIntensity
import com.soumil.moneytracker.ui.haptics.AppHaptics
import com.soumil.moneytracker.ui.haptics.NoOpAppHaptics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class HapticFeedbackTest {

    @Test
    fun `haptic intensity scaling factors are well calibrated`() {
        assertEquals(0.65f, HapticIntensity.SOFT.scaleFactor, 0.001f)
        assertEquals(1.0f, HapticIntensity.BALANCED.scaleFactor, 0.001f)
        assertEquals(1.4f, HapticIntensity.STRONG.scaleFactor, 0.001f)
    }

    @Test
    fun `haptic intensity labels are human readable`() {
        assertEquals("Soft", HapticIntensity.SOFT.label)
        assertEquals("Balanced", HapticIntensity.BALANCED.label)
        assertEquals("Strong", HapticIntensity.STRONG.label)
    }

    @Test
    fun `amplitude calculation clamps correctly within valid byte range`() {
        fun scaleAmplitude(base: Int, factor: Float): Int {
            return (base * factor).roundToInt().coerceIn(1, 255)
        }

        // Base tick amplitude (40)
        assertEquals(26, scaleAmplitude(40, HapticIntensity.SOFT.scaleFactor))
        assertEquals(40, scaleAmplitude(40, HapticIntensity.BALANCED.scaleFactor))
        assertEquals(56, scaleAmplitude(40, HapticIntensity.STRONG.scaleFactor))

        // High base amplitude (220)
        assertEquals(143, scaleAmplitude(220, HapticIntensity.SOFT.scaleFactor))
        assertEquals(220, scaleAmplitude(220, HapticIntensity.BALANCED.scaleFactor))
        assertEquals(255, scaleAmplitude(220, HapticIntensity.STRONG.scaleFactor))

        // Low base amplitude edge case (1)
        assertEquals(1, scaleAmplitude(1, HapticIntensity.SOFT.scaleFactor))
    }

    @Test
    fun `NoOpAppHaptics safely executes without exceptions`() {
        val haptics: AppHaptics = NoOpAppHaptics
        haptics.tick()
        haptics.click()
        haptics.selection()
        haptics.toggle()
        haptics.success()
        haptics.warning()
        haptics.heavy()
        haptics.sweetImpact()
        assertTrue("NoOpAppHaptics executed all methods without exception", true)
    }

    @Test
    fun `haptic intensity enum serialization and deserialization works correctly`() {
        for (intensity in HapticIntensity.entries) {
            val name = intensity.name
            val restored = HapticIntensity.valueOf(name)
            assertEquals(intensity, restored)
        }
    }
}
