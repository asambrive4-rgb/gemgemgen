// 역할: 준비된 프롬프트 목록을 순차적으로 대상 앱에 자동 입력하고 전송하는 실행을 담당합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase

class ExecuteAutomationUseCase(
    private val checkAutomationStart: CheckAutomationStartUseCase,
    private val automationStartRecorder: AutomationStartRecorder,
    private val automation: RunAutomationUseCase,
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase,
    private val promptHistoryStore: PromptHistoryStore? = null
) {
    /** OverlayPermissionGateway 직접 주입을 위한 편의 생성자 */
    constructor(
        overlayPermissionGateway: OverlayPermissionGateway,
        automationStartRecorder: AutomationStartRecorder,
        automation: RunAutomationUseCase,
        manageRemoteAutomation: ManageRemoteAutomationUseCase,
        promptHistoryStore: PromptHistoryStore? = null
    ) : this(
        checkAutomationStart = CheckAutomationStartUseCase(overlayPermissionGateway),
        automationStartRecorder = automationStartRecorder,
        automation = automation,
        manageRemoteAutomation = manageRemoteAutomation,
        promptHistoryStore = promptHistoryStore
    )

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
        automationStartRecorder.record(request)
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
