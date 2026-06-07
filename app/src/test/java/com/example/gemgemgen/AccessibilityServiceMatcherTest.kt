package com.example.gemgemgen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceMatcherTest {
    @Test
    fun containsService_matchesFullClassName() {
        assertTrue(
            AccessibilityServiceMatcher.containsService(
                enabledServices = "com.example.gemgemgen/com.example.gemgemgen.GeminiAccessibilityService",
                expectedPackageName = "com.example.gemgemgen",
                expectedClassName = "com.example.gemgemgen.GeminiAccessibilityService"
            )
        )
    }

    @Test
    fun containsService_matchesShortClassName() {
        assertTrue(
            AccessibilityServiceMatcher.containsService(
                enabledServices = "com.example.gemgemgen/.GeminiAccessibilityService",
                expectedPackageName = "com.example.gemgemgen",
                expectedClassName = "com.example.gemgemgen.GeminiAccessibilityService"
            )
        )
    }

    @Test
    fun containsService_ignoresDifferentService() {
        assertFalse(
            AccessibilityServiceMatcher.containsService(
                enabledServices = "other.package/.OtherService",
                expectedPackageName = "com.example.gemgemgen",
                expectedClassName = "com.example.gemgemgen.GeminiAccessibilityService"
            )
        )
    }
}
