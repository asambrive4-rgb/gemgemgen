// 역할: Gemini 계정 전환 과정의 5단계 진행 상태 및 메시지 정책을 정의합니다.
package com.example.gemgemgen.automation.domain

object GeminiAccountSwitchProgressPolicy {
    const val PHASE_1 = "1/5"
    const val PHASE_2 = "2/5"
    const val PHASE_3 = "3/5"
    const val PHASE_4 = "4/5"
    const val PHASE_5 = "5/5"

    const val INITIAL_PHASE = PHASE_1
    const val INITIAL_PROGRESS_MESSAGE = "Gemini 앱 실행 확인 중..."

    const val ERROR_ACCESSIBILITY_REQUIRED = "접근성 서비스를 먼저 활성화해주세요."
    const val ERROR_IDENTIFIER_BLANK = "계정 식별자(구글 이메일)가 비어 있습니다."
    const val ERROR_SERVICE_UNAVAILABLE = "접근성 서비스를 사용할 수 없습니다."

    fun step1CheckForeground(): String = INITIAL_PROGRESS_MESSAGE
    fun step1LaunchGemini(): String = "Gemini 앱을 전면으로 실행하는 중..."
    fun step2OpenProfile(): String = "Google 계정 및 프로필 창 여는 중..."
    fun step3ExpandAccounts(): String = "계정 목록 펼치는 중..."
    fun step4FindAccount(target: String): String = "대상 계정 [$target] 찾는 중..."
    fun step4ScrollAccounts(attempt: Int, maxAttempts: Int): String = "계정 목록 스크롤 중 ($attempt/$maxAttempts)..."
    fun step5SwitchingAccount(target: String): String = "계정 전환 중: [$target]..."

    fun startingMaintenanceMessage(alias: String, isSenderMode: Boolean): String =
        if (isSenderMode) "수신 기기 계정 교체 중: [$alias]..." else "Gemini 계정 교체 중: [$alias]..."

    fun formatMaintenanceErrorMessage(errorMessage: String): String = when (errorMessage) {
        ERROR_ACCESSIBILITY_REQUIRED,
        ERROR_IDENTIFIER_BLANK -> errorMessage
        ERROR_SERVICE_UNAVAILABLE -> "계정 전환 실패: 접근성 서비스 없음"
        else -> "계정 전환 실패: $errorMessage"
    }
}
