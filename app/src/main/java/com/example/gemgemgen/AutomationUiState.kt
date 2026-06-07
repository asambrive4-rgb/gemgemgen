package com.example.gemgemgen

sealed interface AutomationUiState {
    data object Idle : AutomationUiState
    data class Running(val step: String) : AutomationUiState
    data object Success : AutomationUiState
    data class Failure(val message: String) : AutomationUiState
}
