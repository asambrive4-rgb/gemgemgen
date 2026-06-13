package com.example.gemgemgen

sealed interface AutomationRunState {
    data object Idle : AutomationRunState
    data class Running(
        val step: String,
        val currentIndex: Int? = null,
        val totalCount: Int? = null,
        val lastPrompt: String = ""
    ) : AutomationRunState
    data object Success : AutomationRunState
    data object Stopped : AutomationRunState
    data class Failure(val message: String) : AutomationRunState
}

fun AutomationRunState.isTerminal(): Boolean {
    return this == AutomationRunState.Success ||
        this == AutomationRunState.Stopped ||
        this is AutomationRunState.Failure
}

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
