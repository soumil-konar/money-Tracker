package com.soumil.moneytracker.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class HapticIntensity(val label: String, val scaleFactor: Float) {
    SOFT("Soft", 0.65f),
    BALANCED("Balanced", 1.0f),
    STRONG("Strong", 1.4f),
}

class HapticPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isHapticEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_HAPTIC_ENABLED, true),
    )
    val isHapticEnabled: StateFlow<Boolean> = _isHapticEnabled

    private val _hapticIntensity = MutableStateFlow(
        runCatching {
            HapticIntensity.valueOf(
                preferences.getString(KEY_HAPTIC_INTENSITY, HapticIntensity.BALANCED.name)
                    ?: HapticIntensity.BALANCED.name,
            )
        }.getOrDefault(HapticIntensity.BALANCED),
    )
    val hapticIntensity: StateFlow<HapticIntensity> = _hapticIntensity

    fun setHapticEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
        _isHapticEnabled.value = enabled
    }

    fun setHapticIntensity(intensity: HapticIntensity) {
        preferences.edit().putString(KEY_HAPTIC_INTENSITY, intensity.name).apply()
        _hapticIntensity.value = intensity
    }

    companion object {
        private const val PREFS_NAME = "money_tracker_haptics"
        private const val KEY_HAPTIC_ENABLED = "haptics_enabled"
        private const val KEY_HAPTIC_INTENSITY = "haptics_intensity"
    }
}
