// 역할: 자동화 실행 전 오버레이 권한 및 도메인 비즈니스 불변식을 사전 판별하는 CheckAutomationStartUseCase 동작을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.OverlayPermissionGateway
import com.example.gemgemgen.environment.domain.EnvironmentStatus
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
        val useCase = CheckAutomationStartUseCase(
            OverlayPermissionGateway { false }
        )

        val decision = useCase.decide(
            environmentStatus = readyEnvironment(),
            targetApp = AutomationTargetApp.GEMINI,
            promptTemplate = "test prompt"
        )

        assertEquals(AutomationStartDecision.PermissionRequired, decision)
    }

    @Test
    fun decide_withDomainContext_startsWhenAllInvariantsSatisfied() {
        val useCase = CheckAutomationStartUseCase(
            OverlayPermissionGateway { true }
        )

        val decision = useCase.decide(
            environmentStatus = readyEnvironment(),
            targetApp = AutomationTargetApp.GEMINI,
            promptTemplate = "test prompt"
        )

        assertEquals(AutomationStartDecision.Started, decision)
    }

    @Test
    fun decide_withDomainContext_rejectsWhenInvariantsViolated() {
        val useCase = CheckAutomationStartUseCase(
            OverlayPermissionGateway { true }
        )

        // 빈 프롬프트
        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decide(
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = ""
            )
        )

        // 이미 실행 중
        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decide(
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isRunning = true
            )
        )

        // 시작 준비 진행 중 (StartInProgress)
        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decide(
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isStartInProgress = true
            )
        )

        // 유지보수 진행 중
        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decide(
                environmentStatus = readyEnvironment(),
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isMaintenanceBusy = true
            )
        )
    }

    @Test
    fun decide_requiresOverlayPermissionBeforeStarting() {
        val useCase = CheckAutomationStartUseCase(
            OverlayPermissionGateway { false }
        )

        assertEquals(
            AutomationStartDecision.PermissionRequired,
            useCase.decide(canRun = true, isStartInProgress = false)
        )
    }

    @Test
    fun decide_startsOnlyWhenRequirementsAreReadyAndNoStartIsInProgress() {
        val useCase = CheckAutomationStartUseCase(
            OverlayPermissionGateway { true }
        )

        assertEquals(
            AutomationStartDecision.Started,
            useCase.decide(canRun = true, isStartInProgress = false)
        )
        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decide(canRun = false, isStartInProgress = false)
        )
        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decide(canRun = true, isStartInProgress = true)
        )
    }
}
