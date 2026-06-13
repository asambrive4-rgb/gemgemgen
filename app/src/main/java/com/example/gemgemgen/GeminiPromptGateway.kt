package com.example.gemgemgen

enum class GeminiNewChatMode {
    SidebarThenNearestToSearch,
    DirectVisibleButton
}

interface GeminiPromptGateway {
    fun sendPrompt(
        prompt: String,
        newChatMode: GeminiNewChatMode,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    )

    fun cancelCurrentRun()
}
