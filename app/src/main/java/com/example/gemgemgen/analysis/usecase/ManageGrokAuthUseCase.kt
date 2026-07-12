package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class GrokAuthStatus(
    val isLoggedIn: Boolean,
    val accountPreview: String = ""
)

class ManageGrokAuthUseCase(
    private val gateway: GrokAuthGateway,
    private val repository: GrokAuthRepository,
    private val billingGateway: GrokBillingGateway? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun status(): GrokAuthStatus = withContext(dispatchers.io) {
        val session = repository.loadSession()
        if (session == null || session.accessToken.isBlank()) {
            GrokAuthStatus(isLoggedIn = false)
        } else {
            GrokAuthStatus(
                isLoggedIn = true,
                accountPreview = session.accountPreview.ifBlank { "연결됨" }
            )
        }
    }

    suspend fun startDeviceLogin(): GrokDeviceLoginChallenge = withContext(dispatchers.io) {
        gateway.startDeviceLogin()
    }

    /**
     * device code 승인까지 폴링한다. 완료 시 세션 저장.
     * @return 저장된 계정 preview
     */
    suspend fun awaitDeviceLogin(challenge: GrokDeviceLoginChallenge): GrokAuthStatus =
        withContext(dispatchers.io) {
            val deadline = clock() + challenge.expiresInSeconds * 1000L
            var intervalMs = (challenge.intervalSeconds.coerceAtLeast(1) * 1000L)
                .coerceAtLeast(MIN_POLL_INTERVAL_MS)
            while (clock() < deadline) {
                delay(intervalMs)
                try {
                    val session = gateway.pollDeviceLogin(challenge)
                    if (session != null) {
                        repository.saveSession(session)
                        return@withContext GrokAuthStatus(
                            isLoggedIn = true,
                            accountPreview = session.accountPreview.ifBlank { "연결됨" }
                        )
                    }
                } catch (error: AnalysisException) {
                    if (error.message?.contains("slow_down", ignoreCase = true) == true) {
                        intervalMs = (intervalMs + 5_000L).coerceAtMost(30_000L)
                        continue
                    }
                    throw error
                }
            }
            throw AnalysisException("로그인 시간이 만료되었습니다. 다시 시도해주세요.")
        }

    suspend fun logout(): GrokAuthStatus = withContext(dispatchers.io) {
        repository.clearSession()
        GrokAuthStatus(isLoggedIn = false)
    }

    /**
     * 남은 크레딧 비율(%). 미로그인·조회 실패 시 null.
     */
    suspend fun fetchQuota(): GrokQuotaInfo? = withContext(dispatchers.io) {
        val billing = billingGateway ?: return@withContext null
        val token = runCatching { requireValidAccessToken() }.getOrNull()
            ?: return@withContext null
        runCatching { billing.fetchQuota(token) }.getOrNull()
    }

    /**
     * 분석 호출용 access token. 만료 임박 시 refresh 후 저장.
     */
    suspend fun requireValidAccessToken(): String = withContext(dispatchers.io) {
        val session = repository.loadSession()
            ?: throw AnalysisException("Grok 로그인이 필요합니다.")
        if (session.accessToken.isBlank()) {
            repository.clearSession()
            throw AnalysisException("Grok 로그인이 필요합니다.")
        }
        val expiresAt = session.expiresAtMillis
        val needsRefresh = expiresAt != null &&
            clock() + REFRESH_SKEW_MS >= expiresAt
        if (!needsRefresh) {
            return@withContext session.accessToken
        }
        if (session.refreshToken.isNullOrBlank() || session.tokenEndpoint.isNullOrBlank()) {
            repository.clearSession()
            throw AnalysisException("Grok 세션이 만료되었습니다. 다시 로그인해주세요.")
        }
        try {
            val refreshed = gateway.refreshSession(session)
            repository.saveSession(refreshed)
            refreshed.accessToken
        } catch (error: AnalysisException) {
            if (error.message?.contains("403") == true ||
                error.message?.contains("권한") == true
            ) {
                throw error
            }
            repository.clearSession()
            throw AnalysisException("Grok 세션 갱신에 실패했습니다. 다시 로그인해주세요.")
        }
    }

    private companion object {
        const val REFRESH_SKEW_MS = 2 * 60 * 1000L
        const val MIN_POLL_INTERVAL_MS = 5_000L
    }
}
