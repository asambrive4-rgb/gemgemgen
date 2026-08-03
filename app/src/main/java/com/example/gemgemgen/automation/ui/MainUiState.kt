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

data class MainUiState(
    val promptTemplate: String = "",
    val selectedTargetApp: AutomationTargetApp = AutomationTargetApp.GEMINI,
    val repeatCountText: String = AppDefaults.DEFAULT_REPEAT_COUNT.toString(),
    val environmentStatus: EnvironmentStatus = EnvironmentStatus(),
    val environmentSetupInfo: EnvironmentSetupInfo = EnvironmentSetupInfo(),
    val automationState: AutomationRunState = AutomationRunState.Idle,
    val showSettings: Boolean = false,
    val showAccessibilityPrompt: Boolean = false,
    val settingsMessage: String = "",
    val settingsError: String = "",
    val isParagraphSelectionMode: Boolean = false,
    val selectedParagraphRange: PromptParagraphRange? = null,
    val paragraphSelectionMessage: String = "",
    val canUndoPromptEdit: Boolean = false,
    val isClosingGemini: Boolean = false,
    val geminiCloseMessage: String = "",
    val isCleaningMemory: Boolean = false,
    val memoryCleanupMessage: String = "",
    /** 와일드카드 파일 기반 토큰 추천 후보 (입력창 위 칩용). */
    val wildcardTokenCandidates: List<WildcardTokenAutocomplete.Candidate> = emptyList()
) {
    val hasPromptTemplate: Boolean
        get() = promptTemplate.isNotBlank()

    val isRunning: Boolean
        get() = automationState is AutomationRunState.Running

    val hasRunRequirements: Boolean
        get() = environmentStatus.isReadyFor(selectedTargetApp) && hasPromptTemplate

    val canRun: Boolean
        get() = hasRunRequirements && !isRunning

    val canCloseGemini: Boolean
        get() = GeminiAppControlPolicy.canClose(
            isGeminiInstalled = environmentStatus.isGeminiInstalled,
            isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = isRunning,
            isClosingInProgress = isClosingGemini || isCleaningMemory
        )

    val canCloseSelfApp: Boolean
        get() = SelfAppControlPolicy.canClose(
            isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = isRunning,
            isClosingInProgress = isClosingGemini || isCleaningMemory
        )

    val canCleanMemory: Boolean
        get() = environmentStatus.isAccessibilityServiceEnabled &&
            !isRunning &&
            !isCleaningMemory &&
            !isClosingGemini
}
