// 역할: 자동화 메인 화면 UI 상태의 기본값, 예약·송신 단독 계정 전환 가용성 및 파생 상태 계산을 검증합니다.
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
import com.example.gemgemgen.automation.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.usage.domain.GeminiQuotaExhaustionReason
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

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
    fun canCleanMemory_requiresAccessibilityAndNotMaintenanceBusy() {
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
        // 자동화 실행 중에도 메모리 정리 예약이 가능하므로 canCleanMemory는 true입니다.
        assertTrue(
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

    @Test
    fun geminiSidebarOpenMode_defaultsToFalse() {
        assertFalse(AutomationUiState().geminiSidebarOpenMode)
    }

    @Test
    fun reserveAccountBlockReason_and_canSwitchPendingSenderAccount_reflectPolicy() {
        val pairedStatus = RemoteAutomationStatus(
            mode = AutomationMode.SENDER,
            discoveredDeviceName = "S25 FE",
            isPaired = true
        )
        val readySenderState = AutomationUiState(
            promptTemplate = "prompt",
            selectedTargetApp = AutomationTargetApp.GEMINI,
            automationMode = AutomationMode.SENDER,
            remoteAutomationStatus = pairedStatus,
            environmentStatus = readyEnvironment(),
            automationState = AutomationRunState.Running("수신 기기 생성 중"),
            pendingSenderAccountSwitch = PendingSenderAccountSwitch(
                accountId = "acc_2",
                email = "b@gmail.com",
                alias = "서브2"
            )
        )

        assertTrue(readySenderState.canSwitchPendingSenderAccount)
        assertNull(readySenderState.senderLocalSwitchUnavailableReason)
        assertNotNull(readySenderState.reserveAccountBlockReason(exhaustionReason = null))
        assertNull(
            readySenderState.copy(automationState = AutomationRunState.Idle)
                .reserveAccountBlockReason(exhaustionReason = null)
        )
        assertNotNull(
            readySenderState.copy(
                automationState = AutomationRunState.Idle,
                selectedTargetApp = AutomationTargetApp.FLOW
            ).reserveAccountBlockReason(exhaustionReason = null)
        )
        assertNotNull(
            readySenderState.copy(automationState = AutomationRunState.Idle)
                .reserveAccountBlockReason(exhaustionReason = GeminiQuotaExhaustionReason.MANUAL_EXHAUSTED)
        )
        assertFalse(
            readySenderState.copy(
                environmentStatus = readyEnvironment().copy(isAccessibilityServiceEnabled = false)
            ).canSwitchPendingSenderAccount
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
