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
}
