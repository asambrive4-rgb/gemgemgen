// 역할: Jetpack DataStore와 JSON을 활용하여 프롬프트 실험(연금술) 프리셋 세트 및 항목 목록을 로컬에 영구 저장합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.automation.domain.PromptExperimentItem
import com.example.gemgemgen.automation.domain.PromptExperimentPolicy
import com.example.gemgemgen.automation.domain.PromptExperimentPreset
import com.example.gemgemgen.automation.usecase.PromptExperimentRepository
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

private val Context.promptExperimentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PromptExperimentStore.DATASTORE_NAME
)

class PromptExperimentStore(
    private val dataStore: DataStore<Preferences>
) : PromptExperimentRepository {
    constructor(context: Context) : this(context.applicationContext.promptExperimentDataStore)

    @Volatile
    private var cachedPresets: List<PromptExperimentPreset>? = null

    @Volatile
    private var cachedActivePresetId: String? = null

    val presets: Flow<List<PromptExperimentPreset>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            parsePresets(preferences[KEY_EXPERIMENT_PRESETS]).also {
                cachedPresets = it
            }
        }

    override fun loadPresets(): List<PromptExperimentPreset> {
        cachedPresets?.let { return it }
        return runBlocking {
            presets.first()
        }
    }

    override fun savePresets(presets: List<PromptExperimentPreset>) {
        val normalized = presets.ifEmpty { listOf(PromptExperimentPolicy.defaultPreset()) }
        cachedPresets = normalized
        runBlocking {
            savePresetsAsync(normalized)
        }
    }

    suspend fun savePresetsAsync(presets: List<PromptExperimentPreset>) {
        val normalized = presets.ifEmpty { listOf(PromptExperimentPolicy.defaultPreset()) }
        cachedPresets = normalized
        val jsonString = serializePresets(normalized)
        dataStore.edit { preferences ->
            preferences[KEY_EXPERIMENT_PRESETS] = jsonString
        }
    }

    override fun loadActivePresetId(): String? {
        cachedActivePresetId?.let { return it }
        return runBlocking {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { it[KEY_ACTIVE_PRESET_ID] }
                .first()
                .also { cachedActivePresetId = it }
        }
    }

    override fun saveActivePresetId(presetId: String?) {
        cachedActivePresetId = presetId
        runBlocking {
            dataStore.edit { preferences ->
                if (presetId.isNullOrBlank()) {
                    preferences.remove(KEY_ACTIVE_PRESET_ID)
                } else {
                    preferences[KEY_ACTIVE_PRESET_ID] = presetId
                }
            }
        }
    }

    private fun parsePresets(jsonString: String?): List<PromptExperimentPreset> {
        if (jsonString.isNullOrBlank()) return listOf(PromptExperimentPolicy.defaultPreset())
        return runCatching {
            val jsonArray = Json.parseToJsonElement(jsonString).jsonArray
            val parsed = jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj[KEY_ID]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj[KEY_NAME]?.jsonPrimitive?.content ?: return@mapNotNull null
                val createdAt = obj[KEY_CREATED_AT]?.jsonPrimitive?.longOrNull ?: 0L
                val itemsArray = obj[KEY_ITEMS]?.jsonArray
                val items = itemsArray?.mapNotNull { itemEl ->
                    val itemObj = itemEl.jsonObject
                    val itemId = itemObj[KEY_ID]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val content = itemObj[KEY_CONTENT]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val itemCreatedAt = itemObj[KEY_CREATED_AT]?.jsonPrimitive?.longOrNull ?: 0L
                    PromptExperimentItem(
                        id = itemId,
                        content = content,
                        createdAtMillis = itemCreatedAt
                    )
                }.orEmpty()
                PromptExperimentPreset(
                    id = id,
                    name = name,
                    items = items,
                    createdAtMillis = createdAt
                )
            }
            val normalized = parsed.map { preset ->
                if (preset.id == PromptExperimentPolicy.DEFAULT_PRESET_ID && preset.items.isEmpty()) {
                    PromptExperimentPolicy.defaultPreset()
                } else {
                    preset
                }
            }
            normalized.ifEmpty { listOf(PromptExperimentPolicy.defaultPreset()) }
        }.getOrElse { listOf(PromptExperimentPolicy.defaultPreset()) }
    }

    private fun serializePresets(presets: List<PromptExperimentPreset>): String {
        val array = buildJsonArray {
            presets.forEach { preset ->
                add(
                    buildJsonObject {
                        put(KEY_ID, preset.id)
                        put(KEY_NAME, preset.name)
                        put(KEY_CREATED_AT, preset.createdAtMillis)
                        put(
                            KEY_ITEMS,
                            buildJsonArray {
                                preset.items.forEach { item ->
                                    add(
                                        buildJsonObject {
                                            put(KEY_ID, item.id)
                                            put(KEY_CONTENT, item.content)
                                            put(KEY_CREATED_AT, item.createdAtMillis)
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
            }
        }
        return array.toString()
    }

    companion object {
        const val DATASTORE_NAME = "prompt_experiments"
        val KEY_EXPERIMENT_PRESETS = stringPreferencesKey("experiment_presets")
        val KEY_ACTIVE_PRESET_ID = stringPreferencesKey("active_preset_id")

        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_CONTENT = "content"
        private const val KEY_ITEMS = "items"
        private const val KEY_CREATED_AT = "created_at"
    }
}
