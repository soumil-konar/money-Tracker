package com.soumil.moneytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Expressive Fintech Color Tokens
private val ObsidianBg = Color(0xFF0C0C12)
private val ObsidianSurface = Color(0xFF14141E)
private val ObsidianCard = Color(0xFF1C1C2A)
private val ObsidianElevated = Color(0xFF262638)
private val TextWhiteHigh = Color(0xFFF6F6FA)
private val TextWhiteMedium = Color(0xFFA2A2B8)
private val BorderSubtleDark = Color(0xFF2B2B3E)

private val EmberFlame = Color(0xFFFF5E2B)
private val EmberFlameDark = Color(0xFFE0491A)
private val EmeraldMint = Color(0xFF10B981)
private val EmeraldMintContainer = Color(0xFF0E3827)
private val ElectricBlue = Color(0xFF38BDF8)
private val ChampagneGold = Color(0xFFF3C77C)
private val CocoaDark = Color(0xFF2B1D14)

private val IvoryBg = Color(0xFFF8F6F2)
private val PureWhite = Color(0xFFFFFFFF)
private val WarmCardLight = Color(0xFFF0EBE3)
private val TextDarkHigh = Color(0xFF1C1713)
private val TextDarkMedium = Color(0xFF6B5E53)
private val BorderSubtleLight = Color(0xFFDDD4C8)

private val LightScheme = lightColorScheme(
    primary = EmberFlameDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFECE5),
    onPrimaryContainer = Color(0xFF4A1204),
    secondary = ChampagneGold,
    onSecondary = CocoaDark,
    secondaryContainer = Color(0xFFFBF1DF),
    onSecondaryContainer = Color(0xFF38260D),
    tertiary = EmeraldMint,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2F8EE),
    onTertiaryContainer = Color(0xFF064426),
    background = IvoryBg,
    onBackground = TextDarkHigh,
    surface = PureWhite,
    onSurface = TextDarkHigh,
    surfaceVariant = WarmCardLight,
    onSurfaceVariant = TextDarkMedium,
    outline = BorderSubtleLight,
    outlineVariant = Color(0xFFECE5DB),
)

private val DarkScheme = darkColorScheme(
    primary = EmberFlame,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1A10),
    onPrimaryContainer = Color(0xFFFFCCBA),
    secondary = ChampagneGold,
    onSecondary = CocoaDark,
    secondaryContainer = Color(0xFF2C241B),
    onSecondaryContainer = Color(0xFFF5E4CE),
    tertiary = EmeraldMint,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldMintContainer,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = ObsidianBg,
    onBackground = TextWhiteHigh,
    surface = ObsidianSurface,
    onSurface = TextWhiteHigh,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextWhiteMedium,
    outline = BorderSubtleDark,
    outlineVariant = ObsidianElevated,
)

@Composable
fun MoneyTrackerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
