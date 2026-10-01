// 역할: 자동화 실행 전 오버레이 권한·도메인 불변식을 사전 판별하고 로컬/원격 자동화 실행 경로를 조율합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase

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
    private val overlayPermissionGateway: OverlayPermissionGateway = OverlayPermissionGateway { true }
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
}

class CoordinateAutomationExecutionUseCase(
    private val checkAutomationStart: CheckAutomationStartUseCase = CheckAutomationStartUseCase(),
    private val automationHistoryRecorder: AutomationHistoryRecorder = AutomationHistoryRecorder {},
    private val automation: ExecuteAutomationLoopUseCase,
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase
) {

    /**
     * 비즈니스 컨텍스트를 직접 전달받아 모드별 도메인 규칙을 직접 평가하고 자동화 시작 여부를 결정합니다.
     */
    fun decideStart(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        targetApp: AutomationTargetApp,
        promptTemplate: String,
        isRunning: Boolean = false,
        remoteAutomationStatus: RemoteAutomationStatus = RemoteAutomationStatus(),
        isVariationRunning: Boolean = false,
        isMaintenanceBusy: Boolean = false,
        isStartInProgress: Boolean = false
    ): AutomationStartDecision = when (mode) {
        AutomationMode.RECEIVER -> AutomationStartDecision.Rejected
        AutomationMode.SENDER -> {
            val canRunSender = AutomationExecutionPolicy.canRun(
                mode = AutomationMode.SENDER,
                environmentStatus = environmentStatus,
                targetApp = targetApp,
                promptTemplate = promptTemplate,
                isRunning = isRunning,
                remoteAutomationStatus = remoteAutomationStatus,
                isVariationRunning = isVariationRunning,
                isMaintenanceBusy = isMaintenanceBusy
            )
            if (canRunSender && !isStartInProgress) {
                AutomationStartDecision.RemoteStarted
            } else {
                AutomationStartDecision.Rejected
            }
        }
        AutomationMode.NORMAL -> checkAutomationStart.decide(
            environmentStatus = environmentStatus,
            targetApp = targetApp,
            promptTemplate = promptTemplate,
            isRunning = isRunning,
            isVariationRunning = isVariationRunning,
            isMaintenanceBusy = isMaintenanceBusy,
            isStartInProgress = isStartInProgress
        )
    }

    suspend fun executeRemote(
        request: AutomationRunRequest
    ): RemoteActionResult = manageRemoteAutomation.start(request)

    suspend fun executeLocal(request: AutomationRunRequest) {
        automationHistoryRecorder.record(request)
        automation.run(request)
    }

    fun cancel(
        mode: AutomationMode,
        isRemoteRunActive: Boolean,
        isPreparationActive: Boolean,
        onStateChange: (AutomationRunState) -> Unit,
        onCancelLocal: () -> Unit
    ) = when {
        mode == AutomationMode.SENDER || isRemoteRunActive -> {
            manageRemoteAutomation.forceStop()
            onStateChange(AutomationRunState.Stopped)
        }
        isPreparationActive -> onStateChange(AutomationRunState.Stopped)
        else -> onCancelLocal()
    }
}

