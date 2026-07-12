package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.GeminiAppControlBlockReason
import com.example.gemgemgen.automation.domain.SelfAppControlBlockReason
import com.example.gemgemgen.automation.ui.AutomationUiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationUiTextTest {
    @Test
    fun accessibilityPrompt_mentionsServiceName() {
        assertTrue(
            AutomationUiText.accessibilityPromptMessage().contains("GemGemGen 자동화")
        )
        assertEquals("접근성 서비스 필요", AutomationUiText.accessibilityPromptTitle())
    }

    @Test
    fun statusText_showsStoppedState() {
        assertEquals(
            "자동화 중지",
            AutomationUiText.statusText(AutomationRunState.Stopped)
        )
    }

    @Test
    fun geminiUnavailableMessages_mapBlockReasons() {
        assertEquals(
            "자동화 중에는 Gemini를 재시작할 수 없습니다.",
            AutomationUiText.geminiRestartUnavailableMessage(
                GeminiAppControlBlockReason.AutomationRunning
            )
        )
        assertEquals(
            "접근성 서비스를 먼저 켜주세요.",
            AutomationUiText.geminiTerminateUnavailableMessage(
                GeminiAppControlBlockReason.AccessibilityDisabled
            )
        )
    }

    @Test
    fun selfAppUnavailableMessages_mapBlockReasons() {
        assertEquals(
            "자동화 중에는 앱을 종료할 수 없습니다.",
            AutomationUiText.selfAppTerminateUnavailableMessage(
                SelfAppControlBlockReason.AutomationRunning
            )
        )
        assertEquals(
            "접근성 서비스를 먼저 켜주세요.",
            AutomationUiText.selfAppTerminateUnavailableMessage(
                SelfAppControlBlockReason.AccessibilityDisabled
            )
        )
    }
}
