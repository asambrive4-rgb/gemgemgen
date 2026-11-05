// 역할: 다중 계정(5개 이상) 제미나이 사용량 모니터링을 위한 개별 Google 계정 프로필 메타데이터(메인/서브 그룹, 수동 사용불가 내림, 당분간 사용 금지, 이메일 오염 방지 규칙 포함)와 독립 세션 식별자 모델을 정의합니다.
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
     * WebView DOM에서 자동 감지된 이메일([detectedEmail])이 현재 계정의 설정된 이메일과 충돌(불일치)하는지 확인합니다.
     * - 현재 계정에 이메일이 지정되어 있는데 감지된 이메일이 다른 계정이면 true를 반환합니다.
     */
    fun isConflictingDetectedEmail(detectedEmail: String): Boolean {
        val normCurrent = email.trim().lowercase()
        val normDetected = detectedEmail.trim().lowercase()
        if (normCurrent.isEmpty() || normDetected.isEmpty()) return false
        return normCurrent != normDetected
    }

    /**
     * 자동 감지된 이메일([detectedEmail])을 현재 프로필의 빈 이메일 필드에 안전하게 채울 수 있는지 판별합니다.
     * - 현재 프로필의 이메일이 비어 있어야 하며,
     * - 다른 계정이 이미 사용 중인 이메일이 아니어야 하며,
     * - 별칭에 영문/숫자 식별자(예: "jjjoa31", "asam50")가 포함된 경우 감지된 이메일 로컬 파트와도 모순되지 않아야 합니다.
     */
    fun canAcceptDetectedEmail(
        detectedEmail: String,
        allProfiles: List<GeminiAccountProfile>
    ): Boolean {
        val normDetected = detectedEmail.trim().lowercase()
        if (normDetected.isEmpty() || email.isNotBlank()) return false

        val usedByOtherAccount = allProfiles.any { other ->
            other.id != id && other.email.trim().equals(normDetected, ignoreCase = true)
        }
        if (usedByOtherAccount) return false

        return !isAliasMismatchWithEmail(alias = alias, email = normDetected)
    }

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
        private val UNSAFE_PROFILE_CHAR_REGEX = Regex("""[^a-zA-Z0-9_]""")
        private val ASCII_IDENTIFIER_REGEX = Regex("""^[a-z0-9._-]+$""")
        private val TRAILING_DIGITS_REGEX = Regex("""(\d+)$""")

        /**
         * 별칭이 영문+숫자 계정 약어(예: "jjjoa31", "asam50") 형태일 때,
         * 이메일 로컬 파트(예: "asambrive50", "jjjoajjjoajjjoa31")와 명백히 다른 계정인지 검사합니다.
         */
        fun isAliasMismatchWithEmail(alias: String, email: String): Boolean {
            val normAlias = alias.trim().lowercase()
            val localPart = email.trim().lowercase().substringBefore("@")
            if (normAlias.length < 3 || localPart.isEmpty()) return false
            if (!ASCII_IDENTIFIER_REGEX.matches(normAlias)) return false

            val aliasPrefix = normAlias.takeWhile { it.isLetter() }
            val localPrefix = localPart.takeWhile { it.isLetter() }
            if (aliasPrefix.length >= 3 && localPrefix.length >= 3) {
                if (!localPrefix.startsWith(aliasPrefix) && !aliasPrefix.startsWith(localPrefix)) {
                    return true
                }
            }

            val aliasDigits = TRAILING_DIGITS_REGEX.find(normAlias)?.value
            val localDigits = TRAILING_DIGITS_REGEX.find(localPart)?.value
            if (!aliasDigits.isNullOrEmpty() && !localDigits.isNullOrEmpty() && aliasDigits != localDigits) {
                return true
            }
            return false
        }

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
