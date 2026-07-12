package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.usecase.AnalysisCredentialResolver
import com.example.gemgemgen.analysis.usecase.AnalysisException
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRecord
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRepository
import com.example.gemgemgen.analysis.usecase.GrokAuthGateway
import com.example.gemgemgen.analysis.usecase.GrokAuthRepository
import com.example.gemgemgen.analysis.usecase.GrokAuthSession
import com.example.gemgemgen.analysis.usecase.GrokDeviceLoginChallenge
import com.example.gemgemgen.analysis.usecase.ManageGrokAuthUseCase
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GrokAuthUseCaseTest {
    @Test
    fun status_reportsLoggedOutWhenEmpty() = runBlocking {
        val useCase = ManageGrokAuthUseCase(
            gateway = RecordingGrokGateway(),
            repository = MemoryGrokAuthRepository(),
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )
        val status = useCase.status()
        assertFalse(status.isLoggedIn)
    }

    @Test
    fun requireValidAccessToken_returnsStoredToken() = runBlocking {
        val repository = MemoryGrokAuthRepository(
            GrokAuthSession(
                accessToken = "access-token",
                refreshToken = "refresh",
                expiresAtMillis = System.currentTimeMillis() + 10 * 60_000L,
                tokenEndpoint = "https://auth.x.ai/oauth2/token",
                accountPreview = "user@x.ai"
            )
        )
        val useCase = ManageGrokAuthUseCase(
            gateway = RecordingGrokGateway(),
            repository = repository,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )
        assertEquals("access-token", useCase.requireValidAccessToken())
    }

    @Test
    fun requireValidAccessToken_refreshesWhenExpired() = runBlocking {
        val repository = MemoryGrokAuthRepository(
            GrokAuthSession(
                accessToken = "old",
                refreshToken = "refresh",
                expiresAtMillis = System.currentTimeMillis() - 1_000L,
                tokenEndpoint = "https://auth.x.ai/oauth2/token",
                accountPreview = "user@x.ai"
            )
        )
        val gateway = RecordingGrokGateway(
            refreshResult = GrokAuthSession(
                accessToken = "new-token",
                refreshToken = "refresh-2",
                expiresAtMillis = System.currentTimeMillis() + 60_000L,
                tokenEndpoint = "https://auth.x.ai/oauth2/token",
                accountPreview = "user@x.ai"
            )
        )
        val useCase = ManageGrokAuthUseCase(
            gateway = gateway,
            repository = repository,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )
        assertEquals("new-token", useCase.requireValidAccessToken())
        assertEquals(1, gateway.refreshCount)
        assertEquals("new-token", repository.loadSession()?.accessToken)
    }

    @Test
    fun credentialResolver_usesGrokTokenWhenGenerationRoleIsGrok() = runBlocking {
        val keyRepo = MemoryKeyRepository(generationProvider = "grok")
        val grokAuth = ManageGrokAuthUseCase(
            gateway = RecordingGrokGateway(),
            repository = MemoryGrokAuthRepository(
                GrokAuthSession(
                    accessToken = "grok-token",
                    refreshToken = "r",
                    expiresAtMillis = System.currentTimeMillis() + 10 * 60_000L,
                    tokenEndpoint = "https://auth.x.ai/oauth2/token",
                    accountPreview = "me"
                )
            ),
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )
        val resolver = AnalysisCredentialResolver(
            apiKeyRepository = keyRepo,
            grokAuth = grokAuth,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )
        val credential = resolver.resolveForRole(AnalysisModelRole.GENERATION)
        assertEquals(AnalysisProvider.GROK, credential.provider)
        assertEquals("grok-token", credential.accessTokenOrApiKey)
        assertEquals("grok-4.5", credential.modelId)
    }

    @Test(expected = AnalysisException::class)
    fun credentialResolver_throwsWhenGrokNotLoggedIn() = runBlocking {
        val keyRepo = MemoryKeyRepository(generationProvider = "grok")
        val grokAuth = ManageGrokAuthUseCase(
            gateway = RecordingGrokGateway(),
            repository = MemoryGrokAuthRepository(),
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )
        AnalysisCredentialResolver(
            apiKeyRepository = keyRepo,
            grokAuth = grokAuth,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        ).resolveForRole(AnalysisModelRole.GENERATION)
        Unit
    }

    private class RecordingGrokGateway(
        private val refreshResult: GrokAuthSession? = null
    ) : GrokAuthGateway {
        var refreshCount = 0

        override suspend fun startDeviceLogin(): GrokDeviceLoginChallenge {
            error("not used")
        }

        override suspend fun pollDeviceLogin(challenge: GrokDeviceLoginChallenge): GrokAuthSession? {
            return null
        }

        override suspend fun refreshSession(session: GrokAuthSession): GrokAuthSession {
            refreshCount++
            return refreshResult ?: session.copy(accessToken = "refreshed")
        }
    }

    private class MemoryGrokAuthRepository(
        private var session: GrokAuthSession? = null
    ) : GrokAuthRepository {
        override fun loadSession(): GrokAuthSession? = session
        override fun saveSession(session: GrokAuthSession) {
            this.session = session
        }
        override fun clearSession() {
            session = null
        }
    }

    private class MemoryKeyRepository(
        private val generationProvider: String = "gemini",
        private val activeKey: String? = "gemini-key"
    ) : GeminiApiKeyRepository {
        override fun listKeys(): List<GeminiApiKeyRecord> = emptyList()
        override fun addKey(label: String, rawKey: String, createdAtMillis: Long) =
            error("unused")
        override fun deleteKey(id: String) = Unit
        override fun activateKey(id: String) = Unit
        override fun activeKeyValue(): String? = activeKey
        override fun updateKeyLabel(id: String, newLabel: String) = Unit
        override fun getRoleProvider(role: String): String =
            if (role == "generation") generationProvider else "gemini"
        override fun setRoleProvider(role: String, providerId: String) = Unit
        override fun getRoleModel(role: String): String =
            if (getRoleProvider(role) == "grok") "grok-4.5" else "gemini-3.5-flash"
        override fun setRoleModel(role: String, modelId: String) = Unit
    }
}
