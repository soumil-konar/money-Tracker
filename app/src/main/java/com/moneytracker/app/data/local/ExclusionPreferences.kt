package com.moneytracker.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ExclusionPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isExclusionFilterEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_EXCLUSION_ENABLED, true),
    )
    val isExclusionFilterEnabled: StateFlow<Boolean> = _isExclusionFilterEnabled

    private val _excludedKeywords = MutableStateFlow(
        preferences.getStringSet(KEY_EXCLUDED_KEYWORDS, DEFAULT_KEYWORDS)?.toSet() ?: DEFAULT_KEYWORDS,
    )
    val excludedKeywords: StateFlow<Set<String>> = _excludedKeywords

    fun setExclusionFilterEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_EXCLUSION_ENABLED, enabled).apply()
        _isExclusionFilterEnabled.value = enabled
    }

    fun addKeyword(keyword: String): Boolean {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return false
        val current = _excludedKeywords.value.toMutableSet()
        val added = current.add(trimmed)
        if (added) {
            preferences.edit().putStringSet(KEY_EXCLUDED_KEYWORDS, current).apply()
            _excludedKeywords.value = current
        }
        return added
    }

    fun removeKeyword(keyword: String): Boolean {
        val current = _excludedKeywords.value.toMutableSet()
        val removed = current.remove(keyword.trim())
        if (removed) {
            preferences.edit().putStringSet(KEY_EXCLUDED_KEYWORDS, current).apply()
            _excludedKeywords.value = current
        }
        return removed
    }

    fun resetToDefaults() {
        preferences.edit().putStringSet(KEY_EXCLUDED_KEYWORDS, DEFAULT_KEYWORDS).apply()
        _excludedKeywords.value = DEFAULT_KEYWORDS
    }

    fun clearAll() {
        val empty = emptySet<String>()
        preferences.edit().putStringSet(KEY_EXCLUDED_KEYWORDS, empty).apply()
        _excludedKeywords.value = empty
    }

    /**
     * Checks if any of the provided texts match an active exclusion keyword.
     * Uses word-boundary matching for short acronyms (<= 3 chars) to prevent false positives,
     * and case-insensitive substring matching for phrases and merchant names.
     *
     * @return The matched keyword if excluded, or null if no active keyword matched.
     */
    fun findMatchedKeyword(vararg texts: String?): String? {
        if (!_isExclusionFilterEnabled.value) return null
        val activeKeywords = _excludedKeywords.value
        if (activeKeywords.isEmpty()) return null

        for (keyword in activeKeywords) {
            val trimmed = keyword.trim()
            if (trimmed.isBlank()) continue

            val pattern = if (trimmed.length <= 3) {
                Regex("""\b${Regex.escape(trimmed)}\b""", RegexOption.IGNORE_CASE)
            } else {
                Regex(Regex.escape(trimmed), RegexOption.IGNORE_CASE)
            }

            for (text in texts) {
                if (text != null && pattern.containsMatchIn(text)) {
                    return trimmed
                }
            }
        }
        return null
    }

    fun isExcluded(vararg texts: String?): Boolean {
        return findMatchedKeyword(*texts) != null
    }

    companion object {
        internal const val PREFS_NAME = "money_tracker_exclusion_prefs"
        internal const val KEY_EXCLUSION_ENABLED = "exclusion_filter_enabled"
        internal const val KEY_EXCLUDED_KEYWORDS = "excluded_keywords"

        val DEFAULT_KEYWORDS = setOf(
            "Steam",
            "Epic Games",
            "PlayStation",
            "Refund",
            "Declined",
            "Failed",
            "Reversal",
            "OTP",
        )
    }
}
