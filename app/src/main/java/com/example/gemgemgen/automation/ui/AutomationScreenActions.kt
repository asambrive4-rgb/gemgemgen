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
    fun onCancelAutomation() {}
    fun onAutomationModeSelected(mode: AutomationMode) {}
    fun onPairRemoteDevice(pairingCode: String) {}
    fun onDisconnectRemoteDevice() {}

    // 프롬프트 텍스트 편집 및 클립보드
    fun onPromptTemplateChange(value: String) {}
    fun onImportPromptFromClipboard() {}
    fun onCopyPromptToClipboard() {}
    fun onPastePromptFromClipboard() {}
    fun onApplyWildcardTokenSuggestion(token: String) {}
    fun onApplySuggestion(candidate: WildcardTokenAutocomplete.Candidate) {}

    // 히스토리 탐색
    fun onNavigatePromptHistoryBack() {}
    fun onNavigatePromptHistoryForward() {}

    // 문단 선택 모드
    fun onToggleParagraphSelectionMode() {}
    fun onSelectPromptParagraphAt(offset: Int) {}
    fun onDeleteSelectedPromptParagraph() {}
    fun onReplaceSelectedPromptParagraph(replacement: String) {}
    fun onCancelParagraphSelection() {}

    // 검색 기능
    fun onToggleSearch(active: Boolean? = null) {}
    fun onSetSearchQuery(query: String) {}
    fun onNavigateSearchNext() {}
    fun onNavigateSearchPrevious() {}
    fun onCloseSearch() {}

    // 인스트럭션 설정
    fun onInsertTopInstruction() {}
    fun onInsertBottomInstruction() {}
    fun onOpenInstructionConfigDialog(initialTab: InstructionTab = InstructionTab.TOP) {}
    fun onCloseInstructionConfigDialog() {}
    fun onSaveInstructionConfig(config: PromptInstructionConfig) {}

    // 변주 (Variation) 자동화
    fun onRunVariation(selectedText: String? = null) {}
    fun onOpenVariationPromptConfigDialog() {}
    fun onCloseVariationPromptConfigDialog() {}
    fun onSaveVariationPromptConfig(config: VariationPromptConfig) {}

    // 상용구 (Prompt Snippet)
    fun onShowPromptSnippetDialog() {}
    fun onDismissPromptSnippetDialog() {}
    fun onAddPromptSnippet(shortcut: String, content: String) {}
    fun onUpdatePromptSnippet(id: String, shortcut: String, content: String) {}
    fun onDeletePromptSnippet(id: String) {}

    // 유지보수 및 앱 제어
    fun onCloseGeminiApp() {}
    fun onTerminateGeminiApp() {}
    fun onTerminateSelfApp() {}
    fun onCleanDeviceMemory() {}
    fun onOpenGeminiAccountPicker() {}

    // 테마 및 환경 설정
    fun onRefreshStatus() {}
    fun onShowSettings() {}
    fun onHideSettings() {}
    fun onConfirmAccessibilityPrompt() {}
    fun onDismissAccessibilityPromptToSettings() {}
    fun onSelectThemePalette(palette: AppThemePalette) {}
    fun onSelectThemeMode(mode: AppThemeMode) {}
    fun onClearFocus() {}

    companion object {
        val Empty = object : AutomationScreenActions {}
    }
}
