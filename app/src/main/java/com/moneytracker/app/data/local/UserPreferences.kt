package com.moneytracker.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class UserPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _userName = MutableStateFlow(
        preferences.getString(KEY_USER_NAME, "").orEmpty(),
    )
    val userName: StateFlow<String> = _userName

    private val _hasPromptedForName = MutableStateFlow(
        preferences.getBoolean(KEY_HAS_PROMPTED_FOR_NAME, false),
    )
    val hasPromptedForName: StateFlow<Boolean> = _hasPromptedForName

    fun setUserName(name: String) {
        val sanitized = name.trim()
        preferences.edit()
            .putString(KEY_USER_NAME, sanitized)
            .putBoolean(KEY_HAS_PROMPTED_FOR_NAME, true)
            .apply()
        _userName.value = sanitized
        _hasPromptedForName.value = true
    }

    fun markPromptedForName() {
        preferences.edit()
            .putBoolean(KEY_HAS_PROMPTED_FOR_NAME, true)
            .apply()
        _hasPromptedForName.value = true
    }

    companion object {
        private const val PREFS_NAME = "user_preferences"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_HAS_PROMPTED_FOR_NAME = "has_prompted_for_name"
    }
}
