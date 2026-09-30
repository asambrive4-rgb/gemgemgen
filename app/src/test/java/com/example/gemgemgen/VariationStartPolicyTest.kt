// 역할: 변주 자동화의 실행 가능 여부 및 각 케이스별 거절 사유 판별 정책을 검증하는 단위 테스트입니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
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
        val canRun = AutomationExecutionPolicy.canRunVariation(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment,
            isRunning = false,
            isMaintenanceBusy = false,
            isVariationRunning = false
        )
        val reason = AutomationExecutionPolicy.variationUnavailableReason(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment,
            isRunning = false,
            isMaintenanceBusy = false,
            isVariationRunning = false
        )

        assertTrue(canRun)
        assertNull(reason)
        assertTrue(
            AutomationExecutionPolicy.canInteractWithVariation(
                mode = AutomationMode.NORMAL,
                isRunning = false,
                isMaintenanceBusy = false,
                isVariationRunning = false
            )
        )
    }

    @Test
    fun receiverMode_rejectsWithReceiverModeMessage() {
        val canRun = AutomationExecutionPolicy.canRunVariation(
            mode = AutomationMode.RECEIVER,
            environmentStatus = readyEnvironment,
            isRunning = false,
            isMaintenanceBusy = false
        )
        val reason = AutomationExecutionPolicy.variationUnavailableReason(
            mode = AutomationMode.RECEIVER,
            environmentStatus = readyEnvironment,
            isRunning = false,
            isMaintenanceBusy = false
        )

        assertFalse(canRun)
        assertEquals("수신 모드에서는 변주를 실행할 수 없습니다.", reason)
        assertFalse(
            AutomationExecutionPolicy.canInteractWithVariation(
                mode = AutomationMode.RECEIVER,
                isRunning = false,
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun runningAutomation_rejectsWithAutomationRunningMessage() {
        val canRun = AutomationExecutionPolicy.canRunVariation(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment,
            isRunning = true,
            isMaintenanceBusy = false
        )
        val reason = AutomationExecutionPolicy.variationUnavailableReason(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment,
            isRunning = true,
            isMaintenanceBusy = false
        )

        assertFalse(canRun)
        assertEquals("자동화 실행 중에는 변주를 실행할 수 없습니다.", reason)
        assertFalse(
            AutomationExecutionPolicy.canInteractWithVariation(
                mode = AutomationMode.NORMAL,
                isRunning = true,
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun variationRunning_rejectsWithVariationRunningMessage() {
        val canRun = AutomationExecutionPolicy.canRunVariation(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment,
            isRunning = false,
            isMaintenanceBusy = false,
            isVariationRunning = true
        )
        val reason = AutomationExecutionPolicy.variationUnavailableReason(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment,
            isRunning = false,
            isMaintenanceBusy = false,
            isVariationRunning = true
        )

        assertFalse(canRun)
        assertEquals("변주 자동화가 이미 실행 중입니다.", reason)
        assertFalse(
            AutomationExecutionPolicy.canInteractWithVariation(
                mode = AutomationMode.NORMAL,
                isRunning = false,
                isMaintenanceBusy = false,
                isVariationRunning = true
            )
        )
    }

    @Test
    fun maintenanceBusy_rejectsWithMaintenanceBusyMessage() {
        val canRun = AutomationExecutionPolicy.canRunVariation(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment,
            isRunning = false,
            isMaintenanceBusy = true
        )
        val reason = AutomationExecutionPolicy.variationUnavailableReason(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment,
            isRunning = false,
            isMaintenanceBusy = true
        )

        assertFalse(canRun)
        assertEquals("유지보수 작업이 진행 중입니다.", reason)
        assertFalse(
            AutomationExecutionPolicy.canInteractWithVariation(
                mode = AutomationMode.NORMAL,
                isRunning = false,
                isMaintenanceBusy = true
            )
        )
    }

    @Test
    fun geminiNotInstalled_rejectsWithGeminiNotInstalledMessage() {
        val envWithoutGemini = readyEnvironment.copy(isGeminiInstalled = false)
        val canRun = AutomationExecutionPolicy.canRunVariation(
            mode = AutomationMode.NORMAL,
            environmentStatus = envWithoutGemini,
            isRunning = false,
            isMaintenanceBusy = false
        )
        val reason = AutomationExecutionPolicy.variationUnavailableReason(
            mode = AutomationMode.NORMAL,
            environmentStatus = envWithoutGemini,
            isRunning = false,
            isMaintenanceBusy = false
        )

        assertFalse(canRun)
        assertEquals("Gemini 앱을 먼저 설치해주세요.", reason)
        // Gemini 미설치여도 다른 실행/유지보수 상태가 아니면 인터랙트(버튼 노출 등)는 가능
        assertTrue(
            AutomationExecutionPolicy.canInteractWithVariation(
                mode = AutomationMode.NORMAL,
                isRunning = false,
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun accessibilityDisabled_rejectsWithAccessibilityDisabledMessage() {
        val envWithoutAccessibility = readyEnvironment.copy(isAccessibilityServiceEnabled = false)
        val canRun = AutomationExecutionPolicy.canRunVariation(
            mode = AutomationMode.NORMAL,
            environmentStatus = envWithoutAccessibility,
            isRunning = false,
            isMaintenanceBusy = false
        )
        val reason = AutomationExecutionPolicy.variationUnavailableReason(
            mode = AutomationMode.NORMAL,
            environmentStatus = envWithoutAccessibility,
            isRunning = false,
            isMaintenanceBusy = false
        )

        assertFalse(canRun)
        assertEquals("접근성 서비스를 먼저 켜주세요.", reason)
        assertTrue(
            AutomationExecutionPolicy.canInteractWithVariation(
                mode = AutomationMode.NORMAL,
                isRunning = false,
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun priorityOrder_receiverModeHasPrecedenceOverOtherFailures() {
        val unreadyEnv = EnvironmentStatus(
            isGeminiInstalled = false,
            isAccessibilityServiceEnabled = false
        )
        val reason = AutomationExecutionPolicy.variationUnavailableReason(
            mode = AutomationMode.RECEIVER,
            environmentStatus = unreadyEnv,
            isRunning = true,
            isMaintenanceBusy = true
        )
        assertEquals("수신 모드에서는 변주를 실행할 수 없습니다.", reason)
    }

    @Test
    fun priorityOrder_runningHasPrecedenceOverGeminiNotInstalled() {
        val unreadyEnv = EnvironmentStatus(
            isGeminiInstalled = false,
            isAccessibilityServiceEnabled = false
        )
        val reason = AutomationExecutionPolicy.variationUnavailableReason(
            mode = AutomationMode.NORMAL,
            environmentStatus = unreadyEnv,
            isRunning = true,
            isMaintenanceBusy = false
        )
        assertEquals("자동화 실행 중에는 변주를 실행할 수 없습니다.", reason)
    }
}

