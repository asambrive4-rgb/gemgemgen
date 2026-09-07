package com.example.gemgemgen.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.ui.AnalysisScreen
import com.example.gemgemgen.analysis.ui.AnalysisUiState
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.ui.AutomationBarUiState
import com.example.gemgemgen.automation.ui.AutomationScreen
import com.example.gemgemgen.automation.ui.MainUiState
import com.example.gemgemgen.automation.ui.SettingsDialogHost
import com.example.gemgemgen.ui.theme.AppThemeMode
import com.example.gemgemgen.ui.theme.AppThemePalette
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.ui.WildcardManagerScreen
import com.example.gemgemgen.wildcard.ui.WildcardManagerUiState
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
    val onPromptTemplateChange: (String) -> Unit,
    val onWildcardTokenSuggestionClick: (String) -> Unit,
    val onUndoPromptEdit: () -> Unit,
    val onInsertSystemInstruction: () -> Unit,
    val onParagraphOffsetSelected: (Int) -> Unit,
    val onDeleteSelectedParagraph: () -> Unit,
    val onReplaceSelectedParagraph: (String) -> Unit,
    val onImportFromClipboard: () -> Unit,
    val onCopyPromptToClipboard: () -> Unit,
    val onPasteFromClipboard: () -> Unit,
    val onCloseGeminiApp: () -> Unit,
    val onTerminateGeminiApp: () -> Unit,
    val onCleanDeviceMemory: () -> Unit,
    val onTerminateSelfApp: () -> Unit,
    val onRepeatCountChange: (String) -> Unit,
    val onRunAutomation: () -> Unit,
    val onCancelAutomation: () -> Unit,
    val onAutomationModeSelected: (AutomationMode) -> Unit,
    val onPairRemoteDevice: (String) -> Unit,
    val onOpenPromptHistory: () -> Unit = {},
    val onClosePromptHistory: () -> Unit = {},
    val onSelectPromptHistoryItem: (PromptHistoryItem) -> Unit = {},
    val onClearPromptHistory: () -> Unit = {}
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
)

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
)

