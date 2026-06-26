package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.theme.GemgemgenTheme
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.automation.domain.AutomationTargetApp

@Composable
internal fun AutomationScreen(
    uiState: MainUiState,
    promptTemplateState: TextFieldState,
    onClearFocus: () -> Unit,
    onHideSettings: () -> Unit,
    onRefreshStatus: () -> Unit,
    onSelectWildcardFolder: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onTargetAppSelected: (AutomationTargetApp) -> Unit,
    onPromptTemplateChange: (String) -> Unit,
    onUndoPromptEdit: () -> Unit,
    onToggleParagraphSelectionMode: () -> Unit,
    onParagraphOffsetSelected: (Int) -> Unit,
    onDeleteSelectedParagraph: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onCloseGeminiApp: () -> Unit,
    onRepeatCountChange: (String) -> Unit,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    onToggleRecentLogs: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .clearFocusOnOutsideTap(onClearFocus)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                PromptSection(
                    promptTemplateState = promptTemplateState,
                    selectedTargetApp = uiState.selectedTargetApp,
                    isTargetSelectionEnabled = !uiState.isRunning,
                    isParagraphSelectionMode = uiState.isParagraphSelectionMode,
                    canUndoPromptEdit = uiState.canUndoPromptEdit,
                    canCloseGemini = uiState.canCloseGemini,
                    isClosingGemini = uiState.isClosingGemini,
                    geminiCloseMessage = uiState.geminiCloseMessage,
                    selectedParagraphRange = uiState.selectedParagraphRange,
                    paragraphSelectionMessage = uiState.paragraphSelectionMessage,
                    onTargetAppSelected = onTargetAppSelected,
                    onPromptTemplateChange = onPromptTemplateChange,
                    onCloseGeminiApp = onCloseGeminiApp,
                    onUndoPromptEdit = onUndoPromptEdit,
                    onToggleParagraphSelectionMode = onToggleParagraphSelectionMode,
                    onParagraphOffsetSelected = onParagraphOffsetSelected,
                    onDeleteSelectedParagraph = onDeleteSelectedParagraph,
                    onImportFromClipboard = onImportFromClipboard
                )

                AutomationActionBar(
                    repeatCountText = uiState.repeatCountText,
                    onRepeatCountChange = onRepeatCountChange,
                    onRunMvp = onRunMvp,
                    onCancelAutomation = onCancelAutomation,
                    canRun = uiState.canRun,
                    isRunning = uiState.isRunning,
                    automationState = uiState.automationState
                )

                RecentLogsSection(
                    recentLogs = uiState.recentLogs,
                    showRecentLogs = uiState.showRecentLogs,
                    onToggleRecentLogs = onToggleRecentLogs
                )
            }

            if (uiState.showSettings) {
                StatusSettingsDialog(
                    status = uiState.environmentStatus,
                    setupInfo = uiState.environmentSetupInfo,
                    hasPromptTemplate = uiState.hasPromptTemplate,
                    message = uiState.settingsMessage,
                    error = uiState.settingsError,
                    onDismiss = onHideSettings,
                    onRefresh = onRefreshStatus,
                    onSelectWildcardFolder = onSelectWildcardFolder,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AutomationAppPreview() {
    GemgemgenTheme {
        AutomationScreen(
            uiState = MainUiState(),
            promptTemplateState = TextFieldState(),
            onClearFocus = {},
            onHideSettings = {},
            onRefreshStatus = {},
            onSelectWildcardFolder = {},
            onOpenAccessibilitySettings = {},
            onTargetAppSelected = {},
            onPromptTemplateChange = {},
            onUndoPromptEdit = {},
            onToggleParagraphSelectionMode = {},
            onParagraphOffsetSelected = {},
            onDeleteSelectedParagraph = {},
            onImportFromClipboard = {},
            onCloseGeminiApp = {},
            onRepeatCountChange = {},
            onRunMvp = {},
            onCancelAutomation = {},
            onToggleRecentLogs = {}
        )
    }
}

