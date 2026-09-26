package com.moneytracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moneytracker.app.data.local.ThemeAccent
import com.moneytracker.app.data.local.ThemeMode

// ============================================================================
// 1. MATERIAL 3 EXPRESSIVE FINTECH PALETTE (High-Chroma Vibrant Tonal Schemes)
// ============================================================================

private val M3ExpressiveLight = lightColorScheme(
    primary = Color(0xFF635BFF),             // Expressive Electric Violet
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEECFF),
    onPrimaryContainer = Color(0xFF1E1763),
    inversePrimary = Color(0xFF9D95FF),
    secondary = Color(0xFF0077B6),           // Vibrant Azure Blue
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF002B45),
    tertiary = Color(0xFF059669),            // Emerald Mint
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF023625),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF141724),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141724),
    surfaceVariant = Color(0xFFEFF1F7),
    onSurfaceVariant = Color(0xFF555B70),
    surfaceTint = Color(0xFF635BFF),
    inverseSurface = Color(0xFF141724),
    inverseOnSurface = Color(0xFFF3F4F8),
    outline = Color(0xFFD3D7E3),
    outlineVariant = Color(0xFFE5E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val M3ExpressiveDark = darkColorScheme(
    primary = Color(0xFF8B5CF6),             // Luminous Neon Violet / Iris
    onPrimary = Color(0xFF1B0E38),
    primaryContainer = Color(0xFF381E72),
    onPrimaryContainer = Color(0xFFE2D9FF),
    inversePrimary = Color(0xFF635BFF),
    secondary = Color(0xFF38BDF8),           // Luminous Sky / Electric Cyan
    onSecondary = Color(0xFF03283E),
    secondaryContainer = Color(0xFF0B3F60),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = Color(0xFF34D399),            // High-Chroma Emerald Mint
    onTertiary = Color(0xFF022E1F),
    tertiaryContainer = Color(0xFF0B4D35),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = Color(0xFF0A0C13),          // Pitch Luminous Obsidian
    onBackground = Color(0xFFF3F4F8),
    surface = Color(0xFF111420),
    onSurface = Color(0xFFF3F4F8),
    surfaceVariant = Color(0xFF191D2E),
    onSurfaceVariant = Color(0xFFA1A7BC),
    surfaceTint = Color(0xFF8B5CF6),
    inverseSurface = Color(0xFFF3F4F8),
    inverseOnSurface = Color(0xFF141724),
    outline = Color(0xFF2C324B),
    outlineVariant = Color(0xFF21263B),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

// ============================================================================
// 2. CURATED ACCENTS (Disables Material 3 Expressive)
// ============================================================================

// --- A. Black and White (Monochrome Luxury) ---
private val MonochromeLight = lightColorScheme(
    primary = Color(0xFF18181B),             // Zinc-900
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = Color(0xFF18181B),
    secondary = Color(0xFF3F3F46),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4F4F5),
    onSecondaryContainer = Color(0xFF18181B),
    tertiary = Color(0xFF71717A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4E4E7),
    onTertiaryContainer = Color(0xFF27272A),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF09090B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF09090B),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF71717A),
    outline = Color(0xFFD4D4D8),
    outlineVariant = Color(0xFFE4E4E7),
)

private val MonochromeDark = darkColorScheme(
    primary = Color(0xFFFAFAFA),             // Stark Zinc White
    onPrimary = Color(0xFF09090B),
    primaryContainer = Color(0xFF27272A),
    onPrimaryContainer = Color(0xFFF4F4F5),
    secondary = Color(0xFFA1A1AA),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF27272A),
    onSecondaryContainer = Color(0xFFE4E4E7),
    tertiary = Color(0xFFD4D4D8),
    onTertiary = Color(0xFF18181B),
    tertiaryContainer = Color(0xFF3F3F46),
    onTertiaryContainer = Color(0xFFF4F4F5),
    background = Color(0xFF09090B),          // Deep Onyx
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF121215),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF1C1C22),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF2E2E36),
    outlineVariant = Color(0xFF23232A),
)

