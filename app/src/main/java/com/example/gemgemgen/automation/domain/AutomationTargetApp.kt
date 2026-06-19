package com.example.gemgemgen.automation.domain

enum class AutomationTargetApp(
    val storageValue: String,
    val displayName: String
) {
    GEMINI(
        storageValue = "gemini",
        displayName = "Gemini"
    ),
    CHATGPT(
        storageValue = "chatgpt",
        displayName = "ChatGPT"
    );

    companion object {
        fun fromStorageValue(value: String): AutomationTargetApp {
            return entries.firstOrNull { it.storageValue == value } ?: GEMINI
        }
    }
}
