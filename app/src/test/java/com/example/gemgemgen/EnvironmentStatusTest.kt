package com.example.gemgemgen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentStatusTest {
    @Test
    fun isReadyToStart_isTrueOnlyWhenEveryRequiredStateIsReady() {
        assertTrue(
            EnvironmentStatus(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                hasWriteSecureSettingsPermission = true,
                isWildcardDirectoryAccessible = true,
                hasPromptTemplate = true
            ).isReadyToStart
        )

        assertFalse(
            EnvironmentStatus(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                hasWriteSecureSettingsPermission = true,
                isWildcardDirectoryAccessible = true,
                hasPromptTemplate = false
            ).isReadyToStart
        )
    }
}
