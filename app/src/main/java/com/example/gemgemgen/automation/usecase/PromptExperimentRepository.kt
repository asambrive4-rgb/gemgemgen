// 역할: 프롬프트 실험(연금술) 프리셋 세트 목록을 영구 저장소에 보관하고 불러오는 저장소 인터페이스입니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.PromptExperimentPreset

interface PromptExperimentRepository {
    fun loadPresets(): List<PromptExperimentPreset>
    fun savePresets(presets: List<PromptExperimentPreset>)
    fun loadActivePresetId(): String? = null
    fun saveActivePresetId(presetId: String?) {}
}
