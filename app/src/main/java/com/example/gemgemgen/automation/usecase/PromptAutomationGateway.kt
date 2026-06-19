package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState

enum class NewChatMode {
    Initial,
    Subsequent
}

interface PromptAutomationGateway {
    fun sendPrompt(
        prompt: String,
        newChatMode: NewChatMode,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    )

    fun cancelCurrentRun()
}

