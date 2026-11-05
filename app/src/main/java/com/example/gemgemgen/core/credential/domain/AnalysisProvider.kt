// 역할: 지원하는 AI 서비스 제공자(Gemini, Grok 등)와 기본 모델 판별 로직을 정의합니다.
package com.example.gemgemgen.core.credential.domain

enum class AnalysisProvider(val storageValue: String) {
    GEMINI("gemini"),
    GROK("grok");

    companion object {
        fun fromStorage(value: String?): AnalysisProvider {
            return entries.firstOrNull { it.storageValue == value } ?: GEMINI
        }

        fun defaultModel(provider: AnalysisProvider): String {
            return when (provider) {
                GEMINI -> DEFAULT_ANALYSIS_MODEL
                GROK -> MODEL_GROK_4_5
            }
        }

        fun isModelForProvider(modelId: String, provider: AnalysisProvider): Boolean {
            return when (provider) {
                GEMINI -> modelId.startsWith("gemini-")
                GROK -> modelId.startsWith("grok-")
            }
        }
    }
}
