// 역할: 도메인 비즈니스 불변식 검사 및 로컬/원격 자동화 실행 경로를 분기하고 조율합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase

class CoordinateAutomationExecutionUseCase(
    private val checkAutomationStart: CheckAutomationStartUseCase,
    private val automationHistoryRecorder: AutomationHistoryRecorder,
    private val automation: ExecuteAutomationLoopUseCase,
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase,
    private val promptHistoryStore: PromptHistoryStore? = null
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

    /**
     * 외부에서 계산된 플래그를 수신하는 오버로딩 (기존 테스트 및 호출처와의 하위 호환성 유지).
     */
    fun decideStart(
        canRun: Boolean,
        isStartInProgress: Boolean,
        mode: AutomationMode
    ): AutomationStartDecision = when (mode) {
        AutomationMode.RECEIVER -> AutomationStartDecision.Rejected
        AutomationMode.SENDER -> if (canRun && !isStartInProgress) AutomationStartDecision.RemoteStarted else AutomationStartDecision.Rejected
        AutomationMode.NORMAL -> checkAutomationStart.decide(canRun = canRun, isStartInProgress = isStartInProgress)
    }

    suspend fun executeRemote(
        request: AutomationRunRequest,
        onStateChange: (AutomationRunState) -> Unit
    ): RemoteActionResult {
        promptHistoryStore?.record(request.promptTemplate, request.targetApp)
        return manageRemoteAutomation.start(request, onStateChange)
    }

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
        mode == AutomationMode.SENDER || isRemoteRunActive -> manageRemoteAutomation.forceStop(onStateChange)
        isPreparationActive -> onStateChange(AutomationRunState.Stopped)
        else -> onCancelLocal()
    }
}
