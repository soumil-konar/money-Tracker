package com.soumil.moneytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Sand = Color(0xFFF6E8D0)
private val Cream = Color(0xFFFFFBF6)
private val BurntOrange = Color(0xFFF16621)
private val WarmGold = Color(0xFFF2C661)
private val Cocoa = Color(0xFF4A2D1B)
private val Mint = Color(0xFF2FA56A)
private val SoftGray = Color(0xFFF0E8DB)

private val LightScheme = lightColorScheme(
    primary = BurntOrange,
    onPrimary = Color.White,
    secondary = WarmGold,
    onSecondary = Cocoa,
    tertiary = Mint,
    background = Sand,
    onBackground = Cocoa,
    surface = Cream,
    onSurface = Cocoa,
    surfaceVariant = SoftGray,
    onSurfaceVariant = Color(0xFF6E6256),
    outline = Color(0xFFD6C6AF),
)

private val DarkScheme = darkColorScheme(
    primary = BurntOrange,
    secondary = WarmGold,
    tertiary = Mint,
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

