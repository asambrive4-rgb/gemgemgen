// 역할: 선택된 AI 서비스(Gemini/Grok)에 따라 알맞은 분석 게이트웨이로 요청을 전달합니다.
package com.example.gemgemgen.analysis.android

import com.example.gemgemgen.analysis.domain.AnalysisPromptPayload
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.usecase.AnalysisAiGateway

/**
 * 프로바이더(Gemini/Grok) 설정에 따라 직접 알맞은 게이트웨이로 라우팅한다.
 */
class RoutingAnalysisAiGateway(
    private val gemini: AnalysisAiGateway,
    private val grok: AnalysisAiGateway
) : AnalysisAiGateway {
    override suspend fun analyze(
        apiKey: String,
        modelId: String,
        payload: AnalysisPromptPayload,
        provider: AnalysisProvider
    ): String {
        return delegate(provider).analyze(apiKey, modelId, payload, provider)
    }

    private fun delegate(provider: AnalysisProvider): AnalysisAiGateway {
        return when (provider) {
            AnalysisProvider.GROK -> grok
            AnalysisProvider.GEMINI -> gemini
        }
    }
}
