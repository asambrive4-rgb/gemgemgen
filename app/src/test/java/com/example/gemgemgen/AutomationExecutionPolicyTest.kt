// 역할: 자동화 실행, 변주, 앱 제어 및 메모리 정리 인가 정책의 도메인 규칙을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationExecutionPolicyTest {

    private fun readyEnvironment(): EnvironmentStatus = EnvironmentStatus(
        isGeminiInstalled = true,
        isChatGptInstalled = true,
        isAccessibilityServiceEnabled = true,
        hasWriteSecureSettingsPermission = true,
        isWildcardDirectoryAccessible = true
    )

    @Test
    fun canRun_normalMode_requiresEnvironmentPromptAndIdleState() {
        assertTrue(
            AutomationExecutionPolicy.canRun(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isRunning = false,
                remoteAutomationStatus = RemoteAutomationStatus()
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canRun(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "",
                isRunning = false,
                remoteAutomationStatus = RemoteAutomationStatus()
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canRun(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isRunning = true,
                remoteAutomationStatus = RemoteAutomationStatus()
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canRun(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isRunning = false,
                remoteAutomationStatus = RemoteAutomationStatus(),
                isVariationRunning = true
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canRun(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isRunning = false,
                remoteAutomationStatus = RemoteAutomationStatus(),
                isMaintenanceBusy = true
            )
        )
    }

    @Test
    fun canRun_senderMode_requiresRemoteSendableAndPrompt() {
        val pairedStatus = RemoteAutomationStatus(
            mode = AutomationMode.SENDER,
            discoveredDeviceName = "S25 FE",
            isPaired = true
        )

        assertTrue(
            AutomationExecutionPolicy.canRun(
                mode = AutomationMode.SENDER,
                environmentStatus = EnvironmentStatus(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isRunning = false,
                remoteAutomationStatus = pairedStatus
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canRun(
                mode = AutomationMode.SENDER,
                environmentStatus = EnvironmentStatus(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isRunning = false,
                remoteAutomationStatus = RemoteAutomationStatus()
            )
        )
    }

    @Test
    fun canRun_receiverMode_alwaysReturnsFalse() {
        assertFalse(
            AutomationExecutionPolicy.canRun(
                mode = AutomationMode.RECEIVER,
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isRunning = false,
                remoteAutomationStatus = RemoteAutomationStatus()
            )
        )
    }

    @Test
    fun canRunVariation_evaluatesConditionsCorrectly() {
        assertTrue(
            AutomationExecutionPolicy.canRunVariation(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                isRunning = false,
                isMaintenanceBusy = false,
                isVariationRunning = false
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canRunVariation(
                mode = AutomationMode.RECEIVER,
                environmentStatus = readyEnvironment(),
                isRunning = false,
                isMaintenanceBusy = false
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canRunVariation(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                isRunning = true,
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun variationUnavailableReason_returnsHelpfulMessageWhenBlocked() {
        assertNull(
            AutomationExecutionPolicy.variationUnavailableReason(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                isRunning = false,
                isMaintenanceBusy = false
            )
        )

        val reasonReceiver = AutomationExecutionPolicy.variationUnavailableReason(
            mode = AutomationMode.RECEIVER,
            environmentStatus = readyEnvironment(),
            isRunning = false,
            isMaintenanceBusy = false
        )
        assertEquals("수신 모드에서는 변주를 실행할 수 없습니다.", reasonReceiver)

        val reasonRunning = AutomationExecutionPolicy.variationUnavailableReason(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment(),
            isRunning = true,
            isMaintenanceBusy = false
        )
        assertEquals("자동화 실행 중에는 변주를 실행할 수 없습니다.", reasonRunning)
    }

    @Test
    fun canCloseGemini_requiresInstalledAccessibilityAndIdleState() {
        assertTrue(
            AutomationExecutionPolicy.canCloseGemini(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = false
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canCloseGemini(
                isGeminiInstalled = false,
                isAccessibilityServiceEnabled = true,
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

        assertFalse(
            AutomationExecutionPolicy.canCloseGemini(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = true,
                isClosingInProgress = false
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canCloseGemini(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = false,
                isClosingInProgress = true
            )
        )
    }

    @Test
    fun canCloseSelfApp_doesNotRequireGeminiInstallation() {
        assertTrue(
            AutomationExecutionPolicy.canCloseSelfApp(
                isAccessibilityServiceEnabled = true,
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

        assertFalse(
            AutomationExecutionPolicy.canCloseSelfApp(
                isAccessibilityServiceEnabled = true,
                isAutomationRunning = true,
                isClosingInProgress = false
            )
        )
    }

    @Test
    fun canCleanMemory_normalMode_requiresAccessibilityAndIdleMaintenance() {
        assertTrue(
            AutomationExecutionPolicy.canCleanMemory(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                remoteAutomationStatus = RemoteAutomationStatus(),
                isMaintenanceBusy = false
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canCleanMemory(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment().copy(isAccessibilityServiceEnabled = false),
                remoteAutomationStatus = RemoteAutomationStatus(),
                isMaintenanceBusy = false
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canCleanMemory(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                remoteAutomationStatus = RemoteAutomationStatus(),
                isMaintenanceBusy = true
            )
        )
    }

    @Test
    fun canCleanMemory_senderAndReceiverModes() {
        val pairedStatus = RemoteAutomationStatus(
            mode = AutomationMode.SENDER,
            discoveredDeviceName = "S25 FE",
            isPaired = true
        )

        assertTrue(
            AutomationExecutionPolicy.canCleanMemory(
                mode = AutomationMode.SENDER,
                environmentStatus = EnvironmentStatus(),
                remoteAutomationStatus = pairedStatus,
                isMaintenanceBusy = false
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canCleanMemory(
                mode = AutomationMode.SENDER,
                environmentStatus = EnvironmentStatus(),
                remoteAutomationStatus = RemoteAutomationStatus(),
                isMaintenanceBusy = false
            )
        )

        assertFalse(
            AutomationExecutionPolicy.canCleanMemory(
                mode = AutomationMode.RECEIVER,
                environmentStatus = readyEnvironment(),
                remoteAutomationStatus = RemoteAutomationStatus(),
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun memoryCleanupTiming_immediatelyVsScheduled() {
        val env = readyEnvironment()
        val remote = RemoteAutomationStatus()

        // 비실행 상태: 즉시 실행 가능, 예약 불가
        assertTrue(
            AutomationExecutionPolicy.canExecuteCleanMemoryImmediately(
                mode = AutomationMode.NORMAL,
                environmentStatus = env,
                remoteAutomationStatus = remote,
                isRunning = false,
                isVariationRunning = false,
                isMaintenanceBusy = false
            )
        )
        assertFalse(
            AutomationExecutionPolicy.canScheduleCleanMemory(
                mode = AutomationMode.NORMAL,
                environmentStatus = env,
                remoteAutomationStatus = remote,
                isRunning = false,
                isVariationRunning = false,
                isMaintenanceBusy = false
            )
        )

        // 자동화 실행 중 상태: 즉시 실행 불가, 예약 가능
        assertFalse(
            AutomationExecutionPolicy.canExecuteCleanMemoryImmediately(
                mode = AutomationMode.NORMAL,
                environmentStatus = env,
                remoteAutomationStatus = remote,
                isRunning = true,
                isVariationRunning = false,
                isMaintenanceBusy = false
            )
        )
        assertTrue(
            AutomationExecutionPolicy.canScheduleCleanMemory(
                mode = AutomationMode.NORMAL,
                environmentStatus = env,
                remoteAutomationStatus = remote,
                isRunning = true,
                isVariationRunning = false,
                isMaintenanceBusy = false
            )
        )
    }

    @Test
    fun evaluatePermissions_returnsConsistentSnapshot() {
        val permissions = AutomationExecutionPolicy.evaluatePermissions(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment(),
            targetApp = AutomationTargetApp.GEMINI,
            promptTemplate = "test prompt",
            isRunning = false,
            remoteAutomationStatus = RemoteAutomationStatus()
        )

        assertTrue(permissions.canRun)
        assertTrue(permissions.canRunVariation)
        assertTrue(permissions.canInteractWithVariation)
        assertNull(permissions.variationUnavailableReason)
        assertTrue(permissions.canCloseGemini)
        assertTrue(permissions.canCloseSelfApp)
        assertTrue(permissions.canCleanMemory)
    }
}
