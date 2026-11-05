// 역할: AI 모델 역할(마스킹, 생성, 분류)에 따라 활성화된 Gemini API 키 또는 Grok 토큰을 결정하는 유스케이스입니다.
package com.example.gemgemgen.core.credential.usecase

import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.credential.domain.AnalysisException
import com.example.gemgemgen.core.credential.domain.AnalysisModelRole
import com.example.gemgemgen.core.credential.domain.AnalysisProvider
import kotlinx.coroutines.withContext

fun interface GrokTokenProvider {
    suspend fun requireValidAccessToken(): String
}

data class ResolvedAnalysisCredential(
    val accessTokenOrApiKey: String,
    val modelId: String,
    val provider: AnalysisProvider
)

class ResolveAnalysisCredentialUseCase(
    private val apiKeyRepository: GeminiApiKeyRepository,
    private val grokAuth: GrokTokenProvider,
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
                accessTokenOrApiKey = token,
                modelId = modelId,
                provider = provider
            )
        }
}
