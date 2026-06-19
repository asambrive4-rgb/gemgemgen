package com.example.gemgemgen.ui

import com.example.gemgemgen.automation.domain.AutomationRunLog
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.environment.domain.EnvironmentStatus

data class MainUiState(
    val promptTemplate: String = "",
    val selectedTargetApp: AutomationTargetApp = AutomationTargetApp.GEMINI,
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
        get() = environmentStatus.isReadyFor(selectedTargetApp) && hasPromptTemplate

    val canRun: Boolean
        get() = hasRunRequirements && !isRunning

}

