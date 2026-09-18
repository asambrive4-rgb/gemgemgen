// 역할: 가상 키보드 노출 시 시작/중지 제어, 개수 조절 스텝퍼, 문단 편집 및 에디터 액션 버튼을 단일 행으로 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.InstructionTab

@Composable
internal fun KeyboardPromptAccessoryBar(
    canRun: Boolean,
    isRunning: Boolean,
    repeatCountText: String,
    onRepeatCountChange: (String) -> Unit = {},
    automationState: AutomationRunState,
    isRemoteSendMode: Boolean = false,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    canCopyPrompt: Boolean,
    isTargetSelectionEnabled: Boolean,
    onInsertTopInstruction: () -> Unit,
    onInsertBottomInstruction: () -> Unit,
    onOpenInstructionConfigDialog: (InstructionTab) -> Unit,
    onImportFromClipboard: () -> Unit,
    onCopyPromptToClipboard: () -> Unit,
    showVariationButton: Boolean = true,
    isVariationButtonEnabled: Boolean = true,
    variationAutomationState: AutomationRunState = AutomationRunState.Idle,
    onRunVariation: () -> Unit = {},
    onVariationPointerDown: (() -> Unit)? = null,
    onOpenVariationPromptConfigDialog: () -> Unit = {},
    isParagraphSelectionMode: Boolean = false,
    onToggleParagraphSelectionMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val countBadgeText = if (automationState is AutomationRunState.Running &&
        automationState.currentIndex != null &&
        automationState.totalCount != null
    ) {
        "${automationState.currentIndex}/${automationState.totalCount}"
    } else {
        "×${repeatCountText.ifBlank { "1" }}"
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
                canRun = canRun,
                isRunning = isRunning,
                onRunMvp = onRunMvp,
                onCancelAutomation = onCancelAutomation,
                isRemoteSendMode = isRemoteSendMode
            )

            if (!isRunning) {
                RepeatCountStepper(
                    repeatCountText = repeatCountText,
                    onRepeatCountChange = onRepeatCountChange
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
                    selected = isParagraphSelectionMode,
                    enabled = isTargetSelectionEnabled,
                    onClick = onToggleParagraphSelectionMode
                )
            }

            PromptEditorActionGroup(
                isTargetSelectionEnabled = isTargetSelectionEnabled,
                canCopyPrompt = canCopyPrompt,
                onInsertTopInstruction = onInsertTopInstruction,
                onInsertBottomInstruction = onInsertBottomInstruction,
                onOpenInstructionConfigDialog = onOpenInstructionConfigDialog,
                onImportFromClipboard = onImportFromClipboard,
                onCopyPromptToClipboard = onCopyPromptToClipboard,
                showInsertButtons = false,
                showVariationButton = showVariationButton,
                isVariationButtonEnabled = isVariationButtonEnabled,
                variationAutomationState = variationAutomationState,
                onRunVariation = onRunVariation,
                onVariationPointerDown = onVariationPointerDown,
                onOpenVariationPromptConfigDialog = onOpenVariationPromptConfigDialog
            )
        }
    }
}
