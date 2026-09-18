// 역할: 시작 조건 검사 및 로컬/원격 자동화 실행 경로를 분기하고 조율합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase

class CoordinateAutomationExecutionUseCase(
    private val checkAutomationStart: CheckAutomationStartUseCase,
    private val automationHistoryRecorder: AutomationHistoryRecorder,
    private val automation: ExecuteAutomationLoopUseCase,
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase,
    private val promptHistoryStore: PromptHistoryStore? = null
) {
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
