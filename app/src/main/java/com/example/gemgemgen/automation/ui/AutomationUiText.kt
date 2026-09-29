// 역할: 자동화 화면과 플로팅 바에 표시되는 상태 메시지 및 버튼 문구를 제공합니다.
package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.GeminiAppControlBlockReason
import com.example.gemgemgen.automation.domain.PromptParagraphMessageKey
import com.example.gemgemgen.automation.domain.SelfAppControlBlockReason
import com.example.gemgemgen.remote.domain.AutomationMode

object AutomationUiText {
    fun accessibilityPromptTitle(): String = "접근성 서비스 필요"

    fun accessibilityPromptMessage(): String =
        "자동화를 쓰려면 접근성 서비스「GemGemGen 자동화」를 켜야 합니다. 접근성 설정으로 이동할까요?"

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

    fun geminiRestartStartingText(): String = "Gemini 재시작 중..."

    fun selfAppTerminateStartingText(): String = "앱 종료 중..."

    fun geminiRestartCanceledText(): String = "Gemini 재시작을 취소했습니다."

    fun selfAppTerminateCanceledText(): String = "앱 종료를 취소했습니다."

    fun memoryCleanupStartingText(mode: AutomationMode = AutomationMode.NORMAL): String {
        return if (mode == AutomationMode.SENDER) "수신 기기 메모리 정리 중..." else "메모리 정리 중..."
    }

    fun memoryCleanupCanceledText(mode: AutomationMode = AutomationMode.NORMAL): String {
        return if (mode == AutomationMode.SENDER) "수신 기기 메모리 정리를 취소했습니다." else "메모리 정리를 취소했습니다."
    }

    fun memoryCleanupScheduledText(): String = "자동화 종료 후 메모리 정리가 예약되었습니다."

    fun memoryCleanupScheduleCanceledText(): String = "메모리 정리 예약이 취소되었습니다."

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
        return geminiRestartUnavailableMessage(blockReasonFor(state))
    }

    fun selfAppTerminateUnavailableMessage(state: AutomationUiState): String {
        return selfAppTerminateUnavailableMessage(selfBlockReasonFor(state))
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

    private fun blockReasonFor(state: AutomationUiState): GeminiAppControlBlockReason? {
        return AutomationExecutionPolicy.geminiBlockReason(
            isGeminiInstalled = state.environmentStatus.isGeminiInstalled,
            isAccessibilityServiceEnabled = state.environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = state.isRunning,
            isClosingInProgress = state.isMaintenanceBusy
        )
    }

    private fun selfBlockReasonFor(state: AutomationUiState): SelfAppControlBlockReason? {
        return AutomationExecutionPolicy.selfAppBlockReason(
            isAccessibilityServiceEnabled = state.environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = state.isRunning,
            isClosingInProgress = state.isMaintenanceBusy
        )
    }
}

