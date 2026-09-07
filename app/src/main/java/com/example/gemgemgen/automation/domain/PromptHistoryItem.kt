package com.example.gemgemgen.automation.domain

data class PromptHistoryItem(
    val id: String,
    val prompt: String,
    val targetApp: AutomationTargetApp,
    val createdAtMillis: Long
)
