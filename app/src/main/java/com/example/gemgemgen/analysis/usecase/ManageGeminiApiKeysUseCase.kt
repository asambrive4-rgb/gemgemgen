package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.withContext

data class GeminiApiKeySummary(
    val id: String,
    val label: String,
    val preview: String,
    val isActive: Boolean
)

class ManageGeminiApiKeysUseCase(
    private val repository: GeminiApiKeyRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun listKeys(): List<GeminiApiKeySummary> = withContext(dispatchers.io) {
        repository.listKeys().map { it.toSummary() }
    }

    suspend fun addKey(label: String, rawKey: String): List<GeminiApiKeySummary> =
        withContext(dispatchers.io) {
            val normalizedLabel = label.trim().ifBlank { "Gemini API 키" }
            val normalizedKey = rawKey.trim()
            if (normalizedKey.isBlank()) {
                throw AnalysisException("API 키를 입력해주세요.")
            }
            repository.addKey(
                label = normalizedLabel,
                rawKey = normalizedKey,
                createdAtMillis = clock()
            )
            repository.listKeys().map { it.toSummary() }
        }

    suspend fun deleteKey(id: String): List<GeminiApiKeySummary> = withContext(dispatchers.io) {
        repository.deleteKey(id)
        repository.listKeys().map { it.toSummary() }
    }

    suspend fun activateKey(id: String): List<GeminiApiKeySummary> =
        withContext(dispatchers.io) {
            repository.activateKey(id)
            repository.listKeys().map { it.toSummary() }
        }

    suspend fun updateKeyLabel(id: String, newLabel: String): List<GeminiApiKeySummary> =
        withContext(dispatchers.io) {
            val normalizedLabel = newLabel.trim().ifBlank { "Gemini API 키" }
            repository.updateKeyLabel(id, normalizedLabel)
            repository.listKeys().map { it.toSummary() }
        }

    suspend fun getSelectedModel(): String = withContext(dispatchers.io) {
        repository.getSelectedModel()
    }

    suspend fun setSelectedModel(modelId: String) = withContext(dispatchers.io) {
        repository.setSelectedModel(modelId)
    }

    private fun GeminiApiKeyRecord.toSummary(): GeminiApiKeySummary {
        return GeminiApiKeySummary(
            id = id,
            label = label,
            preview = preview,
            isActive = isActive
        )
    }
}
