// 역할: 상단 탭 전환과 각 화면별 ScreenActions 인터페이스를 바인딩하여 조율하는 최상위 UI 진입점입니다.
package com.example.gemgemgen.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.ui.AnalysisScreen
import com.example.gemgemgen.analysis.ui.AnalysisScreenActions
import com.example.gemgemgen.analysis.ui.AnalysisUiState
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.automation.ui.AutomationBarUiState
import com.example.gemgemgen.automation.ui.AutomationScreen
import com.example.gemgemgen.automation.ui.AutomationScreenActions
import com.example.gemgemgen.automation.ui.AutomationUiState
import com.example.gemgemgen.automation.ui.SettingsDialogHost
import com.example.gemgemgen.ui.theme.AppThemeMode
import com.example.gemgemgen.ui.theme.AppThemePalette
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.ui.WildcardScreen
import com.example.gemgemgen.wildcard.ui.WildcardScreenActions
import com.example.gemgemgen.wildcard.ui.WildcardUiState
import com.example.gemgemgen.remote.domain.AutomationMode

internal data class AutomationAppActions(
    val onSelectTab: (MainTab) -> Unit,
    val onShowSettings: () -> Unit,
    val onSelectThemePalette: (AppThemePalette) -> Unit = {},
    val onSelectThemeMode: (AppThemeMode) -> Unit = {},
    val onClearFocus: () -> Unit,
    val onHideSettings: () -> Unit,
    val onConfirmAccessibilityPrompt: () -> Unit,
    val onDismissAccessibilityPromptToSettings: () -> Unit,
    val onRefreshStatus: () -> Unit,
    val onSelectWildcardFolder: () -> Unit,
    val onSelectSafWildcardFolder: () -> Unit,
    val onOpenWildcardStorageSettings: () -> Unit,
    val onOpenAccessibilitySettings: () -> Unit,
    val onTargetAppSelected: (AutomationTargetApp) -> Unit,
    val onFlowImageCountSelected: (Int) -> Unit = {},
    val onPromptTemplateChange: (String) -> Unit,
    val onSuggestionClick: (com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete.Candidate) -> Unit = {},
    val onNavigateHistoryBack: () -> Unit,
    val onNavigateHistoryForward: () -> Unit,
    val onInsertTopInstruction: () -> Unit = {},
    val onInsertBottomInstruction: () -> Unit = {},
    val onOpenInstructionConfigDialog: (com.example.gemgemgen.automation.domain.InstructionTab) -> Unit = {},
    val onCloseInstructionConfigDialog: () -> Unit = {},
    val onSaveInstructionConfig: (com.example.gemgemgen.automation.domain.PromptInstructionConfig) -> Unit = {},
    val onOpenPromptSnippetDialog: () -> Unit = {},
    val onClosePromptSnippetDialog: () -> Unit = {},
    val onAddPromptSnippet: (shortcut: String, content: String) -> Unit = { _, _ -> },
    val onUpdatePromptSnippet: (id: String, shortcut: String, content: String) -> Unit = { _, _, _ -> },
    val onDeletePromptSnippet: (id: String) -> Unit = {},
    val onToggleParagraphSelectionMode: () -> Unit = {},
    val onParagraphOffsetSelected: (Int) -> Unit,
    val onDeleteSelectedParagraph: () -> Unit,
    val onReplaceSelectedParagraph: (String) -> Unit,
    val onImportFromClipboard: () -> Unit,
    val onCopyPromptToClipboard: () -> Unit,
    val onPasteFromClipboard: () -> Unit,
    val onCloseGeminiApp: () -> Unit,
    val onCleanDeviceMemory: () -> Unit,
    val onTerminateSelfApp: () -> Unit,
    val onRepeatCountChange: (String) -> Unit,
    val onRunAutomation: () -> Unit,
    val onCancelAutomation: () -> Unit,
    val onAutomationModeSelected: (AutomationMode) -> Unit,
    val onPairRemoteDevice: (String) -> Unit,
    val onDisconnectRemoteDevice: () -> Unit = {},
    val onToggleSearch: () -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onNavigateSearchNext: () -> Unit = {},
    val onNavigateSearchPrevious: () -> Unit = {},
    val onCloseSearch: () -> Unit = {},
    val onOpenGeminiAccountPicker: () -> Unit = {},
    val onRunVariation: (String?) -> Unit = {},
    val onOpenVariationPromptConfigDialog: () -> Unit = {},
    val onCloseVariationPromptConfigDialog: () -> Unit = {},
    val onSaveVariationPromptConfig: (VariationPromptConfig) -> Unit = {}
) : AutomationScreenActions {
    override fun onTargetAppSelected(targetApp: AutomationTargetApp) = onTargetAppSelected.invoke(targetApp)
    override fun onFlowImageCountSelected(count: Int) = onFlowImageCountSelected.invoke(count)
    override fun onRepeatCountChange(value: String) = onRepeatCountChange.invoke(value)
    override fun onRunAutomation() = onRunAutomation.invoke()
    override fun onCancelAutomation() = onCancelAutomation.invoke()
    override fun onAutomationModeSelected(mode: AutomationMode) = onAutomationModeSelected.invoke(mode)
    override fun onPairRemoteDevice(pairingCode: String) = onPairRemoteDevice.invoke(pairingCode)
    override fun onDisconnectRemoteDevice() = onDisconnectRemoteDevice.invoke()
    override fun onPromptTemplateChange(value: String) = onPromptTemplateChange.invoke(value)
    override fun onImportPromptFromClipboard() = onImportFromClipboard.invoke()
    override fun onCopyPromptToClipboard() = onCopyPromptToClipboard.invoke()
    override fun onPastePromptFromClipboard() = onPasteFromClipboard.invoke()
    override fun onApplySuggestion(candidate: com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete.Candidate) = onSuggestionClick.invoke(candidate)
    override fun onNavigatePromptHistoryBack() = onNavigateHistoryBack.invoke()
    override fun onNavigatePromptHistoryForward() = onNavigateHistoryForward.invoke()
    override fun onToggleParagraphSelectionMode() = onToggleParagraphSelectionMode.invoke()
    override fun onSelectPromptParagraphAt(offset: Int) = onParagraphOffsetSelected.invoke(offset)
    override fun onDeleteSelectedPromptParagraph() = onDeleteSelectedParagraph.invoke()
    override fun onReplaceSelectedPromptParagraph(replacement: String) = onReplaceSelectedParagraph.invoke(replacement)
    override fun onToggleSearch(active: Boolean?) = onToggleSearch.invoke()
    override fun onSetSearchQuery(query: String) = onSearchQueryChange.invoke(query)
    override fun onNavigateSearchNext() = onNavigateSearchNext.invoke()
    override fun onNavigateSearchPrevious() = onNavigateSearchPrevious.invoke()
    override fun onCloseSearch() = onCloseSearch.invoke()
    override fun onInsertTopInstruction() = onInsertTopInstruction.invoke()
    override fun onInsertBottomInstruction() = onInsertBottomInstruction.invoke()
    override fun onOpenInstructionConfigDialog(initialTab: com.example.gemgemgen.automation.domain.InstructionTab) = onOpenInstructionConfigDialog.invoke(initialTab)
    override fun onCloseInstructionConfigDialog() = onCloseInstructionConfigDialog.invoke()
    override fun onSaveInstructionConfig(config: com.example.gemgemgen.automation.domain.PromptInstructionConfig) = onSaveInstructionConfig.invoke(config)
    override fun onRunVariation(selectedText: String?) = onRunVariation.invoke(selectedText)
    override fun onOpenVariationPromptConfigDialog() = onOpenVariationPromptConfigDialog.invoke()
    override fun onCloseVariationPromptConfigDialog() = onCloseVariationPromptConfigDialog.invoke()
    override fun onSaveVariationPromptConfig(config: VariationPromptConfig) = onSaveVariationPromptConfig.invoke(config)
    override fun onShowPromptSnippetDialog() = onOpenPromptSnippetDialog.invoke()
    override fun onDismissPromptSnippetDialog() = onClosePromptSnippetDialog.invoke()
    override fun onAddPromptSnippet(shortcut: String, content: String) = onAddPromptSnippet.invoke(shortcut, content)
    override fun onUpdatePromptSnippet(id: String, shortcut: String, content: String) = onUpdatePromptSnippet.invoke(id, shortcut, content)
    override fun onDeletePromptSnippet(id: String) = onDeletePromptSnippet.invoke(id)
    override fun onCloseGeminiApp() = onCloseGeminiApp.invoke()
    override fun onTerminateSelfApp() = onTerminateSelfApp.invoke()
    override fun onCleanDeviceMemory() = onCleanDeviceMemory.invoke()
    override fun onOpenGeminiAccountPicker() = onOpenGeminiAccountPicker.invoke()
    override fun onRefreshStatus() = onRefreshStatus.invoke()
    override fun onShowSettings() = onShowSettings.invoke()
    override fun onHideSettings() = onHideSettings.invoke()
    override fun onConfirmAccessibilityPrompt() = onConfirmAccessibilityPrompt.invoke()
    override fun onDismissAccessibilityPromptToSettings() = onDismissAccessibilityPromptToSettings.invoke()
    override fun onSelectThemePalette(palette: AppThemePalette) = onSelectThemePalette.invoke(palette)
    override fun onSelectThemeMode(mode: AppThemeMode) = onSelectThemeMode.invoke(mode)
    override fun onClearFocus() = onClearFocus.invoke()
}

