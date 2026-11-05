// 역할: 자동화 설정(클립보드 백업, 사이드바 모드, 메모리 자동 정리 및 인스트럭션/변주 프롬프트)을 조회하고 갱신하는 저장소 인터페이스입니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.PromptInstructionConfig
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import kotlinx.coroutines.flow.Flow

interface AutomationSettingsRepository {
    val copyPromptOnStart: Flow<Boolean>
    suspend fun setCopyPromptOnStart(enabled: Boolean)

    val geminiSidebarOpenMode: Flow<Boolean>
    suspend fun setGeminiSidebarOpenMode(enabled: Boolean)

    val autoMemoryCleanupEnabled: Flow<Boolean>
    suspend fun setAutoMemoryCleanupEnabled(enabled: Boolean)

    val autoMemoryCleanupThreshold: Flow<Int>
    suspend fun setAutoMemoryCleanupThreshold(threshold: Int)

    val geminiGenerationCountSinceCleanup: Flow<Int>
    suspend fun setGeminiGenerationCountSinceCleanup(count: Int)

    val promptInstructionConfig: Flow<PromptInstructionConfig>
    suspend fun setPromptInstructionConfig(config: PromptInstructionConfig)

    val variationPromptConfig: Flow<VariationPromptConfig>
    suspend fun setVariationPromptConfig(config: VariationPromptConfig)
}
