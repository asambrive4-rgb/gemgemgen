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

class MainUiStateTest {
    @Test
    fun canRun_senderMode_usesPairedRemoteDeviceInsteadOfLocalEnvironment() {
        assertTrue(
            MainUiState(
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
            MainUiState(
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
            MainUiState(
                promptTemplate = "base prompt",
                environmentStatus = readyEnvironment()
            ).canRun
        )

        assertFalse(
            MainUiState(
                promptTemplate = "",
                environmentStatus = readyEnvironment()
            ).canRun
        )

        assertFalse(
            MainUiState(
                promptTemplate = "base prompt",
                environmentStatus = readyEnvironment(),
                automationState = AutomationRunState.Running("실행 중")
            ).canRun
        )
    }

    @Test
    fun canRun_usesSelectedTargetAppInstallationState() {
        val environment = readyEnvironment().copy(isChatGptInstalled = false)

        assertFalse(
            MainUiState(
                promptTemplate = "base prompt",
                selectedTargetApp = AutomationTargetApp.CHATGPT,
                environmentStatus = environment
            ).canRun
        )
    }

    @Test
    fun canCloseGemini_requiresGeminiAccessibilityAndIdleState() {
        assertTrue(
            MainUiState(environmentStatus = readyEnvironment()).canCloseGemini
        )

        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment().copy(isAccessibilityServiceEnabled = false)
            ).canCloseGemini
        )

        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment(),
                automationState = AutomationRunState.Running("실행 중")
            ).canCloseGemini
        )

        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment(),
                isClosingGemini = true
            ).canCloseGemini
        )

        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment(),
                isCleaningMemory = true
            ).canCloseGemini
        )
        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment(),
                isCleaningMemory = true
            ).canCloseSelfApp
        )
    }

    @Test
    fun canCleanMemory_requiresAccessibilityAndNoConcurrentWork() {
        assertTrue(
            MainUiState(environmentStatus = readyEnvironment()).canCleanMemory
        )

        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment().copy(
                    isAccessibilityServiceEnabled = false
                )
            ).canCleanMemory
        )
        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment(),
                automationState = AutomationRunState.Running("running")
            ).canCleanMemory
        )
        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment(),
                isCleaningMemory = true
            ).canCleanMemory
        )
        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment(),
                isClosingGemini = true
            ).canCleanMemory
        )
    }

    @Test
    fun canCloseSelfApp_requiresAccessibilityAndIdleState_withoutGeminiInstall() {
        assertTrue(
            MainUiState(
                environmentStatus = readyEnvironment().copy(isGeminiInstalled = false)
            ).canCloseSelfApp
        )

        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment().copy(isAccessibilityServiceEnabled = false)
            ).canCloseSelfApp
        )

        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment(),
                automationState = AutomationRunState.Running("실행 중")
            ).canCloseSelfApp
        )

        assertFalse(
            MainUiState(
                environmentStatus = readyEnvironment(),
                isClosingGemini = true
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
