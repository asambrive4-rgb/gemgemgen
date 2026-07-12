package com.example.gemgemgen.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.ui.AnalysisScreen
import com.example.gemgemgen.analysis.ui.AnalysisUiState
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.ui.AutomationBarUiState
import com.example.gemgemgen.automation.ui.AutomationScreen
import com.example.gemgemgen.automation.ui.MainUiState
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.ui.WildcardManagerScreen
import com.example.gemgemgen.wildcard.ui.WildcardManagerUiState

internal data class AutomationAppActions(
    val onSelectTab: (MainTab) -> Unit,
    val onShowSettings: () -> Unit,
    val onClearFocus: () -> Unit,
    val onHideSettings: () -> Unit,
    val onRefreshStatus: () -> Unit,
    val onSelectWildcardFolder: () -> Unit,
    val onOpenAccessibilitySettings: () -> Unit,
    val onTargetAppSelected: (AutomationTargetApp) -> Unit,
    val onPromptTemplateChange: (String) -> Unit,
    val onUndoPromptEdit: () -> Unit,
    val onToggleParagraphSelectionMode: () -> Unit,
    val onParagraphOffsetSelected: (Int) -> Unit,
    val onDeleteSelectedParagraph: () -> Unit,
    val onReplaceSelectedParagraph: (String) -> Unit,
    val onImportFromClipboard: () -> Unit,
    val onCopyPromptToClipboard: () -> Unit,
    val onPasteFromClipboard: () -> Unit,
    val onCloseGeminiApp: () -> Unit,
    val onTerminateGeminiApp: () -> Unit,
    val onRepeatCountChange: (String) -> Unit,
    val onRunAutomation: () -> Unit,
    val onCancelAutomation: () -> Unit
)

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
    val onConfirmPendingSave: () -> Unit,
    val onConfirmPendingDiscard: () -> Unit,
    val onCancelPending: () -> Unit
)

internal data class AnalysisAppActions(
    val onClearFocus: () -> Unit,
    val onSourcePromptChange: (String) -> Unit,
    val onCategorySelected: (AnalysisCategory) -> Unit,
    val onApplyManualSelection: () -> Unit,
    val onClearTargetSegment: () -> Unit,
    val onAnalyzeAndMask: () -> Unit,
    val onGenerateTxt: () -> Unit,
    val onCancelWork: () -> Unit,
    val onTxtCountChange: (Int) -> Unit,
    val onToggleDirection: (String) -> Unit,
    val onCustomHintChange: (String) -> Unit,
    val onResultFileNameChange: (String) -> Unit,
    val onCopyResults: () -> Unit,
    val onSaveResults: () -> Unit,
    val onConfirmOverwrite: () -> Unit,
    val onDismissOverwrite: () -> Unit,
    val onShowKeyDialog: () -> Unit,
    val onDismissKeyDialog: () -> Unit,
    val onKeyLabelChange: (String) -> Unit,
    val onKeyValueChange: (String) -> Unit,
    val onModelSelected: (String) -> Unit,
    val onAddApiKey: () -> Unit,
    val onDeleteApiKey: (String) -> Unit,
    val onActivateApiKey: (String) -> Unit,
    val onStartEditApiKey: (GeminiApiKeySummary) -> Unit,
    val onEditKeyLabelChange: (String) -> Unit,
    val onCancelEditApiKey: () -> Unit,
    val onUpdateKeyLabel: () -> Unit
)

