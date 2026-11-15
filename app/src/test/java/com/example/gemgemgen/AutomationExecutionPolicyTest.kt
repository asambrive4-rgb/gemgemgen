// 역할: 자동화 실행, 변주, 앱 제어, 메모리 정리, Gemini 계정 전환 및 송신 기기 대기·수신 기기 선행(예약) 생성 인가 정책의 도메인 규칙을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptExperimentItem
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.automation.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.usage.domain.GeminiQuotaExhaustionReason
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
    fun reservePreGenerationUnavailableReason_validatesSenderGeminiAccountConnectionAndPrompt() {
        val pairedStatus = RemoteAutomationStatus(
            mode = AutomationMode.SENDER,
            discoveredDeviceName = "S25 FE",
            isPaired = true
        )

        assertNull(
            AutomationExecutionPolicy.reservePreGenerationUnavailableReason(
                mode = AutomationMode.SENDER,
                targetApp = AutomationTargetApp.GEMINI,
                remoteAutomationStatus = pairedStatus,
                promptTemplate = "portrait prompt",
                isRunning = false
            )
        )
        assertTrue(
            AutomationExecutionPolicy.canReservePreGeneration(
                mode = AutomationMode.SENDER,
                targetApp = AutomationTargetApp.GEMINI,
                remoteAutomationStatus = pairedStatus,
                promptTemplate = "portrait prompt",
                isRunning = false
            )
        )

        assertNotNull(
            AutomationExecutionPolicy.reservePreGenerationUnavailableReason(
                mode = AutomationMode.NORMAL,
                targetApp = AutomationTargetApp.GEMINI,
                remoteAutomationStatus = pairedStatus,
                promptTemplate = "portrait prompt",
                isRunning = false
            )
        )
        val nonGeminiReason = AutomationExecutionPolicy.reservePreGenerationUnavailableReason(
            mode = AutomationMode.SENDER,
            targetApp = AutomationTargetApp.FLOW,
            remoteAutomationStatus = pairedStatus,
            promptTemplate = "portrait prompt",
            isRunning = false
        )
        assertTrue(nonGeminiReason?.contains("Gemini") == true)

        assertNotNull(
            AutomationExecutionPolicy.reservePreGenerationUnavailableReason(
                mode = AutomationMode.SENDER,
                targetApp = AutomationTargetApp.GEMINI,
                remoteAutomationStatus = pairedStatus,
                promptTemplate = "portrait prompt",
                isRunning = false,
                exhaustionReason = GeminiQuotaExhaustionReason.FIVE_HOUR_EXHAUSTED
            )
        )
        val suspendedReason = AutomationExecutionPolicy.reservePreGenerationUnavailableReason(
            mode = AutomationMode.SENDER,
            targetApp = AutomationTargetApp.GEMINI,
            remoteAutomationStatus = pairedStatus,
            promptTemplate = "portrait prompt",
            isRunning = false,
            exhaustionReason = GeminiQuotaExhaustionReason.SUSPENDED
        )
        assertTrue(suspendedReason?.contains("사용 금지") == true)

        assertNotNull(
            AutomationExecutionPolicy.reservePreGenerationUnavailableReason(
                mode = AutomationMode.SENDER,
                targetApp = AutomationTargetApp.GEMINI,
                remoteAutomationStatus = RemoteAutomationStatus(),
                promptTemplate = "portrait prompt",
                isRunning = false
            )
        )
        assertNotNull(
            AutomationExecutionPolicy.reservePreGenerationUnavailableReason(
                mode = AutomationMode.SENDER,
                targetApp = AutomationTargetApp.GEMINI,
                remoteAutomationStatus = pairedStatus,
                promptTemplate = "portrait prompt",
                isRunning = true
            )
        )
        assertNotNull(
            AutomationExecutionPolicy.reservePreGenerationUnavailableReason(
                mode = AutomationMode.SENDER,
                targetApp = AutomationTargetApp.GEMINI,
                remoteAutomationStatus = pairedStatus,
                promptTemplate = "   ",
                isRunning = false
            )
        )
        assertNotNull(
            AutomationExecutionPolicy.reservePreGenerationUnavailableReason(
                mode = AutomationMode.SENDER,
                targetApp = AutomationTargetApp.GEMINI,
                remoteAutomationStatus = pairedStatus,
                promptTemplate = "portrait without tag",
                isRunning = false,
                isExperimentMode = true,
                selectedExperimentItem = PromptExperimentItem(id = "1", content = "style")
            )
        )
    }

    @Test
    fun canSwitchSenderLocalAccount_allowsRunningRemoteAutomation_andChecksLocalPermissions() {
        assertTrue(
            AutomationExecutionPolicy.canSwitchSenderLocalAccount(
                mode = AutomationMode.SENDER,
                environmentStatus = readyEnvironment(),
                isVariationRunning = false,
                isMaintenanceBusy = false
            )
        )
        assertFalse(
            AutomationExecutionPolicy.canSwitchSenderLocalAccount(
                mode = AutomationMode.SENDER,
                environmentStatus = readyEnvironment().copy(isAccessibilityServiceEnabled = false)
            )
        )
        assertFalse(
            AutomationExecutionPolicy.canSwitchSenderLocalAccount(
                mode = AutomationMode.SENDER,
                environmentStatus = readyEnvironment().copy(isGeminiInstalled = false)
            )
        )
        assertFalse(
            AutomationExecutionPolicy.canSwitchSenderLocalAccount(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment()
            )
        )
    }
}
