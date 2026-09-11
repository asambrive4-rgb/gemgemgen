// 역할: 선택된 AI 서비스(Gemini/Grok)에 따라 알맞은 분석 게이트웨이로 요청을 전달합니다.
package com.example.gemgemgen.analysis.android

import com.example.gemgemgen.analysis.domain.AnalysisPromptPayload
import com.example.gemgemgen.analysis.domain.AnalysisTxtPromptPayload
import com.example.gemgemgen.analysis.usecase.AnalysisAiGateway

/**
 * modelId 로 Gemini/Grok 경로를 고른다.
 * (단계별 모델이 달라질 수 있어 전역 프로바이더 설정에 의존하지 않는다.)
 */
class RoutingAnalysisAiGateway(
    private val gemini: AnalysisAiGateway,
    private val grok: AnalysisAiGateway
) : AnalysisAiGateway {
    override suspend fun analyze(
        apiKey: String,
        modelId: String,
        payload: AnalysisPromptPayload
    ): String {
        return delegate(modelId).analyze(apiKey, modelId, payload)
    }

    override suspend fun generateTxt(
        apiKey: String,
        modelId: String,
        payload: AnalysisTxtPromptPayload
    ): String {
        return delegate(modelId).generateTxt(apiKey, modelId, payload)
    }

    private fun delegate(modelId: String): AnalysisAiGateway {
        return if (modelId.startsWith("grok-")) grok else gemini
    }
}
