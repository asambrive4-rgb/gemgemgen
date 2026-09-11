// 역할: 원격으로 전달받은 프롬프트와 옵션을 기반으로 로컬 자동화를 대리 실행합니다.
package com.example.gemgemgen.remote.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.PromptGenerator
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.RunAutomationUseCase
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteExecutionConditions
import com.example.gemgemgen.remote.domain.RemoteExecutionDecision

class ExecuteRemoteAutomationUseCase(
    private val checkExecution: CheckRemoteExecutionUseCase,
    private val automation: RunAutomationUseCase,
    private val promptGenerator: PromptGenerator = PromptGenerator()
) {
    suspend fun execute(
        request: RemoteAutomationRequest,
        conditions: RemoteExecutionConditions,
        onStateChange: (AutomationRunState) -> Unit
    ): RemoteExecutionDecision {
        val wildcardTokens = promptGenerator.extractTokens(request.promptTemplate)
        val requiresLocalWildcards = wildcardTokens.isNotEmpty() && request.wildcards.isEmpty()
        val decision = checkExecution.decide(
            conditions = conditions,
            requiresWildcardDirectory = requiresLocalWildcards
        )
        if (decision is RemoteExecutionDecision.Rejected) {
            onStateChange(AutomationRunState.Failure(decision.message))
            return decision
        }

        onStateChange(AutomationRunState.Running("S25 FE가 요청을 수락했습니다."))
        automation.run(
            request = AutomationRunRequest(
                promptTemplate = request.promptTemplate,
                repeatCountText = request.repeatCountText,
                targetApp = request.targetApp,
                initialWildcards = request.wildcards.ifEmpty { null }
            ),
            onStateChange = onStateChange
        )
        return RemoteExecutionDecision.Allowed
    }

    fun cancel() {
        automation.cancel()
    }
}
