// 역할: 화면 액션 인터페이스 지원, 가상 키보드 반응형 에디터, 추천 후보 바, 제어 바 및 상태 화면을 구성합니다.
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.remote.ui.AutomationModePairDialog
import com.example.gemgemgen.remote.ui.AutomationModePanel
import com.example.gemgemgen.remote.ui.resolveConnectionText

/** 스크롤 본문·키보드 하단 고정 패널이 같은 가로 폭을 쓰도록 공통 패딩 */
private val AutomationScreenContentPadding = 8.dp

/** 액션 줄·시작 바·섹션 사이 간격 */
private val AutomationScreenSectionSpacing = 5.dp

@Composable
internal fun AutomationScreen(
    uiState: AutomationUiState,
    automationBarUiState: AutomationBarUiState,
    promptTemplateState: TextFieldState,
    actions: AutomationScreenActions,
    modifier: Modifier = Modifier
) {
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var showPairDialog by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .clearFocusOnOutsideTap { actions.onClearFocus() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AutomationScreenContentPadding),
                verticalArrangement = Arrangement.spacedBy(AutomationScreenSectionSpacing)
            ) {

                PromptEditorSection(
                    uiState = uiState,
                    promptTemplateState = promptTemplateState,
                    actions = actions,
                    showPromptActions = !isKeyboardVisible,
                    showWildcardSuggestions = !isKeyboardVisible
                )

                if (!isKeyboardVisible && uiState.automationMode != AutomationMode.RECEIVER) {
                    AutomationBottomBar(
                        repeatCountText = uiState.repeatCountText,
                        onRepeatCountChange = actions::onRepeatCountChange,
                        onRunMvp = actions::onRunAutomation,
                        onCancelAutomation = actions::cancelAutomation,
                        canRun = uiState.canRun,
                        isRunning = uiState.isRunning,
                        automationState = automationBarUiState.automationState,
                        isRemoteSendMode = uiState.automationMode == AutomationMode.SENDER
                    )
                } else if (isKeyboardVisible) {
                    val hasSuggestions = uiState.activeSuggestionCandidates.isNotEmpty()
                    val bottomSpacerHeight = if (hasSuggestions) 105.dp else 56.dp
                    Spacer(modifier = Modifier.height(bottomSpacerHeight))
                }

                if (!isKeyboardVisible) {
                    AutomationModePanel(
                        selectedMode = uiState.automationMode,
                        status = uiState.remoteAutomationStatus,
                        enabled = !uiState.isRunning,
                        onModeSelected = actions::onAutomationModeSelected,
                        onRequestPair = { showPairDialog = true }
                    )

                    AutomationFooterStatus(
                        maintenanceMessage = uiState.maintenanceMessage,
                        variationAutomationState = uiState.variationAutomationState,
                        automationMode = uiState.automationMode,
                        remoteStatus = uiState.remoteAutomationStatus
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
                        if (uiState.activeSuggestionCandidates.isNotEmpty()) {
                            PromptSuggestionBar(
                                suggestions = uiState.activeSuggestionCandidates,
                                onSuggestionClick = { candidate ->
                                    actions.applySuggestion(candidate)
                                }
                            )
                        }

                        KeyboardPromptAccessoryBar(
                            uiState = uiState,
                            automationBarUiState = automationBarUiState,
                            promptTemplateState = promptTemplateState,
                            actions = actions
                        )
                    }
                }
            }

            if (showPairDialog) {
                AutomationModePairDialog(
                    targetDeviceName = uiState.remoteAutomationStatus.discoveredDeviceName.ifBlank { "S25 FE" },
                    onConfirm = { code ->
                        actions.pairRemoteDevice(code)
                        showPairDialog = false
                    },
                    onDismiss = {
                        showPairDialog = false
                    }
                )
            }

            if (uiState.showInstructionConfigDialog) {
                PromptInstructionDialog(
                    showDialog = true,
                    config = uiState.promptInstructionConfig,
                    initialTab = uiState.instructionConfigDialogInitialTab,
                    onSave = actions::saveInstructionConfig,
                    onDismiss = actions::closeInstructionConfigDialog
                )
            }
            if (uiState.showVariationPromptConfigDialog) {
                VariationPromptDialog(
                    showDialog = true,
                    config = uiState.variationPromptConfig,
                    onSave = actions::saveVariationPromptConfig,
                    onDismiss = actions::closeVariationPromptConfigDialog
                )
            }

            if (uiState.showPromptSnippetDialog) {
                val selectedText = promptTemplateState.selectedTextOrNull()
                    ?: uiState.selectedParagraphRange?.let { range ->
                        val full = promptTemplateState.text.toString()
                        val s = range.start.coerceIn(0, full.length)
                        val e = range.endExclusive.coerceIn(s, full.length)
                        full.substring(s, e).takeIf { it.isNotEmpty() }
                    }
                PromptSnippetDialog(
                    showDialog = true,
                    snippets = uiState.promptSnippets,
                    currentPromptText = selectedText ?: promptTemplateState.text.toString(),
                    isSelectionPrompt = selectedText != null,
                    onAddSnippet = actions::addPromptSnippet,
                    onUpdateSnippet = actions::updatePromptSnippet,
                    onDeleteSnippet = actions::deletePromptSnippet,
                    onDismiss = actions::dismissPromptSnippetDialog
                )
            }
        }
    }
}

internal data class FooterStatusInfo(
    val text: String,
    val isError: Boolean = false
)

internal fun resolveFooterStatus(
    maintenanceMessage: String,
    variationAutomationState: AutomationRunState,
    automationMode: AutomationMode,
    remoteStatus: RemoteAutomationStatus
): FooterStatusInfo? {
    if (maintenanceMessage.isNotBlank()) {
        return FooterStatusInfo(text = maintenanceMessage, isError = false)
    }

    if (variationAutomationState != AutomationRunState.Idle) {
        val variationText = resolveVariationStatusText(variationAutomationState)
        if (variationText.isNotBlank()) {
            return FooterStatusInfo(
                text = variationText,
                isError = variationAutomationState is AutomationRunState.Failure
            )
        }
    }

    val connectionText = resolveConnectionText(automationMode, remoteStatus)
    if (connectionText.isNotBlank()) {
        return FooterStatusInfo(text = connectionText, isError = false)
    }

    return null
}

@Composable
internal fun AutomationFooterStatus(
    maintenanceMessage: String,
    variationAutomationState: AutomationRunState,
    automationMode: AutomationMode,
    remoteStatus: RemoteAutomationStatus,
    modifier: Modifier = Modifier
) {
    val statusInfo = resolveFooterStatus(
        maintenanceMessage = maintenanceMessage,
        variationAutomationState = variationAutomationState,
        automationMode = automationMode,
        remoteStatus = remoteStatus
    ) ?: return

    Text(
        text = statusInfo.text,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (statusInfo.isError) {
            MaterialTheme.colorScheme.error
        } else {
            AppTheme.colors.textSecondary
        },
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

