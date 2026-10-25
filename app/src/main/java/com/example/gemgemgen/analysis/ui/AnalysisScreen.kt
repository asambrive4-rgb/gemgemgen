// 역할: AI 프롬프트 분석 화면의 전체 레이아웃과 접이식 모델 설정, 마스킹 결과 및 하단 액션 패널을 화면에 표시합니다.
package com.example.gemgemgen.analysis.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisDirection
import com.example.gemgemgen.analysis.domain.AnalysisDummyDirections
import com.example.gemgemgen.analysis.domain.AnalysisGenerationCountPolicy
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisResultPresentation
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.ui.AppMultilineTextField
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.ui.rememberIsImeVisible
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.NeuPillChip
import com.example.gemgemgen.ui.theme.appTextFieldColors
import kotlin.math.roundToInt

// =============================================================================
// 1. Screen Root & Layout Host
// =============================================================================

@Composable
internal fun AnalysisScreen(
    uiState: AnalysisUiState,
    sourcePromptState: TextFieldState,
    actions: AnalysisScreenActions,
    modifier: Modifier = Modifier
) {
    val isKeyboardVisible = rememberIsImeVisible()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.canvas)
            .clearFocusOnOutsideTap { actions.onClearFocus() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ApiKeyHeader(
                uiState = uiState,
                onRoleProviderSelected = { role, provider ->
                    actions.onRoleProviderSelected(role, provider)
                },
                onRoleModelSelected = { role, modelId ->
                    actions.onRoleModelSelected(role, modelId)
                },
                onShowKeyDialog = { actions.showKeyDialog() },
                onStartGrokLogin = { actions.startGrokLogin() },
                onLogoutGrok = { actions.logoutGrok() }
            )

            SourcePromptAndMaskingRow(
                sourcePromptState = sourcePromptState,
                onSourcePromptChange = { actions.onSourcePromptChange(it) },
                onImportFromAutomation = { actions.importSourcePromptFromAutomation() },
                targetSegment = uiState.targetSegment,
                onClearTargetSegment = { actions.clearTargetSegment() }
            )

            DirectionSection(
                directions = AnalysisDummyDirections.values,
                selectedIds = uiState.selectedDirectionIds,
                onToggleDirection = { actions.toggleDirection(it) }
            )

            CustomHintSection(
                customHint = uiState.customHint,
                onCustomHintChange = { actions.onCustomHintChange(it) }
            )

            ResultSection(
                uiState = uiState,
                onResultFileNameChange = { actions.onResultFileNameChange(it) },
                onApplyCandidate = { actions.applyCandidate(it) },
                onCopyCandidate = { actions.copyCandidate(it) },
                onRestoreOriginalPrompt = { actions.restoreOriginalPrompt() }
            )

            // 하단 고정바에 가려지지 않도록 메인 스크롤 하단에 키보드 반응형 동적 여백 제공
            AnalysisBottomSpacer()
        }

        // 하단 고정바는 항상 유지하되, 키보드가 올라왔을 때는 슬림 모드로 표시하여
        // TXT 개수 입력창의 포커스가 끊기지 않게 보호하면서 에디터 시야를 확보합니다.
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = AppTheme.colors.card.copy(alpha = 0.98f),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            StickyBottomActionPanel(
                uiState = uiState,
                isKeyboardVisible = isKeyboardVisible,
                onCategorySelected = { actions.onCategorySelected(it) },
                onTxtCountChange = { actions.onTxtCountChange(it) },
                onGenerate = { actions.generate() },
                onGenerateTxt = { actions.generateTxt() },
                onCancelWork = { actions.cancelActiveWork() },
                onRequestResetSession = { actions.requestResetSession() },
                onCopyResults = { actions.copyGeneratedResults() },
                onSaveResults = { actions.onSaveResults() }
            )
        }
    }

    val activeDialog = deriveActiveAnalysisDialog(uiState)
    if (activeDialog !is AnalysisDialogType.None) {
        AnalysisDialogHost(
            activeDialog = activeDialog,
            uiState = uiState,
            actions = actions
        )
    }
}

@Composable
private fun AnalysisBottomSpacer() {
    val isKeyboardVisible = rememberIsImeVisible()
    Spacer(modifier = Modifier.height(if (isKeyboardVisible) 80.dp else 148.dp))
}

