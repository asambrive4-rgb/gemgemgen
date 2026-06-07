package com.example.gemgemgen

sealed interface AutomationUiState {
    data object Idle : AutomationUiState
    data class Running(
        val step: String,
        val currentIndex: Int? = null,
        val totalCount: Int? = null,
        val lastPrompt: String = ""
    ) : AutomationUiState
    data object Success : AutomationUiState
    data object Stopped : AutomationUiState
    data class Failure(val message: String) : AutomationUiState
}

fun AutomationUiState.isTerminal(): Boolean {
    return this == AutomationUiState.Success ||
        this == AutomationUiState.Stopped ||
        this is AutomationUiState.Failure
}

object AutomationUiText {
    fun statusText(automationState: AutomationUiState): String {
        return when (automationState) {
            AutomationUiState.Idle -> "자동화 대기 중"
            is AutomationUiState.Running -> {
                val progress = if (
                    automationState.currentIndex != null &&
                    automationState.totalCount != null
                ) {
                    " (${automationState.currentIndex}/${automationState.totalCount})"
                } else {
                    ""
                }
                "진행 중: ${automationState.step}$progress"
            }
            AutomationUiState.Success -> "자동화 성공"
            AutomationUiState.Stopped -> "자동화 중지됨"
            is AutomationUiState.Failure -> "자동화 실패: ${automationState.message}"
        }
    }
}
