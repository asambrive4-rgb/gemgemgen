// 역할: SharedPreferences와 JSON을 활용하여 프롬프트 상용구 목록을 로컬에 영구 저장하고 불러옵니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.automation.usecase.PromptSnippetRepository
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesPromptSnippetRepository(
    context: Context
) : PromptSnippetRepository {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): List<PromptSnippet> {
        val jsonString = preferences.getString(KEY_SNIPPET_ITEMS, null) ?: return emptyList()
        return runCatching {
            val jsonArray = JSONArray(jsonString)
            val items = mutableListOf<PromptSnippet>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    PromptSnippet(
                        id = obj.getString(KEY_ID),
                        shortcut = obj.getString(KEY_SHORTCUT),
                        content = obj.getString(KEY_CONTENT),
                        createdAtMillis = obj.optLong(KEY_CREATED_AT, 0L)
                    )
                )
            }
            items
        }.getOrDefault(emptyList())
    }

    override fun save(snippets: List<PromptSnippet>) {
        val jsonArray = JSONArray()
        snippets.forEach { item ->
            val obj = JSONObject().apply {
                put(KEY_ID, item.id)
                put(KEY_SHORTCUT, item.shortcut)
                put(KEY_CONTENT, item.content)
                put(KEY_CREATED_AT, item.createdAtMillis)
            }
            jsonArray.put(obj)
        }
        preferences.edit()
            .putString(KEY_SNIPPET_ITEMS, jsonArray.toString())
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "prompt_snippets"
        private const val KEY_SNIPPET_ITEMS = "snippet_items"
        private const val KEY_ID = "id"
        private const val KEY_SHORTCUT = "shortcut"
        private const val KEY_CONTENT = "content"
        private const val KEY_CREATED_AT = "created_at"
    }
}
