package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.withContext

data class GeminiApiKeySummary(
    val id: String,
    val label: String,
    val preview: String,
    val isActive: Boolean
)

data class AnalysisRoleModelSetting(
    val role: AnalysisModelRole,
    val provider: AnalysisProvider,
    val modelId: String
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

    suspend fun getRoleSetting(role: AnalysisModelRole): AnalysisRoleModelSetting =
        withContext(dispatchers.io) {
            val provider = AnalysisProvider.fromStorage(
                repository.getRoleProvider(role.storageValue)
            )
            val modelId = repository.getRoleModel(role.storageValue)
            AnalysisRoleModelSetting(role = role, provider = provider, modelId = modelId)
        }

    suspend fun setRoleProvider(
        role: AnalysisModelRole,
        provider: AnalysisProvider
    ): AnalysisRoleModelSetting = withContext(dispatchers.io) {
        repository.setRoleProvider(role.storageValue, provider.storageValue)
        getRoleSetting(role)
    }

    suspend fun setRoleModel(
        role: AnalysisModelRole,
        modelId: String
    ): AnalysisRoleModelSetting = withContext(dispatchers.io) {
        repository.setRoleModel(role.storageValue, modelId)
        getRoleSetting(role)
    }

    /**
     * 단계에 실제로 쓴 조합을 최근 사용으로 저장.
     */
    suspend fun rememberLastUsed(
        role: AnalysisModelRole,
        provider: AnalysisProvider,
        modelId: String
    ) = withContext(dispatchers.io) {
        repository.setRoleProvider(role.storageValue, provider.storageValue)
        repository.setRoleModel(role.storageValue, modelId)
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
