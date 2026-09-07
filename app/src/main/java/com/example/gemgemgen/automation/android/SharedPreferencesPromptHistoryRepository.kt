package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.usecase.PromptHistoryRepository
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesPromptHistoryRepository(
    context: Context
) : PromptHistoryRepository {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): List<PromptHistoryItem> {
        val jsonString = preferences.getString(KEY_HISTORY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val jsonArray = JSONArray(jsonString)
            val items = mutableListOf<PromptHistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    PromptHistoryItem(
                        id = obj.getString(KEY_ID),
                        prompt = obj.getString(KEY_PROMPT),
                        targetApp = AutomationTargetApp.fromStorageValue(obj.optString(KEY_TARGET_APP, "")),
                        createdAtMillis = obj.optLong(KEY_CREATED_AT, 0L)
                    )
                )
            }
            items
        }.getOrDefault(emptyList())
    }

    override fun save(items: List<PromptHistoryItem>) {
        val jsonArray = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put(KEY_ID, item.id)
                put(KEY_PROMPT, item.prompt)
                put(KEY_TARGET_APP, item.targetApp.storageValue)
                put(KEY_CREATED_AT, item.createdAtMillis)
            }
            jsonArray.put(obj)
        }
        preferences.edit()
            .putString(KEY_HISTORY_ITEMS, jsonArray.toString())
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "prompt_history"
        private const val KEY_HISTORY_ITEMS = "history_items"
        private const val KEY_ID = "id"
        private const val KEY_PROMPT = "prompt"
        private const val KEY_TARGET_APP = "target_app"
        private const val KEY_CREATED_AT = "created_at"
    }
}
