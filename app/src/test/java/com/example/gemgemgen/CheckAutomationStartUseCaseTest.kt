// 역할: 자동화 실행 전 오버레이 권한 및 도메인 비즈니스 불변식을 사전 판별하는 AutomationExecutionPolicy.decideStart 순수 함수 동작을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
import com.example.gemgemgen.automation.domain.AutomationStartDecision
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.automation.domain.AutomationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckAutomationStartUseCaseTest {

    private fun readyEnvironment(): EnvironmentStatus = EnvironmentStatus(
        isGeminiInstalled = true,
        isChatGptInstalled = true,
        isAccessibilityServiceEnabled = true,
        hasWriteSecureSettingsPermission = true,
        isWildcardDirectoryAccessible = true
    )

    @Test
    fun decide_withDomainContext_requiresOverlayPermission() {
        val decision = AutomationExecutionPolicy.decideStart(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment(),
            targetApp = AutomationTargetApp.GEMINI,
            promptTemplate = "test prompt",
            isOverlayGranted = false
        )

        assertEquals(AutomationStartDecision.PermissionRequired, decision)
    }

    @Test
    fun decide_withDomainContext_startsWhenAllInvariantsSatisfied() {
        val decision = AutomationExecutionPolicy.decideStart(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment(),
            targetApp = AutomationTargetApp.GEMINI,
            promptTemplate = "test prompt",
            isOverlayGranted = true
        )

        assertEquals(AutomationStartDecision.Started, decision)
    }

    @Test
    fun decide_withDomainContext_rejectsWhenInvariantsViolated() {
        // 빈 프롬프트
        assertEquals(
            AutomationStartDecision.Rejected,
            AutomationExecutionPolicy.decideStart(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "",
                isOverlayGranted = true
            )
        )

        // 이미 실행 중
        assertEquals(
            AutomationStartDecision.Rejected,
            AutomationExecutionPolicy.decideStart(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isRunning = true,
                isOverlayGranted = true
            )
        )

        // 시작 준비 진행 중 (StartInProgress)
        assertEquals(
            AutomationStartDecision.Rejected,
            AutomationExecutionPolicy.decideStart(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isStartInProgress = true,
                isOverlayGranted = true
            )
        )

        // 유지보수 진행 중
        assertEquals(
            AutomationStartDecision.Rejected,
            AutomationExecutionPolicy.decideStart(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isMaintenanceBusy = true,
                isOverlayGranted = true
            )
        )
    }
}
