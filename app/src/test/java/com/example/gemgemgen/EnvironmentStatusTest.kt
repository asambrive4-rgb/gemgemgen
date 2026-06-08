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
