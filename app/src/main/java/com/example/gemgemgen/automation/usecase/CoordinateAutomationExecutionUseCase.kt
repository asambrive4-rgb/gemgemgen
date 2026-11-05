// 역할: 자동화 실행 전 오버레이 권한·도메인 불변식을 사전 판별하고 로컬/원격 자동화 실행 경로를 조율합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationStartDecision
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptExperimentItem
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.automation.domain.AutomationMode
import com.example.gemgemgen.automation.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus

fun interface OverlayPermissionGateway {
    fun isGranted(): Boolean
}

class CoordinateAutomationExecutionUseCase(
    private val overlayPermissionGateway: OverlayPermissionGateway = OverlayPermissionGateway { true },
    private val automationHistoryRecorder: AutomationHistoryRecorder = AutomationHistoryRecorder {},
    private val automation: ExecuteAutomationLoopUseCase,
    private val remoteAutomationSenderGateway: RemoteAutomationSenderGateway
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
        isStartInProgress: Boolean = false,
        isExperimentMode: Boolean = false,
        selectedExperimentItem: PromptExperimentItem? = null
    ): AutomationStartDecision = AutomationExecutionPolicy.decideStart(
        mode = mode,
        environmentStatus = environmentStatus,
        targetApp = targetApp,
        promptTemplate = promptTemplate,
        isRunning = isRunning,
        remoteAutomationStatus = remoteAutomationStatus,
        isVariationRunning = isVariationRunning,
        isMaintenanceBusy = isMaintenanceBusy,
        isStartInProgress = isStartInProgress,
        isExperimentMode = isExperimentMode,
        selectedExperimentItem = selectedExperimentItem,
        isOverlayGranted = overlayPermissionGateway.isGranted()
    )

    suspend fun executeRemote(
        request: AutomationRunRequest
    ): RemoteActionResult = remoteAutomationSenderGateway.start(request)

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
            remoteAutomationSenderGateway.forceStop()
            onStateChange(AutomationRunState.Stopped)
        }
        isPreparationActive -> {
            onStateChange(AutomationRunState.Stopped)
        }
        else -> {
            onCancelLocal()
        }
    }
}
