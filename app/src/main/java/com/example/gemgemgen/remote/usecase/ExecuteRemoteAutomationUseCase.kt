package com.example.gemgemgen.remote.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.RunAutomationUseCase
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteExecutionConditions
import com.example.gemgemgen.remote.domain.RemoteExecutionDecision

class ExecuteRemoteAutomationUseCase(
    private val checkExecution: CheckRemoteExecutionUseCase,
    private val automation: RunAutomationUseCase
) {
    suspend fun execute(
        request: RemoteAutomationRequest,
        conditions: RemoteExecutionConditions,
        onStateChange: (AutomationRunState) -> Unit
    ): RemoteExecutionDecision {
        val decision = checkExecution.decide(conditions)
        if (decision is RemoteExecutionDecision.Rejected) {
            onStateChange(AutomationRunState.Failure(decision.message))
            return decision
        }

        onStateChange(AutomationRunState.Running("S25 FE가 요청을 수락했습니다."))
        automation.run(
            request = AutomationRunRequest(
                promptTemplate = request.promptTemplate,
                repeatCountText = request.repeatCountText,
                targetApp = request.targetApp
            ),
            onStateChange = onStateChange
        )
        return RemoteExecutionDecision.Allowed
    }

    fun cancel() {
        automation.cancel()
    }
}
