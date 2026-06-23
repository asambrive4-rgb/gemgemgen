package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.theme.GemgemgenTheme
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.automation.domain.AutomationTargetApp

@Composable
internal fun AutomationScreen(
    uiState: MainUiState,
    onClearFocus: () -> Unit,
    onHideSettings: () -> Unit,
    onRefreshStatus: () -> Unit,
    onSelectWildcardFolder: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onTargetAppSelected: (AutomationTargetApp) -> Unit,
    onPromptTemplateChange: (String) -> Unit,
    onImportFromClipboard: () -> Unit,
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
                    promptTemplate = uiState.promptTemplate,
                    selectedTargetApp = uiState.selectedTargetApp,
                    isTargetSelectionEnabled = !uiState.isRunning,
                    onTargetAppSelected = onTargetAppSelected,
                    onPromptTemplateChange = onPromptTemplateChange,
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
            onClearFocus = {},
            onHideSettings = {},
            onRefreshStatus = {},
            onSelectWildcardFolder = {},
            onOpenAccessibilitySettings = {},
            onTargetAppSelected = {},
            onPromptTemplateChange = {},
            onImportFromClipboard = {},
            onRepeatCountChange = {},
            onRunMvp = {},
            onCancelAutomation = {},
            onToggleRecentLogs = {}
        )
    }
}

