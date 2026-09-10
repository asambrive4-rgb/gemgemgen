package com.example.gemgemgen.automation.usecase

@Deprecated("ExecuteAutomationUseCase에 통합되었습니다. ExecuteAutomationUseCase를 직접 사용하세요.")
class StartAutomationUseCase(
    internal val checkAutomationStart: CheckAutomationStartUseCase,
    internal val automationStartRecorder: AutomationStartRecorder,
    internal val automation: RunAutomationUseCase
) {
    fun decideStart(canRun: Boolean, isStartInProgress: Boolean): AutomationStartDecision {
        return checkAutomationStart.decide(canRun = canRun, isStartInProgress = isStartInProgress)
    }

    suspend fun start(request: AutomationRunRequest) {
        automationStartRecorder.record(request)
        automation.run(request)
    }
}
