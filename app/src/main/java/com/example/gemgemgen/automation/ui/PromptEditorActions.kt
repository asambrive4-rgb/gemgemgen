// 역할: 프롬프트 에디터의 인스트럭션 삽입, 문단 편집, 히스토리 탐색 등 액션 버튼 툴바를 토스 3단계 무테두리 플랫 칩 위계로 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.InstructionTab
import com.example.gemgemgen.ui.theme.AppTheme

@Composable
internal fun PromptActionRow(
    promptTemplateState: TextFieldState,
    isParagraphSelectionMode: Boolean = false,
    onToggleParagraphSelectionMode: () -> Unit = {},
    canCloseGemini: Boolean,
    canCloseSelfApp: Boolean,
    canCleanMemory: Boolean,
    isMemoryCleanupScheduled: Boolean = false,
    isMaintenanceBusy: Boolean = false,
    canNavigateHistoryBack: Boolean,
    canNavigateHistoryForward: Boolean,
    isHistoryIndicatorVisible: Boolean,
    historyDotCount: Int,
    activeHistoryDotIndex: Int,
    canCopyPrompt: Boolean,
    onCloseGeminiApp: () -> Unit,
    onCleanDeviceMemory: () -> Unit,
    onTerminateSelfApp: () -> Unit,
    onNavigateHistoryBack: () -> Unit,
    onNavigateHistoryForward: () -> Unit,
    onInsertTopInstruction: () -> Unit,
    onInsertBottomInstruction: () -> Unit,
    onOpenInstructionConfigDialog: (InstructionTab) -> Unit,
    onImportFromClipboard: () -> Unit,
    onCopyPromptToClipboard: () -> Unit,
    showVariationButton: Boolean = true,
    isVariationButtonEnabled: Boolean = true,
    variationAutomationState: AutomationRunState = AutomationRunState.Idle,
    onRunVariation: (String?) -> Unit = {},
    onOpenVariationPromptConfigDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // 1행: 시스템 & 디바이스 관리 바 (앱 종료, 리셋, 메모리 정리 ── 변주, 문단 편집)
        PromptSystemDeviceBar(
            promptTemplateState = promptTemplateState,
            canCloseSelfApp = canCloseSelfApp,
            canCloseGemini = canCloseGemini,
            canCleanMemory = canCleanMemory,
            isMemoryCleanupScheduled = isMemoryCleanupScheduled,
            isMaintenanceBusy = isMaintenanceBusy,
            isParagraphSelectionMode = isParagraphSelectionMode,
            onTerminateSelfApp = onTerminateSelfApp,
            onCloseGeminiApp = onCloseGeminiApp,
            onCleanDeviceMemory = onCleanDeviceMemory,
            onToggleParagraphSelectionMode = onToggleParagraphSelectionMode,
            showVariationButton = showVariationButton,
            isVariationButtonEnabled = isVariationButtonEnabled,
            variationAutomationState = variationAutomationState,
            onRunVariation = onRunVariation,
            onOpenVariationPromptConfigDialog = onOpenVariationPromptConfigDialog
        )

        // 2행: 프롬프트 에디터 전용 툴바 (히스토리 네비게이션 ── 삽입 아이콘군 ── 복사 + 가져오기)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PromptHistoryNavigationIsland(
                canNavigateHistoryBack = canNavigateHistoryBack,
                canNavigateHistoryForward = canNavigateHistoryForward,
                isHistoryIndicatorVisible = isHistoryIndicatorVisible,
                historyDotCount = historyDotCount,
                activeHistoryDotIndex = activeHistoryDotIndex,
                onNavigateHistoryBack = onNavigateHistoryBack,
                onNavigateHistoryForward = onNavigateHistoryForward
            )

            PromptEditorActionGroup(
                canCopyPrompt = canCopyPrompt,
                onInsertTopInstruction = onInsertTopInstruction,
                onInsertBottomInstruction = onInsertBottomInstruction,
                onOpenInstructionConfigDialog = onOpenInstructionConfigDialog,
                onImportFromClipboard = onImportFromClipboard,
                onCopyPromptToClipboard = onCopyPromptToClipboard
            )
        }
    }
}

