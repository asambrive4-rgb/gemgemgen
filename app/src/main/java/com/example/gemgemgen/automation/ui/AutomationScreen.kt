package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
    onReplaceSelectedParagraph: (String) -> Unit,
    onImportFromClipboard: () -> Unit,
    onCloseGeminiApp: () -> Unit,
    onTerminateGeminiApp: () -> Unit,
    onRepeatCountChange: (String) -> Unit,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    onToggleRecentLogs: () -> Unit
) {
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

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
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    showPromptActions = !isKeyboardVisible,
                    onTargetAppSelected = onTargetAppSelected,
                    onPromptTemplateChange = onPromptTemplateChange,
                    onCloseGeminiApp = onCloseGeminiApp,
                    onTerminateGeminiApp = onTerminateGeminiApp,
                    onUndoPromptEdit = onUndoPromptEdit,
                    onToggleParagraphSelectionMode = onToggleParagraphSelectionMode,
                    onParagraphOffsetSelected = onParagraphOffsetSelected,
                    onDeleteSelectedParagraph = onDeleteSelectedParagraph,
                    onReplaceSelectedParagraph = onReplaceSelectedParagraph,
                    onImportFromClipboard = onImportFromClipboard
                )

                if (!isKeyboardVisible) {
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
                } else {
                    Spacer(modifier = Modifier.height(144.dp))
                }
            }

            if (isKeyboardVisible) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PromptActionRow(
                            canCloseGemini = uiState.canCloseGemini,
                            isClosingGemini = uiState.isClosingGemini,
                            canUndoPromptEdit = uiState.canUndoPromptEdit,
                            isTargetSelectionEnabled = !uiState.isRunning,
                            isParagraphSelectionMode = uiState.isParagraphSelectionMode,
                            onCloseGeminiApp = onCloseGeminiApp,
                            onTerminateGeminiApp = onTerminateGeminiApp,
                            onUndoPromptEdit = onUndoPromptEdit,
                            onToggleParagraphSelectionMode = onToggleParagraphSelectionMode,
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
                    }
                }
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
            onReplaceSelectedParagraph = {},
            onImportFromClipboard = {},
            onCloseGeminiApp = {},
            onTerminateGeminiApp = {},
            onRepeatCountChange = {},
            onRunMvp = {},
            onCancelAutomation = {},
            onToggleRecentLogs = {}
        )
    }
}

