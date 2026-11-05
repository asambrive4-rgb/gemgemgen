// 역할: Jetpack DataStore와 kotlinx.serialization을 활용하여 프롬프트 실험(연금술) 프리셋 세트 및 항목 목록을 로컬에 영구 저장합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.automation.domain.PromptExperimentPolicy
import com.example.gemgemgen.automation.domain.PromptExperimentPreset
import com.example.gemgemgen.automation.usecase.PromptExperimentRepository
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

private val Context.promptExperimentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PromptExperimentStore.DATASTORE_NAME
)

private val experimentJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

class PromptExperimentStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    init {
        scope.launch {
            presets.collect {
                // updates cachedPresets
            }
        }
        scope.launch {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .collect { preferences ->
                    cachedActivePresetId = preferences[KEY_ACTIVE_PRESET_ID]
                }
        }
    }

    override fun loadPresets(): List<PromptExperimentPreset> {
        return cachedPresets ?: listOf(PromptExperimentPolicy.defaultPreset())
    }

    override fun savePresets(presets: List<PromptExperimentPreset>) {
        val normalized = presets.ifEmpty { listOf(PromptExperimentPolicy.defaultPreset()) }
        cachedPresets = normalized
        scope.launch {
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
        return cachedActivePresetId
    }

    override fun saveActivePresetId(presetId: String?) {
        cachedActivePresetId = presetId
        scope.launch {
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
            val parsed = experimentJson.decodeFromString<List<PromptExperimentPreset>>(jsonString)
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
        return experimentJson.encodeToString(presets)
    }

    companion object {
        const val DATASTORE_NAME = "prompt_experiments"
        val KEY_EXPERIMENT_PRESETS = stringPreferencesKey("experiment_presets")
        val KEY_ACTIVE_PRESET_ID = stringPreferencesKey("active_preset_id")
    }
}
