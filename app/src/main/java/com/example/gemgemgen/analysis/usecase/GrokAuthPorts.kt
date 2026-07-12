package com.example.gemgemgen.analysis.usecase

data class GrokDeviceLoginChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
    val tokenEndpoint: String
)

data class GrokAuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long?,
    val tokenEndpoint: String?,
    val accountPreview: String
)

interface GrokAuthGateway {
    suspend fun startDeviceLogin(): GrokDeviceLoginChallenge

    /**
     * 한 번의 폴링. null이면 아직 대기 중, 값이 있으면 로그인 완료.
     * 거절·만료 등은 예외.
     */
    suspend fun pollDeviceLogin(challenge: GrokDeviceLoginChallenge): GrokAuthSession?

    suspend fun refreshSession(session: GrokAuthSession): GrokAuthSession
}

data class GrokQuotaInfo(
    val remainingPercent: Int,
    val usedVal: Long,
    val limitVal: Long
)

interface GrokBillingGateway {
    suspend fun fetchQuota(accessToken: String): GrokQuotaInfo
}

interface GrokAuthRepository {
    fun loadSession(): GrokAuthSession?
    fun saveSession(session: GrokAuthSession)
    fun clearSession()
}
