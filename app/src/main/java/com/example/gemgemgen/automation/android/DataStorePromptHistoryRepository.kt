// 역할: Jetpack DataStore와 JSON을 활용하여 사용자의 프롬프트 실행 기록 목록을 영구 저장하고 불러옵니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.usecase.PromptHistoryRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val Context.promptHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStorePromptHistoryRepository.DATASTORE_NAME
)

class DataStorePromptHistoryRepository(
    private val dataStore: DataStore<Preferences>
) : PromptHistoryRepository {
    constructor(context: Context) : this(context.applicationContext.promptHistoryDataStore)

    val historyFlow: Flow<List<PromptHistoryItem>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            parseHistoryItems(preferences[KEY_HISTORY_ITEMS])
        }

    override fun load(): List<PromptHistoryItem> {
        return runBlocking {
            historyFlow.first()
        }
    }

    override fun save(items: List<PromptHistoryItem>) {
        runBlocking {
            saveAsync(items)
        }
    }

    suspend fun saveAsync(items: List<PromptHistoryItem>) {
        val jsonString = serializeHistoryItems(items)
        dataStore.edit { preferences ->
            preferences[KEY_HISTORY_ITEMS] = jsonString
        }
    }

    private fun parseHistoryItems(jsonString: String?): List<PromptHistoryItem> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return runCatching {
            val jsonArray = Json.parseToJsonElement(jsonString).jsonArray
            jsonArray.mapNotNull { element ->
                val prompt = element.jsonPrimitive.content
                if (prompt.isNotBlank()) PromptHistoryItem(prompt = prompt) else null
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeHistoryItems(items: List<PromptHistoryItem>): String {
        val array = buildJsonArray {
            items.forEach { item ->
                add(kotlinx.serialization.json.JsonPrimitive(item.prompt))
            }
        }
        return array.toString()
    }

    companion object {
        const val DATASTORE_NAME = "prompt_history"
        val KEY_HISTORY_ITEMS = stringPreferencesKey("history_items")
    }
}
