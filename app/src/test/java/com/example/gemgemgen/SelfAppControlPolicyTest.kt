package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.SelfAppControlBlockReason
import com.example.gemgemgen.automation.domain.SelfAppControlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfAppControlPolicyTest {
    @Test
    fun canClose_whenAccessibleAndIdle() {
        assertTrue(
            SelfAppControlPolicy.canClose(
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
        assertNull(
            SelfAppControlPolicy.blockReason(
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
    }

    @Test
    fun blockReason_prefersAutomationRunningFirst() {
        assertEquals(
            SelfAppControlBlockReason.AutomationRunning,
            SelfAppControlPolicy.blockReason(
                isAccessibilityServiceEnabled = false,
                isAutomationRunning = true,
                isClosingInProgress = true
            )
        )
    }

    @Test
    fun blockReason_coversEachCondition() {
        assertEquals(
            SelfAppControlBlockReason.AlreadyInProgress,
            SelfAppControlPolicy.blockReason(
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = true
            )
        )
        assertEquals(
            SelfAppControlBlockReason.AccessibilityDisabled,
            SelfAppControlPolicy.blockReason(
                isAccessibilityServiceEnabled = false,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
        assertFalse(
            SelfAppControlPolicy.canClose(
                isAccessibilityServiceEnabled = false,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
    }
}
