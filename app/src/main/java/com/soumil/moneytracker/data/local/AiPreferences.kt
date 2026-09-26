package com.soumil.moneytracker.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AiEngineMode(val label: String, val description: String) {
    AUTO_PIXEL_FIRST(
        label = "Auto (Pixel 9 Local First)",
        description = "Runs on-device via Tensor G4 first for zero latency and privacy; falls back to cloud Gemini Flash Lite when needed.",
    ),
    ON_DEVICE_ONLY(
        label = "On-Device Only (100% Offline)",
        description = "No data ever leaves your device. All parsing, places, and spending chat run 100% offline.",
    ),
    CLOUD_ONLY(
        label = "Cloud Only (Google AI Studio)",
        description = "Processes transactions and RAG chat through Google AI Studio Gemini API.",
    ),
}

class AiPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val secureHelper = SecurePreferencesHelper(context, PREFS_NAME)

    private val _engineMode = MutableStateFlow(
        runCatching {
            AiEngineMode.valueOf(preferences.getString(KEY_ENGINE_MODE, AiEngineMode.AUTO_PIXEL_FIRST.name) ?: AiEngineMode.AUTO_PIXEL_FIRST.name)
        }.getOrDefault(AiEngineMode.AUTO_PIXEL_FIRST),
    )
    val engineMode: StateFlow<AiEngineMode> = _engineMode

    private val _apiKey = MutableStateFlow(
        secureHelper.getSecureString(KEY_API_KEY, DEFAULT_API_KEY),
    )
    val apiKey: StateFlow<String> = _apiKey

    private val _isAiEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_AI_ENABLED, true),
    )
    val isAiEnabled: StateFlow<Boolean> = _isAiEnabled

    private val _selectedModel = MutableStateFlow(
        preferences.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
    )
    val selectedModel: StateFlow<String> = _selectedModel

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        secureHelper.putSecureString(KEY_API_KEY, trimmed)
        _apiKey.value = trimmed
    }

    fun setAiEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
        _isAiEnabled.value = enabled
    }

    fun setSelectedModel(model: String) {
        val sanitized = model.trim().ifBlank { DEFAULT_MODEL }
        preferences.edit().putString(KEY_SELECTED_MODEL, sanitized).apply()
        _selectedModel.value = sanitized
    }

    fun resetToDefaultKey() {
        setApiKey(DEFAULT_API_KEY)
    }

    fun setEngineMode(mode: AiEngineMode) {
        preferences.edit().putString(KEY_ENGINE_MODE, mode.name).apply()
        _engineMode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "money_tracker_ai_prefs"
        private const val KEY_API_KEY = "ai_studio_api_key"
        private const val KEY_AI_ENABLED = "ai_parsing_enabled"
        private const val KEY_SELECTED_MODEL = "ai_selected_model"
        private const val KEY_ENGINE_MODE = "ai_engine_mode"

        // Default API key - configured by user via Settings or runtime config
        const val DEFAULT_API_KEY = ""
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        const val FALLBACK_MODEL = "gemini-3.1-flash-lite"

        val AVAILABLE_MODELS = listOf(
            "gemini-3.5-flash-lite" to "Gemini 3.5 Flash Lite (Recommended, 500 RPD)",
            "gemini-3.1-flash-lite" to "Gemini 3.1 Flash Lite (Fast fallback, 500 RPD)",
            "gemini-3.6-flash" to "Gemini 3.6 Flash (High Reasoning, 20 RPD)",
            "gemini-3.8-flash" to "Gemini 3.8 Flash (Latest, 20 RPD)",
        )
    }
}
