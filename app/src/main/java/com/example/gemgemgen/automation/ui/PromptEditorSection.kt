// 역할: 프롬프트 에디터의 메인 컨테이너 레이아웃, 헤더, 추천 칩 국소 격리 및 텍스트필드 조합을 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.ui.AppMultilineTextField
import com.example.gemgemgen.ui.TextHighlightRange
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuPillChip
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

// ==========================================================================
// 1. Main Editor Container & Entry Point
// ==========================================================================

@Composable
internal fun PromptEditorSection(
    uiState: AutomationUiState,
    promptTemplateState: TextFieldState,
    actions: AutomationScreenActions,
    showPromptActions: Boolean = true,
    showWildcardSuggestions: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PromptEditorHeader(
            selectedTargetApp = uiState.selectedTargetApp,
            isRunning = uiState.isRunning,
            isSearchActive = uiState.editor.isSearchActive,
            onTargetAppSelected = actions::onTargetAppSelected,
            onShowPromptSnippetDialog = actions::showPromptSnippetDialog,
            onToggleSearch = actions::toggleSearch
        )

        AnimatedVisibility(
            visible = uiState.editor.isSearchActive,
            enter = fadeIn(tween(150)) + expandVertically(tween(150)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
        ) {
            PromptSearchBar(
                query = uiState.editor.searchQuery,
                onQueryChange = actions::setSearchQuery,
                matchCount = uiState.editor.searchMatches.size,
                currentMatchIndex = uiState.editor.activeSearchMatchIndex,
                onNavigateNext = actions::navigateSearchNext,
                onNavigatePrevious = actions::navigateSearchPrevious,
                onClose = actions::closeSearch
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

        IsolatedEditorSuggestionSection(
            activeSuggestionsFlow = actions.activeSuggestions,
            showWildcardSuggestions = showWildcardSuggestions,
            onSuggestionClick = actions::applySuggestion
        )

        AppMultilineTextField(
            state = promptTemplateState,
            onValueChange = actions::onPromptTemplateChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            paragraphSelectionEnabled = uiState.editor.isParagraphSelectionMode,
            highlightRange = uiState.editor.selectedParagraphRange?.toHighlightRange(),
            selectedParagraphColor = AppTheme.colors.primary.copy(alpha = 0.22f),
            searchHighlightRanges = uiState.editor.searchMatches,
            activeSearchMatchIndex = uiState.editor.activeSearchMatchIndex,
            supportingText = uiState.editor.paragraphSelectionMessage,
            onParagraphOffsetSelected = actions::selectPromptParagraphAt,
            onDeleteSelectedParagraph = actions::deleteSelectedPromptParagraph,
            onReplaceSelectedParagraph = actions::replaceSelectedPromptParagraph
        )

        if (showPromptActions) {
            PromptActionRow(
                promptTemplateState = promptTemplateState,
                isParagraphSelectionMode = uiState.editor.isParagraphSelectionMode,
                onToggleParagraphSelectionMode = actions::toggleParagraphSelectionMode,
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
                onNavigateHistoryBack = actions::navigatePromptHistoryBack,
                onNavigateHistoryForward = actions::navigatePromptHistoryForward,
                onInsertTopInstruction = actions::insertTopInstruction,
                onInsertBottomInstruction = actions::insertBottomInstruction,
                onOpenInstructionConfigDialog = actions::openInstructionConfigDialog,
                onImportFromClipboard = actions::importPromptFromClipboard,
                onCopyPromptToClipboard = actions::copyPromptToClipboard,
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

// ==========================================================================
// 2. Header & Top Control Components
// ==========================================================================

@Composable
internal fun PromptEditorHeader(
    selectedTargetApp: AutomationTargetApp,
    isRunning: Boolean,
    isSearchActive: Boolean,
    onTargetAppSelected: (AutomationTargetApp) -> Unit,
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
            Text(
                text = "프롬프트 템플릿",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutomationTargetApp.entries.forEach { targetApp ->
                    NeuPillChip(
                        text = targetApp.displayName,
                        selected = selectedTargetApp == targetApp,
                        enabled = !isRunning,
                        onClick = { onTargetAppSelected(targetApp) }
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onShowPromptSnippetDialog,
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = "상용구(텍스트 대치) 관리" }
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmarks,
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = "문구 찾기" }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isSearchActive) AppTheme.colors.accent else AppTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
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
    showWildcardSuggestions: Boolean,
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
        visible = showWildcardSuggestions && suggestions.isNotEmpty(),
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
