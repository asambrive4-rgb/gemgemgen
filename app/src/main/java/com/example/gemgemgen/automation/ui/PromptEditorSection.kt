// 역할: 프롬프트 에디터의 2단 분리형 토스 카드(카드 1: 프롬프트 입력 워크스페이스 / 카드 2: 빠른 도구 모음), 무테두리 대상 앱 드롭다운 및 자동화 모드 헤더, 실험·검색·추천 칩 바를 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.ui.AutomationModeSubBar
import com.example.gemgemgen.remote.ui.CompactAutomationModeToggle
import com.example.gemgemgen.ui.AppMultilineTextField
import com.example.gemgemgen.ui.TextHighlightRange
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuPillChip
import kotlinx.coroutines.flow.StateFlow

// ==========================================================================
// 1. Main Editor Container & Entry Point (2단 분리형 토스 카드)
// ==========================================================================

@Composable
internal fun PromptEditorSection(
    uiState: AutomationUiState,
    promptTemplateState: TextFieldState,
    actions: AutomationScreenActions,
    onRequestPair: () -> Unit = {},
    showPromptActions: Boolean = true,
    showWildcardSuggestions: Boolean = true,
    modifier: Modifier = Modifier
) {
    val subBarStatusInfo = resolveFooterStatus(
        maintenanceMessage = uiState.maintenanceMessage,
        variationAutomationState = uiState.variationAutomationState,
        automationMode = if (uiState.automationMode == AutomationMode.RECEIVER) {
            AutomationMode.NORMAL
        } else {
            uiState.automationMode
        },
        remoteStatus = uiState.remoteAutomationStatus
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 카드 1: 프롬프트 입력 워크스페이스 카드 (헤더 + 보조 바 + 멀티라인 인셋 입력창)
        NeuCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PromptEditorHeader(
                    selectedTargetApp = uiState.selectedTargetApp,
                    selectedAutomationMode = uiState.automationMode,
                    isRunning = uiState.isRunning,
                    isSearchActive = uiState.editor.isSearchActive,
                    isExperimentMode = uiState.isExperimentMode,
                    onTargetAppSelected = actions::onTargetAppSelected,
                    onAutomationModeSelected = actions::onAutomationModeSelected,
                    onToggleExperimentMode = actions::toggleExperimentMode,
                    onShowPromptSnippetDialog = actions::showPromptSnippetDialog,
                    onToggleSearch = actions.promptEditor::toggleSearch
                )

                AutomationModeSubBar(
                    selectedMode = if (uiState.automationMode == AutomationMode.RECEIVER) {
                        AutomationMode.NORMAL
                    } else {
                        uiState.automationMode
                    },
                    status = uiState.remoteAutomationStatus,
                    statusText = subBarStatusInfo?.text,
                    isStatusError = subBarStatusInfo?.isError == true,
                    onRequestPair = onRequestPair
                )

                AnimatedVisibility(
                    visible = uiState.isExperimentMode,
                    enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
                ) {
                    PromptExperimentBar(
                        presets = uiState.experimentPresets,
                        activePreset = uiState.activeExperimentPreset,
                        selectedItemId = uiState.selectedExperimentItemId,
                        hasExperimentTagInPrompt = uiState.hasExperimentTagInPrompt,
                        experimentBlockReason = uiState.experimentBlockReason,
                        onSelectPreset = actions::selectExperimentPreset,
                        onSelectItem = actions::selectExperimentItem,
                        onInsertTag = actions::insertExperimentTag,
                        onOpenManageDialog = actions::showExperimentDialog
                    )
                }

                AnimatedVisibility(
                    visible = uiState.editor.isSearchActive,
                    enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
                ) {
                    PromptSearchBar(
                        query = uiState.editor.searchQuery,
                        onQueryChange = actions.promptEditor::setSearchQuery,
                        matchCount = uiState.editor.searchMatches.size,
                        currentMatchIndex = uiState.editor.activeSearchMatchIndex,
                        onNavigateNext = actions.promptEditor::navigateSearchNext,
                        onNavigatePrevious = actions.promptEditor::navigateSearchPrevious,
                        onClose = actions.promptEditor::closeSearch
                    )
                }

                AnimatedVisibility(
                    visible = uiState.selectedTargetApp == AutomationTargetApp.FLOW,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    FlowImageCountRow(
                        selectedCount = uiState.flowImageCount,
                        enabled = !uiState.isRunning,
                        onCountSelected = actions::onFlowImageCountSelected
                    )
                }

                if (showWildcardSuggestions) {
                    IsolatedEditorSuggestionSection(
                        activeSuggestionsFlow = actions.promptEditor.activeSuggestions,
                        onSuggestionClick = actions.promptEditor::applySuggestion
                    )
                }

                AppMultilineTextField(
                    state = promptTemplateState,
                    onValueChange = actions.promptEditor::onPromptTemplateFromEditor,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 18,
                    maxLines = 18,
                    paragraphSelectionEnabled = uiState.editor.isParagraphSelectionMode,
                    highlightRange = uiState.editor.selectedParagraphRange?.toHighlightRange(),
                    selectedParagraphColor = AppTheme.colors.primary.copy(alpha = 0.22f),
                    searchHighlightRanges = uiState.editor.searchMatches,
                    activeSearchMatchIndex = uiState.editor.activeSearchMatchIndex,
                    supportingText = uiState.editor.paragraphSelectionMessage,
                    onParagraphOffsetSelected = actions.promptEditor::selectPromptParagraphAt,
                    onDeleteSelectedParagraph = actions.promptEditor::deleteSelectedPromptParagraph,
                    onReplaceSelectedParagraph = actions.promptEditor::replaceSelectedPromptParagraph
                )
            }
        }

        // 카드 2: 빠른 도구 모음 카드 (시스템 제어 & 프롬프트 조작 액션)
        if (showPromptActions) {
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    PromptActionRow(
                        promptTemplateState = promptTemplateState,
                        isParagraphSelectionMode = uiState.editor.isParagraphSelectionMode,
                        onToggleParagraphSelectionMode = actions.promptEditor::toggleParagraphSelectionMode,
                        canCloseGemini = uiState.canCloseGemini,
                        canCloseSelfApp = uiState.canCloseSelfApp,
                        canCleanMemory = uiState.canCleanMemory,
                        isMemoryCleanupScheduled = uiState.isMemoryCleanupScheduled,
                        isMaintenanceBusy = uiState.isMaintenanceBusy,
                        canNavigateHistoryBack = uiState.editor.canNavigateHistoryBack,
                        canNavigateHistoryForward = uiState.editor.canNavigateHistoryForward,
                        isHistoryIndicatorVisible = uiState.editor.isHistoryIndicatorVisible,
                        historyDotCount = uiState.editor.historyDotCount,
                        activeHistoryDotIndex = uiState.editor.activeHistoryDotIndex,
                        canCopyPrompt = uiState.hasPromptTemplate,
                        onCloseGeminiApp = actions::closeGeminiApp,
                        onCleanDeviceMemory = actions::cleanDeviceMemory,
                        onTerminateSelfApp = actions::terminateSelfApp,
                        onNavigateHistoryBack = actions.promptEditor::navigatePromptHistoryBack,
                        onNavigateHistoryForward = actions.promptEditor::navigatePromptHistoryForward,
                        onInsertTopInstruction = actions::insertTopInstruction,
                        onInsertBottomInstruction = actions::insertBottomInstruction,
                        onOpenInstructionConfigDialog = actions::openInstructionConfigDialog,
                        onImportFromClipboard = actions.promptEditor::importPromptFromClipboard,
                        onCopyPromptToClipboard = actions.promptEditor::copyPromptToClipboard,
                        showVariationButton = uiState.automationMode != AutomationMode.RECEIVER,
                        isVariationButtonEnabled = uiState.canInteractWithVariation,
                        variationAutomationState = uiState.variationAutomationState,
                        onRunVariation = { selectedText ->
                            actions.onClearFocus()
                            actions.onRunVariation(selectedText)
                        },
                        onOpenVariationPromptConfigDialog = actions::openVariationPromptConfigDialog
                    )
                }
            }
        }
    }
}

