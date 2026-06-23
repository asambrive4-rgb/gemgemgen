package com.example.gemgemgen.automation.usecase

fun interface OverlayPermissionGateway {
    fun isGranted(): Boolean
}

sealed interface AutomationStartDecision {
    data object Started : AutomationStartDecision
    data object PermissionRequired : AutomationStartDecision
    data object Rejected : AutomationStartDecision
}

class CheckAutomationStartUseCase(
    private val overlayPermissionGateway: OverlayPermissionGateway
) {
    fun decide(
        canRun: Boolean,
        isStartInProgress: Boolean
    ): AutomationStartDecision {
        if (!overlayPermissionGateway.isGranted()) {
            return AutomationStartDecision.PermissionRequired
        }
        if (!canRun || isStartInProgress) {
            return AutomationStartDecision.Rejected
        }
        return AutomationStartDecision.Started
    }
}
