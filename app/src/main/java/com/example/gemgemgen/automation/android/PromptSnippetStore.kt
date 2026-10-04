// 역할: Jetpack DataStore와 JSON을 활용하여 프롬프트 상용구 목록을 로컬에 영구 저장하고 Flow 및 동기 조회를 제공합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.automation.usecase.PromptSnippetRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private val Context.promptSnippetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PromptSnippetStore.DATASTORE_NAME
)

class PromptSnippetStore(
    private val dataStore: DataStore<Preferences>
) : PromptSnippetRepository {
    constructor(context: Context) : this(context.applicationContext.promptSnippetDataStore)

    @Volatile
    private var cachedSnippets: List<PromptSnippet>? = null

    val snippets: Flow<List<PromptSnippet>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            parseSnippets(preferences[KEY_SNIPPET_ITEMS]).also {
                cachedSnippets = it
            }
        }

    override fun load(): List<PromptSnippet> {
        cachedSnippets?.let { return it }
        return runBlocking {
            snippets.first()
        }
    }

    override fun save(snippets: List<PromptSnippet>) {
        cachedSnippets = snippets.toList()
        runBlocking {
            saveAsync(snippets)
        }
    }

    suspend fun saveAsync(snippets: List<PromptSnippet>) {
        cachedSnippets = snippets.toList()
        val jsonString = serializeSnippets(snippets)
        dataStore.edit { preferences ->
            preferences[KEY_SNIPPET_ITEMS] = jsonString
        }
    }

    private fun parseSnippets(jsonString: String?): List<PromptSnippet> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return runCatching {
            val jsonArray = Json.parseToJsonElement(jsonString).jsonArray
            jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj[KEY_ID]?.jsonPrimitive?.content ?: return@mapNotNull null
                val shortcut = obj[KEY_SHORTCUT]?.jsonPrimitive?.content ?: return@mapNotNull null
                val content = obj[KEY_CONTENT]?.jsonPrimitive?.content ?: return@mapNotNull null
                val createdAt = obj[KEY_CREATED_AT]?.jsonPrimitive?.longOrNull ?: 0L
                PromptSnippet(
                    id = id,
                    shortcut = shortcut,
                    content = content,
                    createdAtMillis = createdAt
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeSnippets(snippets: List<PromptSnippet>): String {
        val array = buildJsonArray {
            snippets.forEach { item ->
                add(
                    buildJsonObject {
                        put(KEY_ID, item.id)
                        put(KEY_SHORTCUT, item.shortcut)
                        put(KEY_CONTENT, item.content)
                        put(KEY_CREATED_AT, item.createdAtMillis)
                    }
                )
            }
        }
        return array.toString()
    }

    companion object {
        const val DATASTORE_NAME = "prompt_snippets"
        val KEY_SNIPPET_ITEMS = stringPreferencesKey("snippet_items")

        private const val KEY_ID = "id"
        private const val KEY_SHORTCUT = "shortcut"
        private const val KEY_CONTENT = "content"
        private const val KEY_CREATED_AT = "created_at"
    }
}
