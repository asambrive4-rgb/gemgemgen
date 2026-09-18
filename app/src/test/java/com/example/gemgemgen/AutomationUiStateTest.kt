// 역할: 자동화 메인 화면 UI 상태의 기본값과 파생 상태 계산을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.*
import com.example.gemgemgen.automation.domain.*
import com.example.gemgemgen.automation.usecase.*
import com.example.gemgemgen.core.*
import com.example.gemgemgen.environment.android.*
import com.example.gemgemgen.environment.domain.*
import com.example.gemgemgen.environment.usecase.*
import com.example.gemgemgen.ui.*
import com.example.gemgemgen.automation.ui.*
import com.example.gemgemgen.wildcard.domain.*
import com.example.gemgemgen.wildcard.usecase.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus

class AutomationUiStateTest {
    @Test
    fun canRun_senderMode_usesPairedRemoteDeviceInsteadOfLocalEnvironment() {
        assertTrue(
            AutomationUiState(
                promptTemplate = "remote prompt",
                environmentStatus = EnvironmentStatus(),
                automationMode = AutomationMode.SENDER,
                remoteAutomationStatus = RemoteAutomationStatus(
                    mode = AutomationMode.SENDER,
                    discoveredDeviceName = "S25 FE",
                    isPaired = true
                )
            ).canRun
        )
    }

    @Test
    fun canRun_receiverMode_neverStartsFromLocalStartButton() {
        assertFalse(
            AutomationUiState(
                promptTemplate = "prompt",
                environmentStatus = readyEnvironment(),
                automationMode = AutomationMode.RECEIVER,
                remoteAutomationStatus = RemoteAutomationStatus(
                    mode = AutomationMode.RECEIVER,
                    isReceiverRunning = true
                )
            ).canRun
        )
    }

    @Test
    fun canRun_requiresReadyEnvironmentPromptAndNotRunning() {
        assertTrue(
            AutomationUiState(
                promptTemplate = "base prompt",
                environmentStatus = readyEnvironment()
            ).canRun
        )

        assertFalse(
            AutomationUiState(
                promptTemplate = "",
                environmentStatus = readyEnvironment()
            ).canRun
        )

        assertFalse(
            AutomationUiState(
                promptTemplate = "base prompt",
                environmentStatus = readyEnvironment(),
                automationState = AutomationRunState.Running("실행 중")
            ).canRun
        )

        assertFalse(
            AutomationUiState(
                promptTemplate = "base prompt",
                environmentStatus = readyEnvironment(),
                variationAutomationState = AutomationRunState.Running("변형 실행 중")
            ).canRun
        )
    }

    @Test
    fun canRun_usesSelectedTargetAppInstallationState() {
        val environment = readyEnvironment().copy(isChatGptInstalled = false)

        assertFalse(
            AutomationUiState(
                promptTemplate = "base prompt",
                selectedTargetApp = AutomationTargetApp.CHATGPT,
                environmentStatus = environment
            ).canRun
        )
    }

    @Test
    fun canCloseGemini_requiresGeminiAccessibilityAndIdleState() {
        assertTrue(
            AutomationUiState(environmentStatus = readyEnvironment()).canCloseGemini
        )

        assertFalse(
            AutomationUiState(
                environmentStatus = readyEnvironment().copy(isAccessibilityServiceEnabled = false)
            ).canCloseGemini
        )

        assertFalse(
            AutomationUiState(
                environmentStatus = readyEnvironment(),
                automationState = AutomationRunState.Running("실행 중")
            ).canCloseGemini
        )

        assertFalse(
            AutomationUiState(
                environmentStatus = readyEnvironment(),
                maintenanceState = MaintenanceState(isBusy = true)
            ).canCloseGemini
        )

        assertFalse(
            AutomationUiState(
                environmentStatus = readyEnvironment(),
                maintenanceState = MaintenanceState(isBusy = true)
            ).canCloseSelfApp
        )
    }

    @Test
    fun canCleanMemory_requiresAccessibilityAndNoConcurrentWork() {
        assertTrue(
            AutomationUiState(environmentStatus = readyEnvironment()).canCleanMemory
        )

        assertFalse(
            AutomationUiState(
                environmentStatus = readyEnvironment().copy(
                    isAccessibilityServiceEnabled = false
                )
            ).canCleanMemory
        )
        assertFalse(
            AutomationUiState(
                environmentStatus = readyEnvironment(),
                automationState = AutomationRunState.Running("running")
            ).canCleanMemory
        )
        assertFalse(
            AutomationUiState(
                environmentStatus = readyEnvironment(),
                maintenanceState = MaintenanceState(isBusy = true)
            ).canCleanMemory
        )
    }

    @Test
    fun canCloseSelfApp_requiresAccessibilityAndIdleState_withoutGeminiInstall() {
        assertTrue(
            AutomationUiState(
                environmentStatus = readyEnvironment().copy(isGeminiInstalled = false)
            ).canCloseSelfApp
        )

        assertFalse(
            AutomationUiState(
                environmentStatus = readyEnvironment().copy(isAccessibilityServiceEnabled = false)
            ).canCloseSelfApp
        )

        assertFalse(
            AutomationUiState(
                environmentStatus = readyEnvironment(),
                automationState = AutomationRunState.Running("실행 중")
            ).canCloseSelfApp
        )

        assertFalse(
            AutomationUiState(
                environmentStatus = readyEnvironment(),
                maintenanceState = MaintenanceState(isBusy = true)
            ).canCloseSelfApp
        )
    }

    private fun readyEnvironment(): EnvironmentStatus {
        return EnvironmentStatus(
            isGeminiInstalled = true,
            isChatGptInstalled = true,
            isAccessibilityServiceEnabled = true,
            hasWriteSecureSettingsPermission = true,
            isWildcardDirectoryAccessible = true
        )
    }
}
