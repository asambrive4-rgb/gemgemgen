package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase

class ExecuteAutomationUseCase(
    private val startAutomation: StartAutomationUseCase,
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase,
    private val promptHistoryStore: PromptHistoryStore? = null
) {
    fun decideStart(
        canRun: Boolean,
        isStartInProgress: Boolean,
        mode: AutomationMode
    ): AutomationStartDecision {
        return when (mode) {
            AutomationMode.RECEIVER -> AutomationStartDecision.Rejected
            AutomationMode.SENDER -> {
                if (canRun && !isStartInProgress) {
                    AutomationStartDecision.RemoteStarted
                } else {
                    AutomationStartDecision.Rejected
                }
            }
            AutomationMode.NORMAL -> {
                startAutomation.decideStart(
                    canRun = canRun,
                    isStartInProgress = isStartInProgress
                )
            }
        }
    }

    suspend fun executeRemote(
        request: AutomationRunRequest,
        onStateChange: (AutomationRunState) -> Unit
    ): RemoteActionResult {
        promptHistoryStore?.record(request.promptTemplate, request.targetApp)
        return manageRemoteAutomation.start(request, onStateChange)
    }

    suspend fun executeLocal(request: AutomationRunRequest) {
        startAutomation.start(request)
    }

    fun cancel(
        mode: AutomationMode,
        isRemoteRunActive: Boolean,
        isPreparationActive: Boolean,
        onStateChange: (AutomationRunState) -> Unit,
        onCancelLocal: () -> Unit
    ) {
        if (mode == AutomationMode.SENDER || isRemoteRunActive) {
            manageRemoteAutomation.forceStop(onStateChange)
            return
        }
        if (isPreparationActive) {
            onStateChange(AutomationRunState.Stopped)
            return
        }
        onCancelLocal()
    }
}