@Composable
internal fun PromptSystemDeviceBar(
    promptTemplateState: TextFieldState,
    canCloseSelfApp: Boolean,
    canCloseGemini: Boolean,
    canCleanMemory: Boolean,
    isMemoryCleanupScheduled: Boolean,
    isMaintenanceBusy: Boolean,
    isParagraphSelectionMode: Boolean,
    onTerminateSelfApp: () -> Unit,
    onCloseGeminiApp: () -> Unit,
    onCleanDeviceMemory: () -> Unit,
    onToggleParagraphSelectionMode: () -> Unit,
    showVariationButton: Boolean = true,
    isVariationButtonEnabled: Boolean = true,
    variationAutomationState: AutomationRunState = AutomationRunState.Idle,
    onRunVariation: (String?) -> Unit = {},
    onOpenVariationPromptConfigDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val variationSelectedTextAtPress = remember { mutableStateOf<String?>(null) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIsland(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PebbleButton(
                onClick = onTerminateSelfApp,
                enabled = canCloseSelfApp && !isMaintenanceBusy,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
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
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
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
                isHighlighted = isMemoryCleanupScheduled,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                modifier = Modifier.semantics {
                    contentDescription = if (isMemoryCleanupScheduled) "메모리 정리 예약됨" else "메모리 정리"
                }
            ) {
                Text(
                    text = if (isMemoryCleanupScheduled) "정리 예약됨" else "메모리 정리",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isMemoryCleanupScheduled) AppTheme.colors.primary else AppTheme.colors.textPrimary
                )
            }
        }

        // 우측: [변주] (일반 버튼 처리) + [문단 편집]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showVariationButton) {
                PebbleButton(
                    onClick = {
                        val selectedText = variationSelectedTextAtPress.value
                            ?: promptTemplateState.selectedTextOrNull()
                        variationSelectedTextAtPress.value = null
                        onRunVariation(selectedText)
                    },
                    onPointerDown = {
                        variationSelectedTextAtPress.value = promptTemplateState.selectedTextOrNull()
                    },
                    onLongClick = onOpenVariationPromptConfigDialog,
                    enabled = isVariationButtonEnabled,
                    isHighlighted = false,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = "변주 (길게 눌러 변주 프롬프트 설정)"
                    }
                ) {
                    Text(
                        text = if (variationAutomationState is AutomationRunState.Running) {
                            "변주 중"
                        } else {
                            "변주"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                }
            }

            ParagraphSelectionModeButton(
                selected = isParagraphSelectionMode,
                onClick = onToggleParagraphSelectionMode
            )
        }
    }
}

