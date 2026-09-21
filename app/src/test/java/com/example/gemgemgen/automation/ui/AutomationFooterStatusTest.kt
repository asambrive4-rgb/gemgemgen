// 역할: 화면 최하단 통합 안내 문구의 우선순위 결정 로직을 검증합니다.
package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationFooterStatusTest {

    @Test
    fun resolveFooterStatus_prioritizesMaintenanceMessageOverOthers() {
        val result = resolveFooterStatus(
            maintenanceMessage = "메모리 정리 중...",
            variationAutomationState = AutomationRunState.Running(step = "변주 생성 중"),
            automationMode = AutomationMode.SENDER,
            remoteStatus = RemoteAutomationStatus(connectionMessage = "S25 FE를 찾는 중입니다.")
        )

        assertNotNull(result)
        assertEquals("메모리 정리 중...", result?.text)
        assertFalse(result?.isError ?: true)
    }

    @Test
    fun resolveFooterStatus_prioritizesVariationStateWhenNoMaintenanceMessage() {
        val result = resolveFooterStatus(
            maintenanceMessage = "",
            variationAutomationState = AutomationRunState.Running(step = "변주 프롬프트 생성 중"),
            automationMode = AutomationMode.SENDER,
            remoteStatus = RemoteAutomationStatus(connectionMessage = "S25 FE를 찾는 중입니다.")
        )

        assertNotNull(result)
        assertEquals("변주 프롬프트 생성 중", result?.text)
        assertFalse(result?.isError ?: true)
    }

    @Test
    fun resolveFooterStatus_marksErrorOnVariationFailure() {
        val result = resolveFooterStatus(
            maintenanceMessage = "",
            variationAutomationState = AutomationRunState.Failure("API 오류"),
            automationMode = AutomationMode.NORMAL,
            remoteStatus = RemoteAutomationStatus()
        )

        assertNotNull(result)
        assertEquals("변주 실패: API 오류", result?.text)
        assertTrue(result?.isError ?: false)
    }

    @Test
    fun resolveFooterStatus_showsRemoteConnectionWhenNoMaintenanceOrVariation() {
        val result = resolveFooterStatus(
            maintenanceMessage = "",
            variationAutomationState = AutomationRunState.Idle,
            automationMode = AutomationMode.SENDER,
            remoteStatus = RemoteAutomationStatus(connectionMessage = "")
        )

        assertNotNull(result)
        assertEquals("S25 FE를 찾는 중입니다.", result?.text)
        assertFalse(result?.isError ?: true)
    }

    @Test
    fun resolveFooterStatus_returnsNullInNormalModeWithoutActiveMessages() {
        val result = resolveFooterStatus(
            maintenanceMessage = "",
            variationAutomationState = AutomationRunState.Idle,
            automationMode = AutomationMode.NORMAL,
            remoteStatus = RemoteAutomationStatus()
        )

        assertNull(result)
    }
}
