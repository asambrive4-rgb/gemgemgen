// 역할: 자동화 대상이 되는 외부 AI 앱 목록을 정의합니다.
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
    ),
    FLOW(
        storageValue = "flow",
        displayName = "Flow"
    );

    companion object {
        fun fromStorageValue(value: String): AutomationTargetApp {
            return entries.firstOrNull { it.storageValue == value } ?: GEMINI
        }
    }
}