@Composable
internal fun AutomationApp(
    selectedTab: MainTab,
    mainUiState: MainUiState,
    automationBarUiState: AutomationBarUiState,
    promptTemplateState: TextFieldState,
    analysisUiState: AnalysisUiState,
    analysisPromptState: TextFieldState,
    wildcardUiState: WildcardManagerUiState?,
    automationActions: AutomationAppActions,
    analysisActions: AnalysisAppActions,
    wildcardActions: WildcardAppActions?
) {
    MainTabbedScreen(
        selectedTab = selectedTab,
        onSelectTab = automationActions.onSelectTab,
        onShowSettings = automationActions.onShowSettings,
        tabs = listOf(
            MainTabPage(MainTab.AUTOMATION) {
                AutomationScreen(
                    uiState = mainUiState,
                    automationBarUiState = automationBarUiState,
                    promptTemplateState = promptTemplateState,
                    onClearFocus = automationActions.onClearFocus,
                    onHideSettings = automationActions.onHideSettings,
                    onRefreshStatus = automationActions.onRefreshStatus,
                    onSelectWildcardFolder = automationActions.onSelectWildcardFolder,
                    onOpenAccessibilitySettings =
                        automationActions.onOpenAccessibilitySettings,
                    onTargetAppSelected = automationActions.onTargetAppSelected,
                    onPromptTemplateChange = automationActions.onPromptTemplateChange,
                    onUndoPromptEdit = automationActions.onUndoPromptEdit,
                    onToggleParagraphSelectionMode =
                        automationActions.onToggleParagraphSelectionMode,
                    onParagraphOffsetSelected =
                        automationActions.onParagraphOffsetSelected,
                    onDeleteSelectedParagraph =
                        automationActions.onDeleteSelectedParagraph,
                    onReplaceSelectedParagraph =
                        automationActions.onReplaceSelectedParagraph,
                    onImportFromClipboard = automationActions.onImportFromClipboard,
                    onCopyPromptToClipboard =
                        automationActions.onCopyPromptToClipboard,
                    onPasteFromClipboard = automationActions.onPasteFromClipboard,
                    onCloseGeminiApp = automationActions.onCloseGeminiApp,
                    onTerminateGeminiApp = automationActions.onTerminateGeminiApp,
                    onRepeatCountChange = automationActions.onRepeatCountChange,
                    onRunMvp = automationActions.onRunAutomation,
                    onCancelAutomation = automationActions.onCancelAutomation
                )
            },
            MainTabPage(MainTab.ANALYSIS) {
                AnalysisScreen(
                    uiState = analysisUiState,
                    sourcePromptState = analysisPromptState,
                    onClearFocus = analysisActions.onClearFocus,
                    onSourcePromptChange = analysisActions.onSourcePromptChange,
                    onCategorySelected = analysisActions.onCategorySelected,
                    onApplyManualSelection = analysisActions.onApplyManualSelection,
                    onClearTargetSegment = analysisActions.onClearTargetSegment,
                    onAnalyzeAndMask = analysisActions.onAnalyzeAndMask,
                    onGenerateTxt = analysisActions.onGenerateTxt,
                    onCancelWork = analysisActions.onCancelWork,
                    onTxtCountChange = analysisActions.onTxtCountChange,
                    onToggleDirection = analysisActions.onToggleDirection,
                    onCustomHintChange = analysisActions.onCustomHintChange,
                    onResultFileNameChange = analysisActions.onResultFileNameChange,
                    onCopyResults = analysisActions.onCopyResults,
                    onSaveResults = analysisActions.onSaveResults,
                    onConfirmOverwrite = analysisActions.onConfirmOverwrite,
                    onDismissOverwrite = analysisActions.onDismissOverwrite,
                    onShowKeyDialog = analysisActions.onShowKeyDialog,
                    onDismissKeyDialog = analysisActions.onDismissKeyDialog,
                    onKeyLabelChange = analysisActions.onKeyLabelChange,
                    onKeyValueChange = analysisActions.onKeyValueChange,
                    onModelSelected = analysisActions.onModelSelected,
                    onAddApiKey = analysisActions.onAddApiKey,
                    onDeleteApiKey = analysisActions.onDeleteApiKey,
                    onActivateApiKey = analysisActions.onActivateApiKey,
                    onStartEditApiKey = analysisActions.onStartEditApiKey,
                    onEditKeyLabelChange = analysisActions.onEditKeyLabelChange,
                    onCancelEditApiKey = analysisActions.onCancelEditApiKey,
                    onUpdateKeyLabel = analysisActions.onUpdateKeyLabel
                )
            },
            MainTabPage(MainTab.WILDCARD) {
                val state = wildcardUiState
                val actions = wildcardActions
                if (state != null && actions != null) {
                    WildcardManagerScreen(
                        uiState = state,
                        environmentStatus = mainUiState.environmentStatus,
                        environmentSetupInfo = mainUiState.environmentSetupInfo,
                        onClearFocus = automationActions.onClearFocus,
                        onRefresh = actions.onRefresh,
                        onSelectFolder = actions.onSelectFolder,
                        onFileClick = actions.onFileClick,
                        onTextChange = actions.onTextChange,
                        onSave = actions.onSave,
                        onRequestNewFile = actions.onRequestNewFile,
                        onNewFileNameChange = actions.onNewFileNameChange,
                        onCreateNewFile = actions.onCreateNewFile,
                        onDismissNewFile = actions.onDismissNewFile,
                        onRequestDelete = actions.onRequestDelete,
                        onConfirmDelete = actions.onConfirmDelete,
                        onDismissDelete = actions.onDismissDelete,
                        onRequestRename = actions.onRequestRename,
                        onRenameFileNameChange = actions.onRenameFileNameChange,
                        onConfirmRename = actions.onConfirmRename,
                        onDismissRename = actions.onDismissRename,
                        onPaste = actions.onPaste,
                        onPasteBelow = actions.onPasteBelow,
                        onCopy = actions.onCopy,
                        onUndo = actions.onUndo,
                        onConfirmPendingSave = actions.onConfirmPendingSave,
                        onConfirmPendingDiscard = actions.onConfirmPendingDiscard,
                        onCancelPending = actions.onCancelPending
                    )
                }
            }
        )
    )
}
