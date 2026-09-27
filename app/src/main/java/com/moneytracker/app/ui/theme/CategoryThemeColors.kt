package com.moneytracker.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.moneytracker.app.data.local.ThemeAccent
import com.moneytracker.app.data.model.TransactionCategory
import kotlin.math.abs

object CategoryThemeColors {

    // ========================================================================
    // 1. THEME-AWARE 10-TONE CURATED HARMONIOUS PALETTES
    // ========================================================================

    // --- A. Expressive (High-chroma vibrant M3 tones) ---
    private val ExpressiveLight = listOf(
        Color(0xFF635BFF), // 0: Electric Violet (Food/Primary)
        Color(0xFF0077B6), // 1: Vibrant Azure (Travel)
        Color(0xFF059669), // 2: Emerald Mint (Bills)
        Color(0xFFD97706), // 3: Sunset Amber (Shopping)
        Color(0xFF0D9488), // 4: Deep Teal (Transfer)
        Color(0xFF16A34A), // 5: Green Mint (Salary)
        Color(0xFF9333EA), // 6: Orchid Purple (Subscription)
        Color(0xFFE11D48), // 7: Rose Crimson (Health)
        Color(0xFF64748B), // 8: Slate Gray (Other)
        Color(0xFFEA580C), // 9: Vivid Tangerine
    )

    private val ExpressiveDark = listOf(
        Color(0xFF8B5CF6), // 0: Luminous Neon Violet
        Color(0xFF38BDF8), // 1: Electric Sky Cyan
        Color(0xFF34D399), // 2: Emerald Mint
        Color(0xFFFBBF24), // 3: Radiant Topaz
        Color(0xFF2DD4BF), // 4: Aquamarine Glow
        Color(0xFF4ADE80), // 5: Spring Green
        Color(0xFFA855F7), // 6: Luminous Orchid
        Color(0xFFFB7185), // 7: Vibrant Rose
        Color(0xFF94A3B8), // 8: Soft Slate
        Color(0xFFFB923C), // 9: Coral Sunset
    )

    // --- B. Crimson (Ruby, rosewood, coral, blush, warm tones) ---
    private val CrimsonLight = listOf(
        Color(0xFFBE123C), // 0: Bold Crimson
        Color(0xFF9F1239), // 1: Rosewood
        Color(0xFFDC2626), // 2: Warm Ruby
        Color(0xFFE11D48), // 3: Rose Pink
        Color(0xFF881337), // 4: Deep Wine
        Color(0xFFD97706), // 5: Amber Topaz
        Color(0xFF701A75), // 6: Berry Plum
        Color(0xFFDB2777), // 7: Blush Magenta
        Color(0xFF6B7280), // 8: Muted Ash
        Color(0xFFC2410C), // 9: Terracotta Red
    )

    private val CrimsonDark = listOf(
        Color(0xFFFB7185), // 0: Vibrant Rose Crimson
        Color(0xFFF43F5E), // 1: Strawberry Coral
        Color(0xFFFDA4AF), // 2: Ruby Bloom
        Color(0xFFFF6B81), // 3: Radiant Coral
        Color(0xFFFCA5A5), // 4: Warm Peach
        Color(0xFFFBBF24), // 5: Sunset Amber
        Color(0xFFE879F9), // 6: Plum Orchid
        Color(0xFFF9A8D4), // 7: Rose Gold
        Color(0xFF9CA3AF), // 8: Silver Mist
        Color(0xFFFF758F), // 9: Bright Coral
    )

    // --- C. Ocean (Deep sapphire, vibrant marine cyan, azure, teal, sky) ---
    private val OceanLight = listOf(
        Color(0xFF0284C7), // 0: Azure Marine
        Color(0xFF0369A1), // 1: Deep Sapphire
        Color(0xFF0891B2), // 2: Sea Teal
        Color(0xFF0D9488), // 3: Aquamarine
        Color(0xFF1D4ED8), // 4: Deep Oceanic
        Color(0xFF059669), // 5: Mint Lagoon
        Color(0xFF4338CA), // 6: Royal Indigo
        Color(0xFF2563EB), // 7: Cobalt Blue
        Color(0xFF64748B), // 8: Slate Gray
        Color(0xFF0EA5E9), // 9: Sky Cyan
    )

