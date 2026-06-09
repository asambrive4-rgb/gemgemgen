package com.example.gemgemgen

enum class GeminiNewChatMode {
    SidebarThenNearestToSearch,
    DirectVisibleButton
}

interface GeminiPromptSender {
    fun sendPrompt(
        prompt: String,
        newChatMode: GeminiNewChatMode,
        onStateChange: (AutomationUiState) -> Unit,
        onDone: () -> Unit
    )

    fun cancelCurrentRun()
}
