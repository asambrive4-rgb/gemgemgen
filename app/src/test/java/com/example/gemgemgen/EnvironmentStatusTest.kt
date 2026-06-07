package com.example.gemgemgen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentStatusTest {
    @Test
    fun isReady_isTrueOnlyWhenEveryEnvironmentStateIsReady() {
        assertTrue(
            EnvironmentStatus(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                hasWriteSecureSettingsPermission = true,
                isWildcardDirectoryAccessible = true
            ).isReady
        )

        assertFalse(
            EnvironmentStatus(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                hasWriteSecureSettingsPermission = true,
                isWildcardDirectoryAccessible = false
            ).isReady
        )
    }
}
