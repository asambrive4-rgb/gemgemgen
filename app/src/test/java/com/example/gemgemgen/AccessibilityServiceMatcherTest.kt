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
