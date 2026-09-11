// 역할: AI 서비스 호출에 필요한 API 키나 인증 토큰이 유효하게 등록되었는지 확인합니다.
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
