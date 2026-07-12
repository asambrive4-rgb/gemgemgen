package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.withContext

class AnalysisCredentialResolver(
    private val apiKeyRepository: GeminiApiKeyRepository,
    private val grokAuth: ManageGrokAuthUseCase,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun resolveForRole(role: AnalysisModelRole): ResolvedAnalysisCredential =
        withContext(dispatchers.io) {
            val provider = AnalysisProvider.fromStorage(
                apiKeyRepository.getRoleProvider(role.storageValue)
            )
            val modelId = apiKeyRepository.getRoleModel(role.storageValue)
            val token = when (provider) {
                AnalysisProvider.GEMINI -> {
                    apiKeyRepository.activeKeyValue()
                        ?: throw AnalysisException("활성 Gemini API 키를 먼저 선택해주세요.")
                }
                AnalysisProvider.GROK -> grokAuth.requireValidAccessToken()
            }
            ResolvedAnalysisCredential(
                role = role,
                provider = provider,
                accessTokenOrApiKey = token,
                modelId = modelId
            )
        }
}

data class ResolvedAnalysisCredential(
    val role: AnalysisModelRole,
    val provider: AnalysisProvider,
    val accessTokenOrApiKey: String,
    val modelId: String
)
