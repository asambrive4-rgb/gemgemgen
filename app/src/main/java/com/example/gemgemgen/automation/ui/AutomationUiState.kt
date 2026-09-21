// 역할: 자동화 메인 화면의 프롬프트 편집, 실행 상태, 추천 후보, 메모리 정리 예약, 다이얼로그 가시성 등 UI 상태를 정의합니다.
package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationStartPolicy
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.GeminiAppControlPolicy
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.SelfAppControlPolicy
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.automation.domain.VariationStartPolicy
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.environment.domain.EnvironmentSetupInfo
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus

data class AutomationUiState(
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
    /** 등록된 프롬프트 상용구(스니펫) 목록 */
    val promptSnippets: List<com.example.gemgemgen.automation.domain.PromptSnippet> = emptyList(),
    /** 와일드카드 토큰 및 상용구가 모두 포함된 통합 자동완성 후보 목록 */
    val allAutocompleteCandidates: List<WildcardTokenAutocomplete.Candidate> = emptyList(),
    /** 현재 커서 및 입력 상태에 따라 활성화된 추천 후보 목록 */
    val activeSuggestionCandidates: List<WildcardTokenAutocomplete.Candidate> = emptyList(),
    /** 상용구(텍스트 대치) 관리 다이얼로그 표시 여부 */
    val showPromptSnippetDialog: Boolean = false,
    val showPromptHistory: Boolean = false,
    val promptHistoryItems: List<com.example.gemgemgen.automation.domain.PromptHistoryItem> = emptyList(),
    val selectedThemePalette: com.example.gemgemgen.ui.theme.AppThemePalette = com.example.gemgemgen.ui.theme.AppThemePalette.DEFAULT,
    val selectedThemeMode: com.example.gemgemgen.ui.theme.AppThemeMode = com.example.gemgemgen.ui.theme.AppThemeMode.DEFAULT,
    val promptInstructionConfig: com.example.gemgemgen.automation.domain.PromptInstructionConfig =
        com.example.gemgemgen.automation.domain.PromptInstructionConfig.DEFAULT,
    val showInstructionConfigDialog: Boolean = false,
    val instructionConfigDialogInitialTab: com.example.gemgemgen.automation.domain.InstructionTab =
        com.example.gemgemgen.automation.domain.InstructionTab.TOP,
    val variationPromptConfig: VariationPromptConfig = VariationPromptConfig.DEFAULT,
    val variationAutomationState: AutomationRunState = AutomationRunState.Idle,
    val showVariationPromptConfigDialog: Boolean = false,
    val isMemoryCleanupScheduled: Boolean = false
) {
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
            isVariationRunning = isVariationRunning,
            isMaintenanceBusy = isMaintenanceBusy
        )

    val isVariationRunning: Boolean
        get() = variationAutomationState is AutomationRunState.Running

    val canRunVariation: Boolean
        get() = VariationStartPolicy.canRun(
            mode = automationMode,
            environmentStatus = environmentStatus,
            isRunning = isRunning,
            isMaintenanceBusy = isMaintenanceBusy,
            isVariationRunning = isVariationRunning
        )

    val canInteractWithVariation: Boolean
        get() = VariationStartPolicy.canInteract(
            mode = automationMode,
            isRunning = isRunning,
            isMaintenanceBusy = isMaintenanceBusy,
            isVariationRunning = isVariationRunning
        )

    val variationUnavailableReason: String?
        get() = VariationStartPolicy.unavailableReason(
            mode = automationMode,
            environmentStatus = environmentStatus,
            isRunning = isRunning,
            isMaintenanceBusy = isMaintenanceBusy,
            isVariationRunning = isVariationRunning
        )

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
            AutomationMode.SENDER -> remoteAutomationStatus.canSend && !isMaintenanceBusy
            AutomationMode.RECEIVER -> false
            AutomationMode.NORMAL -> environmentStatus.isAccessibilityServiceEnabled && !isMaintenanceBusy
        }
}

data class MaintenanceState(
    val isBusy: Boolean = false,
    val message: String = ""
)
