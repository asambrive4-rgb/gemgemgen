package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisPromptBuilder
import com.example.gemgemgen.analysis.domain.AnalysisReport
import com.example.gemgemgen.analysis.domain.AnalysisResponseParser
import com.example.gemgemgen.analysis.domain.DEFAULT_ANALYSIS_MODEL
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.withContext

class AnalysisException(message: String) : RuntimeException(message)

class AnalyzePromptForCategoryUseCase(
    private val aiGateway: AnalysisAiGateway,
    private val apiKeyRepository: GeminiApiKeyRepository,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun analyze(
        sourcePrompt: String,
        category: AnalysisCategory,
        modelId: String? = null
    ): AnalysisReport = withContext(dispatchers.io) {
        if (sourcePrompt.isBlank()) {
            throw AnalysisException("원본 프롬프트를 입력해주세요.")
        }
        val apiKey = apiKeyRepository.activeKeyValue()
            ?: throw AnalysisException("활성 Gemini API 키를 먼저 선택해주세요.")
        val resolvedModelId = modelId ?: apiKeyRepository.getSelectedModel()

        val payload = AnalysisPromptBuilder.buildAnalysisPrompt(
            sourcePrompt = sourcePrompt,
            category = category
        )
        val responseText = aiGateway.analyze(
            apiKey = apiKey,
            modelId = resolvedModelId,
            payload = payload
        )
        AnalysisResponseParser.parseReport(responseText, sourcePrompt)
    }
}
