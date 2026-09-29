// 역할: 가상 키보드 노출 시 시작/중지 제어, 개수 조절 스텝퍼, 문단 편집 및 에디터 액션 버튼을 단일 행으로 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.remote.domain.AutomationMode

@Composable
internal fun KeyboardPromptAccessoryBar(
    uiState: AutomationUiState,
    automationBarUiState: AutomationBarUiState,
    promptTemplateState: TextFieldState,
    actions: AutomationScreenActions = AutomationScreenActions.Empty,
    modifier: Modifier = Modifier
) {
    val keyboardVariationSelectedTextAtPress = remember { mutableStateOf<String?>(null) }

    val countBadgeText = if (automationBarUiState.automationState is AutomationRunState.Running &&
        automationBarUiState.automationState.currentIndex != null &&
        automationBarUiState.automationState.totalCount != null
    ) {
        "${automationBarUiState.automationState.currentIndex}/${automationBarUiState.automationState.totalCount}"
    } else {
        "×${uiState.repeatCountText.ifBlank { "1" }}"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 좌측 제어부: [시작/중지] 버튼 + [생성 개수 조절 스텝퍼 또는 진행률 뱃지]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AutomationRunButton(
                canRun = uiState.canRun,
                isRunning = uiState.isRunning,
                onRunMvp = {
                    actions.onClearFocus()
                    actions.onRunAutomation()
                },
                onCancelAutomation = {
                    actions.onClearFocus()
                    actions.onCancelAutomation()
                },
                isRemoteSendMode = uiState.automationMode == AutomationMode.SENDER
            )

            if (!uiState.isRunning) {
                RepeatCountStepper(
                    repeatCountText = uiState.repeatCountText,
                    onRepeatCountChange = actions::onRepeatCountChange
                )
            } else {
                AutomationProgressBadge(
                    text = countBadgeText
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 중앙 및 우측: [문단 편집] 별도 섬 + [변주] · [복사] · [가져오기] 섬 (상단/하단 삽입 제외)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ActionIsland {
                ParagraphSelectionModeButton(
                    selected = uiState.isParagraphSelectionMode,
                    enabled = true,
                    onClick = actions::onToggleParagraphSelectionMode
                )
            }

            PromptEditorActionGroup(
                isTargetSelectionEnabled = true,
                canCopyPrompt = uiState.hasPromptTemplate,
                onInsertTopInstruction = actions::onInsertTopInstruction,
                onInsertBottomInstruction = actions::onInsertBottomInstruction,
                onOpenInstructionConfigDialog = actions::onOpenInstructionConfigDialog,
                onImportFromClipboard = actions::onImportPromptFromClipboard,
                onCopyPromptToClipboard = actions::onCopyPromptToClipboard,
                showInsertButtons = false,
                showVariationButton = uiState.automationMode != AutomationMode.RECEIVER,
                isVariationButtonEnabled = uiState.canInteractWithVariation,
                variationAutomationState = uiState.variationAutomationState,
                onRunVariation = {
                    val selectedText = keyboardVariationSelectedTextAtPress.value
                        ?: promptTemplateState.selectedTextOrNull()
                    keyboardVariationSelectedTextAtPress.value = null
                    actions.onClearFocus()
                    actions.onRunVariation(selectedText)
                },
                onVariationPointerDown = {
                    keyboardVariationSelectedTextAtPress.value = promptTemplateState.selectedTextOrNull()
                },
                onOpenVariationPromptConfigDialog = actions::onOpenVariationPromptConfigDialog
            )
        }
    }
}
