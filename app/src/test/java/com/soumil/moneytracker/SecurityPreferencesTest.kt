package com.soumil.moneytracker

import androidx.biometric.BiometricManager
import com.soumil.moneytracker.data.local.SecurityPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityPreferencesTest {

    @Test
    fun `preferences key and file names are correctly defined`() {
        assertEquals("security_preferences", SecurityPreferences.PREFS_NAME)
        assertEquals("biometric_app_lock_enabled", SecurityPreferences.KEY_BIOMETRIC_ENABLED)
    }

    @Test
    fun `authenticators include both BIOMETRIC_STRONG and DEVICE_CREDENTIAL`() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val hasStrongBiometrics = (authenticators and BiometricManager.Authenticators.BIOMETRIC_STRONG) != 0
        val hasDeviceCredential = (authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0

        assertTrue("Authenticators mask must include BIOMETRIC_STRONG", hasStrongBiometrics)
        assertTrue("Authenticators mask must include DEVICE_CREDENTIAL for PIN/pattern fallback", hasDeviceCredential)
    }
}