internal data class WildcardAppActions(
    val onRefresh: () -> Unit,
    val onSelectFolder: () -> Unit,
    val onFileClick: (WildcardTextFile) -> Unit,
    val onTextChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onRequestNewFile: () -> Unit,
    val onNewFileNameChange: (String) -> Unit,
    val onCreateNewFile: () -> Unit,
    val onDismissNewFile: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissDelete: () -> Unit,
    val onRequestRename: () -> Unit,
    val onRenameFileNameChange: (String) -> Unit,
    val onConfirmRename: () -> Unit,
    val onDismissRename: () -> Unit,
    val onPaste: () -> Unit,
    val onPasteBelow: () -> Unit,
    val onCopy: () -> Unit,
    val onUndo: () -> Unit,
    val onEnterLineSelectionMode: () -> Unit,
    val onExitLineSelectionMode: () -> Unit,
    val onToggleLineSelection: (Int) -> Unit,
    val onSelectAllLines: () -> Unit,
    val onDeselectAllLines: () -> Unit,
    val onComposeDynamicPrompt: () -> Unit,
    val onRequestClassify: () -> Unit,
    val onClassifyCriteriaChange: (String) -> Unit,
    val onClassifyProviderSelected: (AnalysisProvider) -> Unit,
    val onClassifyModelSelected: (String) -> Unit,
    val onDismissClassifyCriteria: () -> Unit,
    val onRunClassify: () -> Unit,
    val onDismissClassifyPreview: () -> Unit,
    val onClassifyFileNameChange: (Int, String) -> Unit,
    val onToggleClassifyFileNameEdit: (Int) -> Unit,
    val onSaveClassifyResult: () -> Unit,
    val onConfirmClassifyOverwrite: () -> Unit,
    val onDismissClassifyOverwrite: () -> Unit,
    val onConfirmPendingSave: () -> Unit,
    val onConfirmPendingDiscard: () -> Unit,
    val onCancelPending: () -> Unit
) : WildcardScreenActions {
    override fun onRefresh() = onRefresh.invoke()
    override fun onSelectFolder() = onSelectFolder.invoke()
    override fun onFileClick(file: WildcardTextFile) = onFileClick.invoke(file)
    override fun onRequestNewFile() = onRequestNewFile.invoke()
    override fun onNewFileNameChange(name: String) = onNewFileNameChange.invoke(name)
    override fun onCreateNewFile() = onCreateNewFile.invoke()
    override fun onDismissNewFile() = onDismissNewFile.invoke()
    override fun onRequestRename() = onRequestRename.invoke()
    override fun onRenameFileNameChange(name: String) = onRenameFileNameChange.invoke(name)
    override fun onConfirmRename() = onConfirmRename.invoke()
    override fun onDismissRename() = onDismissRename.invoke()
    override fun onRequestDelete() = onRequestDelete.invoke()
    override fun onConfirmDelete() = onConfirmDelete.invoke()
    override fun onDismissDelete() = onDismissDelete.invoke()
    override fun onTextChanged(text: String) = onTextChange.invoke(text)
    override fun onSaveFile() = onSave.invoke()
    override fun onPaste() = onPaste.invoke()
    override fun onPasteBelow() = onPasteBelow.invoke()
    override fun onCopy() = onCopy.invoke()
    override fun onUndo() = onUndo.invoke()
    override fun onEnterLineSelectionMode() = onEnterLineSelectionMode.invoke()
    override fun onExitLineSelectionMode() = onExitLineSelectionMode.invoke()
    override fun onToggleLineSelection(index: Int) = onToggleLineSelection.invoke(index)
    override fun onSelectAllLines() = onSelectAllLines.invoke()
    override fun onDeselectAllLines() = onDeselectAllLines.invoke()
    override fun onComposeDynamicPrompt() = onComposeDynamicPrompt.invoke()
    override fun onConfirmPendingSave() = onConfirmPendingSave.invoke()
    override fun onConfirmPendingDiscard() = onConfirmPendingDiscard.invoke()
    override fun onCancelPending() = onCancelPending.invoke()

    override fun requestClassify() = onRequestClassify.invoke()
    override fun onClassifyCriteriaChange(value: String) = onClassifyCriteriaChange.invoke(value)
    override fun onClassifyProviderSelected(provider: AnalysisProvider) = onClassifyProviderSelected.invoke(provider)
    override fun onClassifyModelSelected(modelId: String) = onClassifyModelSelected.invoke(modelId)
    override fun dismissClassifyCriteriaDialog() = onDismissClassifyCriteria.invoke()
    override fun runClassify() = onRunClassify.invoke()
    override fun dismissClassifyPreview() = onDismissClassifyPreview.invoke()
    override fun onClassifyFileNameChange(index: Int, value: String) = onClassifyFileNameChange.invoke(index, value)
    override fun onToggleClassifyFileNameEdit(index: Int) = onToggleClassifyFileNameEdit.invoke(index)
    override fun saveClassifyResult(overwrite: Boolean) = onSaveClassifyResult.invoke()
    override fun confirmClassifyOverwrite() = onConfirmClassifyOverwrite.invoke()
    override fun dismissClassifyOverwrite() = onDismissClassifyOverwrite.invoke()
}

