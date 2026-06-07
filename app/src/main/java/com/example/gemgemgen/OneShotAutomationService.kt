package com.example.gemgemgen

interface OneShotAutomationService {
    fun sendPrompt(
        prompt: String,
        onStateChange: (AutomationUiState) -> Unit,
        onDone: () -> Unit,
        startDelayMillis: Long = 0L
    )

    fun runOneShot(
        prompt: String,
        onStateChange: (AutomationUiState) -> Unit
    )

    fun cancelCurrentRun()
}
