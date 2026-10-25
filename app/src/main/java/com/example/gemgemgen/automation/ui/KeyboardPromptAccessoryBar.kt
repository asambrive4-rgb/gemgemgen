// 역할: 가상 키보드 노출 시 시작/중지 제어, 개수 조절 스텝퍼, 문단 편집 및 에디터 액션 버튼을 단일 행으로 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.ui.theme.AppTheme

@Composable
internal fun KeyboardPromptAccessoryBar(
    uiState: AutomationUiState,
    automationBarUiState: AutomationBarUiState,
    promptTemplateState: TextFieldState,
    actions: AutomationScreenActions,
    modifier: Modifier = Modifier
) {
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
                    actions.cancelAutomation()
                },
                isRemoteSendMode = uiState.automationMode == AutomationMode.SENDER,
                hasPreStartMemoryCleanup = uiState.shouldAutoCleanMemoryBeforeStart
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

        Spacer(modifier = Modifier.width(8.dp))

        // 중앙 및 우측: [변주] · [문단 편집] 섬 + [복사] · [가져오기] 섬 (상단/하단 삽입 제외)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ActionIsland(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (uiState.automationMode != AutomationMode.RECEIVER) {
                    PebbleButton(
                        onClick = {
                            actions.onClearFocus()
                            actions.onRunVariation(promptTemplateState.selectedTextOrNull())
                        },
                        onLongClick = actions::openVariationPromptConfigDialog,
                        enabled = uiState.canInteractWithVariation,
                        isHighlighted = false,
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                        modifier = Modifier.semantics { contentDescription = "변주" }
                    ) {
                        Text(
                            text = if (uiState.variationAutomationState is AutomationRunState.Running) "변주 중" else "변주",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                    }
                }

                ParagraphSelectionModeButton(
                    selected = uiState.editor.isParagraphSelectionMode,
                    onClick = actions.promptEditor::toggleParagraphSelectionMode
                )
            }

            PromptEditorActionGroup(
                canCopyPrompt = uiState.hasPromptTemplate,
                onInsertTopInstruction = actions::insertTopInstruction,
                onInsertBottomInstruction = actions::insertBottomInstruction,
                onOpenInstructionConfigDialog = actions::openInstructionConfigDialog,
                onImportFromClipboard = actions.promptEditor::importPromptFromClipboard,
                onCopyPromptToClipboard = actions.promptEditor::copyPromptToClipboard,
                showInsertButtons = false
            )
        }
    }
}
