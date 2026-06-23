package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.AutomationRunState

object AutomationUiText {
    fun statusText(automationState: AutomationRunState): String {
        return when (automationState) {
            AutomationRunState.Idle -> "자동화 대기 중"
            is AutomationRunState.Running -> automationState.step
            AutomationRunState.Success -> "자동화 성공"
            AutomationRunState.Stopped -> "자동화 중지됨"
            is AutomationRunState.Failure -> "자동화 실패: ${automationState.message}"
        }
    }
}
