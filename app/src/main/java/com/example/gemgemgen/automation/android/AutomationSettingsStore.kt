// 역할: Jetpack DataStore를 통해 자동화 설정(클립보드 복사 여부, 상/하단 인스트럭션, 변주 프롬프트)을 로컬에 영구 저장하고 Flow로 제공합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.automation.domain.PromptInstructionConfig
import com.example.gemgemgen.automation.domain.SystemInstructionPrompt
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.automationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AutomationSettingsStore.DATASTORE_NAME
)

/**
 * =========================================================================================
 * [주의: 사용자 명시적 요구 기능 - 임의 삭제 절대 금지]
 * - 기능: 자동화 시작 시 사용자의 원본 프롬프트 템플릿을 시스템 클립보드에 1회 백업할지 여부 제어
 * - 목적: 자동화 오류 발생 시 수동 복구 및 타 앱 수동 붙여넣기 편의성 제공
 * - 규칙: 성능 최적화, 클립보드 오염 방지, 미사용 코드 정리 등의 이유로 이 설정을
 *         삭제하거나 기본값을 false로 임의 변경하지 마세요.
 * =========================================================================================
 */
class AutomationSettingsStore(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.applicationContext.automationDataStore)

    val copyPromptOnStart: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_COPY_PROMPT_ON_START] ?: DEFAULT_COPY_PROMPT_ON_START
        }

    suspend fun setCopyPromptOnStart(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_COPY_PROMPT_ON_START] = enabled
        }
    }

    val promptInstructionConfig: Flow<PromptInstructionConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val top = preferences[KEY_TOP_INSTRUCTION] ?: SystemInstructionPrompt.text
            val bottom = preferences[KEY_BOTTOM_INSTRUCTION]
            PromptInstructionConfig(
                topInstruction = top,
                bottomInstruction = bottom
            )
        }

    suspend fun setPromptInstructionConfig(config: PromptInstructionConfig) {
        dataStore.edit { preferences ->
            preferences[KEY_TOP_INSTRUCTION] = config.topInstruction
            if (config.bottomInstruction != null) {
                preferences[KEY_BOTTOM_INSTRUCTION] = config.bottomInstruction
            } else {
                preferences.remove(KEY_BOTTOM_INSTRUCTION)
            }
        }
    }

    val variationPromptConfig: Flow<VariationPromptConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val prompt = preferences[KEY_VARIATION_PROMPT] ?: VariationPromptConfig.DEFAULT_VARIATION_PROMPT
            VariationPromptConfig(prompt = prompt)
        }

    suspend fun setVariationPromptConfig(config: VariationPromptConfig) {
        dataStore.edit { preferences ->
            preferences[KEY_VARIATION_PROMPT] = config.prompt
        }
    }

    companion object {
        const val DATASTORE_NAME = "automation_settings"

        val KEY_COPY_PROMPT_ON_START = booleanPreferencesKey("copy_prompt_on_start")
        val KEY_TOP_INSTRUCTION = stringPreferencesKey("top_instruction")
        val KEY_BOTTOM_INSTRUCTION = stringPreferencesKey("bottom_instruction")
        val KEY_VARIATION_PROMPT = stringPreferencesKey("variation_prompt")

        const val DEFAULT_COPY_PROMPT_ON_START = true
    }
}
