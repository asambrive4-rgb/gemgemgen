// 역할: 자동화 시작 시 자기 앱 닫기 또는 숨기기 허용 여부 정책을 판정합니다.
package com.example.gemgemgen.automation.domain

/**
 * GemGemGen 앱 자체 종료를 지금 허용할지 판단하는 규칙.
 * UI 메시지 문자열은 포함하지 않는다.
 */
enum class SelfAppControlBlockReason {
    AutomationRunning,
    AlreadyInProgress,
    AccessibilityDisabled
}

object SelfAppControlPolicy {
    fun canClose(
        isAccessibilityServiceEnabled: Boolean,
        isAutomationRunning: Boolean,
        isClosingInProgress: Boolean
    ): Boolean {
        return blockReason(
            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled,
            isAutomationRunning = isAutomationRunning,
            isClosingInProgress = isClosingInProgress
        ) == null
    }

    fun blockReason(
        isAccessibilityServiceEnabled: Boolean,
        isAutomationRunning: Boolean,
        isClosingInProgress: Boolean
    ): SelfAppControlBlockReason? {
        return when {
            isAutomationRunning -> SelfAppControlBlockReason.AutomationRunning
            isClosingInProgress -> SelfAppControlBlockReason.AlreadyInProgress
            !isAccessibilityServiceEnabled -> SelfAppControlBlockReason.AccessibilityDisabled
            else -> null
        }
    }
}
