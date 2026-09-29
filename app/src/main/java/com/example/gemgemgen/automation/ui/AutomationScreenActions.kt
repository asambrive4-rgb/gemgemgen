// 역할: 자동화 화면에서 발생하는 모든 사용자 입력 인터랙션을 캡슐화한 인터페이스입니다.
package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.InstructionTab
import com.example.gemgemgen.automation.domain.PromptInstructionConfig
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.ui.theme.AppThemeMode
import com.example.gemgemgen.ui.theme.AppThemePalette

interface AutomationScreenActions {
    // 기본 실행 및 제어
    fun onTargetAppSelected(targetApp: AutomationTargetApp) {}
    fun onFlowImageCountSelected(count: Int) {}
    fun onRepeatCountChange(value: String) {}
    fun onRunAutomation() {}
    fun cancelAutomation() {}
    fun onAutomationModeSelected(mode: AutomationMode) {}
    fun pairRemoteDevice(pairingCode: String) {}
    fun disconnectRemoteDevice() {}

    // 프롬프트 텍스트 편집 및 클립보드
    fun onPromptTemplateChange(value: String) {}
    fun importPromptFromClipboard() {}
    fun copyPromptToClipboard() {}
    fun applySuggestion(candidate: WildcardTokenAutocomplete.Candidate) {}

    // 히스토리 탐색
    fun navigatePromptHistoryBack() {}
    fun navigatePromptHistoryForward() {}

    // 문단 선택 모드
    fun toggleParagraphSelectionMode() {}
    fun selectPromptParagraphAt(offset: Int) {}
    fun deleteSelectedPromptParagraph() {}
    fun replaceSelectedPromptParagraph(replacement: String) {}
    fun cancelParagraphSelection() {}

    // 검색 기능
    fun toggleSearch(active: Boolean? = null) {}
    fun setSearchQuery(query: String) {}
    fun navigateSearchNext() {}
    fun navigateSearchPrevious() {}
    fun closeSearch() {}

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

    // 상용구 (Prompt Snippet)
    fun showPromptSnippetDialog() {}
    fun dismissPromptSnippetDialog() {}
    fun addPromptSnippet(shortcut: String, content: String) {}
    fun updatePromptSnippet(id: String, shortcut: String, content: String) {}
    fun deletePromptSnippet(id: String) {}

    // 유지보수 및 앱 제어
    fun closeGeminiApp() {}
    fun terminateSelfApp() {}
    fun cleanDeviceMemory() {}

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

    companion object {
        val Empty = object : AutomationScreenActions {}
    }
}
