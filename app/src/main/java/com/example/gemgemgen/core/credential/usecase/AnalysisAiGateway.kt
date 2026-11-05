// 역할: Gemini, Grok 등 다양한 AI 모델 프로바이더와의 텍스트 분석 및 생성 통신을 위한 게이트웨이 인터페이스입니다.
package com.example.gemgemgen.core.credential.usecase

import com.example.gemgemgen.core.credential.domain.AnalysisPromptPayload
import com.example.gemgemgen.core.credential.domain.AnalysisProvider

fun interface AnalysisAiGateway {
    suspend fun analyze(
        apiKey: String,
        modelId: String,
        payload: AnalysisPromptPayload,
        provider: AnalysisProvider
    ): String

    suspend fun analyze(
        apiKey: String,
        modelId: String,
        payload: AnalysisPromptPayload
    ): String = analyze(apiKey, modelId, payload, AnalysisProvider.GEMINI)
}
