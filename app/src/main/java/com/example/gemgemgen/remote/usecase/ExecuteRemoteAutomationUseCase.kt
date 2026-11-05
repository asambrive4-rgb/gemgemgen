// 역할: 수신된 원격 명령의 실행 가능 상태를 점검하고 기존 작업 선점 취소 및 대리 실행을 조율합니다.
package com.example.gemgemgen.remote.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.ExecuteAutomationLoopUseCase
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteExecutionConditions
import com.example.gemgemgen.remote.domain.RemoteExecutionDecision

class ExecuteRemoteAutomationUseCase(
    private val automation: ExecuteAutomationLoopUseCase
) {
    suspend fun execute(
        request: RemoteAutomationRequest,
        conditions: RemoteExecutionConditions,
        onStateChange: (AutomationRunState) -> Unit
    ): RemoteExecutionDecision {
        val decision = conditions.decide(requiresWildcardDirectory = false)
        if (decision is RemoteExecutionDecision.Rejected) {
            onStateChange(AutomationRunState.Failure(decision.message))
            return decision
        }

        if (automation.runState.value is AutomationRunState.Running) {
            automation.cancelAndAwait()
        }

        onStateChange(AutomationRunState.Running("S25 FE가 요청을 수락했습니다."))
        automation.run(
            request = AutomationRunRequest(
                promptTemplate = request.promptTemplate,
                repeatCountText = request.repeatCountText,
                targetApp = request.targetApp,
                flowImageCount = request.flowImageCount,
                initialWildcards = request.wildcards.ifEmpty { null }
            ),
            onStateChange = onStateChange
        )
        return RemoteExecutionDecision.Allowed
    }

    fun cancel() {
        automation.cancel()
    }

    suspend fun cancelAndAwait() {
        automation.cancelAndAwait()
    }

    fun updateRepeatCount(requestedCount: Int): Int? = automation.updateRepeatCount(requestedCount)
}
