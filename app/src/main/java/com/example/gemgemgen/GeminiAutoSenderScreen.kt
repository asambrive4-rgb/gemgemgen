package com.example.gemgemgen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.theme.GemgemgenTheme

@Composable
internal fun GeminiAutoSenderScreen(
    uiState: MainUiState,
    onClearFocus: () -> Unit,
    onShowSettings: () -> Unit,
    onHideSettings: () -> Unit,
    onRefreshStatus: () -> Unit,
    onSelectWildcardFolder: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
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
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Final)
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                        if (up != null && !down.isConsumed && !up.isConsumed) {
                            onClearFocus()
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gemini Auto Sender",
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = onShowSettings) {
                        Text("설정")
                    }
                }

                PromptSection(
                    promptTemplate = uiState.promptTemplate,
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
private fun GeminiAutoSenderAppPreview() {
    GemgemgenTheme {
        GeminiAutoSenderScreen(
            uiState = MainUiState(),
            onClearFocus = {},
            onShowSettings = {},
            onHideSettings = {},
            onRefreshStatus = {},
            onSelectWildcardFolder = {},
            onOpenAccessibilitySettings = {},
            onPromptTemplateChange = {},
            onImportFromClipboard = {},
            onRepeatCountChange = {},
            onRunMvp = {},
            onCancelAutomation = {},
            onToggleRecentLogs = {}
        )
    }
}
