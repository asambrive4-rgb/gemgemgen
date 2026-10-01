// 역할: 사용자가 입력한 Gemini API 키의 저장, 조회 및 삭제를 처리합니다.
package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.withContext

data class AnalysisRoleModelSetting(
    val role: AnalysisModelRole,
    val provider: AnalysisProvider,
    val modelId: String
)

class ManageGeminiApiKeysUseCase(
    private val repository: GeminiApiKeyRepository,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun listKeys(): List<GeminiApiKeySummary> = withContext(dispatchers.io) {
        repository.listKeys()
    }

    suspend fun addKey(label: String, rawKey: String): List<GeminiApiKeySummary> = mutateKeys {
        val normalizedLabel = label.trim().ifBlank { "Gemini API 키" }
        val normalizedKey = rawKey.trim()
        if (normalizedKey.isBlank()) {
            throw AnalysisException("API 키를 입력해주세요.")
        }
        repository.addKey(
            label = normalizedLabel,
            rawKey = normalizedKey
        )
    }

    suspend fun deleteKey(id: String): List<GeminiApiKeySummary> = mutateKeys {
        repository.deleteKey(id)
    }

    suspend fun activateKey(id: String): List<GeminiApiKeySummary> = mutateKeys {
        repository.activateKey(id)
    }

    suspend fun updateKeyLabel(id: String, newLabel: String): List<GeminiApiKeySummary> = mutateKeys {
        val normalizedLabel = newLabel.trim().ifBlank { "Gemini API 키" }
        repository.updateKeyLabel(id, normalizedLabel)
    }

    private suspend fun mutateKeys(action: () -> Unit): List<GeminiApiKeySummary> =
        withContext(dispatchers.io) {
            action()
            repository.listKeys()
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
}
