// 역할: 자동화 화면의 안내 문구 변환 로직을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.GeminiAppControlBlockReason
import com.example.gemgemgen.automation.domain.SelfAppControlBlockReason
import com.example.gemgemgen.automation.ui.AutomationUiState
import com.example.gemgemgen.automation.ui.AutomationUiText
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationUiTextTest {
    @Test
    fun accessibilityPrompt_mentionsServiceName() {
        assertTrue(
            AutomationUiText.ACCESSIBILITY_PROMPT_MESSAGE.contains("GemGemGen 자동화")
        )
        assertEquals("접근성 서비스 필요", AutomationUiText.ACCESSIBILITY_PROMPT_TITLE)
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
            AutomationUiText.geminiRestartUnavailableMessage(
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

    @Test
    fun unavailableMessages_withUiState_blocksWhenVariationIsRunning() {
        val runningState = AutomationUiState(
            environmentStatus = EnvironmentStatus(
                isAccessibilityServiceEnabled = true,
                isGeminiInstalled = true
            ),
            variationAutomationState = AutomationRunState.Running("변주 중")
        )
        assertEquals(
            "자동화 중에는 Gemini를 재시작할 수 없습니다.",
            AutomationUiText.geminiRestartUnavailableMessage(runningState)
        )
        assertEquals(
            "자동화 중에는 앱을 종료할 수 없습니다.",
            AutomationUiText.selfAppTerminateUnavailableMessage(runningState)
        )
    }

    @Test
    fun memoryCleanupScheduled_messagesIncludeScheduleKeywords() {
        assertTrue(
            AutomationUiText.MEMORY_CLEANUP_SCHEDULED_TEXT.contains("예약")
        )
        assertTrue(
            AutomationUiText.MEMORY_CLEANUP_SCHEDULE_CANCELED_TEXT.contains("취소")
        )
    }
}
