// 역할: AI 분석 통신과 모델 조회를 위한 외부 서비스 연동 인터페이스를 정의합니다.
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

data class GeminiApiKeyRecord(
    val id: String,
    val label: String,
    val encryptedValue: String,
    val preview: String,
    val createdAtMillis: Long,
    val isActive: Boolean
)

interface GeminiApiKeyRepository {
    fun listKeys(): List<GeminiApiKeyRecord>
    fun addKey(label: String, rawKey: String, createdAtMillis: Long): GeminiApiKeyRecord
    fun deleteKey(id: String)
    fun activateKey(id: String)
    fun activeKeyValue(): String?
    fun updateKeyLabel(id: String, newLabel: String)

    /** 단계(마스킹/생성)별 프로바이더. role = masking | generation */
    fun getRoleProvider(role: String): String
    fun setRoleProvider(role: String, providerId: String)
    /** 단계별 모델 id */
    fun getRoleModel(role: String): String
    fun setRoleModel(role: String, modelId: String)
}
