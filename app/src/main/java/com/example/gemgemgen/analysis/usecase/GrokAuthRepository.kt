// 역할: Grok 인증 세션 토큰을 영구 저장소에 보관하고 불러오는 저장소 인터페이스입니다.
package com.example.gemgemgen.analysis.usecase

interface GrokAuthRepository {
    fun loadSession(): GrokAuthSession?
    fun saveSession(session: GrokAuthSession)
    fun clearSession()
}
