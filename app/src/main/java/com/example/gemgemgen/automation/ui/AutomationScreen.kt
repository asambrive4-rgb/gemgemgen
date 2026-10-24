// 역할: 화면 액션 인터페이스 지원, 가상 키보드 반응형 에디터, 실험(연금술) 관리 모달 연동, 상용구 일괄 치환 모달 연동, 다중 제미나이 계정 사용량 요약 버튼 및 수직 목록 모달, 하단 네비게이션 바 위에 고정되는 시작·수신 제어 바를 구성합니다.
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.remote.ui.AutomationModePairDialog
import com.example.gemgemgen.remote.ui.AutomationReceiverBottomBar
import com.example.gemgemgen.remote.ui.resolveConnectionText
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.ui.rememberIsImeVisible
import com.example.gemgemgen.usage.ui.GeminiUsageSummaryButton
import com.example.gemgemgen.usage.ui.MultiGeminiUsageDialog

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
    val isKeyboardVisible = rememberIsImeVisible()
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
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(AutomationScreenContentPadding),
                    verticalArrangement = Arrangement.spacedBy(AutomationScreenSectionSpacing)
                ) {
                    PromptEditorSection(
                        uiState = uiState,
                        promptTemplateState = promptTemplateState,
                        actions = actions,
                        onRequestPair = { showPairDialog = true },
                        showPromptActions = !isKeyboardVisible,
                        showWildcardSuggestions = !isKeyboardVisible
                    )

                    if (!isKeyboardVisible) {
                        GeminiUsageSummaryButton(
                            multiUsageState = uiState.multiGeminiUsageState,
                            onClick = actions::openMultiGeminiUsageDialog,
                            onRefreshClick = { actions.refreshAllGeminiUsage(null) }
                        )
                    }
                }

                if (!isKeyboardVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = AutomationScreenContentPadding,
                                end = AutomationScreenContentPadding,
                                bottom = AutomationScreenContentPadding,
                                top = 2.dp
                            )
                    ) {
                        if (uiState.automationMode != AutomationMode.RECEIVER) {
                            AutomationBottomBar(
                                repeatCountText = uiState.repeatCountText,
                                onRepeatCountChange = actions::onRepeatCountChange,
                                onRunMvp = actions::onRunAutomation,
                                onCancelAutomation = actions::cancelAutomation,
                                canRun = uiState.canRun,
                                isRunning = uiState.isRunning,
                                automationState = automationBarUiState.automationState,
                                isRemoteSendMode = uiState.automationMode == AutomationMode.SENDER,
                                hasPreStartMemoryCleanup = uiState.shouldAutoCleanMemoryBeforeStart
                            )
                        } else {
                            AutomationReceiverBottomBar(
                                status = uiState.remoteAutomationStatus
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
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
                            IsolatedPromptSuggestionBar(
                                activeSuggestionsFlow = actions.promptEditor.activeSuggestions,
                                onSuggestionClick = actions.promptEditor::applySuggestion
                            )

                            KeyboardPromptAccessoryBar(
                                uiState = uiState,
                                automationBarUiState = automationBarUiState,
                                promptTemplateState = promptTemplateState,
                                actions = actions
                            )
                        }
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
                    config = uiState.promptInstructionConfig,
                    initialTab = uiState.instructionConfigDialogInitialTab,
                    onSave = actions::saveInstructionConfig,
                    onDismiss = actions::closeInstructionConfigDialog
                )
            }
            if (uiState.showVariationPromptConfigDialog) {
                VariationPromptDialog(
                    config = uiState.variationPromptConfig,
                    onSave = actions::saveVariationPromptConfig,
                    onDismiss = actions::closeVariationPromptConfigDialog
                )
            }

            if (uiState.showExperimentDialog) {
                PromptExperimentDialog(
                    presets = uiState.experimentPresets,
                    activePresetId = uiState.activeExperimentPresetId,
                    selectedItemId = uiState.selectedExperimentItemId,
                    onSelectPreset = actions::selectExperimentPreset,
                    onSelectItem = actions::selectExperimentItem,
                    onAddPreset = actions::addExperimentPreset,
                    onUpdatePreset = actions::updateExperimentPreset,
                    onDeletePreset = actions::deleteExperimentPreset,
                    onMovePreset = actions::moveExperimentPreset,
                    onReorderPresets = actions::reorderExperimentPresets,
                    onResetPresetItems = actions::resetExperimentPresetItems,
                    onAddItem = actions::addExperimentItem,
                    onAddItemsBatch = actions::addExperimentItemsBatch,
                    onUpdateItem = actions::updateExperimentItem,
                    onDeleteItem = actions::deleteExperimentItem,
                    onMoveItem = actions::moveExperimentItem,
                    onReorderItems = actions::reorderExperimentItems,
                    onDismiss = actions::dismissExperimentDialog
                )
            }

            if (uiState.showPromptSnippetDialog) {
                val selectedText = promptTemplateState.selectedTextOrNull()
                    ?: uiState.editor.selectedParagraphRange?.let { range ->
                        val full = promptTemplateState.text.toString()
                        val s = range.start.coerceIn(0, full.length)
                        val e = range.endExclusive.coerceIn(s, full.length)
                        full.substring(s, e).takeIf { it.isNotEmpty() }
                    }
                PromptSnippetDialog(
                    snippets = uiState.promptSnippets,
                    currentPromptText = selectedText ?: promptTemplateState.text.toString(),
                    isSelectionPrompt = selectedText != null,
                    onAddSnippet = actions::addPromptSnippet,
                    onUpdateSnippet = actions::updatePromptSnippet,
                    onDeleteSnippet = actions::deletePromptSnippet,
                    onBatchReplaceSnippets = actions::batchReplacePromptSnippets,
                    onDismiss = actions::dismissPromptSnippetDialog
                )
            }

            if (uiState.showMultiGeminiUsageDialog) {
                MultiGeminiUsageDialog(
                    multiUsageState = uiState.multiGeminiUsageState,
                    onAddAccount = { alias, email, accountId, group ->
                        actions.addGeminiAccount(alias = alias, email = email, accountId = accountId, group = group)
                    },
                    onUpdateAccount = { accountId, alias, email ->
                        actions.updateGeminiAccount(accountId = accountId, alias = alias, email = email)
                    },
                    onRemoveAccount = actions::removeGeminiAccount,
                    onSelectRepresentativeAccount = actions::selectRepresentativeGeminiAccount,
                    onRefreshAccount = actions::refreshGeminiAccountUsage,
                    onRefreshAll = actions::refreshAllGeminiUsage,
                    onSwitchAccount = actions::switchGeminiAccount,
                    onMoveAccountGroup = actions::moveGeminiAccountGroup,
                    onToggleManualExhaustion = actions::toggleGeminiAccountManualExhaustion,
                    onToggleAccountSuspension = actions::toggleGeminiAccountSuspension,
                    onDismiss = actions::closeMultiGeminiUsageDialog
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
