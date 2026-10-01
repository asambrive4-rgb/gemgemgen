// 역할: 자동화 화면과 플로팅 바에 표시되는 상태 메시지 및 버튼 문구를 제공합니다.
package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.GeminiAppControlBlockReason
import com.example.gemgemgen.automation.domain.PromptParagraphMessageKey
import com.example.gemgemgen.automation.domain.SelfAppControlBlockReason
import com.example.gemgemgen.remote.domain.AutomationMode

object AutomationUiText {
    const val ACCESSIBILITY_PROMPT_TITLE: String = "접근성 서비스 필요"
    const val ACCESSIBILITY_PROMPT_MESSAGE: String =
        "자동화를 쓰려면 접근성 서비스「GemGemGen 자동화」를 켜야 합니다. 접근성 설정으로 이동할까요?"
    const val GEMINI_RESTART_STARTING_TEXT: String = "Gemini 재시작 중..."
    const val SELF_APP_TERMINATE_STARTING_TEXT: String = "앱 종료 중..."
    const val GEMINI_RESTART_CANCELED_TEXT: String = "Gemini 재시작을 취소했습니다."
    const val SELF_APP_TERMINATE_CANCELED_TEXT: String = "앱 종료를 취소했습니다."
    const val MEMORY_CLEANUP_SCHEDULED_TEXT: String = "자동화 종료 후 메모리 정리가 예약되었습니다."
    const val MEMORY_CLEANUP_SCHEDULE_CANCELED_TEXT: String = "메모리 정리 예약이 취소되었습니다."

    fun paragraphMessage(key: PromptParagraphMessageKey): String {
        return when (key) {
            PromptParagraphMessageKey.None -> ""
            PromptParagraphMessageKey.Selected ->
                "문단이 선택되었습니다. 가져오기 또는 삭제키를 사용하세요."
            PromptParagraphMessageKey.EmptyParagraph ->
                "빈 줄은 선택할 수 없습니다. 텍스트가 있는 문단을 터치하세요."
            PromptParagraphMessageKey.SelectFirst ->
                "먼저 바꿀 문단을 선택하세요."
            PromptParagraphMessageKey.EmptyClipboard ->
                "클립보드가 비어 있어 선택한 문단을 바꾸지 않았습니다."
        }
    }

    fun statusText(automationState: AutomationRunState): String {
        return when (automationState) {
            AutomationRunState.Idle -> "자동화 대기 중"
            is AutomationRunState.Running -> automationState.step
            AutomationRunState.Success -> "자동화 성공"
            AutomationRunState.Stopped -> "자동화 중지"
            is AutomationRunState.Failure -> "자동화 실패: ${automationState.message}"
        }
    }

    fun memoryCleanupStartingText(mode: AutomationMode = AutomationMode.NORMAL): String {
        return if (mode == AutomationMode.SENDER) "수신 기기 메모리 정리 중..." else "메모리 정리 중..."
    }

    fun memoryCleanupCanceledText(mode: AutomationMode = AutomationMode.NORMAL): String {
        return if (mode == AutomationMode.SENDER) "수신 기기 메모리 정리를 취소했습니다." else "메모리 정리를 취소했습니다."
    }

    fun memoryCleanupUnavailableMessage(state: AutomationUiState): String {
        return when {
            state.isMaintenanceBusy -> "유지보수 작업이 이미 진행 중입니다."
            state.automationMode == AutomationMode.SENDER && !state.remoteAutomationStatus.canSend ->
                "연결된 수신 기기를 찾지 못했습니다."
            state.automationMode == AutomationMode.RECEIVER ->
                "수신 모드에서는 메모리를 직접 정리할 수 없습니다."
            !state.environmentStatus.isAccessibilityServiceEnabled ->
                "접근성 서비스를 먼저 켜주세요."
            else -> "메모리 정리를 지금 실행할 수 없습니다."
        }
    }

    fun geminiRestartUnavailableMessage(state: AutomationUiState): String {
        return geminiRestartUnavailableMessage(state.geminiRestartBlockReason)
    }

    fun selfAppTerminateUnavailableMessage(state: AutomationUiState): String {
        return selfAppTerminateUnavailableMessage(state.selfCloseBlockReason)
    }

    fun selfAppTerminateUnavailableMessage(reason: SelfAppControlBlockReason?): String {
        return when (reason) {
            SelfAppControlBlockReason.AutomationRunning ->
                "자동화 중에는 앱을 종료할 수 없습니다."
            SelfAppControlBlockReason.AlreadyInProgress ->
                "앱 종료가 이미 진행 중입니다."
            SelfAppControlBlockReason.AccessibilityDisabled ->
                "접근성 서비스를 먼저 켜주세요."
            null -> "앱 종료를 지금 실행할 수 없습니다."
        }
    }

    fun geminiRestartUnavailableMessage(reason: GeminiAppControlBlockReason?): String {
        return when (reason) {
            GeminiAppControlBlockReason.AutomationRunning ->
                "자동화 중에는 Gemini를 재시작할 수 없습니다."
            GeminiAppControlBlockReason.AlreadyInProgress ->
                "Gemini 재시작이 이미 진행 중입니다."
            GeminiAppControlBlockReason.GeminiNotInstalled ->
                "Gemini 앱이 설치되어 있지 않습니다."
            GeminiAppControlBlockReason.AccessibilityDisabled ->
                "접근성 서비스를 먼저 켜주세요."
            null -> "Gemini 재시작을 지금 실행할 수 없습니다."
        }
    }
}
