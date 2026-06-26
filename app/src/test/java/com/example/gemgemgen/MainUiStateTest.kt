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

class MainUiStateTest {
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