// ==========================================================================
// 2. Header & Top Control Components
// ==========================================================================

@Composable
internal fun PromptEditorHeader(
    selectedTargetApp: AutomationTargetApp,
    selectedAutomationMode: AutomationMode,
    isRunning: Boolean,
    isSearchActive: Boolean,
    isExperimentMode: Boolean,
    onTargetAppSelected: (AutomationTargetApp) -> Unit,
    onAutomationModeSelected: (AutomationMode) -> Unit,
    onToggleExperimentMode: () -> Unit,
    onShowPromptSnippetDialog: () -> Unit,
    onToggleSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TargetAppDropdownChip(
                selectedTargetApp = selectedTargetApp,
                enabled = !isRunning,
                onTargetAppSelected = onTargetAppSelected
            )
            CompactAutomationModeToggle(
                selectedMode = selectedAutomationMode,
                enabled = !isRunning,
                onModeSelected = onAutomationModeSelected
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            HeaderIconPillButton(
                icon = Icons.Default.Science,
                isActive = isExperimentMode,
                contentDescription = "실험(연금술) 모드 토글",
                onClick = onToggleExperimentMode
            )

            HeaderIconPillButton(
                icon = Icons.Default.Bookmarks,
                isActive = false,
                tintOverride = AppTheme.colors.accent,
                contentDescription = "상용구(텍스트 대치) 관리",
                onClick = onShowPromptSnippetDialog
            )

            HeaderIconPillButton(
                icon = Icons.Default.Search,
                isActive = isSearchActive,
                contentDescription = "문구 찾기",
                onClick = onToggleSearch
            )
        }
    }
}

