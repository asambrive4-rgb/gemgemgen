package com.example.gemgemgen

data class MainUiState(
    val promptTemplate: String = "",
    val repeatCountText: String = AppDefaults.DEFAULT_REPEAT_COUNT.toString(),
    val environmentStatus: EnvironmentStatus = EnvironmentStatus(),
    val automationState: AutomationUiState = AutomationUiState.Idle,
    val recentLogs: List<AutomationRunLog> = emptyList(),
    val showSettings: Boolean = false,
    val settingsMessage: String = "",
    val settingsError: String = "",
    val showRecentLogs: Boolean = false
) {
    val hasPromptTemplate: Boolean
        get() = promptTemplate.isNotBlank()

    val isRunning: Boolean
        get() = automationState is AutomationUiState.Running

    val hasRunRequirements: Boolean
        get() = environmentStatus.isReady && hasPromptTemplate

    val canRun: Boolean
        get() = hasRunRequirements && !isRunning

    val readinessMessage: String
        get() = if (hasRunRequirements) {
            "실행 준비 상태가 충족되었습니다."
        } else {
            "실행 전 필요한 상태를 먼저 채워주세요."
        }
}
