package com.soumil.moneytracker.data.local

import android.content.Context
import androidx.biometric.BiometricManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    companion object {
        internal const val PREFS_NAME = "security_preferences"
        internal const val KEY_BIOMETRIC_ENABLED = "biometric_app_lock_enabled"
    }
}
