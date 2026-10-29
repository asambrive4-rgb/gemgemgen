// 역할: 토스 스타일(Clean & Borderless) 단계별 카드와 모델 선택 드롭다운(NeuDropdownMenu), 공통 인셋 베드(NeuInsetBed) 레이아웃으로 AI 프롬프트 분석·변주 설정(1줄 통합 헤더, 풀폭 원문·인라인 마스킹·2줄 그리드 카테고리, 방향·힌트, 추천 후보·TXT 결과 및 플로팅 액션 독)을 표시합니다.
package com.example.gemgemgen.analysis.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.example.gemgemgen.analysis.domain.AnalysisDirectionCatalog
import com.example.gemgemgen.analysis.domain.AnalysisGenerationCountPolicy
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisResultPresentation
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_5_FLASH_LITE
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_6_FLASH
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_7_FLASH
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_8_FLASH
import com.example.gemgemgen.analysis.domain.MODEL_GROK_4_5
import com.example.gemgemgen.ui.AppMultilineTextField
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.ui.rememberIsImeVisible
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.AppThemeMode
import com.example.gemgemgen.ui.theme.LocalAppThemeMode
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuDropdownMenu
import com.example.gemgemgen.ui.theme.NeuDropdownMenuDivider
import com.example.gemgemgen.ui.theme.NeuDropdownMenuItem
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.NeuPillChip
import com.example.gemgemgen.ui.theme.appTextFieldColors
import kotlin.math.roundToInt

// =============================================================================
// 1. Screen Root & Step-by-Step Layout Host
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 상단: 1줄 통합 캡슐 헤더 (모델 선택 + API 키/Grok 배지)
            UnifiedEnginePillHeader(
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

            // STEP 1 카드: 풀폭 원문 에디터 + 인라인 마스킹 타겟 배너 + 2줄 그리드 카테고리 선택
            Step1SourceAndMaskingCard(
                sourcePromptState = sourcePromptState,
                onSourcePromptChange = { actions.onSourcePromptChange(it) },
                onImportFromAutomation = { actions.importSourcePromptFromAutomation() },
                targetSegment = uiState.targetSegment,
                onClearTargetSegment = { actions.clearTargetSegment() },
                selectedCategory = uiState.selectedCategory,
                isBusy = uiState.isBusy,
                onCategorySelected = { actions.onCategorySelected(it) }
            )

            // STEP 2 카드: 변주 방향 칩 + 1줄 인셋 추가 요청사항
            Step2DirectionsAndHintCard(
                directions = uiState.allDirections,
                selectedIds = uiState.selectedDirectionIds,
                onToggleDirection = { actions.toggleDirection(it) },
                onAddDirection = { actions.showAddDirectionDialog() },
                onDeleteDirection = { actions.deleteUserDirection(it) },
                customHint = uiState.customHint,
                onCustomHintChange = { actions.onCustomHintChange(it) }
            )

            // STEP 3 카드: 생성 결과 (카드 6개 또는 TXT 목록 + 일체형 파일 저장)
            Step3ResultSectionCard(
                uiState = uiState,
                onResultFileNameChange = { actions.onResultFileNameChange(it) },
                onApplyCandidate = { actions.applyCandidate(it) },
                onCopyCandidate = { actions.copyCandidate(it) },
                onRestoreOriginalPrompt = { actions.restoreOriginalPrompt() },
                onCopyResults = { actions.copyGeneratedResults() },
                onCopyDynamicPrompt = { actions.copyDynamicPrompt() },
                onSaveResults = { actions.onSaveResults() }
            )

            // 하단 플로팅 독에 가려지지 않도록 여백 확보
            AnalysisBottomSpacer(isKeyboardVisible = isKeyboardVisible)
        }

        // 하단: 토스식 1줄 플로팅 액션 독 (Floating Action Dock)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            FloatingBottomActionDock(
                uiState = uiState,
                isKeyboardVisible = isKeyboardVisible,
                onTxtCountChange = { actions.onTxtCountChange(it) },
                onGenerate = { actions.generate() },
                onGenerateTxt = { actions.generateTxt() },
                onCancelWork = { actions.cancelActiveWork() },
                onRequestResetSession = { actions.requestResetSession() }
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
private fun AnalysisBottomSpacer(isKeyboardVisible: Boolean) {
    Spacer(modifier = Modifier.height(if (isKeyboardVisible) 88.dp else 116.dp))
}

