// 역할: Gemini 앱 자동 제어 및 프로세스 관리 정책을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
import com.example.gemgemgen.automation.domain.GeminiAppControlBlockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAppControlPolicyTest {
    @Test
    fun canClose_whenInstalledAccessibleAndIdle() {
        assertTrue(
            AutomationExecutionPolicy.canCloseGemini(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
        assertNull(
            AutomationExecutionPolicy.geminiBlockReason(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
    }

    @Test
    fun blockReason_prefersAutomationRunningFirst() {
        assertEquals(
            GeminiAppControlBlockReason.AutomationRunning,
            AutomationExecutionPolicy.geminiBlockReason(
                isGeminiInstalled = false,
                isAccessibilityServiceEnabled = false,
                isAutomationRunning = true,
                isClosingInProgress = true
            )
        )
    }

    @Test
    fun blockReason_coversEachCondition() {
        assertEquals(
            GeminiAppControlBlockReason.AlreadyInProgress,
            AutomationExecutionPolicy.geminiBlockReason(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = true
            )
        )
        assertEquals(
            GeminiAppControlBlockReason.GeminiNotInstalled,
            AutomationExecutionPolicy.geminiBlockReason(
                isGeminiInstalled = false,
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
        assertEquals(
            GeminiAppControlBlockReason.AccessibilityDisabled,
            AutomationExecutionPolicy.geminiBlockReason(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = false,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
        assertFalse(
            AutomationExecutionPolicy.canCloseGemini(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = false,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
    }
}
