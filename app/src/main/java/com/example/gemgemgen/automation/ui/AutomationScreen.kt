// 역할: 가상 키보드 반응형 모바일 에디터 집중 모드와 프롬프트 입력 및 자동화 제어 화면을 구성합니다.
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.theme.GemgemgenTheme
import com.example.gemgemgen.ui.theme.AppThemePalette
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.automation.usecase.ResolveWildcardAutocompleteUseCase
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.ui.AutomationModePairDialog
import com.example.gemgemgen.remote.ui.AutomationModePanel

/** 스크롤 본문·키보드 하단 고정 패널이 같은 가로 폭을 쓰도록 공통 패딩 */
private val AutomationScreenContentPadding = 8.dp

/** 액션 줄·시작 바·섹션 사이 간격 */
private val AutomationScreenSectionSpacing = 5.dp

@Composable
internal fun AutomationScreen(
    uiState: AutomationUiState,
    automationBarUiState: AutomationBarUiState,
    promptTemplateState: TextFieldState,
    onClearFocus: () -> Unit,
    onHideSettings: () -> Unit = {},
    onConfirmAccessibilityPrompt: () -> Unit = {},
    onDismissAccessibilityPromptToSettings: () -> Unit = {},
    onRefreshStatus: () -> Unit = {},
    onSelectWildcardFolder: () -> Unit = {},
    onSelectSafWildcardFolder: () -> Unit = {},
    onOpenWildcardStorageSettings: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onTargetAppSelected: (AutomationTargetApp) -> Unit,
    onFlowImageCountSelected: (Int) -> Unit = {},
    onPromptTemplateChange: (String) -> Unit,
    onWildcardTokenSuggestionClick: (String) -> Unit = {},
    onNavigateHistoryBack: () -> Unit,
    onNavigateHistoryForward: () -> Unit,
    onInsertTopInstruction: () -> Unit = {},
    onInsertBottomInstruction: () -> Unit = {},
    onOpenInstructionConfigDialog: (com.example.gemgemgen.automation.domain.InstructionTab) -> Unit = {},
    onCloseInstructionConfigDialog: () -> Unit = {},
    onSaveInstructionConfig: (com.example.gemgemgen.automation.domain.PromptInstructionConfig) -> Unit = {},
    onToggleParagraphSelectionMode: () -> Unit = {},
    onParagraphOffsetSelected: (Int) -> Unit,
    onDeleteSelectedParagraph: () -> Unit,
    onReplaceSelectedParagraph: (String) -> Unit,
    onImportFromClipboard: () -> Unit,
    onCopyPromptToClipboard: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onCloseGeminiApp: () -> Unit,
    onCleanDeviceMemory: () -> Unit,
    onTerminateSelfApp: () -> Unit,
    onRepeatCountChange: (String) -> Unit,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    onAutomationModeSelected: (AutomationMode) -> Unit,
    onPairRemoteDevice: (String) -> Unit,
    onOpenPromptHistory: () -> Unit = {},
    onClosePromptHistory: () -> Unit = {},
    onSelectPromptHistoryItem: (PromptHistoryItem) -> Unit = {},
    onClearPromptHistory: () -> Unit = {},
    onSelectThemePalette: (AppThemePalette) -> Unit = {},
    onSelectThemeMode: (com.example.gemgemgen.ui.theme.AppThemeMode) -> Unit = {},
    onOpenGeminiAccountPicker: () -> Unit = {},
    onRunVariation: (String?) -> Unit = {},
    onOpenVariationPromptConfigDialog: () -> Unit = {},
    onCloseVariationPromptConfigDialog: () -> Unit = {},
    onSaveVariationPromptConfig: (VariationPromptConfig) -> Unit = {}
) {
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val resolveWildcardAutocompleteUseCase = remember { ResolveWildcardAutocompleteUseCase() }
    val suggestionTokens = remember(
        promptTemplateState.text.toString(),
        promptTemplateState.selection,
        uiState.wildcardTokenCandidates,
        uiState.isParagraphSelectionMode,
        uiState.isRunning,
        resolveWildcardAutocompleteUseCase
    ) {
        resolveWildcardAutocompleteUseCase(
            text = promptTemplateState.text.toString(),
            selectionStart = promptTemplateState.selection.min,
            selectionEnd = promptTemplateState.selection.max,
            candidates = uiState.wildcardTokenCandidates,
            isParagraphSelectionMode = uiState.isParagraphSelectionMode,
            isEnabled = !uiState.isRunning
        )
    }
    var showPairDialog by remember { mutableStateOf(false) }
    val keyboardVariationSelectedTextAtPress = remember { mutableStateOf<String?>(null) }

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

                PromptEditorSection(
                    promptTemplateState = promptTemplateState,
                    selectedTargetApp = uiState.selectedTargetApp,
                    isTargetSelectionEnabled = !uiState.isRunning,
                    isParagraphSelectionMode = uiState.isParagraphSelectionMode,
                    onToggleParagraphSelectionMode = onToggleParagraphSelectionMode,
                    canNavigateHistoryBack = uiState.canNavigateHistoryBack,
                    canNavigateHistoryForward = uiState.canNavigateHistoryForward,
                    isHistoryIndicatorVisible = uiState.isHistoryIndicatorVisible,
                    historyDotCount = uiState.historyDotCount,
                    activeHistoryDotIndex = uiState.activeHistoryDotIndex,
                    canCopyPrompt = uiState.hasPromptTemplate && !uiState.isRunning,
                    canCloseGemini = uiState.canCloseGemini,
                    canCloseSelfApp = uiState.canCloseSelfApp,
                    canCleanMemory = uiState.canCleanMemory,
                    isMaintenanceBusy = uiState.isMaintenanceBusy,
                    maintenanceMessage = uiState.maintenanceMessage,
                    selectedParagraphRange = uiState.selectedParagraphRange,
                    paragraphSelectionMessage = uiState.paragraphSelectionMessage,
                    suggestionTokens = suggestionTokens,
                    showPromptActions = !isKeyboardVisible,
                    showWildcardSuggestions = !isKeyboardVisible,
                    onTargetAppSelected = onTargetAppSelected,
                    flowImageCount = uiState.flowImageCount,
                    onFlowImageCountSelected = onFlowImageCountSelected,
                    onPromptTemplateChange = onPromptTemplateChange,
                    onWildcardTokenSuggestionClick = onWildcardTokenSuggestionClick,
                    onCloseGeminiApp = onCloseGeminiApp,
                    onCleanDeviceMemory = onCleanDeviceMemory,
                    onTerminateSelfApp = onTerminateSelfApp,
                    onNavigateHistoryBack = onNavigateHistoryBack,
                    onNavigateHistoryForward = onNavigateHistoryForward,
                    onInsertTopInstruction = onInsertTopInstruction,
                    onInsertBottomInstruction = onInsertBottomInstruction,
                    onOpenInstructionConfigDialog = onOpenInstructionConfigDialog,
                    onParagraphOffsetSelected = onParagraphOffsetSelected,
                    onDeleteSelectedParagraph = onDeleteSelectedParagraph,
                    onReplaceSelectedParagraph = onReplaceSelectedParagraph,
                    onImportFromClipboard = onImportFromClipboard,
                    onCopyPromptToClipboard = onCopyPromptToClipboard,
                    onPasteFromClipboard = onPasteFromClipboard,
                    onOpenPromptHistory = onOpenPromptHistory,
                    onOpenGeminiAccountPicker = onOpenGeminiAccountPicker,
                    showVariationButton = uiState.automationMode != AutomationMode.RECEIVER,
                    isVariationButtonEnabled = uiState.canInteractWithVariation,
                    variationAutomationState = uiState.variationAutomationState,
                    onRunVariation = { selectedText ->
                        onClearFocus()
                        onRunVariation(selectedText)
                    },
                    onOpenVariationPromptConfigDialog = onOpenVariationPromptConfigDialog
                )

                if (!isKeyboardVisible && uiState.automationMode != AutomationMode.RECEIVER) {
                    AutomationBottomBar(
                        repeatCountText = uiState.repeatCountText,
                        onRepeatCountChange = onRepeatCountChange,
                        onRunMvp = onRunMvp,
                        onCancelAutomation = onCancelAutomation,
                        canRun = uiState.canRun,
                        isRunning = uiState.isRunning,
                        automationState = automationBarUiState.automationState,
                        isRemoteSendMode = uiState.automationMode == AutomationMode.SENDER
                    )
                } else if (isKeyboardVisible) {
                    val bottomSpacerHeight = if (suggestionTokens.isNotEmpty()) 105.dp else 56.dp
                    Spacer(modifier = Modifier.height(bottomSpacerHeight))
                }

                if (!isKeyboardVisible) {
                    AutomationModePanel(
                        selectedMode = uiState.automationMode,
                        status = uiState.remoteAutomationStatus,
                        enabled = !uiState.isRunning,
                        onModeSelected = onAutomationModeSelected,
                        onRequestPair = { showPairDialog = true }
                    )
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
                        if (suggestionTokens.isNotEmpty()) {
                            WildcardTokenSuggestionBar(
                                tokens = suggestionTokens,
                                onTokenClick = onWildcardTokenSuggestionClick
                            )
                        }

                        KeyboardPromptAccessoryBar(
                            canRun = uiState.canRun,
                            isRunning = uiState.isRunning,
                            repeatCountText = uiState.repeatCountText,
                            onRepeatCountChange = onRepeatCountChange,
                            automationState = automationBarUiState.automationState,
                            isRemoteSendMode = uiState.automationMode == AutomationMode.SENDER,
                            onRunMvp = {
                                onClearFocus()
                                onRunMvp()
                            },
                            onCancelAutomation = {
                                onClearFocus()
                                onCancelAutomation()
                            },
                            canCopyPrompt = uiState.hasPromptTemplate && !uiState.isRunning,
                            isTargetSelectionEnabled = !uiState.isRunning,
                            onInsertTopInstruction = onInsertTopInstruction,
                            onInsertBottomInstruction = onInsertBottomInstruction,
                            onOpenInstructionConfigDialog = onOpenInstructionConfigDialog,
                            onImportFromClipboard = onImportFromClipboard,
                            onCopyPromptToClipboard = onCopyPromptToClipboard,
                            showVariationButton = uiState.automationMode != AutomationMode.RECEIVER,
                            isVariationButtonEnabled = uiState.canInteractWithVariation,
                            variationAutomationState = uiState.variationAutomationState,
                            onRunVariation = {
                                val selectedText = keyboardVariationSelectedTextAtPress.value
                                    ?: promptTemplateState.selectedTextOrNull()
                                keyboardVariationSelectedTextAtPress.value = null
                                onClearFocus()
                                onRunVariation(selectedText)
                            },
                            onVariationPointerDown = {
                                keyboardVariationSelectedTextAtPress.value =
                                    promptTemplateState.selectedTextOrNull()
                            },
                            onOpenVariationPromptConfigDialog = onOpenVariationPromptConfigDialog,
                            isParagraphSelectionMode = uiState.isParagraphSelectionMode,
                            onToggleParagraphSelectionMode = onToggleParagraphSelectionMode
                        )
                    }
                }
            }


            if (showPairDialog) {
                AutomationModePairDialog(
                    targetDeviceName = uiState.remoteAutomationStatus.discoveredDeviceName.ifBlank { "S25 FE" },
                    onConfirm = { code ->
                        onPairRemoteDevice(code)
                        showPairDialog = false
                    },
                    onDismiss = {
                        showPairDialog = false
                    }
                )
            }
            if (uiState.showPromptHistory) {
                PromptHistoryBottomSheet(
                    items = uiState.promptHistoryItems,
                    onSelect = onSelectPromptHistoryItem,
                    onClear = onClearPromptHistory,
                    onDismiss = onClosePromptHistory
                )
            }

            if (uiState.showInstructionConfigDialog) {
                PromptInstructionDialog(
                    showDialog = true,
                    config = uiState.promptInstructionConfig,
                    initialTab = uiState.instructionConfigDialogInitialTab,
                    onSave = onSaveInstructionConfig,
                    onDismiss = onCloseInstructionConfigDialog
                )
            }
            if (uiState.showVariationPromptConfigDialog) {
                VariationPromptDialog(
                    showDialog = true,
                    config = uiState.variationPromptConfig,
                    onSave = onSaveVariationPromptConfig,
                    onDismiss = onCloseVariationPromptConfigDialog
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
            uiState = AutomationUiState(),
            automationBarUiState = AutomationBarUiState(),
            promptTemplateState = TextFieldState(),
            onClearFocus = {},
            onHideSettings = {},
            onConfirmAccessibilityPrompt = {},
            onDismissAccessibilityPromptToSettings = {},
            onRefreshStatus = {},
            onSelectWildcardFolder = {},
            onSelectSafWildcardFolder = {},
            onOpenWildcardStorageSettings = {},
            onOpenAccessibilitySettings = {},
            onTargetAppSelected = {},
            onPromptTemplateChange = {},
            onNavigateHistoryBack = {},
            onNavigateHistoryForward = {},
            onInsertTopInstruction = {},
            onInsertBottomInstruction = {},
            onOpenInstructionConfigDialog = {},
            onParagraphOffsetSelected = {},
            onDeleteSelectedParagraph = {},
            onReplaceSelectedParagraph = {},
            onImportFromClipboard = {},
            onCopyPromptToClipboard = {},
            onPasteFromClipboard = {},
            onCloseGeminiApp = {},
            onCleanDeviceMemory = {},
            onTerminateSelfApp = {},
            onRepeatCountChange = {},
            onRunMvp = {},
            onCancelAutomation = {},
            onAutomationModeSelected = {},
            onPairRemoteDevice = {}
        )
    }
}
