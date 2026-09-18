// 역할: Grok OAuth 기기 인증 및 세션 갱신을 처리하는 게이트웨이 인터페이스입니다.
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
