package com.moneytracker.app

import androidx.compose.ui.graphics.Color
import com.moneytracker.app.data.db.FinanceTypeConverters
import com.moneytracker.app.data.local.ThemeAccent
import com.moneytracker.app.data.model.CategorySlice
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.ui.theme.CategoryThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCategoryAndThemeColorsTest {

    @Test
    fun testDefaultCategoriesExistAndMatchConstants() {
        assertEquals("Food", TransactionCategory.FOOD.label)
        assertEquals("FOOD", TransactionCategory.FOOD.name)
        assertEquals("Travel", TransactionCategory.TRAVEL.label)
        assertEquals("Bills", TransactionCategory.BILLS.label)
        assertEquals("Shopping", TransactionCategory.SHOPPING.label)
        assertEquals("Transfer", TransactionCategory.TRANSFER.label)
        assertEquals("Salary", TransactionCategory.SALARY.label)
        assertEquals("Subscription", TransactionCategory.SUBSCRIPTION.label)
        assertEquals("Health", TransactionCategory.HEALTH.label)
        assertEquals("Other", TransactionCategory.OTHER.label)
        assertEquals(9, TransactionCategory.defaultCategories.size)
    }

    @Test
    fun testCreateCustomCategoryFormattingAndEquality() {
        val custom1 = TransactionCategory.custom("fitness club")
        assertEquals("Fitness Club", custom1.label)
        assertEquals("Fitness Club", custom1.name)

        val custom2 = TransactionCategory.custom("FITNESS CLUB")
        assertEquals(custom1, custom2)
        assertEquals(custom1.hashCode(), custom2.hashCode())

        val valueOfCustom = TransactionCategory.valueOf("fitness club")
        assertEquals(custom1, valueOfCustom)
    }

    @Test
    fun testRoomTypeConverterCompatibility() {
        val converters = FinanceTypeConverters()

        // 1. Default category
        val defaultSerialized = converters.fromTransactionCategory(TransactionCategory.FOOD)
        assertEquals("FOOD", defaultSerialized)
        val defaultDeserialized = converters.toTransactionCategory(defaultSerialized)
        assertEquals(TransactionCategory.FOOD, defaultDeserialized)

        // 2. Custom category
        val customCategory = TransactionCategory.custom("Online Courses")
        val customSerialized = converters.fromTransactionCategory(customCategory)
        assertNotNull(customSerialized)
        val customDeserialized = converters.toTransactionCategory(customSerialized)
        assertEquals(customCategory, customDeserialized)
        assertEquals("Online Courses", customDeserialized?.label)
    }

    @Test
    fun testThemeDependentColorsDynamicAssignment() {
        val customCat = TransactionCategory.custom("Investments")

        // In Expressive theme (Light vs Dark)
        val expressiveLightColor = CategoryThemeColors.getColor(customCat, ThemeAccent.EXPRESSIVE, isDark = false)
        val expressiveDarkColor = CategoryThemeColors.getColor(customCat, ThemeAccent.EXPRESSIVE, isDark = true)
        assertNotEquals(expressiveLightColor, expressiveDarkColor)

        // Across different themes
        val oceanColor = CategoryThemeColors.getColor(customCat, ThemeAccent.OCEAN, isDark = false)
        val crimsonColor = CategoryThemeColors.getColor(customCat, ThemeAccent.CRIMSON, isDark = false)
        val sageColor = CategoryThemeColors.getColor(customCat, ThemeAccent.SAGE, isDark = false)
        val amberColor = CategoryThemeColors.getColor(customCat, ThemeAccent.AMBER, isDark = false)
        val monochromeColor = CategoryThemeColors.getColor(customCat, ThemeAccent.MONOCHROME, isDark = false)

        // Colors must adapt dynamically to theme palettes
        assertNotNull(oceanColor)
        assertNotNull(crimsonColor)
        assertNotNull(sageColor)
        assertNotNull(amberColor)
        assertNotNull(monochromeColor)

        // Different theme accents should produce distinct thematic colors
        val uniqueAccentColors = setOf(oceanColor, crimsonColor, sageColor, amberColor, monochromeColor)
        assertTrue("Expected theme accents to provide distinct thematic colors", uniqueAccentColors.size >= 4)
    }

    @Test
    fun testPaletteIndexOverride() {
        val customWithPalette = TransactionCategory.custom("Gaming")
        TransactionCategory.register(customWithPalette, paletteIndex = 3)

        val expressivePalette = CategoryThemeColors.getPalette(ThemeAccent.EXPRESSIVE, isDark = false)
        val assignedColor = CategoryThemeColors.getColor(customWithPalette, ThemeAccent.EXPRESSIVE, isDark = false)
        assertEquals(expressivePalette[3], assignedColor)

        val oceanPalette = CategoryThemeColors.getPalette(ThemeAccent.OCEAN, isDark = false)
        val assignedOceanColor = CategoryThemeColors.getColor(customWithPalette, ThemeAccent.OCEAN, isDark = false)
        assertEquals(oceanPalette[3], assignedOceanColor)
    }

    @Test
    fun testChartSliceColorsUniqueAndHarmonious() {
        val categories = listOf(
            TransactionCategory.FOOD,
            TransactionCategory.custom("Gym"),
            TransactionCategory.custom("Rent"),
            TransactionCategory.custom("Books"),
            TransactionCategory.BILLS,
        )

        val colorMap = CategoryThemeColors.getColorsForSlices(
            categories = categories,
            accent = ThemeAccent.OCEAN,
            isDark = true,
        )

        assertEquals(categories.size, colorMap.size)
        // All categories should have a color
        categories.forEach { cat ->
            assertNotNull(colorMap[cat])
        }

        // Each category slice in the chart should have a unique color
        val distinctColors = colorMap.values.toSet()
        assertEquals(categories.size, distinctColors.size)
    }

    @Test
    fun testSpendMixCategoryBreakdown() {
        val customCat = TransactionCategory.custom("Gadgets")
        val slices = listOf(
            CategorySlice(TransactionCategory.FOOD, 1500.0),
            CategorySlice(customCat, 800.0),
            CategorySlice(TransactionCategory.TRAVEL, 400.0),
        )

        val colorMap = CategoryThemeColors.getColorsForSlices(
            categories = slices.map { it.category },
            accent = ThemeAccent.EXPRESSIVE,
            isDark = false,
        )

        assertTrue(colorMap.containsKey(customCat))
        val customColor = colorMap[customCat]
        assertNotNull(customColor)
        assertNotEquals(Color.Unspecified, customColor)
    }

    @Test
    fun testCategoryRegistrationAndAllCategories() {
        val cat = TransactionCategory.custom("Medical Care")
        TransactionCategory.register(cat)

        val all = TransactionCategory.allCategories()
        assertTrue(all.contains(cat))
        assertTrue(all.contains(TransactionCategory.FOOD))

        // Duplicate registration should not duplicate entry
        TransactionCategory.register(TransactionCategory.custom("medical care"))
        val customCount = TransactionCategory.getRegisteredCustomCategories().count {
            it.name.equals("Medical Care", ignoreCase = true)
        }
        assertEquals(1, customCount)
    }

    @Test
    fun testLabelFormattingEdgeCases() {
        assertEquals("Personal Care", TransactionCategory.custom("personal_care").label)
        assertEquals("Home Decor", TransactionCategory.custom("home-decor").label)
        assertEquals("Coffee And Tea", TransactionCategory.custom("coffee   and   tea").label)
        assertEquals(TransactionCategory.OTHER, TransactionCategory.custom(""))
        assertEquals(TransactionCategory.OTHER, TransactionCategory.custom("   "))
    }
}

