package com.example.gemgemgen.automation.usecase

class StartAutomationUseCase(
    private val checkAutomationStart: CheckAutomationStartUseCase,
    private val automationStartRecorder: AutomationStartRecorder,
    private val automation: RunAutomationUseCase
) {
    fun decideStart(canRun: Boolean, isStartInProgress: Boolean): AutomationStartDecision {
        return checkAutomationStart.decide(canRun = canRun, isStartInProgress = isStartInProgress)
    }

    suspend fun start(request: AutomationRunRequest) {
        automationStartRecorder.record(request)
        automation.run(request)
    }
}
