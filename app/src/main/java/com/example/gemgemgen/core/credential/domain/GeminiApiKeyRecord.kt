// 역할: 저장된 Gemini API 키의 고유 식별자, 레이블, 마스킹된 미리보기 및 활성화 상태 모델을 정의합니다.
package com.example.gemgemgen.core.credential.domain

data class GeminiApiKeyRecord(
    val id: String,
    val label: String,
    val preview: String,
    val isActive: Boolean
)

typealias GeminiApiKeySummary = GeminiApiKeyRecord