// --- B. Crimson (Ruby & Rosewood) ---
private val CrimsonLight = lightColorScheme(
    primary = Color(0xFFBE123C),             // Bold Crimson
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4E6),
    onPrimaryContainer = Color(0xFF4C0519),
    secondary = Color(0xFF9F1239),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF1F2),
    onSecondaryContainer = Color(0xFF700A24),
    tertiary = Color(0xFF881337),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFCCD5),
    onTertiaryContainer = Color(0xFF4C0519),
    background = Color(0xFFFFF7F8),
    onBackground = Color(0xFF1F060B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F060B),
    surfaceVariant = Color(0xFFFDE8EB),
    onSurfaceVariant = Color(0xFF6E2835),
    outline = Color(0xFFEAB8C1),
    outlineVariant = Color(0xFFF7D4DA),
)

private val CrimsonDark = darkColorScheme(
    primary = Color(0xFFFB7185),             // Vibrant Rose Crimson
    onPrimary = Color(0xFF4C0519),
    primaryContainer = Color(0xFF881337),
    onPrimaryContainer = Color(0xFFFFCCD5),
    secondary = Color(0xFFF43F5E),
    onSecondary = Color(0xFF4C0519),
    secondaryContainer = Color(0xFF5E0B20),
    onSecondaryContainer = Color(0xFFFFCCD5),
    tertiary = Color(0xFFFDA4AF),
    onTertiary = Color(0xFF4C0519),
    tertiaryContainer = Color(0xFF3D0513),
    onTertiaryContainer = Color(0xFFFFCCD5),
    background = Color(0xFF0C0406),          // Deep Ruby Dark
    onBackground = Color(0xFFFFF1F2),
    surface = Color(0xFF16080C),
    onSurface = Color(0xFFFFF1F2),
    surfaceVariant = Color(0xFF240E14),
    onSurfaceVariant = Color(0xFFB5939A),
    outline = Color(0xFF481A24),
    outlineVariant = Color(0xFF331219),
)

// --- C. Ocean (Deep Sapphire & Azure) ---
private val OceanLight = lightColorScheme(
    primary = Color(0xFF0284C7),             // Azure Marine
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF03446A),
    secondary = Color(0xFF0369A1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F9FF),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = Color(0xFF0891B2),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFFAFE),
    onTertiaryContainer = Color(0xFF155E75),
    background = Color(0xFFF4FAFF),
    onBackground = Color(0xFF041826),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF041826),
    surfaceVariant = Color(0xFFE2F0F9),
    onSurfaceVariant = Color(0xFF38576E),
    outline = Color(0xFFB4D4E8),
    outlineVariant = Color(0xFFD4E6F3),
)

private val OceanDark = darkColorScheme(
    primary = Color(0xFF38BDF8),             // Electric Cyan Azure
    onPrimary = Color(0xFF03223A),
    primaryContainer = Color(0xFF075985),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color(0xFF03223A),
    secondaryContainer = Color(0xFF03446A),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFF22D3EE),
    onTertiary = Color(0xFF083344),
    tertiaryContainer = Color(0xFF0E5164),
    onTertiaryContainer = Color(0xFFA5F3FC),
    background = Color(0xFF050E17),          // Abyssal Navy
    onBackground = Color(0xFFF0F9FF),
    surface = Color(0xFF0B1724),
    onSurface = Color(0xFFF0F9FF),
    surfaceVariant = Color(0xFF132233),
    onSurfaceVariant = Color(0xFF8CAAC2),
    outline = Color(0xFF1E3850),
    outlineVariant = Color(0xFF172D40),
)

// --- D. Sage (Botanical Eucalyptus & Olive) ---
private val SageLight = lightColorScheme(
    primary = Color(0xFF15803D),             // Forest Sage
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF14532D),
    secondary = Color(0xFF166534),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0FDF4),
    onSecondaryContainer = Color(0xFF14532D),
    tertiary = Color(0xFF4D7C0F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECFCCB),
    onTertiaryContainer = Color(0xFF365314),
    background = Color(0xFFF4FAF5),
    onBackground = Color(0xFF091A0F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF091A0F),
    surfaceVariant = Color(0xFFE2EFE5),
    onSurfaceVariant = Color(0xFF415A49),
    outline = Color(0xFFBCD5C3),
    outlineVariant = Color(0xFFD6E7DC),
)

