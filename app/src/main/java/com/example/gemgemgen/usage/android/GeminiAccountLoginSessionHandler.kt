// 역할: 인앱 독립 세션 로그인 WebView의 설정·쿠키 복원/정제·저장·정리·취소 폐기를 추상화하는 세션 핸들러 인터페이스입니다.
package com.example.gemgemgen.usage.android

import android.webkit.WebView
import com.example.gemgemgen.usage.domain.GeminiAccountProfile

/**
 * 인앱 독립 세션 WebView의 로그인 준비, 쿠키 정제, 세션 저장·정리·폐기를 추상화하는 세션 핸들러 인터페이스.
 *
 * - UI 계층이 `CookieManager` 등 안드로이드 쿠키 API를 직접 만지지 않도록 책임을 추상화합니다.
 * - 실제 구현은 [MultiProfileGeminiUsageFetcher]입니다
 *   ([MultiProfileGeminiUsageFetcher.loginSessionHandler]로 획득).
 */
interface GeminiAccountLoginSessionHandler {
    /** 로그인 WebView 설정(JS, UA)과 해당 계정의 독립 쿠키 금고 복원/초기화 (loadUrl 이전에 호출) */
    fun prepareLoginWebView(
        webView: WebView,
        profile: GeminiAccountProfile,
        clearExistingCookies: Boolean
    )

    /** CookieMismatch 감지 시 쿠키 정화 및 로그인 화면 복구 */
    fun recoverFromCookieMismatch(
        profile: GeminiAccountProfile,
        webView: WebView
    )

    /** 로그인 완료 또는 페이지 이동 시 현재 세션 쿠키를 해당 계정 프로필에 반영 */
    fun commitLoginSession(
        profile: GeminiAccountProfile
    )

    /** 로그인 WebView를 파괴합니다. 이후 공유 WebView 새로고침과 쿠키가 경합하지 않도록 먼저 호출해야 합니다. */
    fun releaseLoginWebView(
        webView: WebView,
        profile: GeminiAccountProfile,
        commitCookies: Boolean
    )

    /** 저장하지 않고 취소한 신규 계정의 쿠키를 금고와 CookieManager에서 폐기 */
    fun discardLoginSession(
        profile: GeminiAccountProfile
    )
}