@Composable
internal fun PromptHistoryNavigationIsland(
    canNavigateHistoryBack: Boolean,
    canNavigateHistoryForward: Boolean,
    isHistoryIndicatorVisible: Boolean,
    historyDotCount: Int,
    activeHistoryDotIndex: Int,
    onNavigateHistoryBack: () -> Unit,
    onNavigateHistoryForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    ActionIsland(
        modifier = modifier.animateContentSize()
    ) {
        PebbleButton(
            onClick = onNavigateHistoryBack,
            enabled = canNavigateHistoryBack,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .size(37.dp)
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
            enabled = canNavigateHistoryForward,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .size(37.dp)
                .semantics { contentDescription = "다음 실행 기록" }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
internal fun PromptEditorActionGroup(
    canCopyPrompt: Boolean,
    onInsertTopInstruction: () -> Unit,
    onInsertBottomInstruction: () -> Unit,
    onOpenInstructionConfigDialog: (InstructionTab) -> Unit,
    onImportFromClipboard: () -> Unit,
    onCopyPromptToClipboard: () -> Unit,
    showInsertButtons: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showInsertButtons) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PebbleButton(
                    onClick = onInsertTopInstruction,
                    onLongClick = { onOpenInstructionConfigDialog(InstructionTab.TOP) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(width = 36.dp, height = 36.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "상단 지시문 삽입 (길게 눌러 설정)"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.VerticalAlignTop,
                        contentDescription = "상단 삽입",
                        modifier = Modifier.size(17.dp)
                    )
                }

                PebbleButton(
                    onClick = onInsertBottomInstruction,
                    onLongClick = { onOpenInstructionConfigDialog(InstructionTab.BOTTOM) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(width = 36.dp, height = 36.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "하단 지시문 삽입 (길게 눌러 설정)"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.VerticalAlignBottom,
                        contentDescription = "하단 삽입",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PebbleButton(
                onClick = onCopyPromptToClipboard,
                enabled = canCopyPrompt,
                isHighlighted = true,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(width = 38.dp, height = 36.dp)
                    .semantics { contentDescription = "프롬프트 복사" }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            PebbleButton(
                onClick = onImportFromClipboard,
                isHighlighted = true,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
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

@Composable
internal fun ActionIsland(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(5.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PebbleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPointerDown: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    isHighlighted: Boolean = false,
    @Suppress("UNUSED_PARAMETER") borderColor: Color = Color.Transparent,
    containerColor: Color = if (isHighlighted) {
        AppTheme.colors.primary.copy(alpha = 0.14f)
    } else {
        AppTheme.colors.insetBed
    },
    contentColor: Color = if (isHighlighted) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.textPrimary
    },
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
    content: @Composable RowScope.() -> Unit
) {
    val resolvedContainerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.55f)
    val resolvedContentColor = if (enabled) contentColor else AppTheme.colors.textSecondary.copy(alpha = 0.38f)

    val shape = RoundedCornerShape(10.dp)
    val pointerDownModifier = if (enabled) {
        onPointerDown?.let { callback ->
            Modifier.pointerInput(callback) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    callback()
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                }
            }
        } ?: Modifier
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .then(pointerDownModifier)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        color = resolvedContainerColor,
        contentColor = resolvedContentColor
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
internal fun ParagraphSelectionModeButton(
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        AppTheme.colors.primary.copy(alpha = 0.14f)
    } else {
        AppTheme.colors.insetBed
    }
    val contentColor = if (selected) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.textSecondary
    }
    val shape = RoundedCornerShape(10.dp)

    Surface(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = SegmentIcon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor
            )
            Text(
                text = "문단 편집",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

internal val GeminiGradientLogo: ImageVector by lazy {
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

internal val SegmentIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Segment",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.White)
    ) {
        moveTo(9f, 18f)
        horizontalLineTo(21f)
        verticalLineTo(16f)
        horizontalLineTo(9f)
        close()
        moveTo(3f, 6f)
        verticalLineTo(8f)
        horizontalLineTo(21f)
        verticalLineTo(6f)
        close()
        moveTo(9f, 13f)
        horizontalLineTo(21f)
        verticalLineTo(11f)
        horizontalLineTo(9f)
        close()
    }.build()
}

internal fun TextFieldState.selectedTextOrNull(): String? {
    return selectedPromptText(
        text = text.toString(),
        selectionStart = selection.start,
        selectionEnd = selection.end
    )
}

internal fun selectedPromptText(
    text: String,
    selectionStart: Int,
    selectionEnd: Int
): String? {
    val rangeStart = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val rangeEnd = maxOf(selectionStart, selectionEnd).coerceIn(rangeStart, text.length)
    return text.substring(rangeStart, rangeEnd).takeIf { rangeStart != rangeEnd }
}

internal fun resolveVariationStatusText(state: AutomationRunState): String {
    return when (state) {
        AutomationRunState.Idle -> ""
        is AutomationRunState.Running -> state.step
        AutomationRunState.Success -> "변주 프롬프트를 붙여넣었습니다. 보내지 않았습니다."
        AutomationRunState.Stopped -> "변주 작업을 중지했습니다."
        is AutomationRunState.Failure -> "변주 실패: ${state.message}"
    }
}
