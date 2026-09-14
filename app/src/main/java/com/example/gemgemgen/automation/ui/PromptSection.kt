// 역할: 프롬프트 텍스트 입력창, 대상 앱 선택 토글, 와일드카드 칩 및 2단 조약돌 액션 바(시스템 관리·에디터 도구)를 화면에 표시합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.getValue
import com.example.gemgemgen.core.AppDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
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

@Composable
internal fun PromptSection(
    promptTemplateState: TextFieldState,
    selectedTargetApp: AutomationTargetApp,
    isTargetSelectionEnabled: Boolean,
    isParagraphSelectionMode: Boolean,
    canNavigateHistoryBack: Boolean = false,
    canNavigateHistoryForward: Boolean = false,
    isHistoryIndicatorVisible: Boolean = false,
    historyDotCount: Int = 0,
    activeHistoryDotIndex: Int = 0,
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
    onNavigateHistoryBack: () -> Unit = {},
    onNavigateHistoryForward: () -> Unit = {},
    onInsertSystemInstruction: () -> Unit,
    onParagraphOffsetSelected: (Int) -> Unit,
    onDeleteSelectedParagraph: () -> Unit,
    onReplaceSelectedParagraph: (String) -> Unit,
    onImportFromClipboard: () -> Unit,
    onCopyPromptToClipboard: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onOpenPromptHistory: () -> Unit = {},
    activeGeminiAccountAlias: String = "서브1",
    onOpenGeminiAccountDialog: () -> Unit = {}
) {
    val suggestionTokens = rememberWildcardSuggestionTokens(
        promptTemplateState = promptTemplateState,
        wildcardTokenCandidates = wildcardTokenCandidates,
        isParagraphSelectionMode = isParagraphSelectionMode,
        isTargetSelectionEnabled = isTargetSelectionEnabled
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    .size(30.dp)
                    .semantics { contentDescription = "프롬프트 기록" }
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
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
                canNavigateHistoryBack = canNavigateHistoryBack,
                canNavigateHistoryForward = canNavigateHistoryForward,
                isHistoryIndicatorVisible = isHistoryIndicatorVisible,
                historyDotCount = historyDotCount,
                activeHistoryDotIndex = activeHistoryDotIndex,
                canCopyPrompt = canCopyPrompt,
                isTargetSelectionEnabled = isTargetSelectionEnabled,
                onCloseGeminiApp = onCloseGeminiApp,
                onCleanDeviceMemory = onCleanDeviceMemory,
                onTerminateSelfApp = onTerminateSelfApp,
                onNavigateHistoryBack = onNavigateHistoryBack,
                onNavigateHistoryForward = onNavigateHistoryForward,
                onInsertSystemInstruction = onInsertSystemInstruction,
                onImportFromClipboard = onImportFromClipboard,
                onCopyPromptToClipboard = onCopyPromptToClipboard,
                onPasteFromClipboard = onPasteFromClipboard,
                activeGeminiAccountAlias = activeGeminiAccountAlias,
                onOpenGeminiAccountDialog = onOpenGeminiAccountDialog
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

@Composable
internal fun PromptActionRow(
    canCloseGemini: Boolean,
    canCloseSelfApp: Boolean,
    canCleanMemory: Boolean,
    isMaintenanceBusy: Boolean = false,
    canNavigateHistoryBack: Boolean,
    canNavigateHistoryForward: Boolean,
    isHistoryIndicatorVisible: Boolean,
    historyDotCount: Int,
    activeHistoryDotIndex: Int,
    canCopyPrompt: Boolean,
    isTargetSelectionEnabled: Boolean,
    onCloseGeminiApp: () -> Unit,
    onCleanDeviceMemory: () -> Unit,
    onTerminateSelfApp: () -> Unit,
    onNavigateHistoryBack: () -> Unit,
    onNavigateHistoryForward: () -> Unit,
    onInsertSystemInstruction: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onCopyPromptToClipboard: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    activeGeminiAccountAlias: String = "서브1",
    onOpenGeminiAccountDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 1행: 시스템 & 디바이스 관리 바 (앱 종료, 리셋, 메모리 정리 ── 계정 뱃지)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 좌측: 앱 종료 + 리셋 + 메모리 정리
            ActionIsland {
                PebbleButton(
                    onClick = onTerminateSelfApp,
                    enabled = canCloseSelfApp && !isMaintenanceBusy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.semantics { contentDescription = "GemGemGen 앱 종료" }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
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

                PebbleButton(
                    onClick = onCloseGeminiApp,
                    enabled = canCloseGemini && !isMaintenanceBusy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.semantics { contentDescription = "Gemini 앱 리셋" }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
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

                PebbleButton(
                    onClick = onCleanDeviceMemory,
                    enabled = canCleanMemory && !isMaintenanceBusy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.semantics { contentDescription = "메모리 정리" }
                ) {
                    Text(
                        text = "메모리 정리",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 우측: 계정 관리 뱃지
            ActionIsland {
                PebbleButton(
                    onClick = onOpenGeminiAccountDialog,
                    enabled = !isMaintenanceBusy,
                    borderColor = AppTheme.colors.primary,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                    modifier = Modifier.semantics { contentDescription = "Gemini 계정 관리" }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = AppTheme.colors.primary
                        )
                        Text(
                            text = "ID: ${activeGeminiAccountAlias.ifBlank { "서브1" }}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.primary
                        )
                    }
                }
            }
        }

        // 2행: 프롬프트 에디터 전용 툴바 (히스토리 네비게이션 ── SI 삽입 + 복사 + 가져오기)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 좌측: 히스토리 네비게이션 (가로세로 35dp 1:1 정사각형 조약돌)
            ActionIsland(
                modifier = Modifier.animateContentSize()
            ) {
                PebbleButton(
                    onClick = onNavigateHistoryBack,
                    enabled = canNavigateHistoryBack && isTargetSelectionEnabled,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(35.dp)
                        .semantics { contentDescription = "이전 실행 기록" }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isHistoryIndicatorVisible && historyDotCount > 1,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        repeat(historyDotCount) { index ->
                            val isSelected = index == activeHistoryDotIndex
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) AppTheme.colors.primary
                                        else AppTheme.colors.textSecondary.copy(alpha = 0.35f)
                                    )
                            )
                        }
                    }
                }

                PebbleButton(
                    onClick = onNavigateHistoryForward,
                    enabled = canNavigateHistoryForward && isTargetSelectionEnabled,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(35.dp)
                        .semantics { contentDescription = "다음 실행 기록" }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // 우측: SI 삽입 + 복사 + 가져오기 (여유있는 좌우 패딩)
            ActionIsland {
                PebbleButton(
                    onClick = onInsertSystemInstruction,
                    enabled = isTargetSelectionEnabled,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                    modifier = Modifier.semantics { contentDescription = "[SI 삽입]" }
                ) {
                    Text(
                        text = "[SI 삽입]",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                PebbleButton(
                    onClick = onCopyPromptToClipboard,
                    enabled = canCopyPrompt,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(width = 44.dp, height = 35.dp)
                        .semantics { contentDescription = "프롬프트 복사" }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }

                PebbleButton(
                    onClick = onImportFromClipboard,
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 2.dp),
                    modifier = Modifier.semantics { contentDescription = "클립보드에서 가져오기" }
                ) {
                    Text(
                        text = "가져오기",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
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
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(3.dp),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.2.dp, AppTheme.colors.insetBorder),
        color = AppTheme.colors.insetBed,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun PebbleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = AppTheme.colors.cardBorder,
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    content: @Composable RowScope.() -> Unit
) {
    val animatedBorderColor by animateColorAsState(
        targetValue = if (enabled) borderColor else borderColor.copy(alpha = 0.45f),
        animationSpec = tween(durationMillis = 150),
        label = "PebbleBorder"
    )
    val animatedContainerColor by animateColorAsState(
        targetValue = if (enabled) AppTheme.colors.card else AppTheme.colors.card.copy(alpha = 0.65f),
        animationSpec = tween(durationMillis = 150),
        label = "PebbleContainer"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.textPrimary.copy(alpha = 0.4f),
        animationSpec = tween(durationMillis = 150),
        label = "PebbleContent"
    )

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = animatedContainerColor,
            contentColor = animatedContentColor,
            disabledContainerColor = animatedContainerColor,
            disabledContentColor = animatedContentColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = contentPadding,
        modifier = modifier.height(35.dp),
        border = BorderStroke(1.2.dp, animatedBorderColor),
        content = content
    )
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
            .height(28.dp)
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
