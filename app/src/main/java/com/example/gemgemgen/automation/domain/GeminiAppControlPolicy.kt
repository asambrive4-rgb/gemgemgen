package com.example.gemgemgen.automation.domain

/**
 * Gemini 앱 재시작/종료를 지금 허용할지 판단하는 규칙.
 * UI 메시지 문자열은 포함하지 않는다.
 */
enum class GeminiAppControlBlockReason {
    AutomationRunning,
    AlreadyInProgress,
    GeminiNotInstalled,
    AccessibilityDisabled
}

object GeminiAppControlPolicy {
    fun canClose(
        isGeminiInstalled: Boolean,
        isAccessibilityServiceEnabled: Boolean,
        isAutomationRunning: Boolean,
        isClosingInProgress: Boolean
    ): Boolean {
        return blockReason(
            isGeminiInstalled = isGeminiInstalled,
            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled,
            isAutomationRunning = isAutomationRunning,
            isClosingInProgress = isClosingInProgress
        ) == null
    }

    fun blockReason(
        isGeminiInstalled: Boolean,
        isAccessibilityServiceEnabled: Boolean,
        isAutomationRunning: Boolean,
        isClosingInProgress: Boolean
    ): GeminiAppControlBlockReason? {
        return when {
            isAutomationRunning -> GeminiAppControlBlockReason.AutomationRunning
            isClosingInProgress -> GeminiAppControlBlockReason.AlreadyInProgress
            !isGeminiInstalled -> GeminiAppControlBlockReason.GeminiNotInstalled
            !isAccessibilityServiceEnabled -> GeminiAppControlBlockReason.AccessibilityDisabled
            else -> null
        }
    }
}
