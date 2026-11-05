// 역할: Jetpack DataStore와 kotlinx.serialization을 활용하여 프롬프트 상용구(스니펫) 목록과 카테고리 목록을 로컬에 영구 저장합니다.
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.promptSnippetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PromptSnippetStore.DATASTORE_NAME
)

private val snippetJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

class PromptSnippetStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : PromptSnippetRepository {
    constructor(context: Context) : this(context.applicationContext.promptSnippetDataStore)

    @Volatile
    private var cachedSnippets: List<PromptSnippet>? = null

    @Volatile
    private var cachedCategories: List<String>? = null

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

    val categories: Flow<List<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            parseCategories(preferences[KEY_SNIPPET_CATEGORIES]).also {
                cachedCategories = it
            }
        }

    init {
        scope.launch {
            snippets.collect {
                // updates cachedSnippets
            }
        }
        scope.launch {
            categories.collect {
                // updates cachedCategories
            }
        }
    }

    override fun load(): List<PromptSnippet> {
        return cachedSnippets ?: emptyList()
    }

    override fun save(snippets: List<PromptSnippet>) {
        cachedSnippets = snippets.toList()
        scope.launch {
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

    override fun loadCategories(): List<String> {
        return cachedCategories ?: emptyList()
    }

    override fun saveCategories(categories: List<String>) {
        val sanitized = sanitizeCategories(categories)
        cachedCategories = sanitized
        scope.launch {
            saveCategoriesAsync(sanitized)
        }
    }

    suspend fun saveCategoriesAsync(categories: List<String>) {
        val sanitized = sanitizeCategories(categories)
        cachedCategories = sanitized
        val jsonString = serializeCategories(sanitized)
        dataStore.edit { preferences ->
            preferences[KEY_SNIPPET_CATEGORIES] = jsonString
        }
    }

    private fun sanitizeCategories(categories: List<String>): List<String> {
        return categories
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }

    private fun parseSnippets(jsonString: String?): List<PromptSnippet> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return runCatching {
            snippetJson.decodeFromString<List<PromptSnippet>>(jsonString)
        }.getOrDefault(emptyList())
    }

    private fun serializeSnippets(snippets: List<PromptSnippet>): String {
        return snippetJson.encodeToString(snippets)
    }

    private fun parseCategories(jsonString: String?): List<String> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return runCatching {
            sanitizeCategories(snippetJson.decodeFromString<List<String>>(jsonString))
        }.getOrDefault(emptyList())
    }

    private fun serializeCategories(categories: List<String>): String {
        return snippetJson.encodeToString(categories.map { it.trim() })
    }

    companion object {
        const val DATASTORE_NAME = "prompt_snippets"
        val KEY_SNIPPET_ITEMS = stringPreferencesKey("snippet_items")
        val KEY_SNIPPET_CATEGORIES = stringPreferencesKey("snippet_categories")
    }
}
