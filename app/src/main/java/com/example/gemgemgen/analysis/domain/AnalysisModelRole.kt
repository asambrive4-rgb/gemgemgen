package com.example.gemgemgen.analysis.domain

/**
 * 분석 파이프라인 단계별 모델 역할.
 * - [MASKING]: 자동 마스킹 버튼 + TXT 생성 전 구간 분석/재분석
 * - [GENERATION]: TXT 후보 생성만
 */
enum class AnalysisModelRole(val storageValue: String) {
    MASKING("masking"),
    GENERATION("generation");

    companion object {
        fun fromStorage(value: String?): AnalysisModelRole {
            return entries.firstOrNull { it.storageValue == value } ?: MASKING
        }

        fun defaultProvider(role: AnalysisModelRole): AnalysisProvider {
            return when (role) {
                MASKING -> AnalysisProvider.GEMINI
                GENERATION -> AnalysisProvider.GROK
            }
        }

        fun defaultModel(role: AnalysisModelRole): String {
            return when (role) {
                MASKING -> MODEL_GEMINI_3_1_FLASH_LITE
                GENERATION -> MODEL_GROK_4_5
            }
        }
    }
}