@Composable
private fun StepBadgeHeader(
    stepNumber: String,
    title: String,
    badgeColor: Color = AppTheme.colors.primary,
    trailingContent: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = AppTheme.colors.textPrimary
            )
        }
        trailingContent()
    }
}

// =============================================================================
// 2. Unified Pill Header (1줄 통합 캡슐 바: 모델 선택 + API/Grok 배지)
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnifiedEnginePillHeader(
    uiState: AnalysisUiState,
    onRoleProviderSelected: (AnalysisModelRole, AnalysisProvider) -> Unit,
    onRoleModelSelected: (AnalysisModelRole, String) -> Unit,
    onShowKeyDialog: () -> Unit,
    onStartGrokLogin: () -> Unit,
    onLogoutGrok: () -> Unit
) {
    var expandedRole by remember { mutableStateOf<AnalysisModelRole?>(null) }

    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 좌측: 마스킹 / 생성 모델 캡슐 버튼 (클릭 시 토스 스타일 드롭다운 메뉴로 선택)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box {
                        CompactRolePillButton(
                            roleTitle = "마스킹",
                            modelLabel = formatCompactModelName(uiState.maskingProvider, uiState.maskingModel),
                            isExpanded = expandedRole == AnalysisModelRole.MASKING,
                            onClick = {
                                expandedRole = if (expandedRole == AnalysisModelRole.MASKING) null else AnalysisModelRole.MASKING
                            }
                        )
                        AnalysisModelDropdownMenu(
                            expanded = expandedRole == AnalysisModelRole.MASKING,
                            onDismissRequest = { expandedRole = null },
                            roleTitle = "자동 마스킹 엔진 선택",
                            statusSubtitle = if (uiState.geminiKeyPreview.isNotBlank()) "활성 키: ${uiState.geminiKeyPreview}" else null,
                            selectedProvider = uiState.maskingProvider,
                            selectedModelId = uiState.maskingModel,
                            onSelect = { provider, modelId ->
                                onRoleProviderSelected(AnalysisModelRole.MASKING, provider)
                                onRoleModelSelected(AnalysisModelRole.MASKING, modelId)
                                expandedRole = null
                            }
                        )
                    }
                    Box {
                        CompactRolePillButton(
                            roleTitle = "생성",
                            modelLabel = formatCompactModelName(uiState.generationProvider, uiState.generationModel),
                            isExpanded = expandedRole == AnalysisModelRole.GENERATION,
                            onClick = {
                                expandedRole = if (expandedRole == AnalysisModelRole.GENERATION) null else AnalysisModelRole.GENERATION
                            }
                        )
                        AnalysisModelDropdownMenu(
                            expanded = expandedRole == AnalysisModelRole.GENERATION,
                            onDismissRequest = { expandedRole = null },
                            roleTitle = "변주 생성 엔진 선택",
                            statusSubtitle = if (uiState.grokAccountPreview.isNotBlank()) uiState.grokAccountPreview else null,
                            selectedProvider = uiState.generationProvider,
                            selectedModelId = uiState.generationModel,
                            onSelect = { provider, modelId ->
                                onRoleProviderSelected(AnalysisModelRole.GENERATION, provider)
                                onRoleModelSelected(AnalysisModelRole.GENERATION, modelId)
                                expandedRole = null
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 우측: 키 관리 & Grok 상태 캡슐 배지
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.usesGemini) {
                        val hasActiveKey = uiState.geminiKeyPreview.isNotBlank()
                        TossMiniBadgeButton(
                            text = if (hasActiveKey) "🔑 키 활성" else "🔑 키 등록",
                            isHighlighted = !hasActiveKey,
                            onClick = onShowKeyDialog
                        )
                    }
                    if (uiState.usesGrok) {
                        if (uiState.isGrokLoggedIn) {
                            val grokLabel = uiState.grokRemainingPercent?.let { "Grok ${it}%" } ?: "Grok 연동됨"
                            TossMiniBadgeButton(
                                text = grokLabel,
                                isHighlighted = true,
                                onClick = onLogoutGrok
                            )
                        } else {
                            TossMiniBadgeButton(
                                text = "Grok 로그인",
                                isHighlighted = true,
                                onClick = onStartGrokLogin
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 분석/생성 모델 선택용 토스 스타일 드롭다운 메뉴 (계정사용량 더보기 규격 일원화).
 */
@Composable
private fun AnalysisModelDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    roleTitle: String,
    statusSubtitle: String?,
    selectedProvider: AnalysisProvider,
    selectedModelId: String,
    onSelect: (AnalysisProvider, String) -> Unit
) {
    NeuDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .widthIn(min = 200.dp, max = 260.dp)
        ) {
            Text(
                text = roleTitle,
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textSecondary,
                fontWeight = FontWeight.Bold
            )
            if (!statusSubtitle.isNullOrBlank()) {
                Text(
                    text = statusSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        NeuDropdownMenuDivider()

        // Gemini 모델 그룹
        Text(
            text = "Gemini",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )
        val geminiModels = listOf(
            MODEL_GEMINI_3_5_FLASH_LITE to "3.5 Lite",
            MODEL_GEMINI_3_6_FLASH to "3.6 Flash",
            MODEL_GEMINI_3_7_FLASH to "3.7 Flash",
            MODEL_GEMINI_3_8_FLASH to "3.8 Flash"
        )
        geminiModels.forEach { (modelId, label) ->
            val isSelected = selectedProvider == AnalysisProvider.GEMINI && selectedModelId == modelId
            NeuDropdownMenuItem(
                text = label,
                isSelected = isSelected,
                onClick = { onSelect(AnalysisProvider.GEMINI, modelId) }
            )
        }

        NeuDropdownMenuDivider()

        // Grok 모델 그룹
        Text(
            text = "Grok",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )
        val isGrokSelected = selectedProvider == AnalysisProvider.GROK && selectedModelId == MODEL_GROK_4_5
        NeuDropdownMenuItem(
            text = "Grok 4.5",
            isSelected = isGrokSelected,
            onClick = { onSelect(AnalysisProvider.GROK, MODEL_GROK_4_5) }
        )
    }
}

private fun formatCompactModelName(provider: AnalysisProvider, modelId: String): String {
    val summary = formatModelSummaryLabel(provider, modelId)
    return summary.substringAfter("· ").trim().ifBlank { summary }
}

@Composable
private fun CompactRolePillButton(
    roleTitle: String,
    modelLabel: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isExpanded) {
        AppTheme.colors.primary.copy(alpha = 0.14f)
    } else {
        AppTheme.colors.insetBed
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = roleTitle,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.colors.textSecondary,
            maxLines = 1
        )
        Text(
            text = modelLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isExpanded) AppTheme.colors.primary else AppTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
            contentDescription = if (isExpanded) "접기" else "펼치기",
            tint = if (isExpanded) AppTheme.colors.primary else AppTheme.colors.textSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun TossMiniBadgeButton(
    text: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isHighlighted) {
        AppTheme.colors.primary.copy(alpha = 0.12f)
    } else {
        AppTheme.colors.insetBed
    }
    val contentColor = if (isHighlighted) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.textSecondary
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1
        )
    }
}

// =============================================================================
// 3. STEP 1 Card: 원문 에디터 (100% 풀폭) + 인라인 마스킹 배너 + 2줄 그리드 카테고리
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step1SourceAndMaskingCard(
    sourcePromptState: TextFieldState,
    onSourcePromptChange: (String) -> Unit,
    onImportFromAutomation: () -> Unit,
    targetSegment: AnalysisTargetSegment?,
    onClearTargetSegment: () -> Unit,
    selectedCategory: AnalysisCategory,
    isBusy: Boolean,
    onCategorySelected: (AnalysisCategory) -> Unit
) {
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StepBadgeHeader(
                stepNumber = "1",
                title = "원문 & 분석 타겟",
                trailingContent = {
                    TossMiniBadgeButton(
                        text = "자동화에서 가져오기",
                        isHighlighted = true,
                        onClick = onImportFromAutomation
                    )
                }
            )

            // 100% 풀폭 무테두리 인셋 원문 입력창
            AppMultilineTextField(
                state = sourcePromptState,
                onValueChange = onSourcePromptChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 6,
                placeholder = "분석할 원본 프롬프트를 입력하거나 가져오세요."
            )

            // 인라인 마스킹 타겟 하이라이트 배너 (inline_banner)
            InlineMaskingTargetBanner(
                targetSegment = targetSegment,
                selectedCategory = selectedCategory,
                onClearTargetSegment = onClearTargetSegment
            )

            // 분석 카테고리 선택기: 한눈에 보는 2줄 플로우 그리드 (grid_chips)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "무엇을 집중 변주할까요?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textSecondary
                    )
                    Text(
                        text = selectedCategory.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppTheme.colors.primary
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnalysisCategory.entries.forEach { category ->
                        NeuPillChip(
                            text = category.label,
                            selected = selectedCategory == category,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(10.dp),
                            onClick = { onCategorySelected(category) },
                            modifier = Modifier.alpha(if (isBusy) 0.45f else 1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineMaskingTargetBanner(
    targetSegment: AnalysisTargetSegment?,
    selectedCategory: AnalysisCategory,
    onClearTargetSegment: () -> Unit
) {
    if (targetSegment != null) {
        NeuInsetBed(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            backgroundColor = AppTheme.colors.primary.copy(alpha = 0.10f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🎯 마스킹 타겟 구간",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppTheme.colors.primary
                    )
                    Text(
                        text = "\"${targetSegment.text}\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.colors.card)
                        .clickable(onClick = onClearTargetSegment)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "해제",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
        }
    } else {
        NeuInsetBed(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "✨ 생성 시 [${selectedCategory.label}] 구간을 자동 감지해 마스킹합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary
                )
            }
        }
    }
}

// =============================================================================
// 4. STEP 2 Card: 변주 방향 칩 + 1줄 인셋 추가 요청사항
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step2DirectionsAndHintCard(
    directions: List<AnalysisDirection>,
    selectedIds: Set<String>,
    onToggleDirection: (String) -> Unit,
    onAddDirection: () -> Unit,
    onDeleteDirection: (String) -> Unit,
    customHint: String,
    onCustomHintChange: (String) -> Unit
) {
    val hintState = rememberSyncedTextFieldState(
        value = customHint,
        onValueChange = onCustomHintChange
    )

    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StepBadgeHeader(
                stepNumber = "2",
                title = "변주 방향 & 요청사항",
                trailingContent = {
                    Text(
                        text = if (selectedIds.isEmpty()) "선택 안 함" else "${selectedIds.size}개 선택됨",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedIds.isEmpty()) AppTheme.colors.textSecondary else AppTheme.colors.primary
                    )
                }
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                directions.forEach { direction ->
                    val isSelected = direction.id in selectedIds
                    TossDirectionChip(
                        text = direction.title,
                        selected = isSelected,
                        onClick = { onToggleDirection(direction.id) },
                        onLongClick = if (AnalysisDirectionCatalog.isUserDirection(direction.id)) {
                            { onDeleteDirection(direction.id) }
                        } else {
                            null
                        }
                    )
                }
                TossDirectionChip(
                    text = "+ 추가",
                    selected = false,
                    onClick = onAddDirection
                )
            }

            // 무테두리 인셋 힌트 입력창 (불필요한 제목·글자수 카운터 제거로 시각적 피로도 해소)
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
                placeholder = {
                    Text(
                        text = "💡 추가 요청사항 입력 (선택 · 예: 더 밝은 톤, 실크 소재 등)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TossDirectionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val bgColor = if (selected) {
        AppTheme.colors.primary.copy(alpha = 0.14f)
    } else {
        AppTheme.colors.insetBed
    }
    val textColor = if (selected) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.textSecondary
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (selected) "✓ $text" else text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = textColor
        )
    }
}

// =============================================================================
// 5. STEP 3 Card: 생성 결과 (카드 6개 모드 & TXT 목록 일체형 저장 모드)
// =============================================================================

@Composable
private fun Step3ResultSectionCard(
    uiState: AnalysisUiState,
    onResultFileNameChange: (String) -> Unit,
    onApplyCandidate: (Int) -> Unit,
    onCopyCandidate: (Int) -> Unit,
    onRestoreOriginalPrompt: () -> Unit,
    onCopyResults: () -> Unit,
    onCopyDynamicPrompt: () -> Unit,
    onSaveResults: () -> Unit
) {
    if (uiState.generatedCandidates.isEmpty()) return

    when (uiState.resultPresentation) {
        AnalysisResultPresentation.CARDS -> {
            Step3CardsResultCard(
                candidates = uiState.generatedCandidates,
                selectedIndex = uiState.selectedCandidateIndex,
                canRestoreOriginal = uiState.hasAppliedCandidateToAutomation,
                enabled = !uiState.isBusy,
                onApplyCandidate = onApplyCandidate,
                onCopyCandidate = onCopyCandidate,
                onRestoreOriginalPrompt = onRestoreOriginalPrompt,
                onCopyDynamicPrompt = onCopyDynamicPrompt
            )
        }
        AnalysisResultPresentation.TXT -> {
            Step3TxtResultCard(
                candidates = uiState.generatedCandidates,
                resultFileName = uiState.resultFileName,
                canCopyOrSave = uiState.canCopyOrSave,
                onResultFileNameChange = onResultFileNameChange,
                onCopyResults = onCopyResults,
                onCopyDynamicPrompt = onCopyDynamicPrompt,
                onSaveResults = onSaveResults
            )
        }
        AnalysisResultPresentation.NONE -> Unit
    }
}

@Composable
private fun Step3CardsResultCard(
    candidates: List<String>,
    selectedIndex: Int?,
    canRestoreOriginal: Boolean,
    enabled: Boolean,
    onApplyCandidate: (Int) -> Unit,
    onCopyCandidate: (Int) -> Unit,
    onRestoreOriginalPrompt: () -> Unit,
    onCopyDynamicPrompt: () -> Unit
) {
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StepBadgeHeader(
                stepNumber = "3",
                title = "추천 후보 ${candidates.size}",
                badgeColor = AppTheme.colors.accent,
                trailingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canRestoreOriginal) {
                            TossMiniBadgeButton(
                                text = "원본 되돌리기",
                                isHighlighted = false,
                                onClick = { if (enabled) onRestoreOriginalPrompt() }
                            )
                        }
                        TossMiniBadgeButton(
                            text = "다이나믹 복사",
                            isHighlighted = true,
                            onClick = { if (enabled) onCopyDynamicPrompt() }
                        )
                    }
                }
            )

            candidates.forEachIndexed { index, candidate ->
                val selected = selectedIndex == index
                val itemBg = if (selected) {
                    AppTheme.colors.primary.copy(alpha = 0.12f)
                } else {
                    AppTheme.colors.insetBed
                }
                val numBadgeBg = if (selected) {
                    AppTheme.colors.primary
                } else {
                    AppTheme.colors.card
                }
                val numBadgeText = if (selected) {
                    AppTheme.colors.onPrimary
                } else {
                    AppTheme.colors.textSecondary
                }

                NeuInsetBed(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onApplyCandidate(index) },
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = itemBg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 번호 배지
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(numBadgeBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = numBadgeText
                            )
                        }

                        // 후보 본문
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = candidate,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = AppTheme.colors.textPrimary
                            )
                            if (selected) {
                                Text(
                                    text = "✓ 자동화 탭에 적용됨",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.primary
                                )
                            }
                        }

                        // 우측 복사 미니 버튼
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppTheme.colors.card)
                                .clickable(enabled = enabled) { onCopyCandidate(index) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "복사",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Step3TxtResultCard(
    candidates: List<String>,
    resultFileName: String,
    canCopyOrSave: Boolean,
    onResultFileNameChange: (String) -> Unit,
    onCopyResults: () -> Unit,
    onCopyDynamicPrompt: () -> Unit,
    onSaveResults: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val joinedCandidates = remember(candidates) { candidates.joinToString(separator = "\n") }
    val fileNameState = rememberSyncedTextFieldState(
        value = resultFileName,
        onValueChange = onResultFileNameChange
    )

    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StepBadgeHeader(
                stepNumber = "3",
                title = "TXT 생성 결과 ${candidates.size}줄",
                badgeColor = AppTheme.colors.accent,
                trailingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TossMiniBadgeButton(
                            text = "다이나믹 복사",
                            isHighlighted = false,
                            onClick = { if (canCopyOrSave) onCopyDynamicPrompt() }
                        )
                        TossMiniBadgeButton(
                            text = "전체 복사",
                            isHighlighted = true,
                            onClick = { if (canCopyOrSave) onCopyResults() }
                        )
                    }
                }
            )

            // 줄 목록 프리뷰 박스
            NeuInsetBed(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = joinedCandidates,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = AppTheme.colors.textPrimary
                )
            }

            // 카드 하단 일체형 파일명 입력 & 저장 바 (하단 플로팅 바의 복잡도를 없앰)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    state = fileNameState,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = appTextFieldColors(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onKeyboardAction = { focusManager.clearFocus(force = true) },
                    placeholder = { Text("파일명 (예: 여성의상.txt)") }
                )

                NeuButton(
                    onClick = onSaveResults,
                    enabled = canCopyOrSave,
                    isPrimary = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(52.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = "파일 저장",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =============================================================================
// 6. Floating Bottom Action Dock (토스식 1줄 플로팅 액션 독)
// =============================================================================

private val DockButtonHeight = 46.dp

@Composable
private fun FloatingBottomActionDock(
    uiState: AnalysisUiState,
    isKeyboardVisible: Boolean,
    onTxtCountChange: (Int) -> Unit,
    onGenerate: () -> Unit,
    onGenerateTxt: () -> Unit,
    onCancelWork: () -> Unit,
    onRequestResetSession: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!isKeyboardVisible && !uiState.isBusy) {
            StickyStatusBanner(uiState = uiState)
        }

        NeuCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1) 세션 초기화 아이콘 버튼
                Box(
                    modifier = Modifier
                        .size(DockButtonHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (uiState.canResetSession) AppTheme.colors.insetBed
                            else AppTheme.colors.insetBed.copy(alpha = 0.45f)
                        )
                        .clickable(
                            enabled = uiState.canResetSession,
                            onClick = onRequestResetSession
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "분석 세션 비우기",
                        tint = if (uiState.canResetSession) {
                            AppTheme.colors.textSecondary
                        } else {
                            AppTheme.colors.textSecondary.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (uiState.status == AnalysisStatus.GENERATING) {
                    // 작업 진행 중: 풀폭 중지 버튼
                    NeuButton(
                        onClick = onCancelWork,
                        shape = RoundedCornerShape(14.dp),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        elevation = 2.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(DockButtonHeight)
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${uiState.message.ifBlank { "분석·생성 중..." }} · 중지",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // 2) 일체형 TXT 개수 + TXT 생성 복합 버튼 (Toss Soft Split Button)
                    TossSplitTxtGenerateButton(
                        count = uiState.txtCount,
                        canGenerate = uiState.canGenerate,
                        onCountChange = onTxtCountChange,
                        onGenerateTxt = onGenerateTxt,
                        modifier = Modifier
                            .weight(1.15f)
                            .height(DockButtonHeight)
                    )

                    // 3) 메인 카드 생성 (6) 솔리드 버튼
                    NeuButton(
                        onClick = onGenerate,
                        enabled = uiState.canGenerate,
                        isPrimary = true,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(DockButtonHeight)
                    ) {
                        Text(
                            text = "✨ 카드 생성 (${AnalysisGenerationCountPolicy.FIXED_COUNT})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 토스 플로팅 독 내부의 [개수 입력 | TXT 생성] 일체형 분할 버튼.
 * 좌측 영역을 터치하면 숫자를 직접 타이핑해 바꿀 수 있고, 우측 영역을 터치하면 즉시 TXT 생성을 시작합니다.
 */
@Composable
private fun TossSplitTxtGenerateButton(
    count: Int,
    canGenerate: Boolean,
    onCountChange: (Int) -> Unit,
    onGenerateTxt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(count.toString()) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(count, isFocused) {
        if (!isFocused && text != count.toString()) {
            text = count.toString()
        }
    }

    val containerBg = if (canGenerate) {
        AppTheme.colors.primary.copy(alpha = 0.12f)
    } else {
        AppTheme.colors.insetBed.copy(alpha = 0.6f)
    }
    val activeTextColor = if (canGenerate) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.textSecondary.copy(alpha = 0.45f)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(containerBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 좌측: 인라인 개수 편집 칸
        Column(
            modifier = Modifier
                .width(54.dp)
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "개수",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = activeTextColor.copy(alpha = 0.8f),
                maxLines = 1
            )
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = activeTextColor,
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
                    .widthIn(min = 24.dp, max = 42.dp)
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
        }

        // 미세 구분선
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.6f)
                .background(AppTheme.colors.card.copy(alpha = 0.8f))
        )

        // 우측: TXT 생성 실행 버튼
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(enabled = canGenerate, onClick = onGenerateTxt)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "TXT 생성",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = activeTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StickyStatusBanner(
    uiState: AnalysisUiState,
    modifier: Modifier = Modifier
) {
    val themeMode = LocalAppThemeMode.current
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val bannerData = when {
        uiState.error.isNotBlank() -> {
            BannerData(
                text = uiState.error,
                icon = Icons.Default.ErrorOutline,
                bgColor = MaterialTheme.colorScheme.errorContainer,
                textColor = MaterialTheme.colorScheme.error
            )
        }
        uiState.warning.isNotBlank() -> {
            BannerData(
                text = uiState.warning,
                icon = Icons.Default.WarningAmber,
                bgColor = if (isDark) Color(0xFF332514) else Color(0xFFFFF4E5),
                textColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFB26A00)
            )
        }
        uiState.preconditionHintMessage != null -> {
            BannerData(
                text = uiState.preconditionHintMessage!!,
                icon = Icons.Default.Info,
                bgColor = AppTheme.colors.card,
                textColor = AppTheme.colors.textSecondary
            )
        }
        uiState.message.isNotBlank() -> {
            BannerData(
                text = uiState.message,
                icon = Icons.Default.CheckCircleOutline,
                bgColor = AppTheme.colors.card,
                textColor = AppTheme.colors.primary
            )
        }
        else -> null
    } ?: return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bannerData.bgColor,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
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