// =============================================================================
// 2. Header & Role Model Selector (1줄 요약 접이식)
// =============================================================================

@Composable
private fun ApiKeyHeader(
    uiState: AnalysisUiState,
    onRoleProviderSelected: (AnalysisModelRole, AnalysisProvider) -> Unit,
    onRoleModelSelected: (AnalysisModelRole, String) -> Unit,
    onShowKeyDialog: () -> Unit,
    onStartGrokLogin: () -> Unit,
    onLogoutGrok: () -> Unit
) {
    var expandedRole by remember { mutableStateOf<AnalysisModelRole?>(null) }
    val headerShape = RoundedCornerShape(18.dp)

    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        shape = headerShape,
        elevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 평소에는 두 역할을 좌우 1행에 컴팩트한 1줄 요약 버튼으로 표시
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoleSummaryButton(
                    roleTitle = "자동 마스킹",
                    summaryText = formatModelSummaryLabel(uiState.maskingProvider, uiState.maskingModel),
                    isExpanded = expandedRole == AnalysisModelRole.MASKING,
                    onClick = {
                        expandedRole = if (expandedRole == AnalysisModelRole.MASKING) null else AnalysisModelRole.MASKING
                    },
                    modifier = Modifier.weight(1f)
                )
                RoleSummaryButton(
                    roleTitle = "TXT 생성",
                    summaryText = formatModelSummaryLabel(uiState.generationProvider, uiState.generationModel),
                    isExpanded = expandedRole == AnalysisModelRole.GENERATION,
                    onClick = {
                        expandedRole = if (expandedRole == AnalysisModelRole.GENERATION) null else AnalysisModelRole.GENERATION
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // 특정 역할을 누르면 해당 역할의 모델 선택 칩 영역이 애니메이션과 함께 펼쳐짐
            AnimatedVisibility(visible = expandedRole != null) {
                val currentRole = expandedRole
                if (currentRole != null) {
                    val (provider, modelId) = when (currentRole) {
                        AnalysisModelRole.MASKING -> uiState.maskingProvider to uiState.maskingModel
                        AnalysisModelRole.GENERATION -> uiState.generationProvider to uiState.generationModel
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppTheme.colors.insetBed, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = when (currentRole) {
                                AnalysisModelRole.MASKING -> "자동 마스킹 프로바이더 및 모델 선택"
                                AnalysisModelRole.GENERATION -> "TXT 생성 프로바이더 및 모델 선택"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textSecondary
                        )
                        ModelSelectorChips(
                            selectedProvider = provider,
                            selectedModelId = modelId,
                            onSelectProvider = { newProvider ->
                                onRoleProviderSelected(currentRole, newProvider)
                            },
                            onSelectModel = { newModelId ->
                                onRoleModelSelected(currentRole, newModelId)
                                expandedRole = null
                            }
                        )
                    }
                }
            }

            AuthActionsRow(
                uiState = uiState,
                onShowKeyDialog = onShowKeyDialog,
                onStartGrokLogin = onStartGrokLogin,
                onLogoutGrok = onLogoutGrok
            )
        }
    }
}

@Composable
private fun RoleSummaryButton(
    roleTitle: String,
    summaryText: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isExpanded) AppTheme.colors.primary.copy(alpha = 0.12f) else AppTheme.colors.insetBed,
        border = BorderStroke(
            1.dp,
            if (isExpanded) AppTheme.colors.primary else AppTheme.colors.insetBorder
        ),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = "$roleTitle: ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 1
                )
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExpanded) AppTheme.colors.primary else AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = if (isExpanded) "접기" else "펼치기",
                tint = if (isExpanded) AppTheme.colors.primary else AppTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AuthActionsRow(
    uiState: AnalysisUiState,
    onShowKeyDialog: () -> Unit,
    onStartGrokLogin: () -> Unit,
    onLogoutGrok: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.usesGemini) {
            CompactOutlinedButton(text = "키 관리", onClick = onShowKeyDialog)
            if (uiState.geminiKeyPreview.isNotBlank()) {
                Text(
                    text = "(${uiState.geminiKeyPreview})",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary
                )
            }
        }
        if (uiState.usesGrok) {
            if (uiState.isGrokLoggedIn) {
                CompactOutlinedButton(text = "Grok 로그아웃", onClick = onLogoutGrok)
                if (uiState.grokAccountPreview.isNotBlank()) {
                    Text(
                        text = "(${uiState.grokAccountPreview})",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                }
                uiState.grokRemainingPercent?.let { remaining ->
                    Text(
                        text = "남은 ${remaining}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.primary
                    )
                }
            } else {
                CompactOutlinedButton(text = "Grok 로그인", onClick = onStartGrokLogin)
            }
        }
    }
}

