// 역할: 다중 계정(5개 이상) 제미나이 사용량 모니터링을 위한 개별 Google 계정 프로필 메타데이터(메인/서브 그룹, 수동 사용불가 내림, 당분간 사용 금지 포함)와 독립 세션 식별자 모델을 정의합니다.
package com.example.gemgemgen.usage.domain

import java.util.UUID

/**
 * 다중 계정의 표시 탭 및 용도 구분 (메인: 주 사용 계정, 서브: 보조 계정).
 */
enum class GeminiAccountGroup(val label: String) {
    MAIN("메인"),
    SUB("서브")
}

/**
 * 제미나이 사용량 모니터링에 등록된 개별 Google 계정 프로필 도메인 모델.
 *
 * @property id 계정 고유 식별자 (예: "acc_1", UUID 기반 식별자)
 * @property alias 사용자가 지정한 계정 별칭/라벨 (예: "본계정", "작업용 1")
 * @property email 구글 계정 이메일 (예: "user@gmail.com", 미확인 시 빈 문자열)
 * @property sortOrder 수직 목록 내 표시 순서 (0부터 시작)
 * @property webViewProfileName WebView 독립 세션/쿠키 저장소 격리를 위한 프로필 이름
 * @property createdAt 계정 등록 시각 (epoch millis)
 * @property group 계정 용도 그룹 (메인: 주 사용 계정, 서브: 보조 계정)
 * @property manualExhaustedUntilMillis 수동 '사용불가' 자동 복구 시각 (epoch millis, 0이면 수동 제외 아님)
 */
data class GeminiAccountProfile(
    val id: String,
    val alias: String,
    val email: String = "",
    val sortOrder: Int = 0,
    val webViewProfileName: String = buildWebViewProfileName(id),
    val createdAt: Long = 0L,
    val group: GeminiAccountGroup = GeminiAccountGroup.MAIN,
    val manualExhaustedUntilMillis: Long = 0L,
    val isSuspended: Boolean = false
) {
    /** 수동 사용불가 상태 여부. 자동 복구 시각이 지나면 사용자의 조작 없이 false가 됩니다. */
    fun isManuallyExhausted(nowMillis: Long): Boolean = nowMillis < manualExhaustedUntilMillis

    /**
     * 요약 버튼 및 카드 헤더에 표시할 대표 라벨.
     * 별칭이 비어 있으면 이메일 로컬 파트 또는 기본 라벨을 반환합니다.
     */
    val displayAlias: String
        get() {
            val trimmedAlias = alias.trim()
            if (trimmedAlias.isNotEmpty()) return trimmedAlias
            val emailPrefix = email.trim().substringBefore("@").trim()
            if (emailPrefix.isNotEmpty()) return emailPrefix
            return "계정 ${sortOrder + 1}"
        }

    companion object {
        /** 다중 계정 모니터링 최소 권장 지원 계정 수 (5개 이상 무제한 등록 가능) */
        const val MIN_MULTI_ACCOUNT_CAPACITY = 5

        private val UNSAFE_PROFILE_CHAR_REGEX = Regex("""[^a-zA-Z0-9_]""")

        /**
         * 계정 ID를 기반으로 Android WebView 독립 프로필/세션 저장소에 사용할 안전한 프로필 이름을 생성합니다.
         */
        fun buildWebViewProfileName(accountId: String): String {
            val sanitized = accountId.trim()
                .replace(UNSAFE_PROFILE_CHAR_REGEX, "_")
                .trim('_')
                .ifEmpty { "default" }
            return "gemini_profile_$sanitized"
        }

        /**
         * 신규 계정 추가 시 사용할 고유 계정 ID를 생성합니다.
         */
        fun generateAccountId(): String {
            val shortToken = UUID.randomUUID().toString().replace("-", "").take(10)
            return "acc_$shortToken"
        }
    }
}
