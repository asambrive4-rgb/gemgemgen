package com.example.gemgemgen

data class MainUiState(
    val promptTemplate: String = "",
    val repeatCountText: String = AppDefaults.DEFAULT_REPEAT_COUNT.toString(),
    val environmentStatus: EnvironmentStatus = EnvironmentStatus(),
    val automationState: AutomationRunState = AutomationRunState.Idle,
    val recentLogs: List<AutomationRunLog> = emptyList(),
    val showSettings: Boolean = false,
    val settingsMessage: String = "",
    val settingsError: String = "",
    val showRecentLogs: Boolean = false
) {
    val hasPromptTemplate: Boolean
        get() = promptTemplate.isNotBlank()

    val isRunning: Boolean
        get() = automationState is AutomationRunState.Running

    val hasRunRequirements: Boolean
        get() = environmentStatus.isReady && hasPromptTemplate

    val canRun: Boolean
        get() = hasRunRequirements && !isRunning

}
