package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisPromptBuilder
import com.example.gemgemgen.analysis.domain.AnalysisReport
import com.example.gemgemgen.analysis.domain.AnalysisResponseParser
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.domain.DEFAULT_ANALYSIS_MODEL
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.withContext

data class AnalysisTxtGenerationResult(
    val candidates: List<String>,
    val warning: String = ""
)

class GenerateAnalysisTxtUseCase(
    private val aiGateway: AnalysisAiGateway,
    private val apiKeyRepository: GeminiApiKeyRepository,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun generate(
        sourcePrompt: String,
        category: AnalysisCategory,
        targetSegment: AnalysisTargetSegment,
        analysisReport: AnalysisReport,
        count: Int,
        selectedHints: List<String>,
        customHint: String? = null,
        modelId: String? = null
    ): AnalysisTxtGenerationResult = withContext(dispatchers.io) {
        if (sourcePrompt.isBlank()) {
            throw AnalysisException("원본 프롬프트를 입력해주세요.")
        }
        if (!targetSegment.isValid) {
            throw AnalysisException("변주 대상 구간을 먼저 지정해주세요.")
        }
        val apiKey = apiKeyRepository.activeKeyValue()
            ?: throw AnalysisException("활성 Gemini API 키를 먼저 선택해주세요.")
        val resolvedModelId = modelId ?: apiKeyRepository.getSelectedModel()
        val normalizedCount = AnalysisTxtCountPolicy.coerce(count)
        val payload = AnalysisPromptBuilder.buildTxtPrompt(
            sourcePrompt = sourcePrompt,
            category = category,
            targetSegment = targetSegment,
            analysisReport = analysisReport,
            count = normalizedCount,
            selectedHints = selectedHints,
            customHint = customHint
        )
        val responseText = aiGateway.generateTxt(
            apiKey = apiKey,
            modelId = resolvedModelId,
            payload = payload
        )
        val candidates = AnalysisResponseParser.parseTxtCandidates(responseText)
            .take(normalizedCount)
        val warning = if (candidates.size < normalizedCount) {
            "요청한 ${normalizedCount}개보다 적은 ${candidates.size}개만 생성되었습니다."
        } else {
            ""
        }
        AnalysisTxtGenerationResult(candidates = candidates, warning = warning)
    }
}
