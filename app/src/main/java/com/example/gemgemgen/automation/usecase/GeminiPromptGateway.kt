package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState

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

