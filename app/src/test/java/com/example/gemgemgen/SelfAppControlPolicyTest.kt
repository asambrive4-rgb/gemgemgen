// 역할: 자기 앱 닫기 및 숨기기 제어 정책 판단을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
import com.example.gemgemgen.automation.domain.SelfAppControlBlockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfAppControlPolicyTest {
    @Test
    fun canClose_whenAccessibleAndIdle() {
        assertTrue(
            AutomationExecutionPolicy.canCloseSelfApp(
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
        assertNull(
            AutomationExecutionPolicy.selfAppBlockReason(
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
            AutomationExecutionPolicy.selfAppBlockReason(
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
            AutomationExecutionPolicy.selfAppBlockReason(
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = true
            )
        )
        assertEquals(
            SelfAppControlBlockReason.AccessibilityDisabled,
            AutomationExecutionPolicy.selfAppBlockReason(
                isAccessibilityServiceEnabled = false,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
        assertFalse(
            AutomationExecutionPolicy.canCloseSelfApp(
                isAccessibilityServiceEnabled = false,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )
    }
}
