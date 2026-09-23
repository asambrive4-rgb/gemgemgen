// 역할: 자동화 실행 전 오버레이 권한 및 도메인 비즈니스 불변식을 사전 판별하여 시작 여부를 결정합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus

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
    /**
     * 환경 상태, 대상 앱, 프롬프트 등 도메인 상태를 직접 전달받아 비즈니스 불변식을 평가하고 시작 결정을 내립니다.
     */
    fun decide(
        environmentStatus: EnvironmentStatus,
        targetApp: AutomationTargetApp,
        promptTemplate: String,
        isRunning: Boolean = false,
        isVariationRunning: Boolean = false,
        isMaintenanceBusy: Boolean = false,
        isStartInProgress: Boolean = false
    ): AutomationStartDecision {
        if (!overlayPermissionGateway.isGranted()) {
            return AutomationStartDecision.PermissionRequired
        }
        val canRun = AutomationExecutionPolicy.canRun(
            mode = AutomationMode.NORMAL,
            environmentStatus = environmentStatus,
            targetApp = targetApp,
            promptTemplate = promptTemplate,
            isRunning = isRunning,
            remoteAutomationStatus = RemoteAutomationStatus(),
            isVariationRunning = isVariationRunning,
            isMaintenanceBusy = isMaintenanceBusy
        )
        return if (canRun && !isStartInProgress) {
            AutomationStartDecision.Started
        } else {
            AutomationStartDecision.Rejected
        }
    }

    /**
     * 외부에서 계산된 플래그를 수신하는 오버로딩 (기존 테스트 및 호출처와의 하위 호환성 유지).
     */
    fun decide(
        canRun: Boolean,
        isStartInProgress: Boolean
    ): AutomationStartDecision = when {
        !overlayPermissionGateway.isGranted() -> AutomationStartDecision.PermissionRequired
        canRun && !isStartInProgress -> AutomationStartDecision.Started
        else -> AutomationStartDecision.Rejected
    }
}
