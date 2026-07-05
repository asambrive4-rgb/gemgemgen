package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult

object AutomationUiText {
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

    fun geminiTerminateStartingText(): String = "Gemini 종료 중..."

    fun geminiRestartCanceledText(): String = "Gemini 재시작을 취소했습니다."

    fun geminiTerminateCanceledText(): String = "Gemini 종료를 취소했습니다."

    fun unknownCloseErrorMessage(error: Throwable): String {
        return error.message ?: "알 수 없는 오류가 발생했습니다."
    }

    fun geminiRestartUnavailableMessage(state: MainUiState): String {
        return when {
            state.isRunning -> "자동화 중에는 Gemini를 재시작할 수 없습니다."
            state.isClosingGemini -> "Gemini 재시작이 이미 진행 중입니다."
            !state.environmentStatus.isGeminiInstalled -> "Gemini 앱이 설치되어 있지 않습니다."
            !state.environmentStatus.isAccessibilityServiceEnabled ->
                "접근성 서비스를 먼저 켜주세요."
            else -> "Gemini 재시작을 지금 실행할 수 없습니다."
        }
    }

    fun geminiTerminateUnavailableMessage(state: MainUiState): String {
        return when {
            state.isRunning -> "자동화 중에는 Gemini를 종료할 수 없습니다."
            state.isClosingGemini -> "Gemini 종료가 이미 진행 중입니다."
            !state.environmentStatus.isGeminiInstalled -> "Gemini 앱이 설치되어 있지 않습니다."
            !state.environmentStatus.isAccessibilityServiceEnabled ->
                "접근성 서비스를 먼저 켜주세요."
            else -> "Gemini 종료를 지금 실행할 수 없습니다."
        }
    }

    fun geminiRestartResultMessage(result: CloseGeminiAppResult): String {
        return when (result) {
            is CloseGeminiAppResult.Success -> {
                if (result.closedCount <= 1) {
                    "Gemini 앱을 재시작했습니다."
                } else {
                    "Gemini 앱 ${result.closedCount}개를 종료하고 재시작했습니다."
                }
            }
            CloseGeminiAppResult.AccessibilityUnavailable ->
                "접근성 서비스가 켜져 있지 않습니다."
            CloseGeminiAppResult.RecentsUnavailable ->
                "최근 앱 화면을 열지 못했습니다."
            CloseGeminiAppResult.NotFound ->
                "최근 앱에서 Gemini를 찾지 못했습니다."
            is CloseGeminiAppResult.Failure ->
                "Gemini 재시작 실패: ${result.message}"
        }
    }

    fun geminiTerminateResultMessage(result: CloseGeminiAppResult): String {
        return when (result) {
            is CloseGeminiAppResult.Success -> {
                if (result.closedCount <= 1) {
                    "Gemini 앱을 종료했습니다."
                } else {
                    "Gemini 앱 ${result.closedCount}개를 종료했습니다."
                }
            }
            CloseGeminiAppResult.AccessibilityUnavailable ->
                "접근성 서비스가 켜져 있지 않습니다."
            CloseGeminiAppResult.RecentsUnavailable ->
                "최근 앱 화면을 열지 못했습니다."
            CloseGeminiAppResult.NotFound ->
                "최근 앱에서 Gemini를 찾지 못했습니다."
            is CloseGeminiAppResult.Failure ->
                "Gemini 종료 실패: ${result.message}"
        }
    }
}