@Composable
private fun HeaderIconPillButton(
    icon: ImageVector,
    isActive: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    tintOverride: androidx.compose.ui.graphics.Color? = null
) {
    val shape = RoundedCornerShape(10.dp)
    val bgColor = if (isActive) {
        AppTheme.colors.primary.copy(alpha = 0.15f)
    } else {
        AppTheme.colors.insetBed
    }
    val iconTint = when {
        isActive -> AppTheme.colors.primary
        tintOverride != null -> tintOverride
        else -> AppTheme.colors.textSecondary
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TargetAppDropdownChip(
    selectedTargetApp: AutomationTargetApp,
    enabled: Boolean,
    onTargetAppSelected: (AutomationTargetApp) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(AppTheme.colors.insetBed)
                .clickable(enabled = enabled) { expanded = true }
                .padding(start = 11.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                .semantics {
                    contentDescription = "자동화 대상 앱 선택: 현재 ${selectedTargetApp.displayName}"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = selectedTargetApp.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary.copy(alpha = 0.5f)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (enabled) AppTheme.colors.primary else AppTheme.colors.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = AppTheme.colors.card,
            shadowElevation = 8.dp
        ) {
            AutomationTargetApp.entries.forEach { targetApp ->
                val isSelected = targetApp == selectedTargetApp
                DropdownMenuItem(
                    text = {
                        Text(
                            text = targetApp.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AppTheme.colors.primary else AppTheme.colors.textPrimary
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AppTheme.colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        onTargetAppSelected(targetApp)
                    }
                )
            }
        }
    }
}

// ==========================================================================
// 3. Flow Image Count Row
// ==========================================================================

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
                NeuPillChip(
                    text = "${count}장",
                    selected = selectedCount == count,
                    enabled = enabled,
                    onClick = { onCountSelected(count) },
                    height = 30.dp,
                    textStyle = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * 에디터 내부 추천 칩 영역을 국소 관찰로 감싸 커서 이동 시 에디터 전체 리컴포지션을 방지합니다.
 */
@Composable
private fun IsolatedEditorSuggestionSection(
    activeSuggestionsFlow: StateFlow<List<WildcardTokenAutocomplete.Candidate>>,
    onSuggestionClick: (WildcardTokenAutocomplete.Candidate) -> Unit
) {
    val suggestions by activeSuggestionsFlow.collectAsStateWithLifecycle()
    val lastNonEmptyCandidates = remember {
        mutableStateOf(emptyList<WildcardTokenAutocomplete.Candidate>())
    }
    LaunchedEffect(suggestions) {
        if (suggestions.isNotEmpty()) {
            lastNonEmptyCandidates.value = suggestions
        }
    }
    val displayedCandidates = if (suggestions.isNotEmpty()) {
        suggestions
    } else {
        lastNonEmptyCandidates.value
    }

    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        enter = fadeIn(tween(150)) + expandVertically(tween(150)),
        exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
    ) {
        PromptSuggestionBar(
            suggestions = displayedCandidates,
            onSuggestionClick = onSuggestionClick
        )
    }
}

// ==========================================================================
// 4. Helper Extensions
// ==========================================================================

private fun PromptParagraphRange.toHighlightRange(): TextHighlightRange =
    TextHighlightRange(start = start, endExclusive = endExclusive)
