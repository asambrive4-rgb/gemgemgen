package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.GeminiAppControlBlockReason
import com.example.gemgemgen.automation.domain.GeminiAppControlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAppControlPolicyTest {
    @Test
    fun canClose_whenInstalledAccessibleAndIdle() {
        assertTrue(
            GeminiAppControlPolicy.canClose(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
        assertNull(
            GeminiAppControlPolicy.blockReason(
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
            GeminiAppControlPolicy.blockReason(
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
            GeminiAppControlPolicy.blockReason(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = true
            )
        )
        assertEquals(
            GeminiAppControlBlockReason.GeminiNotInstalled,
            GeminiAppControlPolicy.blockReason(
                isGeminiInstalled = false,
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
        assertEquals(
            GeminiAppControlBlockReason.AccessibilityDisabled,
            GeminiAppControlPolicy.blockReason(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = false,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
        assertFalse(
            GeminiAppControlPolicy.canClose(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = false,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
    }
}
