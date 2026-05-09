package com.soumil.moneytracker.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SetupPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _isInitialSetupComplete = MutableStateFlow(
        preferences.getBoolean(KEY_INITIAL_SETUP_COMPLETE, false),
    )

    val isInitialSetupComplete: StateFlow<Boolean> = _isInitialSetupComplete

    fun markInitialSetupComplete(isComplete: Boolean = true) {
        preferences.edit().putBoolean(KEY_INITIAL_SETUP_COMPLETE, isComplete).apply()
        _isInitialSetupComplete.value = isComplete
    }

    companion object {
        private const val PREFS_NAME = "money_tracker_preferences"
        private const val KEY_INITIAL_SETUP_COMPLETE = "initial_setup_complete"
    }
}
