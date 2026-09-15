// 역할: SharedPreferences를 통해 프롬프트 상단/하단 인스트럭션 설정을 로컬에 영구 저장하고 불러옵니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.domain.PromptInstructionConfig
import com.example.gemgemgen.automation.domain.SystemInstructionPrompt
import com.example.gemgemgen.automation.usecase.PromptInstructionRepository

class SharedPreferencesPromptInstructionRepository(
    context: Context
) : PromptInstructionRepository {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): PromptInstructionConfig {
        val top = preferences.getString(KEY_TOP_INSTRUCTION, null) ?: SystemInstructionPrompt.text
        val bottom = preferences.getString(KEY_BOTTOM_INSTRUCTION, null)
        return PromptInstructionConfig(
            topInstruction = top,
            bottomInstruction = bottom
        )
    }

    override fun save(config: PromptInstructionConfig) {
        preferences.edit()
            .putString(KEY_TOP_INSTRUCTION, config.topInstruction)
            .apply {
                if (config.bottomInstruction != null) {
                    putString(KEY_BOTTOM_INSTRUCTION, config.bottomInstruction)
                } else {
                    remove(KEY_BOTTOM_INSTRUCTION)
                }
            }
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "prompt_instructions"
        private const val KEY_TOP_INSTRUCTION = "top_instruction"
        private const val KEY_BOTTOM_INSTRUCTION = "bottom_instruction"
    }
}
