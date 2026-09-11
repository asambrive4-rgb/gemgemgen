// 역할: 메인 자동화 화면의 모든 입력값, 설정, 환경 상태를 담는 통합 상태 데이터를 정의합니다.
package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.GeminiAppControlPolicy
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.SelfAppControlPolicy
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.environment.domain.EnvironmentSetupInfo
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus

data class MainUiState(
    val promptTemplate: String = "",
    val selectedTargetApp: AutomationTargetApp = AutomationTargetApp.GEMINI,
    val repeatCountText: String = AppDefaults.DEFAULT_REPEAT_COUNT.toString(),
    val environmentStatus: EnvironmentStatus = EnvironmentStatus(),
    val environmentSetupInfo: EnvironmentSetupInfo = EnvironmentSetupInfo(),
    val automationState: AutomationRunState = AutomationRunState.Idle,
    val automationMode: AutomationMode = AutomationMode.NORMAL,
    val remoteAutomationStatus: RemoteAutomationStatus = RemoteAutomationStatus(),
    val showSettings: Boolean = false,
    val showAccessibilityPrompt: Boolean = false,
    val settingsMessage: String = "",
    val settingsError: String = "",
    val isDisconnectingRemote: Boolean = false,
    val remoteDisconnectMessage: String = "",
    val isParagraphSelectionMode: Boolean = false,
    val selectedParagraphRange: PromptParagraphRange? = null,
    val paragraphSelectionMessage: String = "",
    val canUndoPromptEdit: Boolean = false,
    val maintenanceState: MaintenanceState = MaintenanceState(),
    /** 와일드카드 파일 기반 토큰 추천 후보 (입력창 위 칩용). */
    val wildcardTokenCandidates: List<WildcardTokenAutocomplete.Candidate> = emptyList(),
    val showPromptHistory: Boolean = false,
    val promptHistoryItems: List<com.example.gemgemgen.automation.domain.PromptHistoryItem> = emptyList(),
    val selectedThemePalette: com.example.gemgemgen.ui.theme.AppThemePalette = com.example.gemgemgen.ui.theme.AppThemePalette.DEFAULT,
    val selectedThemeMode: com.example.gemgemgen.ui.theme.AppThemeMode = com.example.gemgemgen.ui.theme.AppThemeMode.DEFAULT
) {
    val hasPromptTemplate: Boolean
        get() = promptTemplate.isNotBlank()

    val isRunning: Boolean
        get() = automationState is AutomationRunState.Running

    val hasRunRequirements: Boolean
        get() = environmentStatus.isReadyFor(selectedTargetApp) && hasPromptTemplate

    val canRun: Boolean
        get() = when (automationMode) {
            AutomationMode.NORMAL -> hasRunRequirements && !isRunning
            AutomationMode.SENDER -> hasPromptTemplate &&
                remoteAutomationStatus.canSend &&
                !isRunning
            AutomationMode.RECEIVER -> false
        }

    val isMaintenanceBusy: Boolean
        get() = maintenanceState.isBusy

    val maintenanceMessage: String
        get() = maintenanceState.message

    val canCloseGemini: Boolean
        get() = GeminiAppControlPolicy.canClose(
            isGeminiInstalled = environmentStatus.isGeminiInstalled,
            isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = isRunning,
            isClosingInProgress = isMaintenanceBusy
        )

    val canCloseSelfApp: Boolean
        get() = SelfAppControlPolicy.canClose(
            isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = isRunning,
            isClosingInProgress = isMaintenanceBusy
        )

    val canCleanMemory: Boolean
        get() = environmentStatus.isAccessibilityServiceEnabled &&
            !isRunning &&
            !isMaintenanceBusy
}

data class MaintenanceState(
    val isBusy: Boolean = false,
    val message: String = ""
)