@Composable
internal fun AutomationApp(
    selectedTab: MainTab,
    mainUiState: MainUiState,
    automationBarUiState: AutomationBarUiState,
    promptTemplateState: TextFieldState,
    analysisUiState: AnalysisUiState,
    analysisPromptState: TextFieldState,
    wildcardUiState: WildcardManagerUiState,
    automationActions: AutomationAppActions,
    analysisActions: AnalysisAppActions,
    wildcardActions: WildcardAppActions
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
                    onConfirmAccessibilityPrompt =
                        automationActions.onConfirmAccessibilityPrompt,
                    onDismissAccessibilityPromptToSettings =
                        automationActions.onDismissAccessibilityPromptToSettings,
                    onRefreshStatus = automationActions.onRefreshStatus,
                    onSelectWildcardFolder = automationActions.onSelectWildcardFolder,
                    onSelectSafWildcardFolder = automationActions.onSelectSafWildcardFolder,
                    onOpenWildcardStorageSettings =
                        automationActions.onOpenWildcardStorageSettings,
                    onOpenAccessibilitySettings =
                        automationActions.onOpenAccessibilitySettings,
                    onTargetAppSelected = automationActions.onTargetAppSelected,
                    onPromptTemplateChange = automationActions.onPromptTemplateChange,
                    onWildcardTokenSuggestionClick =
                        automationActions.onWildcardTokenSuggestionClick,
                    onUndoPromptEdit = automationActions.onUndoPromptEdit,
                    onInsertSystemInstruction =
                        automationActions.onInsertSystemInstruction,
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
                    onCleanDeviceMemory = automationActions.onCleanDeviceMemory,
                    onTerminateSelfApp = automationActions.onTerminateSelfApp,
                    onRepeatCountChange = automationActions.onRepeatCountChange,
                    onRunMvp = automationActions.onRunAutomation,
                    onCancelAutomation = automationActions.onCancelAutomation,
                    onAutomationModeSelected = automationActions.onAutomationModeSelected,
                    onPairRemoteDevice = automationActions.onPairRemoteDevice,
                    onOpenPromptHistory = automationActions.onOpenPromptHistory,
                    onClosePromptHistory = automationActions.onClosePromptHistory,
                    onSelectPromptHistoryItem = automationActions.onSelectPromptHistoryItem,
                    onClearPromptHistory = automationActions.onClearPromptHistory,
                    onSelectThemePalette = automationActions.onSelectThemePalette,
                    onSelectThemeMode = automationActions.onSelectThemeMode
                )
            },
            MainTabPage(MainTab.ANALYSIS) {
                AnalysisScreen(
                    uiState = analysisUiState,
                    sourcePromptState = analysisPromptState,
                    onClearFocus = analysisActions.onClearFocus,
                    onSourcePromptChange = analysisActions.onSourcePromptChange,
                    onImportFromAutomation = analysisActions.onImportFromAutomation,
                    onCategorySelected = analysisActions.onCategorySelected,
                    onClearTargetSegment = analysisActions.onClearTargetSegment,
                    onGenerate = analysisActions.onGenerate,
                    onGenerateTxt = analysisActions.onGenerateTxt,
                    onCancelWork = analysisActions.onCancelWork,
                    onRequestResetSession = analysisActions.onRequestResetSession,
                    onConfirmResetSession = analysisActions.onConfirmResetSession,
                    onDismissResetSession = analysisActions.onDismissResetSession,
                    onTxtCountChange = analysisActions.onTxtCountChange,
                    onToggleDirection = analysisActions.onToggleDirection,
                    onCustomHintChange = analysisActions.onCustomHintChange,
                    onResultFileNameChange = analysisActions.onResultFileNameChange,
                    onApplyCandidate = analysisActions.onApplyCandidate,
                    onCopyCandidate = analysisActions.onCopyCandidate,
                    onRestoreOriginalPrompt = analysisActions.onRestoreOriginalPrompt,
                    onCopyResults = analysisActions.onCopyResults,
                    onSaveResults = analysisActions.onSaveResults,
                    onConfirmOverwrite = analysisActions.onConfirmOverwrite,
                    onDismissOverwrite = analysisActions.onDismissOverwrite,
                    onShowKeyDialog = analysisActions.onShowKeyDialog,
                    onDismissKeyDialog = analysisActions.onDismissKeyDialog,
                    onKeyLabelChange = analysisActions.onKeyLabelChange,
                    onKeyValueChange = analysisActions.onKeyValueChange,
                    onRoleProviderSelected = analysisActions.onRoleProviderSelected,
                    onRoleModelSelected = analysisActions.onRoleModelSelected,
                    onStartGrokLogin = analysisActions.onStartGrokLogin,
                    onCancelGrokLogin = analysisActions.onCancelGrokLogin,
                    onLogoutGrok = analysisActions.onLogoutGrok,
                    onOpenGrokLoginUrl = analysisActions.onOpenGrokLoginUrl,
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
                WildcardManagerScreen(
                    uiState = wildcardUiState,
                    environmentStatus = mainUiState.environmentStatus,
                    environmentSetupInfo = mainUiState.environmentSetupInfo,
                    onClearFocus = automationActions.onClearFocus,
                    onRefresh = wildcardActions.onRefresh,
                    onSelectFolder = wildcardActions.onSelectFolder,
                    onFileClick = wildcardActions.onFileClick,
                    onTextChange = wildcardActions.onTextChange,
                    onSave = wildcardActions.onSave,
                    onRequestNewFile = wildcardActions.onRequestNewFile,
                    onNewFileNameChange = wildcardActions.onNewFileNameChange,
                    onCreateNewFile = wildcardActions.onCreateNewFile,
                    onDismissNewFile = wildcardActions.onDismissNewFile,
                    onRequestDelete = wildcardActions.onRequestDelete,
                    onConfirmDelete = wildcardActions.onConfirmDelete,
                    onDismissDelete = wildcardActions.onDismissDelete,
                    onRequestRename = wildcardActions.onRequestRename,
                    onRenameFileNameChange = wildcardActions.onRenameFileNameChange,
                    onConfirmRename = wildcardActions.onConfirmRename,
                    onDismissRename = wildcardActions.onDismissRename,
                    onPaste = wildcardActions.onPaste,
                    onPasteBelow = wildcardActions.onPasteBelow,
                    onCopy = wildcardActions.onCopy,
                    onUndo = wildcardActions.onUndo,
                    onEnterLineSelectionMode = wildcardActions.onEnterLineSelectionMode,
                    onExitLineSelectionMode = wildcardActions.onExitLineSelectionMode,
                    onToggleLineSelection = wildcardActions.onToggleLineSelection,
                    onSelectAllLines = wildcardActions.onSelectAllLines,
                    onDeselectAllLines = wildcardActions.onDeselectAllLines,
                    onComposeDynamicPrompt = wildcardActions.onComposeDynamicPrompt,
                    onRequestClassify = wildcardActions.onRequestClassify,
                    onClassifyCriteriaChange = wildcardActions.onClassifyCriteriaChange,
                    onClassifyProviderSelected = wildcardActions.onClassifyProviderSelected,
                    onClassifyModelSelected = wildcardActions.onClassifyModelSelected,
                    onDismissClassifyCriteria = wildcardActions.onDismissClassifyCriteria,
                    onRunClassify = wildcardActions.onRunClassify,
                    onDismissClassifyPreview = wildcardActions.onDismissClassifyPreview,
                    onClassifyFileNameChange = wildcardActions.onClassifyFileNameChange,
                    onToggleClassifyFileNameEdit = wildcardActions.onToggleClassifyFileNameEdit,
                    onSaveClassifyResult = wildcardActions.onSaveClassifyResult,
                    onConfirmClassifyOverwrite = wildcardActions.onConfirmClassifyOverwrite,
                    onDismissClassifyOverwrite = wildcardActions.onDismissClassifyOverwrite,
                    onConfirmPendingSave = wildcardActions.onConfirmPendingSave,
                    onConfirmPendingDiscard = wildcardActions.onConfirmPendingDiscard,
                    onCancelPending = wildcardActions.onCancelPending
                )
            }
        )
    )

    SettingsDialogHost(
        showSettings = mainUiState.showSettings,
        showAccessibilityPrompt = mainUiState.showAccessibilityPrompt,
        status = mainUiState.environmentStatus,
        setupInfo = mainUiState.environmentSetupInfo,
        hasPromptTemplate = mainUiState.hasPromptTemplate,
        message = mainUiState.settingsMessage,
        error = mainUiState.settingsError,
        selectedThemePalette = mainUiState.selectedThemePalette,
        selectedThemeMode = mainUiState.selectedThemeMode,
        onSelectThemePalette = automationActions.onSelectThemePalette,
        onSelectThemeMode = automationActions.onSelectThemeMode,
        onDismiss = automationActions.onHideSettings,
        onConfirmAccessibilityPrompt = automationActions.onConfirmAccessibilityPrompt,
        onDismissAccessibilityPromptToSettings = automationActions.onDismissAccessibilityPromptToSettings,
        onRefresh = automationActions.onRefreshStatus,
        onSelectWildcardFolder = automationActions.onSelectWildcardFolder,
        onSelectSafWildcardFolder = automationActions.onSelectSafWildcardFolder,
        onOpenWildcardStorageSettings = automationActions.onOpenWildcardStorageSettings,
        onOpenAccessibilitySettings = automationActions.onOpenAccessibilitySettings
    )
}
