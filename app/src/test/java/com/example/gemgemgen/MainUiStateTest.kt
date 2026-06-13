package com.example.gemgemgen

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

    private fun readyEnvironment(): EnvironmentStatus {
        return EnvironmentStatus(
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true,
            hasWriteSecureSettingsPermission = true,
            isWildcardDirectoryAccessible = true
        )
    }
}