internal data class AnalysisAppActions(
    val onClearFocus: () -> Unit,
    val onSourcePromptChange: (String) -> Unit,
    val onImportFromAutomation: () -> Unit,
    val onCategorySelected: (AnalysisCategory) -> Unit,
    val onClearTargetSegment: () -> Unit,
    val onGenerate: () -> Unit,
    val onGenerateTxt: () -> Unit,
    val onCancelWork: () -> Unit,
    val onRequestResetSession: () -> Unit,
    val onConfirmResetSession: () -> Unit,
    val onDismissResetSession: () -> Unit,
    val onTxtCountChange: (Int) -> Unit,
    val onToggleDirection: (String) -> Unit,
    val onCustomHintChange: (String) -> Unit,
    val onResultFileNameChange: (String) -> Unit,
    val onApplyCandidate: (Int) -> Unit,
    val onCopyCandidate: (Int) -> Unit,
    val onRestoreOriginalPrompt: () -> Unit,
    val onCopyResults: () -> Unit,
    val onSaveResults: () -> Unit,
    val onConfirmOverwrite: () -> Unit,
    val onDismissOverwrite: () -> Unit,
    val onShowKeyDialog: () -> Unit,
    val onDismissKeyDialog: () -> Unit,
    val onKeyLabelChange: (String) -> Unit,
    val onKeyValueChange: (String) -> Unit,
    val onRoleProviderSelected: (AnalysisModelRole, AnalysisProvider) -> Unit,
    val onRoleModelSelected: (AnalysisModelRole, String) -> Unit,
    val onStartGrokLogin: () -> Unit,
    val onCancelGrokLogin: () -> Unit,
    val onLogoutGrok: () -> Unit,
    val onOpenGrokLoginUrl: (String) -> Unit,
    val onAddApiKey: () -> Unit,
    val onDeleteApiKey: (String) -> Unit,
    val onActivateApiKey: (String) -> Unit,
    val onStartEditApiKey: (GeminiApiKeySummary) -> Unit,
    val onEditKeyLabelChange: (String) -> Unit,
    val onCancelEditApiKey: () -> Unit,
    val onUpdateKeyLabel: () -> Unit
) : AnalysisScreenActions {
    override fun onClearFocus() = onClearFocus.invoke()
    override fun onSourcePromptChange(value: String) = onSourcePromptChange.invoke(value)
    override fun onImportFromAutomation() = onImportFromAutomation.invoke()
    override fun onCategorySelected(category: AnalysisCategory) = onCategorySelected.invoke(category)
    override fun onClearTargetSegment() = onClearTargetSegment.invoke()
    override fun onGenerate() = onGenerate.invoke()
    override fun onGenerateTxt() = onGenerateTxt.invoke()
    override fun onCancelWork() = onCancelWork.invoke()
    override fun onRequestResetSession() = onRequestResetSession.invoke()
    override fun onConfirmResetSession() = onConfirmResetSession.invoke()
    override fun onDismissResetSession() = onDismissResetSession.invoke()
    override fun onTxtCountChange(value: Int) = onTxtCountChange.invoke(value)
    override fun onToggleDirection(id: String) = onToggleDirection.invoke(id)
    override fun onCustomHintChange(value: String) = onCustomHintChange.invoke(value)
    override fun onResultFileNameChange(value: String) = onResultFileNameChange.invoke(value)
    override fun onApplyCandidate(index: Int) = onApplyCandidate.invoke(index)
    override fun onCopyCandidate(index: Int) = onCopyCandidate.invoke(index)
    override fun onRestoreOriginalPrompt() = onRestoreOriginalPrompt.invoke()
    override fun onCopyResults() = onCopyResults.invoke()
    override fun onSaveResults() = onSaveResults.invoke()
    override fun onConfirmOverwrite() = onConfirmOverwrite.invoke()
    override fun onDismissOverwrite() = onDismissOverwrite.invoke()
    override fun onRoleProviderSelected(role: AnalysisModelRole, provider: AnalysisProvider) = onRoleProviderSelected.invoke(role, provider)
    override fun onRoleModelSelected(role: AnalysisModelRole, modelId: String) = onRoleModelSelected.invoke(role, modelId)
    override fun onStartGrokLogin() = onStartGrokLogin.invoke()
    override fun onCancelGrokLogin() = onCancelGrokLogin.invoke()
    override fun onLogoutGrok() = onLogoutGrok.invoke()
    override fun onOpenGrokLoginUrl(url: String) = onOpenGrokLoginUrl.invoke(url)
    override fun onShowKeyDialog() = onShowKeyDialog.invoke()
    override fun onDismissKeyDialog() = onDismissKeyDialog.invoke()
    override fun onKeyLabelChange(value: String) = onKeyLabelChange.invoke(value)
    override fun onKeyValueChange(value: String) = onKeyValueChange.invoke(value)
    override fun onAddApiKey() = onAddApiKey.invoke()
    override fun onDeleteApiKey(id: String) = onDeleteApiKey.invoke(id)
    override fun onActivateApiKey(id: String) = onActivateApiKey.invoke(id)
    override fun onStartEditApiKey(key: GeminiApiKeySummary) = onStartEditApiKey.invoke(key)
    override fun onEditKeyLabelChange(value: String) = onEditKeyLabelChange.invoke(value)
    override fun onCancelEditApiKey() = onCancelEditApiKey.invoke()
    override fun onUpdateKeyLabel() = onUpdateKeyLabel.invoke()
}

