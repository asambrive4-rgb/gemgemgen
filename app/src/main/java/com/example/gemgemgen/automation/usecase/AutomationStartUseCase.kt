// 역할: 자동화 시작 전제 조건을 점검하고 실제 실행 작업을 트리거합니다.
package com.example.gemgemgen.automation.usecase

fun interface OverlayPermissionGateway {
    fun isGranted(): Boolean
}

sealed interface AutomationStartDecision {
    data object Started : AutomationStartDecision
    data object RemoteStarted : AutomationStartDecision
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
