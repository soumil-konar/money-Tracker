package com.moneytracker.app.data.local

import android.content.Context
import androidx.biometric.BiometricManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class BiometricLockTimeout(val label: String, val seconds: Long) {
    IMMEDIATELY("Immediately", 0L),
    ONE_MINUTE("1 minute", 60L),
    FIVE_MINUTES("5 minutes", 300L);

    companion object {
        fun fromName(name: String?): BiometricLockTimeout {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: IMMEDIATELY
        }
    }
}

class SecurityPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isBiometricEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_BIOMETRIC_ENABLED, false),
    )
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled

    fun setBiometricEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        _isBiometricEnabled.value = enabled
    }

    private val _biometricTimeout = MutableStateFlow(
        BiometricLockTimeout.fromName(preferences.getString(KEY_BIOMETRIC_TIMEOUT, BiometricLockTimeout.IMMEDIATELY.name)),
    )
    val biometricTimeout: StateFlow<BiometricLockTimeout> = _biometricTimeout

    fun setBiometricTimeout(timeout: BiometricLockTimeout) {
        preferences.edit().putString(KEY_BIOMETRIC_TIMEOUT, timeout.name).apply()
        _biometricTimeout.value = timeout
    }

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    companion object {
        internal const val PREFS_NAME = "security_preferences"
        internal const val KEY_BIOMETRIC_ENABLED = "biometric_app_lock_enabled"
        internal const val KEY_BIOMETRIC_TIMEOUT = "biometric_lock_timeout"
    }
}
