// 역할: 사용자가 이전에 실행했던 프롬프트 입력 기록 목록을 로컬에 저장하고 불러옵니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.usecase.PromptHistoryRepository
import org.json.JSONArray

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
                val prompt = jsonArray.optString(i)
                if (prompt.isNotBlank()) {
                    items.add(PromptHistoryItem(prompt = prompt))
                }
            }
            items
        }.getOrDefault(emptyList())
    }

    override fun save(items: List<PromptHistoryItem>) {
        val jsonArray = JSONArray()
        items.forEach { item ->
            jsonArray.put(item.prompt)
        }
        preferences.edit()
            .putString(KEY_HISTORY_ITEMS, jsonArray.toString())
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "prompt_history"
        private const val KEY_HISTORY_ITEMS = "history_items"
    }
}
