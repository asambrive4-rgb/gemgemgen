package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.core.AppDefaults

data class AutomationBarUiState(
    val repeatCountText: String = AppDefaults.DEFAULT_REPEAT_COUNT.toString(),
    val automationState: AutomationRunState = AutomationRunState.Idle
) {
    val isRunning: Boolean
        get() = automationState is AutomationRunState.Running
}