    private val OceanDark = listOf(
        Color(0xFF38BDF8), // 0: Electric Cyan Azure
        Color(0xFF0EA5E9), // 1: Bright Sky Blue
        Color(0xFF22D3EE), // 2: Luminous Turquoise
        Color(0xFF2DD4BF), // 3: Aquamarine Glow
        Color(0xFF60A5FA), // 4: Neon Azure
        Color(0xFF34D399), // 5: Seafoam Mint
        Color(0xFF818CF8), // 6: Bright Periwinkle
        Color(0xFF67E8F9), // 7: Icy Cyan
        Color(0xFF94A3B8), // 8: Soft Slate
        Color(0xFF5EEAD4), // 9: Mint Cyan
    )

    // --- D. Sage (Botanical eucalyptus, moss, olive, forest) ---
    private val SageLight = listOf(
        Color(0xFF15803D), // 0: Forest Sage
        Color(0xFF166534), // 1: Botanical Moss
        Color(0xFF4D7C0F), // 2: Olive Green
        Color(0xFF059669), // 3: Emerald Flora
        Color(0xFF16A34A), // 4: Meadow Green
        Color(0xFF047857), // 5: Pine Green
        Color(0xFF65A30D), // 6: Lime Leaf
        Color(0xFF2E6539), // 7: Earthy Cedar
        Color(0xFF64748B), // 8: Slate Olive
        Color(0xFF854D0E), // 9: Warm Khaki
    )

    private val SageDark = listOf(
        Color(0xFF86EFAC), // 0: Soft Sage Mint
        Color(0xFF4ADE80), // 1: Luminous Flora
        Color(0xFFA3E635), // 2: Radiant Lime
        Color(0xFF34D399), // 3: Emerald Bright
        Color(0xFF22C55E), // 4: Spring Foliage
        Color(0xFF6EE7B7), // 5: Celadon Mint
        Color(0xFFBEF264), // 6: Yellow Moss
        Color(0xFFD9F99D), // 7: Fresh Olive
        Color(0xFF94A3B8), // 8: Soft Sage Slate
        Color(0xFF4FD1C5), // 9: Herbal Mint
    )

    // --- E. Amber (Warm honey gold, sunset topaz, terracotta) ---
    private val AmberLight = listOf(
        Color(0xFFD97706), // 0: Amber Gold
        Color(0xFFB45309), // 1: Warm Honey
        Color(0xFFC2410C), // 2: Sunset Topaz
        Color(0xFFEA580C), // 3: Terracotta
        Color(0xFF92400E), // 4: Honey Bronze
        Color(0xFFCA8A04), // 5: Spiced Ochre
        Color(0xFFBE123C), // 6: Crimson Amber
        Color(0xFFF97316), // 7: Tangerine
        Color(0xFF78716C), // 8: Warm Stone
        Color(0xFF9A3412), // 9: Cinnamon
    )

    private val AmberDark = listOf(
        Color(0xFFFBBF24), // 0: Radiant Topaz
        Color(0xFFF59E0B), // 1: Bright Amber
        Color(0xFFFB923C), // 2: Sunset Glow
        Color(0xFFFDBA74), // 3: Warm Apricot
        Color(0xFFFCD34D), // 4: Honey Blossom
        Color(0xFFFFD166), // 5: Mango Gold
        Color(0xFFF87171), // 6: Peach Topaz
        Color(0xFFFFAA33), // 7: Tangerine Dream
        Color(0xFFA8A29E), // 8: Warm Ash
        Color(0xFFFF7849), // 9: Flame Orange
    )

    // --- F. Monochrome (Sleek minimalist black & white, slate, zinc) ---
    private val MonochromeLight = listOf(
        Color(0xFF18181B), // 0: Deep Zinc
        Color(0xFF3F3F46), // 1: Slate Charcoal
        Color(0xFF52525B), // 2: Neutral Steel
        Color(0xFF71717A), // 3: Cool Stone
        Color(0xFF334155), // 4: Gunmetal
        Color(0xFF09090B), // 5: Pitch Black
        Color(0xFF475569), // 6: Muted Slate
        Color(0xFF27272A), // 7: Dark Pewter
        Color(0xFFA1A1AA), // 8: Silver Mist
        Color(0xFF64748B), // 9: Graphite Gray
    )

