package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.ui.AppMultilineTextField

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PromptSection(
    promptTemplateState: TextFieldState,
    selectedTargetApp: AutomationTargetApp,
    isTargetSelectionEnabled: Boolean,
    isParagraphSelectionMode: Boolean,
    canUndoPromptEdit: Boolean,
    canCloseGemini: Boolean,
    isClosingGemini: Boolean,
    geminiCloseMessage: String,
    selectedParagraphRange: PromptParagraphRange?,
    paragraphSelectionMessage: String,
    onTargetAppSelected: (AutomationTargetApp) -> Unit,
    onPromptTemplateChange: (String) -> Unit,
    onCloseGeminiApp: () -> Unit,
    onUndoPromptEdit: () -> Unit,
    onToggleParagraphSelectionMode: () -> Unit,
    onParagraphOffsetSelected: (Int) -> Unit,
    onDeleteSelectedParagraph: () -> Unit,
    onImportFromClipboard: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "프롬프트 템플릿",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutomationTargetApp.entries.forEach { targetApp ->
                    TargetAppButton(
                        targetApp = targetApp,
                        selected = selectedTargetApp == targetApp,
                        enabled = isTargetSelectionEnabled,
                        onClick = { onTargetAppSelected(targetApp) }
                    )
                }
            }
        }

        AppMultilineTextField(
            state = promptTemplateState,
            onValueChange = onPromptTemplateChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            paragraphSelectionEnabled = isParagraphSelectionMode,
            selectedParagraphRange = selectedParagraphRange,
            selectedParagraphColor = MaterialTheme.colorScheme.primaryContainer,
            supportingText = paragraphSelectionMessage,
            onParagraphOffsetSelected = onParagraphOffsetSelected,
            onDeleteSelectedParagraph = onDeleteSelectedParagraph
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                onClick = onCloseGeminiApp,
                enabled = canCloseGemini && !isClosingGemini,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "Gemini 종료",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = onUndoPromptEdit,
                enabled = canUndoPromptEdit && isTargetSelectionEnabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    modifier = Modifier.size(16.dp)
                )
            }
            OutlinedButton(
                onClick = onToggleParagraphSelectionMode,
                enabled = isTargetSelectionEnabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
                border = BorderStroke(
                    1.dp,
                    if (isParagraphSelectionMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isParagraphSelectionMode) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isParagraphSelectionMode) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            ) {
                Text(
                    text = "문단 선택",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = onImportFromClipboard,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "가져오기",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (geminiCloseMessage.isNotBlank()) {
            Text(
                text = geminiCloseMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TargetAppButton(
    targetApp: AutomationTargetApp,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .height(28.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = targetApp.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
