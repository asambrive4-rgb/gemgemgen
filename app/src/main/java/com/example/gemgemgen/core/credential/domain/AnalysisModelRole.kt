// 역할: AI 모델의 역할(마스킹, 후보 생성, 분류 등)과 기본 프로바이더/모델 매핑을 정의합니다.
package com.example.gemgemgen.core.credential.domain

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
                MASKING -> MODEL_GEMINI_3_5_FLASH_LITE
                GENERATION -> MODEL_GROK_4_5
            }
        }
    }
}

data class AnalysisRoleModelSetting(
    val role: AnalysisModelRole,
    val provider: AnalysisProvider,
    val modelId: String
)
