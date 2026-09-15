// 역할: SharedPreferences를 통해 변주 생성용 프롬프트 설정을 로컬에 영구 저장하고 불러옵니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.automation.usecase.VariationPromptRepository

class SharedPreferencesVariationPromptRepository(
    context: Context
) : VariationPromptRepository {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): VariationPromptConfig {
        val savedPrompt = preferences.getString(KEY_VARIATION_PROMPT, null)
        val prompt = if (savedPrompt == null || savedPrompt == LEGACY_DUMMY_PROMPT) {
            VariationPromptConfig.DEFAULT_VARIATION_PROMPT
        } else {
            savedPrompt
        }
        return VariationPromptConfig(prompt = prompt)
    }

    override fun save(config: VariationPromptConfig) {
        preferences.edit()
            .putString(KEY_VARIATION_PROMPT, config.prompt)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "variation_prompt_preferences"
        private const val KEY_VARIATION_PROMPT = "variation_prompt"
        private const val LEGACY_DUMMY_PROMPT =
            "다음 프롬프트의 스타일과 핵심 의도를 유지하며, 새롭고 창의적인 변주(Variation)를 작성해주세요."
    }
}