@Composable
internal fun AutomationApp(
    selectedTab: MainTab,
    mainUiState: AutomationUiState,
    automationBarUiState: AutomationBarUiState,
    promptTemplateState: TextFieldState,
    analysisUiState: AnalysisUiState,
    analysisPromptState: TextFieldState,
    wildcardUiState: WildcardUiState,
    automationActions: AutomationAppActions,
    analysisActions: AnalysisAppActions,
    wildcardActions: WildcardAppActions
) {
    val tabs = remember(
        mainUiState,
        automationBarUiState,
        promptTemplateState,
        analysisUiState,
        analysisPromptState,
        wildcardUiState,
        automationActions,
        analysisActions,
        wildcardActions
    ) {
        listOf(
            MainTabPage(MainTab.AUTOMATION) {
                AutomationScreen(
                    uiState = mainUiState,
                    automationBarUiState = automationBarUiState,
                    promptTemplateState = promptTemplateState,
                    actions = automationActions
                )
            },
            MainTabPage(MainTab.ANALYSIS) {
                AnalysisScreen(
                    uiState = analysisUiState,
                    sourcePromptState = analysisPromptState,
                    actions = analysisActions
                )
            },
            MainTabPage(MainTab.WILDCARD) {
                WildcardScreen(
                    uiState = wildcardUiState,
                    environmentStatus = mainUiState.environmentStatus,
                    environmentSetupInfo = mainUiState.environmentSetupInfo,
                    actions = wildcardActions
                )
            }
        )
    }

    MainTabbedScreen(
        selectedTab = selectedTab,
        onSelectTab = automationActions.onSelectTab,
        onShowSettings = automationActions.onShowSettings,
        tabs = tabs
    )

    SettingsDialogHost(
        uiState = mainUiState,
        actions = automationActions
    )
}
