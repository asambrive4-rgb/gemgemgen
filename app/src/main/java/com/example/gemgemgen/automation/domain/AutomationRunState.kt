package com.example.gemgemgen.automation.domain

sealed interface AutomationRunState {
    data object Idle : AutomationRunState
    data class Running(
        val step: String,
        val currentIndex: Int? = null,
        val totalCount: Int? = null
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