    private val MonochromeDark = listOf(
        Color(0xFFFAFAFA), // 0: Stark Zinc White
        Color(0xFFE4E4E7), // 1: Crisp Silver
        Color(0xFFD4D4D8), // 2: Polished Platinum
        Color(0xFFA1A1AA), // 3: Light Aluminum
        Color(0xFFF4F4F5), // 4: Cool White
        Color(0xFFCBD5E1), // 5: Frost Gray
        Color(0xFF94A3B8), // 6: Chrome Slate
        Color(0xFFE2E8F0), // 7: Pale Nickel
        Color(0xFF71717A), // 8: Pewter
        Color(0xFFB0B0B8), // 9: Metallic Ash
    )

    // ========================================================================
    // 2. PALETTE RESOLUTION
    // ========================================================================

    fun getPalette(accent: ThemeAccent, isDark: Boolean): List<Color> {
        return when (accent) {
            ThemeAccent.EXPRESSIVE -> if (isDark) ExpressiveDark else ExpressiveLight
            ThemeAccent.CRIMSON -> if (isDark) CrimsonDark else CrimsonLight
            ThemeAccent.OCEAN -> if (isDark) OceanDark else OceanLight
            ThemeAccent.SAGE -> if (isDark) SageDark else SageLight
            ThemeAccent.AMBER -> if (isDark) AmberDark else AmberLight
            ThemeAccent.MONOCHROME -> if (isDark) MonochromeDark else MonochromeLight
        }
    }

    // ========================================================================
    // 3. DETERMINISTIC DYNAMIC COLOR ASSIGNMENT
    // ========================================================================

    fun getColor(
        category: TransactionCategory,
        accent: ThemeAccent,
        isDark: Boolean,
    ): Color {
        val palette = getPalette(accent, isDark)
        val index = getCategoryPaletteIndex(category, palette.size)
        return palette[index]
    }

    fun getCategoryPaletteIndex(
        category: TransactionCategory,
        paletteSize: Int = 10,
    ): Int {
        // 1. User-customized preference if registered
        val registeredIndex = TransactionCategory.getRegisteredPaletteIndex(category)
        if (registeredIndex != null) {
            return (registeredIndex % paletteSize).coerceAtLeast(0)
        }

        // 2. Semantic indices for standard default categories
        return when (category.name.uppercase()) {
            "FOOD" -> 0
            "TRAVEL" -> 1
            "BILLS" -> 2
            "SHOPPING" -> 3
            "TRANSFER" -> 4
            "SALARY" -> 5
            "SUBSCRIPTION" -> 6
            "HEALTH" -> 7
            "OTHER" -> 8
            // 3. Deterministic hash-based distribution for custom categories
            else -> {
                val hash = abs(category.name.uppercase().hashCode())
                // Offset into palette to avoid always taking slot 0
                hash % paletteSize
            }
        }
    }

    // ========================================================================
    // 4. CHART SLICE COLOR RESOLVER (Ensures Maximum Distinctness)
    // ========================================================================

    fun getColorsForSlices(
        categories: List<TransactionCategory>,
        accent: ThemeAccent,
        isDark: Boolean,
    ): Map<TransactionCategory, Color> {
        val palette = getPalette(accent, isDark)
        val result = mutableMapOf<TransactionCategory, Color>()
        val usedIndices = mutableSetOf<Int>()

        // First pass: Assign preferred or natural indices if no collision
        categories.forEach { category ->
            val preferredIndex = getCategoryPaletteIndex(category, palette.size)
            if (preferredIndex !in usedIndices) {
                result[category] = palette[preferredIndex]
                usedIndices.add(preferredIndex)
            }
        }

        // Second pass: For any collided categories, pick the next available palette slot
        var nextAvailable = 0
        categories.forEach { category ->
            if (category !in result) {
                while (nextAvailable in usedIndices && usedIndices.size < palette.size) {
                    nextAvailable = (nextAvailable + 1) % palette.size
                }
                result[category] = palette[nextAvailable % palette.size]
                usedIndices.add(nextAvailable)
                nextAvailable = (nextAvailable + 1) % palette.size
            }
        }

        return result
    }
}
