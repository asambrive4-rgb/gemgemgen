// 역할: 자동화 화면에서 발생하는 사용자 입력 인터랙션, 프롬프트 에디터 액션 그룹, 실험(연금술) 액션, 다중 계정 제미나이 액션 및 상용구 일괄 치환 액션을 제공하는 안정적인(Stable) UI 액션 인터페이스입니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.runtime.Stable
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.InstructionTab
import com.example.gemgemgen.automation.domain.PromptInstructionConfig
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.ui.theme.AppThemeMode
import com.example.gemgemgen.ui.theme.AppThemePalette
import com.example.gemgemgen.usage.domain.GeminiAccountGroup

@Stable
interface AutomationScreenActions {
    // 프롬프트 에디터 전용 액션 그룹
    val promptEditor: PromptEditorUiActions
        get() = object : PromptEditorUiActions {}

    // 기본 실행 및 제어
    fun onTargetAppSelected(targetApp: AutomationTargetApp) {}
    fun onFlowImageCountSelected(count: Int) {}
    fun onRepeatCountChange(value: String) {}
    fun onRunAutomation() {}
    fun cancelAutomation() {}
    fun onAutomationModeSelected(mode: AutomationMode) {}
    fun pairRemoteDevice(pairingCode: String) {}
    fun disconnectRemoteDevice() {}

    // 인스트럭션 설정
    fun insertTopInstruction() {}
    fun insertBottomInstruction() {}
    fun openInstructionConfigDialog(initialTab: InstructionTab = InstructionTab.TOP) {}
    fun closeInstructionConfigDialog() {}
    fun saveInstructionConfig(config: PromptInstructionConfig) {}

    // 변주 (Variation) 자동화
    fun onRunVariation(selectedText: String? = null) {}
    fun openVariationPromptConfigDialog() {}
    fun closeVariationPromptConfigDialog() {}
    fun saveVariationPromptConfig(config: VariationPromptConfig) {}

    // 프롬프트 실험 (연금술)
    fun toggleExperimentMode() {}
    fun showExperimentDialog() {}
    fun dismissExperimentDialog() {}
    fun selectExperimentPreset(presetId: String) {}
    fun selectExperimentItem(itemId: String?) {}
    fun insertExperimentTag() {}
    fun addExperimentPreset(name: String) {}
    fun updateExperimentPreset(presetId: String, name: String) {}
    fun deleteExperimentPreset(presetId: String) {}
    fun moveExperimentPreset(presetId: String, delta: Int) {}
    fun reorderExperimentPresets(fromIndex: Int, toIndex: Int) {}
    fun resetExperimentPresetItems(presetId: String) {}
    fun addExperimentItem(presetId: String, content: String) {}
    fun addExperimentItemsBatch(presetId: String, contents: List<String>) {}
    fun updateExperimentItem(presetId: String, itemId: String, content: String) {}
    fun deleteExperimentItem(presetId: String, itemId: String) {}
    fun moveExperimentItem(presetId: String, itemId: String, delta: Int) {}
    fun reorderExperimentItems(presetId: String, fromIndex: Int, toIndex: Int) {}

    // 상용구 (Prompt Snippet)
    fun showPromptSnippetDialog() {}
    fun dismissPromptSnippetDialog() {}
    fun addPromptSnippet(shortcut: String, content: String) {}
    fun updatePromptSnippet(id: String, shortcut: String, content: String) {}
    fun deletePromptSnippet(id: String) {}
    fun batchReplacePromptSnippets(searchText: String, replacementText: String) {}

    // 유지보수 및 앱 제어
    fun closeGeminiApp() {}
    fun terminateSelfApp() {}
    fun cleanDeviceMemory() {}

    // 다중 계정(5개+) 제미나이 사용량 모니터링 및 모달 액션
    fun openMultiGeminiUsageDialog() {}
    fun closeMultiGeminiUsageDialog() {}
    fun addGeminiAccount(
        alias: String,
        email: String = "",
        accountId: String? = null,
        group: GeminiAccountGroup = GeminiAccountGroup.MAIN
    ) {}
    fun updateGeminiAccount(accountId: String, alias: String, email: String = "") {}
    fun removeGeminiAccount(accountId: String) {}
    fun moveGeminiAccountGroup(accountId: String, targetGroup: GeminiAccountGroup) {}
    fun selectRepresentativeGeminiAccount(accountId: String) {}
    fun switchGeminiAccount(accountId: String) {}
    fun toggleGeminiAccountManualExhaustion(accountId: String, isExhausted: Boolean) {}
    fun toggleGeminiAccountSuspension(accountId: String, isSuspended: Boolean) {}
    fun refreshGeminiAccountUsage(accountId: String) {}
    fun refreshAllGeminiUsage(prioritizedGroup: GeminiAccountGroup? = null) {}

    // 테마 및 환경 설정
    fun refreshStatus() {}
    fun showSettings() {}
    fun hideSettings() {}
    fun onConfirmAccessibilityPrompt() {}
    fun dismissAccessibilityPromptToSettings() {}
    fun onSelectThemePalette(palette: AppThemePalette) {}
    fun onSelectThemeMode(mode: AppThemeMode) {}
    fun onSelectWildcardFolder() {}
    fun onSelectSafWildcardFolder() {}
    fun onOpenWildcardStorageSettings() {}
    fun onOpenAccessibilitySettings() {}
    fun onClearFocus() {}
    fun onToggleCopyPromptOnStart(enabled: Boolean) {}
    fun onToggleGeminiSidebarOpenMode(enabled: Boolean) {}
    fun onToggleAutoMemoryCleanup(enabled: Boolean) {}
    fun onAutoMemoryCleanupThresholdChange(value: String) {}
}