private val SageDark = darkColorScheme(
    primary = Color(0xFF86EFAC),             // Soft Sage Mint
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF166534),
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFF14532D),
    onSecondaryContainer = Color(0xFFDCFCE7),
    tertiary = Color(0xFFA3E635),
    onTertiary = Color(0xFF1A2E05),
    tertiaryContainer = Color(0xFF365314),
    onTertiaryContainer = Color(0xFFD9F99D),
    background = Color(0xFF07120A),          // Deep Moss
    onBackground = Color(0xFFF0FDF4),
    surface = Color(0xFF0E1C12),
    onSurface = Color(0xFFF0FDF4),
    surfaceVariant = Color(0xFF17291D),
    onSurfaceVariant = Color(0xFF90AFA0),
    outline = Color(0xFF243F2D),
    outlineVariant = Color(0xFF1C3224),
)

// --- E. Amber (Topaz, Sunset & Warm Honey) ---
private val AmberLight = lightColorScheme(
    primary = Color(0xFFD97706),             // Amber Gold
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF451A03),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFFBEB),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFFC2410C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDD5),
    onTertiaryContainer = Color(0xFF7C2D12),
    background = Color(0xFFFFFDF7),
    onBackground = Color(0xFF261805),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF261805),
    surfaceVariant = Color(0xFFF8EED9),
    onSurfaceVariant = Color(0xFF6B583E),
    outline = Color(0xFFE4CFAC),
    outlineVariant = Color(0xFFF0E2C8),
)

private val AmberDark = darkColorScheme(
    primary = Color(0xFFFBBF24),             // Radiant Topaz
    onPrimary = Color(0xFF451A03),
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFEF3C7),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF5B2B0A),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFFFB923C),
    onTertiary = Color(0xFF431407),
    tertiaryContainer = Color(0xFF5C1D07),
    onTertiaryContainer = Color(0xFFFFEDD5),
    background = Color(0xFF120C04),          // Warm Dark Bronze
    onBackground = Color(0xFFFFFBEB),
    surface = Color(0xFF1B1408),
    onSurface = Color(0xFFFFFBEB),
    surfaceVariant = Color(0xFF291E0E),
    onSurfaceVariant = Color(0xFFC0A98E),
    outline = Color(0xFF473318),
    outlineVariant = Color(0xFF332512),
)

// ============================================================================
// 3. SHAPES (Expressive Springy Curves vs Standard Clean Curves)
// ============================================================================

val AppExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

val AppStandardShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ============================================================================
// 4. COMPOSITION LOCALS
// ============================================================================

@Immutable
data class ExpressiveThemeState(
    val isExpressive: Boolean,
    val accent: ThemeAccent,
    val isDark: Boolean,
)

val LocalExpressiveTheme = staticCompositionLocalOf {
    ExpressiveThemeState(
        isExpressive = true,
        accent = ThemeAccent.EXPRESSIVE,
        isDark = false,
    )
}

// ============================================================================
// 5. COLOR SCHEME RESOLVER
// ============================================================================

fun resolveColorScheme(
    accent: ThemeAccent,
    isDark: Boolean,
): ColorScheme {
    return when (accent) {
        ThemeAccent.EXPRESSIVE -> if (isDark) M3ExpressiveDark else M3ExpressiveLight
        ThemeAccent.MONOCHROME -> if (isDark) MonochromeDark else MonochromeLight
        ThemeAccent.CRIMSON -> if (isDark) CrimsonDark else CrimsonLight
        ThemeAccent.OCEAN -> if (isDark) OceanDark else OceanLight
        ThemeAccent.SAGE -> if (isDark) SageDark else SageLight
        ThemeAccent.AMBER -> if (isDark) AmberDark else AmberLight
    }
}

// ============================================================================
// 6. MAIN THEME WRAPPER
// ============================================================================

@Composable
fun MoneyTrackerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeAccent: ThemeAccent = ThemeAccent.EXPRESSIVE,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val isExpressive = themeAccent.isExpressive
    val colorScheme = resolveColorScheme(themeAccent, isDark)
    val shapes = if (isExpressive) AppExpressiveShapes else AppStandardShapes

    val themeState = ExpressiveThemeState(
        isExpressive = isExpressive,
        accent = themeAccent,
        isDark = isDark,
    )

    CompositionLocalProvider(LocalExpressiveTheme provides themeState) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = shapes,
            content = content,
        )
    }
}
