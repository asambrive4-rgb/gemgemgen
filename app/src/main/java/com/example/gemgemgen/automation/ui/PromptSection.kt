// 역할: 프롬프트 텍스트 입력창, 대상 앱 선택 토글, 와일드카드 칩 영역을 화면에 표시합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.example.gemgemgen.core.AppDefaults
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.ui.AppMultilineTextField
import com.example.gemgemgen.ui.theme.AppTheme

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
    canCleanMemory: Boolean,
    isMaintenanceBusy: Boolean = false,
    maintenanceMessage: String = "",
    selectedParagraphRange: PromptParagraphRange?,
    paragraphSelectionMessage: String,
    wildcardTokenCandidates: List<WildcardTokenAutocomplete.Candidate> = emptyList(),
    showPromptActions: Boolean = true,
    showWildcardSuggestions: Boolean = true,
    onTargetAppSelected: (AutomationTargetApp) -> Unit,
    flowImageCount: Int = AppDefaults.DEFAULT_FLOW_IMAGE_COUNT,
    onFlowImageCountSelected: (Int) -> Unit = {},
    onPromptTemplateChange: (String) -> Unit,
    onWildcardTokenSuggestionClick: (String) -> Unit = {},
    onCloseGeminiApp: () -> Unit,
    onCleanDeviceMemory: () -> Unit,
    onTerminateSelfApp: () -> Unit,
    onUndoPromptEdit: () -> Unit,
    onInsertSystemInstruction: () -> Unit,
    onParagraphOffsetSelected: (Int) -> Unit,
    onDeleteSelectedParagraph: () -> Unit,
    onReplaceSelectedParagraph: (String) -> Unit,
    onImportFromClipboard: () -> Unit,
    onCopyPromptToClipboard: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onOpenPromptHistory: () -> Unit = {}
) {
    val suggestionTokens = rememberWildcardSuggestionTokens(
        promptTemplateState = promptTemplateState,
        wildcardTokenCandidates = wildcardTokenCandidates,
        isParagraphSelectionMode = isParagraphSelectionMode,
        isTargetSelectionEnabled = isTargetSelectionEnabled
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "프롬프트 템플릿",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary
                )
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

            IconButton(
                onClick = onOpenPromptHistory,
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = "프롬프트 기록" }
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = selectedTargetApp == AutomationTargetApp.FLOW,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            FlowImageCountRow(
                selectedCount = flowImageCount,
                enabled = isTargetSelectionEnabled,
                onCountSelected = onFlowImageCountSelected
            )
        }

        if (showWildcardSuggestions && suggestionTokens.isNotEmpty()) {
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
                canCleanMemory = canCleanMemory,
                isMaintenanceBusy = isMaintenanceBusy,
                canUndoPromptEdit = canUndoPromptEdit,
                canCopyPrompt = canCopyPrompt,
                isTargetSelectionEnabled = isTargetSelectionEnabled,
                onCloseGeminiApp = onCloseGeminiApp,
                onCleanDeviceMemory = onCleanDeviceMemory,
                onTerminateSelfApp = onTerminateSelfApp,
                onUndoPromptEdit = onUndoPromptEdit,
                onInsertSystemInstruction = onInsertSystemInstruction,
                onImportFromClipboard = onImportFromClipboard,
                onCopyPromptToClipboard = onCopyPromptToClipboard,
                onPasteFromClipboard = onPasteFromClipboard
            )
        }

        if (maintenanceMessage.isNotBlank()) {
            Text(
                text = maintenanceMessage,
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
    canCleanMemory: Boolean,
    isMaintenanceBusy: Boolean = false,
    canUndoPromptEdit: Boolean,
    canCopyPrompt: Boolean,
    isTargetSelectionEnabled: Boolean,
    onCloseGeminiApp: () -> Unit,
    onCleanDeviceMemory: () -> Unit,
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
        // 섬 1: 앱 자체 종료(왼쪽) + Gemini 리셋
        ActionIsland {
            OutlinedButton(
                onClick = onTerminateSelfApp,
                enabled = canCloseSelfApp && !isMaintenanceBusy,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppTheme.colors.card,
                    contentColor = AppTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .semantics { contentDescription = "GemGemGen 앱 종료" },
                border = BorderStroke(1.5.dp, AppTheme.colors.cardBorder)
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
                onClick = onCloseGeminiApp,
                enabled = canCloseGemini && !isMaintenanceBusy,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppTheme.colors.card,
                    contentColor = AppTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .semantics { contentDescription = "Gemini 앱 리셋" },
                border = BorderStroke(1.5.dp, AppTheme.colors.cardBorder)
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
                        text = "리셋",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            OutlinedButton(
                onClick = onCleanDeviceMemory,
                enabled = canCleanMemory && !isMaintenanceBusy,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppTheme.colors.card,
                    contentColor = AppTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .semantics { contentDescription = "메모리 정리" },
                border = BorderStroke(1.5.dp, AppTheme.colors.cardBorder)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "메모리 정리",
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
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppTheme.colors.card,
                    contentColor = AppTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .semantics { contentDescription = "[SI 삽입]" },
                border = BorderStroke(1.5.dp, AppTheme.colors.cardBorder)
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
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppTheme.colors.card,
                    contentColor = AppTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(width = 40.dp, height = 28.dp),
                border = BorderStroke(1.5.dp, AppTheme.colors.cardBorder)
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
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppTheme.colors.card,
                    contentColor = AppTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(width = 40.dp, height = 28.dp),
                border = BorderStroke(1.5.dp, AppTheme.colors.cardBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "프롬프트 복사",
                    modifier = Modifier.size(16.dp)
                )
            }
            OutlinedButton(
                onClick = onImportFromClipboard,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppTheme.colors.card,
                    contentColor = AppTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
                border = BorderStroke(1.5.dp, AppTheme.colors.cardBorder)
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
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppTheme.colors.card,
                    contentColor = AppTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(width = 40.dp, height = 28.dp),
                border = BorderStroke(1.5.dp, AppTheme.colors.cardBorder)
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
internal fun rememberWildcardSuggestionTokens(
    promptTemplateState: TextFieldState,
    wildcardTokenCandidates: List<WildcardTokenAutocomplete.Candidate>,
    isParagraphSelectionMode: Boolean,
    isTargetSelectionEnabled: Boolean
): List<String> {
    val fieldText = promptTemplateState.text.toString()
    val selection = promptTemplateState.selection
    return remember(
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
}

@Composable
internal fun WildcardTokenSuggestionBar(
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
            val shape = RoundedCornerShape(10.dp)
            Surface(
                onClick = { onTokenClick(token) },
                shape = shape,
                color = AppTheme.colors.card,
                border = BorderStroke(1.5.dp, AppTheme.colors.primary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .shadow(
                        elevation = 2.dp,
                        shape = shape,
                        ambientColor = AppTheme.colors.primary.copy(alpha = 0.2f),
                        spotColor = AppTheme.colors.primary.copy(alpha = 0.15f)
                    )
                    .semantics {
                        contentDescription = "와일드카드 $token 삽입"
                    }
            ) {
                Text(
                    text = token,
                    color = AppTheme.colors.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionIsland(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, AppTheme.colors.insetBorder),
        color = AppTheme.colors.insetBed,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
        AppTheme.colors.primary
    } else {
        AppTheme.colors.card
    }
    val contentColor = if (selected) {
        AppTheme.colors.onPrimary
    } else {
        AppTheme.colors.textSecondary
    }
    val shape = RoundedCornerShape(10.dp)

    Surface(
        modifier = Modifier
            .height(30.dp)
            .shadow(
                elevation = if (selected) 3.dp else 0.dp,
                shape = shape,
                ambientColor = if (selected) AppTheme.colors.primary.copy(alpha = 0.35f) else AppTheme.colors.shadowDark.copy(alpha = 0.3f),
                spotColor = if (selected) AppTheme.colors.primary.copy(alpha = 0.3f) else AppTheme.colors.shadowDark.copy(alpha = 0.2f)
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) {
            BorderStroke(1.5.dp, AppTheme.colors.primary)
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = targetApp.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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

@Composable
private fun FlowImageCountRow(
    selectedCount: Int,
    enabled: Boolean,
    onCountSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "생성 개수",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.colors.textSecondary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppDefaults.FLOW_IMAGE_COUNT_OPTIONS.forEach { count ->
                FlowImageCountChip(
                    count = count,
                    selected = selectedCount == count,
                    enabled = enabled,
                    onClick = { onCountSelected(count) }
                )
            }
        }
    }
}

@Composable
private fun FlowImageCountChip(
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.card
    }
    val contentColor = if (selected) {
        AppTheme.colors.onPrimary
    } else {
        AppTheme.colors.textSecondary
    }
    val shape = RoundedCornerShape(10.dp)

    Surface(
        modifier = Modifier
            .height(26.dp)
            .shadow(
                elevation = if (selected) 2.dp else 0.dp,
                shape = shape,
                ambientColor = if (selected) AppTheme.colors.primary.copy(alpha = 0.35f) else AppTheme.colors.shadowDark.copy(alpha = 0.3f),
                spotColor = if (selected) AppTheme.colors.primary.copy(alpha = 0.3f) else AppTheme.colors.shadowDark.copy(alpha = 0.2f)
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) {
            BorderStroke(1.5.dp, AppTheme.colors.primary)
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${count}장",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
