package com.soumil.moneytracker.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    DARK("Dark Mode"),
    LIGHT("Light Mode"),
}

enum class ThemeAccent(
    val label: String,
    val description: String,
    val isExpressive: Boolean,
) {
    EXPRESSIVE(
        label = "M3 Expressive",
        description = "Full Material 3 Expressive dynamic palette with high-chroma tones",
        isExpressive = true,
    ),
    MONOCHROME(
        label = "Black & White",
        description = "Minimalist monochrome grayscale and high-contrast styling",
        isExpressive = false,
    ),
    CRIMSON(
        label = "Crimson",
        description = "Bold ruby crimson red and rosewood accents",
        isExpressive = false,
    ),
    OCEAN(
        label = "Ocean",
        description = "Deep sapphire azure and vibrant marine cyan",
        isExpressive = false,
    ),
    SAGE(
        label = "Sage",
        description = "Calming organic eucalyptus and botanical moss",
        isExpressive = false,
    ),
    AMBER(
        label = "Amber",
        description = "Warm honey gold and radiant sunset topaz",
        isExpressive = false,
    ),
}

class ThemePreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching {
            ThemeMode.valueOf(
                preferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name,
            )
        }.getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _themeAccent = MutableStateFlow(
        runCatching {
            ThemeAccent.valueOf(
                preferences.getString(KEY_THEME_ACCENT, ThemeAccent.EXPRESSIVE.name)
                    ?: ThemeAccent.EXPRESSIVE.name,
            )
        }.getOrDefault(ThemeAccent.EXPRESSIVE),
    )
    val themeAccent: StateFlow<ThemeAccent> = _themeAccent

    fun setThemeMode(mode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setThemeAccent(accent: ThemeAccent) {
        preferences.edit().putString(KEY_THEME_ACCENT, accent.name).apply()
        _themeAccent.value = accent
    }

    companion object {
        private const val PREFS_NAME = "money_tracker_theme"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THEME_ACCENT = "theme_accent"
    }
}
