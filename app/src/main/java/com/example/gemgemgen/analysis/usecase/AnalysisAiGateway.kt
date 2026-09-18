// 역할: AI 분석 통신과 텍스트 생성을 위한 외부 서비스 연동 인터페이스를 정의합니다.
package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisPromptPayload
import com.example.gemgemgen.analysis.domain.AnalysisTxtPromptPayload

interface AnalysisAiGateway {
    suspend fun analyze(
        apiKey: String,
        modelId: String,
        payload: AnalysisPromptPayload
    ): String

    suspend fun generateTxt(
        apiKey: String,
        modelId: String,
        payload: AnalysisTxtPromptPayload
    ): String
}
