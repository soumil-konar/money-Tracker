package com.moneytracker.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReviewPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _lastReviewPromptTimestamp = MutableStateFlow(
        preferences.getLong(KEY_LAST_REVIEW_PROMPT_TIMESTAMP, 0L),
    )
    val lastReviewPromptTimestamp: StateFlow<Long> = _lastReviewPromptTimestamp.asStateFlow()

    fun setLastReviewPromptTimestamp(timestamp: Long) {
        preferences.edit().putLong(KEY_LAST_REVIEW_PROMPT_TIMESTAMP, timestamp).apply()
        _lastReviewPromptTimestamp.value = timestamp
    }

    private val _appInstallTimestamp = MutableStateFlow(
        preferences.getLong(KEY_APP_INSTALL_TIMESTAMP, 0L).let { stored ->
            if (stored == 0L) {
                val installTime = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
                }.getOrDefault(System.currentTimeMillis())
                preferences.edit().putLong(KEY_APP_INSTALL_TIMESTAMP, installTime).apply()
                installTime
            } else {
                stored
            }
        },
    )
    val appInstallTimestamp: StateFlow<Long> = _appInstallTimestamp.asStateFlow()

    fun setAppInstallTimestamp(timestamp: Long) {
        preferences.edit().putLong(KEY_APP_INSTALL_TIMESTAMP, timestamp).apply()
        _appInstallTimestamp.value = timestamp
    }

    private val _successfulMonthCloseCount = MutableStateFlow(
        preferences.getInt(KEY_SUCCESSFUL_MONTH_CLOSE_COUNT, 0),
    )
    val successfulMonthCloseCount: StateFlow<Int> = _successfulMonthCloseCount.asStateFlow()

    fun incrementSuccessfulMonthCloseCount() {
        val next = _successfulMonthCloseCount.value + 1
        preferences.edit().putInt(KEY_SUCCESSFUL_MONTH_CLOSE_COUNT, next).apply()
        _successfulMonthCloseCount.value = next
    }

    fun setSuccessfulMonthCloseCount(count: Int) {
        preferences.edit().putInt(KEY_SUCCESSFUL_MONTH_CLOSE_COUNT, count).apply()
        _successfulMonthCloseCount.value = count
    }

    companion object {
        private const val PREFS_NAME = "review_preferences"
        const val KEY_LAST_REVIEW_PROMPT_TIMESTAMP = "last_review_prompt_timestamp"
        const val KEY_APP_INSTALL_TIMESTAMP = "app_install_timestamp"
        const val KEY_SUCCESSFUL_MONTH_CLOSE_COUNT = "successful_month_close_count"
    }
}
