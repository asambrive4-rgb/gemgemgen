// 역할: 메인 자동화 화면의 입력값, 변주 설정, 환경 상태를 담는 통합 상태 데이터를 정의합니다.
package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationStartPolicy
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.GeminiAppControlPolicy
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.SelfAppControlPolicy
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.environment.domain.EnvironmentSetupInfo
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus

data class MainUiState(
    val promptTemplate: String = "",
    val selectedTargetApp: AutomationTargetApp = AutomationTargetApp.GEMINI,
    val flowImageCount: Int = AppDefaults.DEFAULT_FLOW_IMAGE_COUNT,
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
    val canNavigateHistoryBack: Boolean = false,
    val canNavigateHistoryForward: Boolean = false,
    val isHistoryIndicatorVisible: Boolean = false,
    val historyDotCount: Int = 0,
    val activeHistoryDotIndex: Int = 0,
    val maintenanceState: MaintenanceState = MaintenanceState(),
    /** 와일드카드 파일 기반 토큰 추천 후보 (입력창 위 칩용). */
    val wildcardTokenCandidates: List<WildcardTokenAutocomplete.Candidate> = emptyList(),
    val showPromptHistory: Boolean = false,
    val promptHistoryItems: List<com.example.gemgemgen.automation.domain.PromptHistoryItem> = emptyList(),
    val selectedThemePalette: com.example.gemgemgen.ui.theme.AppThemePalette = com.example.gemgemgen.ui.theme.AppThemePalette.DEFAULT,
    val selectedThemeMode: com.example.gemgemgen.ui.theme.AppThemeMode = com.example.gemgemgen.ui.theme.AppThemeMode.DEFAULT,
    val geminiAccounts: List<com.example.gemgemgen.automation.domain.GeminiAccountProfile> = emptyList(),
    val showGeminiAccountDialog: Boolean = false,
    val isSwitchingGeminiAccount: Boolean = false,
    val switchingAccountProgressPhase: String = "",
    val switchingAccountProgressMessage: String = "",
    val accountSwitchError: String? = null,
    val lastFailedTargetAccount: com.example.gemgemgen.automation.domain.GeminiAccountProfile? = null,
    val promptInstructionConfig: com.example.gemgemgen.automation.domain.PromptInstructionConfig =
        com.example.gemgemgen.automation.domain.PromptInstructionConfig.DEFAULT,
    val showInstructionConfigDialog: Boolean = false,
    val instructionConfigDialogInitialTab: com.example.gemgemgen.automation.domain.InstructionTab =
        com.example.gemgemgen.automation.domain.InstructionTab.TOP,
    val variationPromptConfig: VariationPromptConfig = VariationPromptConfig.DEFAULT,
    val variationAutomationState: AutomationRunState = AutomationRunState.Idle,
    val showVariationPromptConfigDialog: Boolean = false
) {
    val activeGeminiAccount: com.example.gemgemgen.automation.domain.GeminiAccountProfile?
        get() = geminiAccounts.firstOrNull { it.isActive } ?: geminiAccounts.firstOrNull()

    val nextGeminiAccount: com.example.gemgemgen.automation.domain.GeminiAccountProfile?
        get() {
            if (geminiAccounts.size <= 1) return null
            val currentIndex = geminiAccounts.indexOfFirst { it.isActive }
            val nextIndex = if (currentIndex in geminiAccounts.indices) {
                (currentIndex + 1) % geminiAccounts.size
            } else {
                0
            }
            return geminiAccounts[nextIndex]
        }
    val hasPromptTemplate: Boolean
        get() = promptTemplate.isNotBlank()

    val isRunning: Boolean
        get() = automationState is AutomationRunState.Running

    val hasRunRequirements: Boolean
        get() = environmentStatus.isReadyFor(selectedTargetApp) && hasPromptTemplate

    val canRun: Boolean
        get() = AutomationStartPolicy.canRun(
            mode = automationMode,
            environmentStatus = environmentStatus,
            targetApp = selectedTargetApp,
            promptTemplate = promptTemplate,
            isRunning = isRunning,
            remoteAutomationStatus = remoteAutomationStatus,
            isVariationRunning = isVariationRunning
        )

    val isVariationRunning: Boolean
        get() = variationAutomationState is AutomationRunState.Running

    val canRunVariation: Boolean
        get() = automationMode != AutomationMode.RECEIVER &&
            environmentStatus.isGeminiInstalled &&
            environmentStatus.isAccessibilityServiceEnabled &&
            !isRunning &&
            !isMaintenanceBusy &&
            !isVariationRunning

    val canInteractWithVariation: Boolean
        get() = automationMode != AutomationMode.RECEIVER &&
            !isRunning &&
            !isMaintenanceBusy &&
            !isVariationRunning

    val isMaintenanceBusy: Boolean
        get() = maintenanceState.isBusy

    val maintenanceMessage: String
        get() = maintenanceState.message

    val canCloseGemini: Boolean
        get() = GeminiAppControlPolicy.canClose(
            isGeminiInstalled = environmentStatus.isGeminiInstalled,
            isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = isRunning || isVariationRunning,
            isClosingInProgress = isMaintenanceBusy
        )

    val canCloseSelfApp: Boolean
        get() = SelfAppControlPolicy.canClose(
            isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = isRunning || isVariationRunning,
            isClosingInProgress = isMaintenanceBusy
        )

    val canCleanMemory: Boolean
        get() = when (automationMode) {
            AutomationMode.SENDER -> remoteAutomationStatus.canSend &&
                !isRunning &&
                !isVariationRunning &&
                !isMaintenanceBusy
            AutomationMode.RECEIVER -> false
            AutomationMode.NORMAL -> environmentStatus.isAccessibilityServiceEnabled &&
                !isRunning &&
                !isVariationRunning &&
                !isMaintenanceBusy
        }
}

data class MaintenanceState(
    val isBusy: Boolean = false,
    val message: String = ""
)
