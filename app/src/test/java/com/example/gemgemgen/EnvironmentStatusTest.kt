package com.example.gemgemgen

import com.example.gemgemgen.automation.android.*
import com.example.gemgemgen.automation.domain.*
import com.example.gemgemgen.automation.usecase.*
import com.example.gemgemgen.core.*
import com.example.gemgemgen.environment.android.*
import com.example.gemgemgen.environment.domain.*
import com.example.gemgemgen.environment.usecase.*
import com.example.gemgemgen.ui.*
import com.example.gemgemgen.wildcard.domain.*
import com.example.gemgemgen.wildcard.usecase.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentStatusTest {
    @Test
    fun isReady_isTrueOnlyWhenEveryEnvironmentStateIsReady() {
        assertTrue(
            EnvironmentStatus(
                isGeminiInstalled = true,
                isChatGptInstalled = true,
                isAccessibilityServiceEnabled = true,
                hasWriteSecureSettingsPermission = true,
                isWildcardDirectoryAccessible = true
            ).isReadyFor(AutomationTargetApp.GEMINI)
        )

        assertFalse(
            EnvironmentStatus(
                isGeminiInstalled = true,
                isChatGptInstalled = true,
                isAccessibilityServiceEnabled = true,
                hasWriteSecureSettingsPermission = true,
                isWildcardDirectoryAccessible = false
            ).isReadyFor(AutomationTargetApp.GEMINI)
        )
    }

    @Test
    fun isReadyFor_usesSelectedTargetAppInstallation() {
        val status = EnvironmentStatus(
            isGeminiInstalled = true,
            isChatGptInstalled = false,
            isAccessibilityServiceEnabled = true,
            hasWriteSecureSettingsPermission = true,
            isWildcardDirectoryAccessible = true
        )

        assertTrue(status.isReadyFor(AutomationTargetApp.GEMINI))
        assertFalse(status.isReadyFor(AutomationTargetApp.CHATGPT))
    }

    @Test
    fun wildcardEditPermission_isSeparateFromAutomationReadPermission() {
        val readOnlyStatus = EnvironmentStatus(
            isWildcardDirectoryAccessible = true,
            isWildcardDirectoryWritable = false
        )
        val editableStatus = EnvironmentStatus(
            isWildcardDirectoryAccessible = true,
            isWildcardDirectoryWritable = true
        )

        assertTrue(readOnlyStatus.isWildcardDirectoryAccessible)
        assertFalse(readOnlyStatus.canEditWildcardFiles)
        assertTrue(editableStatus.canEditWildcardFiles)
    }
}
