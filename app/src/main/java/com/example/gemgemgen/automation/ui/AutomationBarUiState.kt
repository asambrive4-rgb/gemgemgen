// 역할: 플로팅 제어 바의 진행률, 카운터, 재생 및 정지 버튼 상태를 표현합니다.
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
