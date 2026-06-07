package com.example.gemgemgen

interface GeminiPromptSender {
    fun sendPrompt(
        prompt: String,
        onStateChange: (AutomationUiState) -> Unit,
        onDone: () -> Unit
    )

    fun cancelCurrentRun()
}
