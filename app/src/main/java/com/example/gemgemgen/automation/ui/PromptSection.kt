package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.ui.AppMultilineTextField

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PromptSection(
    promptTemplateState: TextFieldState,
    selectedTargetApp: AutomationTargetApp,
    isTargetSelectionEnabled: Boolean,
    isParagraphSelectionMode: Boolean,
    canUndoPromptEdit: Boolean,
    canCopyPrompt: Boolean,
    canCloseGemini: Boolean,
    canCloseSelfApp: Boolean,
    isClosingGemini: Boolean,
    geminiCloseMessage: String,
    selectedParagraphRange: PromptParagraphRange?,
    paragraphSelectionMessage: String,
    wildcardTokenCandidates: List<WildcardTokenAutocomplete.Candidate> = emptyList(),
    showPromptActions: Boolean = true,
    onTargetAppSelected: (AutomationTargetApp) -> Unit,
    onPromptTemplateChange: (String) -> Unit,
    onWildcardTokenSuggestionClick: (String) -> Unit = {},
    onCloseGeminiApp: () -> Unit,
    onTerminateGeminiApp: () -> Unit,
    onTerminateSelfApp: () -> Unit,
    onUndoPromptEdit: () -> Unit,
    onInsertSystemInstruction: () -> Unit,
    onParagraphOffsetSelected: (Int) -> Unit,
    onDeleteSelectedParagraph: () -> Unit,
    onReplaceSelectedParagraph: (String) -> Unit,
    onImportFromClipboard: () -> Unit,
    onCopyPromptToClipboard: () -> Unit,
    onPasteFromClipboard: () -> Unit
) {
    // TextFieldState 스냅샷 구독 — 텍스트·커서 변경 시 추천 재계산
    val fieldText = promptTemplateState.text.toString()
    val selection = promptTemplateState.selection
    val suggestionTokens = remember(
        fieldText,
        selection,
        wildcardTokenCandidates,
        isParagraphSelectionMode,
        isTargetSelectionEnabled
    ) {
        if (isParagraphSelectionMode || !isTargetSelectionEnabled) {
            emptyList()
        } else if (selection.min != selection.max) {
            emptyList()
        } else {
            WildcardTokenAutocomplete.suggestions(
                text = fieldText,
                cursor = selection.max,
                candidates = wildcardTokenCandidates
            )
        }
    }

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

        if (suggestionTokens.isNotEmpty()) {
            WildcardTokenSuggestionBar(
                tokens = suggestionTokens,
                onTokenClick = onWildcardTokenSuggestionClick
            )
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
            onDeleteSelectedParagraph = onDeleteSelectedParagraph,
            onReplaceSelectedParagraph = onReplaceSelectedParagraph
        )

        if (showPromptActions) {
            PromptActionRow(
                canCloseGemini = canCloseGemini,
                canCloseSelfApp = canCloseSelfApp,
                isClosingGemini = isClosingGemini,
                canUndoPromptEdit = canUndoPromptEdit,
                canCopyPrompt = canCopyPrompt,
                isTargetSelectionEnabled = isTargetSelectionEnabled,
                onCloseGeminiApp = onCloseGeminiApp,
                onTerminateGeminiApp = onTerminateGeminiApp,
                onTerminateSelfApp = onTerminateSelfApp,
                onUndoPromptEdit = onUndoPromptEdit,
                onInsertSystemInstruction = onInsertSystemInstruction,
                onImportFromClipboard = onImportFromClipboard,
                onCopyPromptToClipboard = onCopyPromptToClipboard,
                onPasteFromClipboard = onPasteFromClipboard
            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PromptActionRow(
    canCloseGemini: Boolean,
    canCloseSelfApp: Boolean,
    isClosingGemini: Boolean,
    canUndoPromptEdit: Boolean,
    canCopyPrompt: Boolean,
    isTargetSelectionEnabled: Boolean,
    onCloseGeminiApp: () -> Unit,
    onTerminateGeminiApp: () -> Unit,
    onTerminateSelfApp: () -> Unit,
    onUndoPromptEdit: () -> Unit,
    onInsertSystemInstruction: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onCopyPromptToClipboard: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        // 섬 1: 앱 자체 종료(왼쪽) + Gemini 종료/재시작
        ActionIsland {
            OutlinedButton(
                onClick = onTerminateSelfApp,
                enabled = canCloseSelfApp && !isClosingGemini,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .semantics { contentDescription = "GemGemGen 앱 종료" },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "앱 종료",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            OutlinedButton(
                onClick = onTerminateGeminiApp,
                enabled = canCloseGemini && !isClosingGemini,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .semantics { contentDescription = "Gemini 앱 종료" },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Image(
                        imageVector = GeminiGradientLogo,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "종료",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            OutlinedButton(
                onClick = onCloseGeminiApp,
                enabled = canCloseGemini && !isClosingGemini,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .semantics { contentDescription = "Gemini 앱 재시작" },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Image(
                        imageVector = GeminiGradientLogo,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "재시작",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 섬 2: SI 삽입 + Undo
        ActionIsland {
            OutlinedButton(
                onClick = onInsertSystemInstruction,
                enabled = isTargetSelectionEnabled,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .semantics { contentDescription = "[SI 삽입]" },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "[SI 삽입]",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = onUndoPromptEdit,
                enabled = canUndoPromptEdit && isTargetSelectionEnabled,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(width = 40.dp, height = 28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 섬 3: 복사 가져오기 붙여넣기
        ActionIsland {
            OutlinedButton(
                onClick = onCopyPromptToClipboard,
                enabled = canCopyPrompt,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(width = 40.dp, height = 28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "프롬프트 복사",
                    modifier = Modifier.size(16.dp)
                )
            }
            OutlinedButton(
                onClick = onImportFromClipboard,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "가져오기",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = onPasteFromClipboard,
                enabled = isTargetSelectionEnabled,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(width = 40.dp, height = 28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "프롬프트 붙여넣기",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun WildcardTokenSuggestionBar(
    tokens: List<String>,
    onTokenClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tokens.forEach { token ->
            SuggestionChip(
                onClick = { onTokenClick(token) },
                label = {
                    Text(
                        text = token,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                modifier = Modifier.semantics {
                    contentDescription = "와일드카드 $token 삽입"
                }
            )
        }
    }
}

@Composable
private fun ActionIsland(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
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

private val GeminiGradientLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "GeminiGradientLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = Brush.linearGradient(
            colors = listOf(Color(0xFF4285F4), Color(0xFF9B72CB), Color(0xFFE8710A)),
            start = Offset(2f, 22f),
            end = Offset(22f, 2f)
        )
    ) {
        moveTo(12f, 2f)
        curveTo(12f, 2f, 12.5f, 9.5f, 22f, 12f)
        curveTo(12.5f, 14.5f, 12f, 22f, 12f, 22f)
        curveTo(12f, 22f, 11.5f, 14.5f, 2f, 12f)
        curveTo(11.5f, 9.5f, 12f, 2f, 12f, 2f)
        close()
    }.build()
}
