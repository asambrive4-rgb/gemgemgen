// 역할: 자동화 실행 전 오버레이 권한 및 실행 가능 상태를 사전 판별합니다.
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
    ): AutomationStartDecision = when {
        !overlayPermissionGateway.isGranted() -> AutomationStartDecision.PermissionRequired
        canRun && !isStartInProgress -> AutomationStartDecision.Started
        else -> AutomationStartDecision.Rejected
    }
}
