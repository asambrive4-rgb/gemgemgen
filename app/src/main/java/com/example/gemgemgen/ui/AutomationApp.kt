package com.example.gemgemgen.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import com.example.gemgemgen.automation.domain.AutomationTargetApp
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
    val onImportFromClipboard: () -> Unit,
    val onRepeatCountChange: (String) -> Unit,
    val onRunAutomation: () -> Unit,
    val onCancelAutomation: () -> Unit,
    val onToggleRecentLogs: () -> Unit
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

@Composable
internal fun AutomationApp(
    selectedTab: MainTab,
    mainUiState: MainUiState,
    promptTemplateState: TextFieldState,
    wildcardUiState: WildcardManagerUiState?,
    automationActions: AutomationAppActions,
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
                    promptTemplateState = promptTemplateState,
                    onClearFocus = automationActions.onClearFocus,
                    onHideSettings = automationActions.onHideSettings,
                    onRefreshStatus = automationActions.onRefreshStatus,
                    onSelectWildcardFolder = automationActions.onSelectWildcardFolder,
                    onOpenAccessibilitySettings =
                        automationActions.onOpenAccessibilitySettings,
                    onTargetAppSelected = automationActions.onTargetAppSelected,
                    onPromptTemplateChange = automationActions.onPromptTemplateChange,
                    onImportFromClipboard = automationActions.onImportFromClipboard,
                    onRepeatCountChange = automationActions.onRepeatCountChange,
                    onRunMvp = automationActions.onRunAutomation,
                    onCancelAutomation = automationActions.onCancelAutomation,
                    onToggleRecentLogs = automationActions.onToggleRecentLogs
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
