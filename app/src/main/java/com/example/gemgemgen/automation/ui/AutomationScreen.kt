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

/** 스크롤 본문·키보드 하단 고정 패널이 같은 가로 폭을 쓰도록 공통 패딩 */
private val AutomationScreenContentPadding = 8.dp

/** 액션 줄·시작 바·섹션 사이 간격 */
private val AutomationScreenSectionSpacing = 5.dp

@Composable
internal fun AutomationScreen(
    uiState: MainUiState,
    automationBarUiState: AutomationBarUiState,
    promptTemplateState: TextFieldState,
    onClearFocus: () -> Unit,
    onHideSettings: () -> Unit,
    onConfirmAccessibilityPrompt: () -> Unit,
    onDismissAccessibilityPromptToSettings: () -> Unit,
    onRefreshStatus: () -> Unit,
    onSelectWildcardFolder: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onTargetAppSelected: (AutomationTargetApp) -> Unit,
    onPromptTemplateChange: (String) -> Unit,
    onWildcardTokenSuggestionClick: (String) -> Unit = {},
    onUndoPromptEdit: () -> Unit,
    onInsertSystemInstruction: () -> Unit,
    onParagraphOffsetSelected: (Int) -> Unit,
    onDeleteSelectedParagraph: () -> Unit,
    onReplaceSelectedParagraph: (String) -> Unit,
    onImportFromClipboard: () -> Unit,
    onCopyPromptToClipboard: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onCloseGeminiApp: () -> Unit,
    onTerminateGeminiApp: () -> Unit,
    onCleanDeviceMemory: () -> Unit,
    onTerminateSelfApp: () -> Unit,
    onRepeatCountChange: (String) -> Unit,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit
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
                    .padding(AutomationScreenContentPadding),
                verticalArrangement = Arrangement.spacedBy(AutomationScreenSectionSpacing)
            ) {

                PromptSection(
                    promptTemplateState = promptTemplateState,
                    selectedTargetApp = uiState.selectedTargetApp,
                    isTargetSelectionEnabled = !uiState.isRunning,
                    isParagraphSelectionMode = uiState.isParagraphSelectionMode,
                    canUndoPromptEdit = uiState.canUndoPromptEdit,
                    canCopyPrompt = uiState.hasPromptTemplate && !uiState.isRunning,
                    canCloseGemini = uiState.canCloseGemini,
                    canCloseSelfApp = uiState.canCloseSelfApp,
                    isClosingGemini = uiState.isClosingGemini,
                    geminiCloseMessage = uiState.geminiCloseMessage,
                    canCleanMemory = uiState.canCleanMemory,
                    isCleaningMemory = uiState.isCleaningMemory,
                    memoryCleanupMessage = uiState.memoryCleanupMessage,
                    selectedParagraphRange = uiState.selectedParagraphRange,
                    paragraphSelectionMessage = uiState.paragraphSelectionMessage,
                    wildcardTokenCandidates = uiState.wildcardTokenCandidates,
                    showPromptActions = !isKeyboardVisible,
                    onTargetAppSelected = onTargetAppSelected,
                    onPromptTemplateChange = onPromptTemplateChange,
                    onWildcardTokenSuggestionClick = onWildcardTokenSuggestionClick,
                    onCloseGeminiApp = onCloseGeminiApp,
                    onTerminateGeminiApp = onTerminateGeminiApp,
                    onCleanDeviceMemory = onCleanDeviceMemory,
                    onTerminateSelfApp = onTerminateSelfApp,
                    onUndoPromptEdit = onUndoPromptEdit,
                    onInsertSystemInstruction = onInsertSystemInstruction,
                    onParagraphOffsetSelected = onParagraphOffsetSelected,
                    onDeleteSelectedParagraph = onDeleteSelectedParagraph,
                    onReplaceSelectedParagraph = onReplaceSelectedParagraph,
                    onImportFromClipboard = onImportFromClipboard,
                    onCopyPromptToClipboard = onCopyPromptToClipboard,
                    onPasteFromClipboard = onPasteFromClipboard
                )

                if (!isKeyboardVisible) {
                    AutomationActionBar(
                        repeatCountText = uiState.repeatCountText,
                        onRepeatCountChange = onRepeatCountChange,
                        onRunMvp = onRunMvp,
                        onCancelAutomation = onCancelAutomation,
                        canRun = uiState.canRun,
                        isRunning = uiState.isRunning,
                        automationState = automationBarUiState.automationState
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
                            .padding(AutomationScreenContentPadding),
                        verticalArrangement = Arrangement.spacedBy(AutomationScreenSectionSpacing)
                    ) {
                        PromptActionRow(
                            canCloseGemini = uiState.canCloseGemini,
                            canCloseSelfApp = uiState.canCloseSelfApp,
                            isClosingGemini = uiState.isClosingGemini,
                            canCleanMemory = uiState.canCleanMemory,
                            isCleaningMemory = uiState.isCleaningMemory,
                            canUndoPromptEdit = uiState.canUndoPromptEdit,
                            canCopyPrompt = uiState.hasPromptTemplate &&
                                !uiState.isRunning,
                            isTargetSelectionEnabled = !uiState.isRunning,
                            onCloseGeminiApp = onCloseGeminiApp,
                            onTerminateGeminiApp = onTerminateGeminiApp,
                            onCleanDeviceMemory = onCleanDeviceMemory,
                            onTerminateSelfApp = onTerminateSelfApp,
                            onUndoPromptEdit = onUndoPromptEdit,
                            onInsertSystemInstruction = onInsertSystemInstruction,
                            onImportFromClipboard = onImportFromClipboard,
                            onCopyPromptToClipboard = onCopyPromptToClipboard,
                            onPasteFromClipboard = onPasteFromClipboard
                        )

                        AutomationActionBar(
                            repeatCountText = uiState.repeatCountText,
                            onRepeatCountChange = onRepeatCountChange,
                            onRunMvp = onRunMvp,
                            onCancelAutomation = onCancelAutomation,
                            canRun = uiState.canRun,
                            isRunning = uiState.isRunning,
                            automationState = automationBarUiState.automationState
                        )
                    }
                }
            }
            if (uiState.showAccessibilityPrompt) {
                AccessibilityPromptDialog(
                    onConfirm = onConfirmAccessibilityPrompt,
                    onDismissToSettings = onDismissAccessibilityPromptToSettings
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
            automationBarUiState = AutomationBarUiState(),
            promptTemplateState = TextFieldState(),
            onClearFocus = {},
            onHideSettings = {},
            onConfirmAccessibilityPrompt = {},
            onDismissAccessibilityPromptToSettings = {},
            onRefreshStatus = {},
            onSelectWildcardFolder = {},
            onOpenAccessibilitySettings = {},
            onTargetAppSelected = {},
            onPromptTemplateChange = {},
            onUndoPromptEdit = {},
            onInsertSystemInstruction = {},
            onParagraphOffsetSelected = {},
            onDeleteSelectedParagraph = {},
            onReplaceSelectedParagraph = {},
            onImportFromClipboard = {},
            onCopyPromptToClipboard = {},
            onPasteFromClipboard = {},
            onCloseGeminiApp = {},
            onTerminateGeminiApp = {},
            onCleanDeviceMemory = {},
            onTerminateSelfApp = {},
            onRepeatCountChange = {},
            onRunMvp = {},
            onCancelAutomation = {}
        )
    }
}
