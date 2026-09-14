// 역할: Gemini 계정 목록을 SharedPreferences와 JSON을 통해 로컬 저장소에 안전하게 보관하고 불러옵니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.domain.GeminiAccountProfile
import com.example.gemgemgen.automation.usecase.GeminiAccountRepository
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesGeminiAccountRepository(
    context: Context
) : GeminiAccountRepository {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun loadAccounts(): List<GeminiAccountProfile> {
        val jsonString = preferences.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val jsonArray = JSONArray(jsonString)
            val items = mutableListOf<GeminiAccountProfile>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    GeminiAccountProfile(
                        id = obj.getString(KEY_ID),
                        alias = obj.getString(KEY_ALIAS),
                        identifier = obj.optString(KEY_IDENTIFIER, ""),
                        order = obj.optInt(KEY_ORDER, i + 1),
                        isActive = obj.optBoolean(KEY_IS_ACTIVE, false)
                    )
                )
            }
            items
        }.getOrDefault(emptyList())
    }

    override fun saveAccounts(accounts: List<GeminiAccountProfile>) {
        val jsonArray = JSONArray()
        accounts.forEach { item ->
            val obj = JSONObject().apply {
                put(KEY_ID, item.id)
                put(KEY_ALIAS, item.alias)
                put(KEY_IDENTIFIER, item.identifier)
                put(KEY_ORDER, item.order)
                put(KEY_IS_ACTIVE, item.isActive)
            }
            jsonArray.put(obj)
        }
        preferences.edit()
            .putString(KEY_ACCOUNTS, jsonArray.toString())
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "gemini_accounts"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_ID = "id"
        private const val KEY_ALIAS = "alias"
        private const val KEY_IDENTIFIER = "identifier"
        private const val KEY_ORDER = "order"
        private const val KEY_IS_ACTIVE = "is_active"
    }
}
