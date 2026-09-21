// 역할: 변주 자동화의 실행 가능 여부 및 각 케이스별 거절 사유 판별 정책을 검증하는 단위 테스트입니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.VariationStartPolicy
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VariationStartPolicyTest {

    private val readyEnvironment = EnvironmentStatus(
        isGeminiInstalled = true,
        isAccessibilityServiceEnabled = true
    )

    @Test
    fun canRun_whenAllConditionsMet_returnsTrueAndNoReason() {
        val canRun = VariationStartPolicy.canRun(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true,
            isVariationRunning = false
        )
        val reason = VariationStartPolicy.unavailableReason(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true,
            isVariationRunning = false
        )
        val evaluation = VariationStartPolicy.evaluate(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true,
            isVariationRunning = false
        )

        assertTrue(canRun)
        assertNull(reason)
        assertEquals(VariationStartPolicy.Evaluation.Executable, evaluation)
        assertTrue(
            VariationStartPolicy.canInteract(
                isReceiverMode = false,
                isRunning = false,
                isMaintenanceBusy = false,
                isVariationRunning = false
            )
        )
    }

    @Test
    fun receiverMode_rejectsWithReceiverModeMessage() {
        val canRun = VariationStartPolicy.canRun(
            isReceiverMode = true,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true
        )
        val reason = VariationStartPolicy.unavailableReason(
            isReceiverMode = true,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true
        )
        val evaluation = VariationStartPolicy.evaluate(
            isReceiverMode = true,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true
        )

        assertFalse(canRun)
        assertEquals("수신 모드에서는 변주를 실행할 수 없습니다.", reason)
        assertEquals(
            VariationStartPolicy.Evaluation.Rejected("수신 모드에서는 변주를 실행할 수 없습니다."),
            evaluation
        )
        assertFalse(
            VariationStartPolicy.canInteract(
                isReceiverMode = true,
                isRunning = false,
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun runningAutomation_rejectsWithAutomationRunningMessage() {
        val canRun = VariationStartPolicy.canRun(
            isReceiverMode = false,
            isRunning = true,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true
        )
        val reason = VariationStartPolicy.unavailableReason(
            isReceiverMode = false,
            isRunning = true,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true
        )
        val evaluation = VariationStartPolicy.evaluate(
            isReceiverMode = false,
            isRunning = true,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true
        )

        assertFalse(canRun)
        assertEquals("자동화 실행 중에는 변주를 실행할 수 없습니다.", reason)
        assertEquals(
            VariationStartPolicy.Evaluation.Rejected("자동화 실행 중에는 변주를 실행할 수 없습니다."),
            evaluation
        )
        assertFalse(
            VariationStartPolicy.canInteract(
                isReceiverMode = false,
                isRunning = true,
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun variationRunning_rejectsWithVariationRunningMessage() {
        val canRun = VariationStartPolicy.canRun(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true,
            isVariationRunning = true
        )
        val reason = VariationStartPolicy.unavailableReason(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true,
            isVariationRunning = true
        )

        assertFalse(canRun)
        assertEquals("변주 자동화가 이미 실행 중입니다.", reason)
        assertFalse(
            VariationStartPolicy.canInteract(
                isReceiverMode = false,
                isRunning = false,
                isMaintenanceBusy = false,
                isVariationRunning = true
            )
        )
    }

    @Test
    fun maintenanceBusy_rejectsWithMaintenanceBusyMessage() {
        val canRun = VariationStartPolicy.canRun(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = true,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true
        )
        val reason = VariationStartPolicy.unavailableReason(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = true,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true
        )
        val evaluation = VariationStartPolicy.evaluate(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = true,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true
        )

        assertFalse(canRun)
        assertEquals("유지보수 작업이 진행 중입니다.", reason)
        assertEquals(
            VariationStartPolicy.Evaluation.Rejected("유지보수 작업이 진행 중입니다."),
            evaluation
        )
        assertFalse(
            VariationStartPolicy.canInteract(
                isReceiverMode = false,
                isRunning = false,
                isMaintenanceBusy = true
            )
        )
    }

    @Test
    fun geminiNotInstalled_rejectsWithGeminiNotInstalledMessage() {
        val canRun = VariationStartPolicy.canRun(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = false,
            isAccessibilityServiceEnabled = true
        )
        val reason = VariationStartPolicy.unavailableReason(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = false,
            isAccessibilityServiceEnabled = true
        )
        val evaluation = VariationStartPolicy.evaluate(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = false,
            isAccessibilityServiceEnabled = true
        )

        assertFalse(canRun)
        assertEquals("Gemini 앱을 먼저 설치해주세요.", reason)
        assertEquals(
            VariationStartPolicy.Evaluation.Rejected("Gemini 앱을 먼저 설치해주세요."),
            evaluation
        )
        // Gemini 미설치여도 다른 실행/유지보수 상태가 아니면 인터랙트(버튼 노출 등)는 가능
        assertTrue(
            VariationStartPolicy.canInteract(
                isReceiverMode = false,
                isRunning = false,
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun accessibilityDisabled_rejectsWithAccessibilityDisabledMessage() {
        val canRun = VariationStartPolicy.canRun(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = false
        )
        val reason = VariationStartPolicy.unavailableReason(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = false
        )
        val evaluation = VariationStartPolicy.evaluate(
            isReceiverMode = false,
            isRunning = false,
            isMaintenanceBusy = false,
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = false
        )

        assertFalse(canRun)
        assertEquals("접근성 서비스를 먼저 켜주세요.", reason)
        assertEquals(
            VariationStartPolicy.Evaluation.Rejected("접근성 서비스를 먼저 켜주세요."),
            evaluation
        )
        assertTrue(
            VariationStartPolicy.canInteract(
                isReceiverMode = false,
                isRunning = false,
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun overloadsWithModeAndEnvironmentStatus_workIdentically() {
        val readyStatus = readyEnvironment
        val normalCanRun = VariationStartPolicy.canRun(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyStatus,
            isRunning = false,
            isMaintenanceBusy = false
        )
        assertTrue(normalCanRun)
        assertNull(
            VariationStartPolicy.unavailableReason(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyStatus,
                isRunning = false,
                isMaintenanceBusy = false
            )
        )

        val receiverCanRun = VariationStartPolicy.canRun(
            mode = AutomationMode.RECEIVER,
            environmentStatus = readyStatus,
            isRunning = false,
            isMaintenanceBusy = false
        )
        assertFalse(receiverCanRun)
        assertEquals(
            "수신 모드에서는 변주를 실행할 수 없습니다.",
            VariationStartPolicy.unavailableReason(
                mode = AutomationMode.RECEIVER,
                environmentStatus = readyStatus,
                isRunning = false,
                isMaintenanceBusy = false
            )
        )

        val unreadyEnv = readyStatus.copy(isGeminiInstalled = false)
        assertEquals(
            "Gemini 앱을 먼저 설치해주세요.",
            VariationStartPolicy.unavailableReason(
                mode = AutomationMode.NORMAL,
                environmentStatus = unreadyEnv,
                isRunning = false,
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun priorityOrder_receiverModeHasPrecedenceOverOtherFailures() {
        val reason = VariationStartPolicy.unavailableReason(
            isReceiverMode = true,
            isRunning = true,
            isMaintenanceBusy = true,
            isGeminiInstalled = false,
            isAccessibilityServiceEnabled = false
        )
        assertEquals("수신 모드에서는 변주를 실행할 수 없습니다.", reason)
    }

    @Test
    fun priorityOrder_runningHasPrecedenceOverGeminiNotInstalled() {
        val reason = VariationStartPolicy.unavailableReason(
            isReceiverMode = false,
            isRunning = true,
            isMaintenanceBusy = false,
            isGeminiInstalled = false,
            isAccessibilityServiceEnabled = false
        )
        assertEquals("자동화 실행 중에는 변주를 실행할 수 없습니다.", reason)
    }
}
