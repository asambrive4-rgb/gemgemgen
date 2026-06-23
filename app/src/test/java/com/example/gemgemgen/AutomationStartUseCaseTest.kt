package com.example.gemgemgen

import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.OverlayPermissionGateway
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationStartUseCaseTest {
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
