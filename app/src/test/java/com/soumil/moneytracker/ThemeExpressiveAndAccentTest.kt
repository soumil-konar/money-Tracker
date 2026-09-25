package com.soumil.moneytracker

import androidx.compose.ui.graphics.Color
import com.soumil.moneytracker.data.local.ThemeAccent
import com.soumil.moneytracker.data.local.ThemeMode
import com.soumil.moneytracker.ui.theme.AppExpressiveShapes
import com.soumil.moneytracker.ui.theme.AppStandardShapes
import com.soumil.moneytracker.ui.theme.resolveColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeExpressiveAndAccentTest {

    @Test
    fun `theme accent entries contain expressive and all required accents`() {
        val accents = ThemeAccent.entries.toList()
        assertEquals(6, accents.size)

        val expressive = ThemeAccent.EXPRESSIVE
        assertTrue("EXPRESSIVE accent must have isExpressive = true", expressive.isExpressive)

        // All custom accents MUST disable Material 3 Expressive
        val nonExpressiveAccents = listOf(
            ThemeAccent.MONOCHROME,
            ThemeAccent.CRIMSON,
            ThemeAccent.OCEAN,
            ThemeAccent.SAGE,
            ThemeAccent.AMBER,
        )

        nonExpressiveAccents.forEach { accent ->
            assertFalse("${accent.name} must disable Material 3 Expressive", accent.isExpressive)
        }
    }

    @Test
    fun `theme mode enum covers SYSTEM, DARK, and LIGHT`() {
        assertEquals(3, ThemeMode.entries.size)
        assertNotNull(ThemeMode.valueOf("SYSTEM"))
        assertNotNull(ThemeMode.valueOf("DARK"))
        assertNotNull(ThemeMode.valueOf("LIGHT"))
    }

    @Test
    fun `resolveColorScheme returns distinct schemes for light and dark across all accents`() {
        ThemeAccent.entries.forEach { accent ->
            val lightScheme = resolveColorScheme(accent, isDark = false)
            val darkScheme = resolveColorScheme(accent, isDark = true)

            assertNotNull(lightScheme)
            assertNotNull(darkScheme)

            // Light vs Dark should have differing surface and background tones
            assertNotEquals(
                "Light and Dark surface colors must differ for ${accent.name}",
                lightScheme.surface,
                darkScheme.surface,
            )
            assertNotEquals(
                "Light and Dark background colors must differ for ${accent.name}",
                lightScheme.background,
                darkScheme.background,
            )
        }
    }

    @Test
    fun `expressive shapes provide distinct curvature compared to standard shapes`() {
        assertNotEquals(AppExpressiveShapes.medium, AppStandardShapes.medium)
        assertNotEquals(AppExpressiveShapes.large, AppStandardShapes.large)
    }

    @Test
    fun `monochrome accent uses neutral grayscale colors`() {
        val light = resolveColorScheme(ThemeAccent.MONOCHROME, isDark = false)
        val dark = resolveColorScheme(ThemeAccent.MONOCHROME, isDark = true)

        // Light monochrome primary is near black
        assertEquals(Color(0xFF18181B), light.primary)
        // Dark monochrome primary is stark zinc white
        assertEquals(Color(0xFFFAFAFA), dark.primary)
    }

    @Test
    fun `crimson accent uses red hue primary colors`() {
        val light = resolveColorScheme(ThemeAccent.CRIMSON, isDark = false)
        val dark = resolveColorScheme(ThemeAccent.CRIMSON, isDark = true)

        assertEquals(Color(0xFFBE123C), light.primary)
        assertEquals(Color(0xFFFB7185), dark.primary)
    }

    @Test
    fun `ocean accent uses blue and cyan primary colors`() {
        val light = resolveColorScheme(ThemeAccent.OCEAN, isDark = false)
        val dark = resolveColorScheme(ThemeAccent.OCEAN, isDark = true)

        assertEquals(Color(0xFF0284C7), light.primary)
        assertEquals(Color(0xFF38BDF8), dark.primary)
    }

    @Test
    fun `sage accent uses botanical green primary colors`() {
        val light = resolveColorScheme(ThemeAccent.SAGE, isDark = false)
        val dark = resolveColorScheme(ThemeAccent.SAGE, isDark = true)

        assertEquals(Color(0xFF15803D), light.primary)
        assertEquals(Color(0xFF86EFAC), dark.primary)
    }

    @Test
    fun `amber accent uses golden amber primary colors`() {
        val light = resolveColorScheme(ThemeAccent.AMBER, isDark = false)
        val dark = resolveColorScheme(ThemeAccent.AMBER, isDark = true)

        assertEquals(Color(0xFFD97706), light.primary)
        assertEquals(Color(0xFFFBBF24), dark.primary)
    }
}