@Composable
private fun CompactOutlinedButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(30.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.colors.textPrimary
        )
    }
}

// =============================================================================
// 3. Source Prompt & Masking Columns
// =============================================================================

/** 좌·우 섹션 제목 줄 공통 높이 (단차 정렬용). CompactOutlinedButton과 동일. */
private val SectionHeaderHeight = 32.dp

/**
 * 원문 입력(좌)과 마스킹 결과(우)를 50:50으로 나란히 배치한다.
 * 좌·우 헤더 높이(32dp)와 본문 하단을 맞춰 단차가 생기지 않게 한다.
 */
@Composable
private fun SourcePromptAndMaskingRow(
    sourcePromptState: TextFieldState,
    onSourcePromptChange: (String) -> Unit,
    onImportFromAutomation: () -> Unit,
    targetSegment: AnalysisTargetSegment?,
    onClearTargetSegment: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        SourcePromptInputColumn(
            sourcePromptState = sourcePromptState,
            onSourcePromptChange = onSourcePromptChange,
            onImportFromAutomation = onImportFromAutomation,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        MaskingResultColumn(
            targetSegment = targetSegment,
            onClearTargetSegment = onClearTargetSegment,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun SourcePromptInputColumn(
    sourcePromptState: TextFieldState,
    onSourcePromptChange: (String) -> Unit,
    onImportFromAutomation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SectionHeaderHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "원문 입력",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            CompactOutlinedButton(
                text = "가져오기",
                onClick = onImportFromAutomation
            )
        }
        AppMultilineTextField(
            state = sourcePromptState,
            onValueChange = onSourcePromptChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 4,
            placeholder = "분석과 변주의 대상이 되는 전체 이미지 프롬프트를 입력하세요."
        )
    }
}

@Composable
private fun MaskingResultColumn(
    targetSegment: AnalysisTargetSegment?,
    onClearTargetSegment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SectionHeaderHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (targetSegment == null) "마스킹 결과" else "자동 마스킹",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            if (targetSegment != null) {
                TextButton(
                    onClick = onClearTargetSegment,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(SectionHeaderHeight)
                ) {
                    Text("해제", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        TargetSegmentBody(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            targetSegment = targetSegment
        )
    }
}

/** 마스킹 결과 본문 카드. 긴 텍스트도 스크롤해 모두 확인할 수 있도록 지원합니다. */
@Composable
private fun TargetSegmentBody(
    targetSegment: AnalysisTargetSegment?,
    modifier: Modifier = Modifier
) {
    val hasSegment = targetSegment != null
    val shape = RoundedCornerShape(18.dp)
    NeuCard(
        modifier = modifier,
        shape = shape,
        elevation = 2.dp,
        backgroundColor = if (hasSegment) {
            AppTheme.colors.primary.copy(alpha = 0.08f)
        } else {
            AppTheme.colors.inputBackground
        },
        borderColor = if (hasSegment) {
            AppTheme.colors.primary.copy(alpha = 0.45f)
        } else {
            AppTheme.colors.inputBorder
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (targetSegment != null) {
                Text(
                    text = "\"${targetSegment.text}\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
                Text(
                    text = "신뢰도 ${((targetSegment.confidence * 100).roundToInt()).coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary
                )
            } else {
                Text(
                    text = "자동 분석 후 마스킹 구간이 여기에 표시됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// =============================================================================
// 4. Analysis Options & Controls
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionSection(
    directions: List<AnalysisDirection>,
    selectedIds: Set<String>,
    onToggleDirection: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "추천 방향",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textPrimary
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            directions.forEach { direction ->
                NeuPillChip(
                    text = direction.title,
                    selected = direction.id in selectedIds,
                    onClick = { onToggleDirection(direction.id) }
                )
            }
        }
    }
}

@Composable
private fun CustomHintSection(
    customHint: String,
    onCustomHintChange: (String) -> Unit
) {
    val hintState = rememberSyncedTextFieldState(
        value = customHint,
        onValueChange = onCustomHintChange
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "추가 요청사항 (선택)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = "${hintState.text.length}/100",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textSecondary
            )
        }
        OutlinedTextField(
            state = hintState,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = appTextFieldColors(),
            inputTransformation = InputTransformation.maxLength(100),
            lineLimits = TextFieldLineLimits.MultiLine(
                minHeightInLines = 1,
                maxHeightInLines = 3
            ),
            placeholder = { Text("예: 더 밝은 톤으로, 디테일한 묘사 추가 등") }
        )
    }
}

// =============================================================================
// 5. Result Presentation
// =============================================================================

@Composable
private fun ResultSection(
    uiState: AnalysisUiState,
    onResultFileNameChange: (String) -> Unit,
    onApplyCandidate: (Int) -> Unit,
    onCopyCandidate: (Int) -> Unit,
    onRestoreOriginalPrompt: () -> Unit
) {
    if (uiState.generatedCandidates.isEmpty()) return

    when (uiState.resultPresentation) {
        AnalysisResultPresentation.CARDS -> {
            CardResultSection(
                candidates = uiState.generatedCandidates,
                selectedIndex = uiState.selectedCandidateIndex,
                canRestoreOriginal = uiState.hasAppliedCandidateToAutomation,
                enabled = !uiState.isBusy,
                onApplyCandidate = onApplyCandidate,
                onCopyCandidate = onCopyCandidate,
                onRestoreOriginalPrompt = onRestoreOriginalPrompt
            )
        }
        AnalysisResultPresentation.TXT -> {
            TxtResultSection(
                candidates = uiState.generatedCandidates,
                resultFileName = uiState.resultFileName,
                onResultFileNameChange = onResultFileNameChange
            )
        }
        AnalysisResultPresentation.NONE -> Unit
    }
}

@Composable
private fun CardResultSection(
    candidates: List<String>,
    selectedIndex: Int?,
    canRestoreOriginal: Boolean,
    enabled: Boolean,
    onApplyCandidate: (Int) -> Unit,
    onCopyCandidate: (Int) -> Unit,
    onRestoreOriginalPrompt: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = AppTheme.colors.cardBorder)
        Text(
            text = "생성 결과 ${candidates.size}개",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textPrimary
        )
        Text(
            text = "카드 본문을 누르면 자동화 프롬프트에 반영하고, 오른쪽 버튼을 누르면 해당 후보만 복사합니다.",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textSecondary
        )
        if (canRestoreOriginal) {
            NeuButton(
                onClick = onRestoreOriginalPrompt,
                enabled = enabled,
                isPrimary = false,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "원본으로 되돌리기",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        candidates.forEachIndexed { index, candidate ->
            val selected = selectedIndex == index
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = if (selected) 4.dp else 2.dp,
                backgroundColor = if (selected) {
                    AppTheme.colors.primary.copy(alpha = 0.12f)
                } else {
                    AppTheme.colors.card
                },
                borderColor = if (selected) {
                    AppTheme.colors.primary
                } else {
                    AppTheme.colors.cardBorder
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = enabled) { onApplyCandidate(index) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primary
                            )
                            if (selected) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AppTheme.colors.primary
                                ) {
                                    Text(
                                        text = "적용됨",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = candidate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = { onCopyCandidate(index) },
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "${index + 1}번 후보 복사",
                            tint = AppTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtResultSection(
    candidates: List<String>,
    resultFileName: String,
    onResultFileNameChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val joinedCandidates = remember(candidates) { candidates.joinToString(separator = "\n") }
    val fileNameState = rememberSyncedTextFieldState(
        value = resultFileName,
        onValueChange = onResultFileNameChange
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = AppTheme.colors.cardBorder)
        Text(
            text = "생성 결과 ${candidates.size}개",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textPrimary
        )
        NeuCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = 1.dp,
            backgroundColor = AppTheme.colors.inputBackground,
            borderColor = AppTheme.colors.inputBorder
        ) {
            Text(
                text = joinedCandidates,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = AppTheme.colors.textPrimary
            )
        }
        OutlinedTextField(
            state = fileNameState,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = appTextFieldColors(),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { focusManager.clearFocus(force = true) },
            label = { Text("저장할 와일드카드 파일명") },
            placeholder = { Text("옷.txt") }
        )
    }
}

// =============================================================================
// 6. Sticky Bottom Action Panel
// =============================================================================

/** 하단 주 액션(TXT 생성 / 생성) 높이 - 터치 규격 44~52dp 가이드 준수 (48dp) */
private val PrimaryActionButtonHeight = 48.dp

@Composable
private fun StickyBottomActionPanel(
    uiState: AnalysisUiState,
    isKeyboardVisible: Boolean,
    onCategorySelected: (AnalysisCategory) -> Unit,
    onTxtCountChange: (Int) -> Unit,
    onGenerate: () -> Unit,
    onGenerateTxt: () -> Unit,
    onCancelWork: () -> Unit,
    onRequestResetSession: () -> Unit,
    onCopyResults: () -> Unit,
    onSaveResults: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!isKeyboardVisible) {
            StickyCategorySelectorRow(
                selectedCategory = uiState.selectedCategory,
                isBusy = uiState.isBusy,
                onCategorySelected = onCategorySelected
            )

            if (uiState.resultPresentation == AnalysisResultPresentation.TXT &&
                uiState.generatedCandidates.isNotEmpty()
            ) {
                StickyTxtActionRow(
                    canCopyOrSave = uiState.canCopyOrSave,
                    onCopyResults = onCopyResults,
                    onSaveResults = onSaveResults
                )
            }

            if (!uiState.isBusy) {
                StickyStatusBanner(uiState = uiState)
            }
        }

        StickyMainActionButtons(
            canResetSession = uiState.canResetSession,
            canGenerate = uiState.canGenerate,
            status = uiState.status,
            uiState = uiState,
            onTxtCountChange = onTxtCountChange,
            onRequestResetSession = onRequestResetSession,
            onCancelWork = onCancelWork,
            onGenerateTxt = onGenerateTxt,
            onGenerate = onGenerate
        )
    }
}

// =============================================================================
// 7. Compact TXT Count Input
// =============================================================================

@Composable
private fun StickyCategorySelectorRow(
    selectedCategory: AnalysisCategory,
    isBusy: Boolean,
    onCategorySelected: (AnalysisCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnalysisCategory.entries.forEach { category ->
            NeuPillChip(
                text = category.label,
                selected = selectedCategory == category,
                enabled = !isBusy,
                onClick = { onCategorySelected(category) },
                modifier = Modifier.alpha(if (isBusy) 0.45f else 1f)
            )
        }
    }
}

@Composable
private fun StickyTxtActionRow(
    canCopyOrSave: Boolean,
    onCopyResults: () -> Unit,
    onSaveResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeuButton(
            onClick = onCopyResults,
            enabled = canCopyOrSave,
            isPrimary = false,
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("목록 복사", style = MaterialTheme.typography.labelMedium)
        }
        NeuButton(
            onClick = onSaveResults,
            enabled = canCopyOrSave,
            isPrimary = true,
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("와일드카드 파일 저장", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 메인 액션 버튼 바로 위에 노출되는 단일 상태 배너입니다.
 * 우선순위: 1) 에러 -> 2) 경고 -> 3) 전제조건 안내 -> 4) 작업 완료/안내
 */
@Composable
private fun StickyStatusBanner(
    uiState: AnalysisUiState,
    modifier: Modifier = Modifier
) {
    val bannerData = when {
        uiState.error.isNotBlank() -> {
            BannerData(
                text = uiState.error,
                icon = Icons.Default.ErrorOutline,
                bgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                textColor = MaterialTheme.colorScheme.error
            )
        }
        uiState.warning.isNotBlank() -> {
            BannerData(
                text = uiState.warning,
                icon = Icons.Default.WarningAmber,
                bgColor = Color(0xFFFFF4E5),
                textColor = Color(0xFFB26A00)
            )
        }
        uiState.preconditionHintMessage != null -> {
            BannerData(
                text = uiState.preconditionHintMessage!!,
                icon = Icons.Default.Info,
                bgColor = AppTheme.colors.insetBed,
                textColor = AppTheme.colors.textSecondary
            )
        }
        uiState.message.isNotBlank() -> {
            BannerData(
                text = uiState.message,
                icon = Icons.Default.CheckCircleOutline,
                bgColor = AppTheme.colors.primary.copy(alpha = 0.12f),
                textColor = AppTheme.colors.primary
            )
        }
        else -> null
    } ?: return

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bannerData.bgColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = bannerData.icon,
                contentDescription = null,
                tint = bannerData.textColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = bannerData.text,
                style = MaterialTheme.typography.bodySmall,
                color = bannerData.textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private data class BannerData(
    val text: String,
    val icon: ImageVector,
    val bgColor: Color,
    val textColor: Color
)

@Composable
private fun StickyMainActionButtons(
    canResetSession: Boolean,
    canGenerate: Boolean,
    status: AnalysisStatus,
    uiState: AnalysisUiState,
    onTxtCountChange: (Int) -> Unit,
    onRequestResetSession: () -> Unit,
    onCancelWork: () -> Unit,
    onGenerateTxt: () -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 세션 비우기(초기화) 버튼
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (canResetSession) AppTheme.colors.card else AppTheme.colors.card.copy(alpha = 0.5f),
            contentColor = if (canResetSession) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            modifier = Modifier
                .size(PrimaryActionButtonHeight)
                .shadow(
                    elevation = if (canResetSession) 3.dp else 0.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.3f),
                    spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.2f)
                )
                .clickable(
                    enabled = canResetSession,
                    onClick = onRequestResetSession
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "분석 세션 비우기"
                )
            }
        }

        if (status == AnalysisStatus.GENERATING) {
            // 작업 진행 중 상태: 인라인 진행 표시 및 중지 버튼
            NeuButton(
                onClick = onCancelWork,
                shape = RoundedCornerShape(14.dp),
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                borderColor = MaterialTheme.colorScheme.error,
                elevation = 6.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(PrimaryActionButtonHeight)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${uiState.message.ifBlank { "생성 중..." }} · 중지",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            // 대기/완료 상태: TXT 개수 숫자 입력칸 + TXT 생성 버튼 + 생성 버튼
            CompactTxtCountInput(
                count = uiState.txtCount,
                onCountChange = onTxtCountChange
            )

            NeuButton(
                onClick = onGenerateTxt,
                enabled = canGenerate,
                isPrimary = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(PrimaryActionButtonHeight)
            ) {
                Text(
                    text = "TXT 생성 (${uiState.txtCount})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            NeuButton(
                onClick = onGenerate,
                enabled = canGenerate,
                isPrimary = true,
                containerColor = AppTheme.colors.accent,
                contentColor = AppTheme.colors.onPrimary,
                borderColor = AppTheme.colors.accent,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(PrimaryActionButtonHeight)
            ) {
                Text(
                    text = "생성 (${AnalysisGenerationCountPolicy.FIXED_COUNT})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 하단 고정 액션 줄에 배치되는 컴팩트 숫자 직접 타이핑 입력칸입니다.
 */
@Composable
private fun CompactTxtCountInput(
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(count.toString()) }
    var isFocused by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(count, isFocused) {
        if (!isFocused && text != count.toString()) {
            text = count.toString()
        }
    }

    NeuInsetBed(
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .width(68.dp)
            .height(PrimaryActionButtonHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TXT 개수",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = AppTheme.colors.textSecondary,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(3)
                        text = digits
                        digits.toIntOrNull()?.let { parsed ->
                            onCountChange(parsed)
                        }
                    },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val parsed = text.toIntOrNull()
                            val coerced = AnalysisTxtCountPolicy.coerce(parsed ?: count)
                            text = coerced.toString()
                            onCountChange(coerced)
                            focusManager.clearFocus(force = true)
                        }
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(AppTheme.colors.primary),
                    modifier = Modifier
                        .widthIn(min = 20.dp, max = 34.dp)
                        .onFocusChanged { focusState ->
                            val wasFocused = isFocused
                            isFocused = focusState.isFocused
                            if (wasFocused && !focusState.isFocused) {
                                val parsed = text.toIntOrNull()
                                val coerced = AnalysisTxtCountPolicy.coerce(parsed ?: count)
                                text = coerced.toString()
                                onCountChange(coerced)
                            }
                        }
                )
                Text(
                    text = "개",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = AppTheme.colors.textSecondary
                )
            }
        }
    }
}
