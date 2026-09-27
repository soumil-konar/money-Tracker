package com.moneytracker.app.data.local

import android.content.Context
import com.moneytracker.app.data.model.TransactionCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class CustomCategoryItem(
    val label: String,
    val paletteIndex: Int? = null,
)

class CategoryPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _customCategories = MutableStateFlow<List<TransactionCategory>>(emptyList())
    val customCategories: StateFlow<List<TransactionCategory>> = _customCategories.asStateFlow()

    init {
        loadAndRegisterSavedCategories()
    }

    private fun loadAndRegisterSavedCategories() {
        val rawJson = preferences.getString(KEY_CUSTOM_CATEGORIES, null)
        val loadedList = mutableListOf<TransactionCategory>()

        if (!rawJson.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(rawJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val label = obj.optString("label").trim()
                    val paletteIndex = if (obj.has("paletteIndex") && !obj.isNull("paletteIndex")) {
                        obj.getInt("paletteIndex")
                    } else null

                    if (label.isNotBlank()) {
                        val category = TransactionCategory.custom(label)
                        TransactionCategory.register(category, paletteIndex)
                        if (loadedList.none { it.name.equals(category.name, ignoreCase = true) }) {
                            loadedList.add(category)
                        }
                    }
                }
            }
        } else {
            // Check legacy string set if previously used
            val rawSet = preferences.getStringSet(KEY_CUSTOM_CATEGORIES_SET, emptySet()) ?: emptySet()
            for (label in rawSet) {
                if (label.isNotBlank()) {
                    val category = TransactionCategory.custom(label)
                    TransactionCategory.register(category, null)
                    if (loadedList.none { it.name.equals(category.name, ignoreCase = true) }) {
                        loadedList.add(category)
                    }
                }
            }
        }

        loadedList.sortBy { it.label }
        _customCategories.value = loadedList
    }

    @Synchronized
    fun addCategory(label: String, paletteIndex: Int? = null): TransactionCategory {
        val trimmed = label.trim()
        val category = TransactionCategory.custom(trimmed)
        TransactionCategory.register(category, paletteIndex)

        val currentList = _customCategories.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.name.equals(category.name, ignoreCase = true) }

        if (existingIndex >= 0) {
            // Already present, update registration
            currentList[existingIndex] = category
        } else {
            currentList.add(category)
        }
        currentList.sortBy { it.label }
        _customCategories.value = currentList

        persistCategories(currentList)
        return category
    }

    private fun persistCategories(list: List<TransactionCategory>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val paletteIndex = TransactionCategory.getRegisteredPaletteIndex(item)
            val obj = JSONObject().apply {
                put("label", item.label)
                if (paletteIndex != null) {
                    put("paletteIndex", paletteIndex)
                }
            }
            jsonArray.put(obj)
        }
        preferences.edit().putString(KEY_CUSTOM_CATEGORIES, jsonArray.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "money_tracker_categories"
        private const val KEY_CUSTOM_CATEGORIES = "custom_categories_v2"
        private const val KEY_CUSTOM_CATEGORIES_SET = "custom_categories"
    }
}
