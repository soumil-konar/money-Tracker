package com.soumil.moneytracker.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class EmailPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isEmailSyncEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_EMAIL_SYNC_ENABLED, false),
    )
    val isEmailSyncEnabled: StateFlow<Boolean> = _isEmailSyncEnabled

    private val _emailAddress = MutableStateFlow(
        preferences.getString(KEY_EMAIL_ADDRESS, "").orEmpty(),
    )
    val emailAddress: StateFlow<String> = _emailAddress

    private val _appPassword = MutableStateFlow(
        preferences.getString(KEY_APP_PASSWORD, "").orEmpty(),
    )
    val appPassword: StateFlow<String> = _appPassword

    private val _lastSyncTimestamp = MutableStateFlow(
        preferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L),
    )
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp

    private val _lastSyncStatus = MutableStateFlow(
        preferences.getString(KEY_LAST_SYNC_STATUS, null),
    )
    val lastSyncStatus: StateFlow<String?> = _lastSyncStatus

    fun setEmailSyncEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_EMAIL_SYNC_ENABLED, enabled).apply()
        _isEmailSyncEnabled.value = enabled
    }

    fun setCredentials(email: String, appPassword: String) {
        val sanitizer = com.soumil.moneytracker.email.EmailSyncManager()
        val trimmedEmail = sanitizer.sanitizeEmail(email)
        val cleanedPassword = sanitizer.sanitizeAppPassword(appPassword)
        preferences.edit()
            .putString(KEY_EMAIL_ADDRESS, trimmedEmail)
            .putString(KEY_APP_PASSWORD, cleanedPassword)
            .apply()
        _emailAddress.value = trimmedEmail
        _appPassword.value = cleanedPassword
    }

    fun updateSyncResult(timestampMillis: Long, status: String) {
        preferences.edit()
            .putLong(KEY_LAST_SYNC_TIMESTAMP, timestampMillis)
            .putString(KEY_LAST_SYNC_STATUS, status)
            .apply()
        _lastSyncTimestamp.value = timestampMillis
        _lastSyncStatus.value = status
    }

    fun clearCredentials() {
        preferences.edit()
            .remove(KEY_EMAIL_ADDRESS)
            .remove(KEY_APP_PASSWORD)
            .remove(KEY_LAST_SYNC_TIMESTAMP)
            .remove(KEY_LAST_SYNC_STATUS)
            .putBoolean(KEY_EMAIL_SYNC_ENABLED, false)
            .apply()
        _emailAddress.value = ""
        _appPassword.value = ""
        _lastSyncTimestamp.value = 0L
        _lastSyncStatus.value = null
        _isEmailSyncEnabled.value = false
    }

    companion object {
        internal const val PREFS_NAME = "money_tracker_email_prefs"
        internal const val KEY_EMAIL_SYNC_ENABLED = "email_sync_enabled"
        internal const val KEY_EMAIL_ADDRESS = "gmail_address"
        internal const val KEY_APP_PASSWORD = "gmail_app_password"
        internal const val KEY_LAST_SYNC_TIMESTAMP = "email_last_sync_timestamp"
        internal const val KEY_LAST_SYNC_STATUS = "email_last_sync_status"
    }
}
