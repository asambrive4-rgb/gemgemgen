// 역할: 자동화 메인 화면의 프롬프트 편집, 실행 상태, 제미나이 사용량 한도 상태, 추천 후보, 자동/예약 메모리 정리 및 다이얼로그 가시성 등 UI 상태를 정의합니다.
package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.AutomationExecutionPolicy
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.GeminiAppControlBlockReason
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.automation.domain.SelfAppControlBlockReason
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.environment.domain.EnvironmentSetupInfo
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.usage.domain.GeminiUsageState

data class AutomationUiState(
    val promptTemplate: String = "",
    val editor: PromptEditorUiState = PromptEditorUiState(promptTemplate = promptTemplate),
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
    val isDisconnectingRemote: Boolean = false,
    val remoteDisconnectMessage: String = "",
    val maintenanceState: MaintenanceState = MaintenanceState(),
    /** 등록된 프롬프트 상용구(스니펫) 목록 */
    val promptSnippets: List<com.example.gemgemgen.automation.domain.PromptSnippet> = emptyList(),
    /** 상용구(텍스트 대치) 관리 다이얼로그 표시 여부 */
    val showPromptSnippetDialog: Boolean = false,
    val promptInstructionConfig: com.example.gemgemgen.automation.domain.PromptInstructionConfig =
        com.example.gemgemgen.automation.domain.PromptInstructionConfig.DEFAULT,
    val showInstructionConfigDialog: Boolean = false,
    val instructionConfigDialogInitialTab: com.example.gemgemgen.automation.domain.InstructionTab =
        com.example.gemgemgen.automation.domain.InstructionTab.TOP,
    val variationPromptConfig: VariationPromptConfig = VariationPromptConfig.DEFAULT,
    val variationAutomationState: AutomationRunState = AutomationRunState.Idle,
    val showVariationPromptConfigDialog: Boolean = false,
    val isMemoryCleanupScheduled: Boolean = false,
    val autoMemoryCleanupEnabled: Boolean = AppDefaults.DEFAULT_AUTO_MEMORY_CLEANUP_ENABLED,
    val autoMemoryCleanupThresholdText: String = AppDefaults.DEFAULT_AUTO_MEMORY_CLEANUP_THRESHOLD.toString(),
    val cumulativeGeminiGenerationCount: Int = 0,
    /** Google AI Pro 제미나이 사용량 한도 상태 (5시간 단기 한도 / 주간 누적 한도) */
    val geminiUsageState: GeminiUsageState = GeminiUsageState.Loading,
    /** 로컬에 캐싱된 마지막 제미나이 사용량 성공 스냅샷 */
    val geminiUsageCachedSuccess: GeminiUsageState.Success? = null,
    /** 백그라운드에서 제미나이 사용량을 갱신 중인지 여부 */
    val isRefreshingGeminiUsage: Boolean = false,
    /** 세션 만료 시 인앱 Google 로그인 WebView 바텀시트 표시 여부 */
    val showGeminiUsageLoginSheet: Boolean = false,
    /**
     * =========================================================================================
     * [주의: 사용자 명시적 요구 기능 - 임의 삭제 절대 금지]
     * - 기능: 자동화 시작 시 사용자의 원본 프롬프트 템플릿을 시스템 클립보드에 1회 백업할지 여부
     * =========================================================================================
     */
    val copyPromptOnStart: Boolean = true
) {
    val hasPromptTemplate: Boolean
        get() = promptTemplate.isNotBlank()

    val isRunning: Boolean
        get() = automationState is AutomationRunState.Running

    val canRun: Boolean
        get() = AutomationExecutionPolicy.canRun(
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
        get() = AutomationExecutionPolicy.canRunVariation(
            mode = automationMode,
            environmentStatus = environmentStatus,
            isRunning = isRunning,
            isMaintenanceBusy = isMaintenanceBusy,
            isVariationRunning = isVariationRunning
        )

    val canInteractWithVariation: Boolean
        get() = AutomationExecutionPolicy.canInteractWithVariation(
            mode = automationMode,
            isRunning = isRunning,
            isMaintenanceBusy = isMaintenanceBusy,
            isVariationRunning = isVariationRunning
        )

    val variationUnavailableReason: String?
        get() = AutomationExecutionPolicy.variationUnavailableReason(
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

    val geminiRestartBlockReason: GeminiAppControlBlockReason?
        get() = AutomationExecutionPolicy.geminiBlockReason(
            isGeminiInstalled = environmentStatus.isGeminiInstalled,
            isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = isRunning || isVariationRunning,
            isClosingInProgress = isMaintenanceBusy
        )

    val selfCloseBlockReason: SelfAppControlBlockReason?
        get() = AutomationExecutionPolicy.selfAppBlockReason(
            isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = isRunning || isVariationRunning,
            isClosingInProgress = isMaintenanceBusy
        )

    val canCloseGemini: Boolean
        get() = geminiRestartBlockReason == null

    val canCloseSelfApp: Boolean
        get() = selfCloseBlockReason == null

    val canCleanMemory: Boolean
        get() = AutomationExecutionPolicy.canCleanMemory(
            mode = automationMode,
            environmentStatus = environmentStatus,
            remoteAutomationStatus = remoteAutomationStatus,
            isMaintenanceBusy = isMaintenanceBusy
        )

    val autoMemoryCleanupThreshold: Int
        get() = RepeatCountParser.parse(
            autoMemoryCleanupThresholdText.ifBlank {
                AppDefaults.DEFAULT_AUTO_MEMORY_CLEANUP_THRESHOLD.toString()
            }
        )

    val shouldAutoCleanMemoryBeforeStart: Boolean
        get() = autoMemoryCleanupEnabled &&
            selectedTargetApp == AutomationTargetApp.GEMINI &&
            cumulativeGeminiGenerationCount >= autoMemoryCleanupThreshold
}

data class MaintenanceState(
    val isBusy: Boolean = false,
    val message: String = ""
)
