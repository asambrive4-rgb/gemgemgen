package com.example.gemgemgen.analysis.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSource
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.domain.AnalysisDirection
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_1_FLASH_LITE
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_5_FLASH
import com.example.gemgemgen.analysis.domain.MODEL_GROK_4_5
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary
import com.example.gemgemgen.ui.AppMultilineTextField
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import kotlin.math.roundToInt

@Composable
internal fun AnalysisScreen(
    uiState: AnalysisUiState,
    sourcePromptState: TextFieldState,
    onClearFocus: () -> Unit,
    onSourcePromptChange: (String) -> Unit,
    onCategorySelected: (AnalysisCategory) -> Unit,
    onApplyManualSelection: () -> Unit,
    onClearTargetSegment: () -> Unit,
    onAnalyzeAndMask: () -> Unit,
    onGenerateTxt: () -> Unit,
    onCancelWork: () -> Unit,
    onTxtCountChange: (Int) -> Unit,
    onToggleDirection: (String) -> Unit,
    onCustomHintChange: (String) -> Unit,
    onResultFileNameChange: (String) -> Unit,
    onCopyResults: () -> Unit,
    onSaveResults: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    onDismissOverwrite: () -> Unit,
    onShowKeyDialog: () -> Unit,
    onDismissKeyDialog: () -> Unit,
    onKeyLabelChange: (String) -> Unit,
    onKeyValueChange: (String) -> Unit,
    onRoleProviderSelected: (AnalysisModelRole, AnalysisProvider) -> Unit,
    onRoleModelSelected: (AnalysisModelRole, String) -> Unit,
    onStartGrokLogin: () -> Unit,
    onCancelGrokLogin: () -> Unit,
    onLogoutGrok: () -> Unit,
    onOpenGrokLoginUrl: (String) -> Unit,
    onAddApiKey: () -> Unit,
    onDeleteApiKey: (String) -> Unit,
    onActivateApiKey: (String) -> Unit,
    onStartEditApiKey: (GeminiApiKeySummary) -> Unit,
    onEditKeyLabelChange: (String) -> Unit,
    onCancelEditApiKey: () -> Unit,
    onUpdateKeyLabel: () -> Unit
) {
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .clearFocusOnOutsideTap(onClearFocus)
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
                    onRoleProviderSelected = onRoleProviderSelected,
                    onRoleModelSelected = onRoleModelSelected,
                    onShowKeyDialog = onShowKeyDialog,
                    onStartGrokLogin = onStartGrokLogin,
                    onLogoutGrok = onLogoutGrok
                )

                SourcePromptInputSection(
                    sourcePromptState = sourcePromptState,
                    onSourcePromptChange = onSourcePromptChange
                )

                TargetSegmentPanel(
                    targetSegment = uiState.targetSegment,
                    onClearTargetSegment = onClearTargetSegment
                )

                DirectionSection(
                    directions = uiState.directions,
                    selectedIds = uiState.selectedDirectionIds,
                    onToggleDirection = onToggleDirection
                )

                CustomHintSection(
                    customHint = uiState.customHint,
                    onCustomHintChange = onCustomHintChange
                )

                CountSection(
                    count = uiState.txtCount,
                    onCountChange = onTxtCountChange
                )

                FeedbackSection(uiState)

                ResultSection(
                    uiState = uiState,
                    onResultFileNameChange = onResultFileNameChange
                )

                // 하단 고정바에 가려지지 않도록 메인 스크롤 하단에 여백 Spacer 추가
                Spacer(modifier = Modifier.height(if (isKeyboardVisible) 240.dp else 140.dp))
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                StickyBottomActionPanel(
                    uiState = uiState,
                    onCategorySelected = onCategorySelected,
                    onApplyManualSelection = onApplyManualSelection,
                    onClearTargetSegment = onClearTargetSegment,
                    onAnalyzeAndMask = onAnalyzeAndMask,
                    onGenerateTxt = onGenerateTxt,
                    onCancelWork = onCancelWork,
                    onClearFocus = onClearFocus,
                    onCopyResults = onCopyResults,
                    onSaveResults = onSaveResults
                )
            }
        }
    }

    if (uiState.showKeyDialog) {
        ApiKeyDialog(
            uiState = uiState,
            onDismiss = onDismissKeyDialog,
            onLabelChange = onKeyLabelChange,
            onKeyValueChange = onKeyValueChange,
            onAdd = onAddApiKey,
            onDelete = onDeleteApiKey,
            onActivate = onActivateApiKey,
            onStartEdit = onStartEditApiKey
        )
    }

    uiState.editingApiKey?.let { editingKey ->
        EditKeyLabelDialog(
            originalLabel = editingKey.label,
            currentValue = uiState.editingKeyLabelInput,
            onValueChange = onEditKeyLabelChange,
            onDismiss = onCancelEditApiKey,
            onConfirm = onUpdateKeyLabel
        )
    }

    uiState.pendingOverwriteFileName?.let { fileName ->
        OverwriteDialog(
            fileName = fileName,
            onConfirmOverwrite = onConfirmOverwrite,
            onDismiss = onDismissOverwrite
        )
    }

    if (uiState.showGrokLoginDialog) {
        GrokLoginDialog(
            userCode = uiState.grokLoginUserCode,
            verificationUri = uiState.grokLoginVerificationUri,
            isPolling = uiState.isGrokLoginPolling,
            onOpenUrl = onOpenGrokLoginUrl,
            onCancel = onCancelGrokLogin
        )
    }
}

@Composable
private fun ModelChip(
    label: String,
    selected: Boolean,
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
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        },
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ApiKeyHeader(
    uiState: AnalysisUiState,
    onRoleProviderSelected: (AnalysisModelRole, AnalysisProvider) -> Unit,
    onRoleModelSelected: (AnalysisModelRole, String) -> Unit,
    onShowKeyDialog: () -> Unit,
    onStartGrokLogin: () -> Unit,
    onLogoutGrok: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RoleModelRow(
                label = "자동 마스킹",
                provider = uiState.maskingProvider,
                modelId = uiState.maskingModel,
                onProviderSelected = {
                    onRoleProviderSelected(AnalysisModelRole.MASKING, it)
                },
                onModelSelected = {
                    onRoleModelSelected(AnalysisModelRole.MASKING, it)
                }
            )
            RoleModelRow(
                label = "TXT 생성",
                provider = uiState.generationProvider,
                modelId = uiState.generationModel,
                onProviderSelected = {
                    onRoleProviderSelected(AnalysisModelRole.GENERATION, it)
                },
                onModelSelected = {
                    onRoleModelSelected(AnalysisModelRole.GENERATION, it)
                }
            )
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
private fun RoleModelRow(
    label: String,
    provider: AnalysisProvider,
    modelId: String,
    onProviderSelected: (AnalysisProvider) -> Unit,
    onModelSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModelChip(
                    label = "Gemini",
                    selected = provider == AnalysisProvider.GEMINI,
                    onClick = { onProviderSelected(AnalysisProvider.GEMINI) }
                )
                ModelChip(
                    label = "Grok",
                    selected = provider == AnalysisProvider.GROK,
                    onClick = { onProviderSelected(AnalysisProvider.GROK) }
                )
            }
            when (provider) {
                AnalysisProvider.GEMINI -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModelChip(
                            label = "3.5 Flash",
                            selected = modelId == MODEL_GEMINI_3_5_FLASH,
                            onClick = { onModelSelected(MODEL_GEMINI_3_5_FLASH) }
                        )
                        ModelChip(
                            label = "3.1 Lite",
                            selected = modelId == MODEL_GEMINI_3_1_FLASH_LITE,
                            onClick = { onModelSelected(MODEL_GEMINI_3_1_FLASH_LITE) }
                        )
                    }
                }
                AnalysisProvider.GROK -> {
                    ModelChip(
                        label = "Grok 4.5",
                        selected = modelId == MODEL_GROK_4_5,
                        onClick = { onModelSelected(MODEL_GROK_4_5) }
                    )
                }
            }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                uiState.grokRemainingPercent?.let { remaining ->
                    Text(
                        text = "남은 ${remaining}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
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
        // Material3 기본 MinHeight(40dp)보다 낮게 고정해 헤더 세로를 줄인다.
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun GrokLoginDialog(
    userCode: String,
    verificationUri: String,
    isPolling: Boolean,
    onOpenUrl: (String) -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Grok 로그인") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (userCode.isBlank()) {
                    Text("로그인 코드를 준비하는 중...")
                } else {
                    Text("Firefox에서 아래 코드를 승인하세요.")
                    Text(
                        text = userCode,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (verificationUri.isNotBlank()) {
                        Text(
                            text = verificationUri,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isPolling) {
                        Text(
                            text = "승인 대기 중...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (verificationUri.isNotBlank()) {
                TextButton(onClick = { onOpenUrl(verificationUri) }) {
                    Text("Firefox에서 열기")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun SourcePromptInputSection(
    sourcePromptState: TextFieldState,
    onSourcePromptChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "원문 입력",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        AppMultilineTextField(
            state = sourcePromptState,
            onValueChange = onSourcePromptChange,
            modifier = Modifier.fillMaxWidth(),
            // 기본 maxLines(18)의 약 2/3 높이로 제한해 세로 공간을 줄인다.
            minLines = 4,
            maxLines = 12,
            placeholder = "분석과 변주의 대상이 되는 전체 이미지 프롬프트를 입력하세요."
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickyBottomActionPanel(
    uiState: AnalysisUiState,
    onCategorySelected: (AnalysisCategory) -> Unit,
    onApplyManualSelection: () -> Unit,
    onClearTargetSegment: () -> Unit,
    onAnalyzeAndMask: () -> Unit,
    onGenerateTxt: () -> Unit,
    onCancelWork: () -> Unit,
    onClearFocus: () -> Unit,
    onCopyResults: () -> Unit,
    onSaveResults: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "카테고리:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 2.dp)
            )
            AnalysisCategory.entries.forEach { category ->
                CategoryChip(
                    category = category,
                    selected = uiState.selectedCategory == category,
                    enabled = !uiState.isBusy,
                    onClick = { onCategorySelected(category) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.generatedCandidates.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onCopyResults,
                        enabled = uiState.canCopyOrSave,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                    ) {
                        Text("목록 복사", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onSaveResults,
                        enabled = uiState.canCopyOrSave,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                    ) {
                        Text("와일드카드 파일 저장", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onApplyManualSelection()
                        onClearFocus()
                    },
                    enabled = !uiState.isBusy,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                ) {
                    Text("선택 구간 지정", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = if (uiState.status == AnalysisStatus.ANALYZING) {
                        onCancelWork
                    } else {
                        onAnalyzeAndMask
                    },
                    enabled = uiState.canAnalyze || uiState.status == AnalysisStatus.ANALYZING,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                ) {
                    Text(
                        text = if (uiState.status == AnalysisStatus.ANALYZING) "중지" else "자동 분석",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Button(
                    onClick = if (uiState.status == AnalysisStatus.GENERATING) {
                        onCancelWork
                    } else {
                        onGenerateTxt
                    },
                    enabled = uiState.canGenerate || uiState.status == AnalysisStatus.GENERATING,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                ) {
                    Text(
                        text = if (uiState.status == AnalysisStatus.GENERATING) "중지" else "TXT 생성",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: AnalysisCategory,
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
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            text = category.label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TargetSegmentPanel(
    targetSegment: AnalysisTargetSegment?,
    onClearTargetSegment: () -> Unit
) {
    if (targetSegment == null) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (targetSegment.source == AnalysisTargetSource.MANUAL) {
                        "수동 마스킹"
                    } else {
                        "자동 마스킹"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "\"${targetSegment.text}\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "신뢰도 ${((targetSegment.confidence * 100).roundToInt()).coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClearTargetSegment) {
                Text("해제")
            }
        }
    }
}

@Composable
private fun DirectionSection(
    directions: List<AnalysisDirection>,
    selectedIds: Set<String>,
    onToggleDirection: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "추천 방향",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "v1에서는 더미 방향을 사용합니다. 선택한 항목은 생성 힌트로만 반영됩니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        val rows = directions.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { direction ->
                        DirectionChip(
                            direction = direction,
                            selected = direction.id in selectedIds,
                            onClick = { onToggleDirection(direction.id) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    if (rowItems.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionChip(
    direction: AnalysisDirection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = direction.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = direction.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CountSection(
    count: Int,
    onCountChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "생성 개수",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${count}개",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = count.toFloat(),
            onValueChange = { raw ->
                val rounded = ((raw / 5f).roundToInt() * 5)
                onCountChange(rounded)
            },
            valueRange = AnalysisTxtCountPolicy.MIN_COUNT.toFloat()..
                AnalysisTxtCountPolicy.MAX_COUNT.toFloat(),
            steps = 27
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("10", style = MaterialTheme.typography.labelSmall)
            Text("기본 50", style = MaterialTheme.typography.labelSmall)
            Text("150", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FeedbackSection(uiState: AnalysisUiState) {
    val lines = listOf(uiState.message, uiState.warning, uiState.error).filter { it.isNotBlank() }
    if (lines.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val color = when (line) {
                uiState.error -> MaterialTheme.colorScheme.error
                uiState.warning -> Color(0xFFB26A00)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = line,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
private fun ResultSection(
    uiState: AnalysisUiState,
    onResultFileNameChange: (String) -> Unit
) {
    if (uiState.generatedCandidates.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(
            text = "생성 결과 ${uiState.generatedCandidates.size}개",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = uiState.generatedCandidates.joinToString(separator = "\n"),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 200.dp),
            readOnly = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
        OutlinedTextField(
            value = uiState.resultFileName,
            onValueChange = onResultFileNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("저장할 와일드카드 파일명") },
            placeholder = { Text("옷.txt") }
        )
    }
}

@Composable
private fun ApiKeyDialog(
    uiState: AnalysisUiState,
    onDismiss: () -> Unit,
    onLabelChange: (String) -> Unit,
    onKeyValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onActivate: (String) -> Unit,
    onStartEdit: (GeminiApiKeySummary) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini API 키 관리") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = uiState.keyLabelInput,
                    onValueChange = onLabelChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("키 이름") },
                    placeholder = { Text("개인 키") }
                )
                OutlinedTextField(
                    value = uiState.keyValueInput,
                    onValueChange = onKeyValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API 키") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(
                    onClick = onAdd,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("추가")
                }

                HorizontalDivider()

                if (uiState.apiKeys.isEmpty()) {
                    Text(
                        text = "저장된 키가 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    uiState.apiKeys.forEach { key ->
                        ApiKeyRow(
                            key = key,
                            onActivate = onActivate,
                            onDelete = onDelete,
                            onEdit = onStartEdit
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun ApiKeyRow(
    key: GeminiApiKeySummary,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (GeminiApiKeySummary) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (key.isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${key.label} ${key.preview}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onActivate(key.id) },
                    enabled = !key.isActive
                ) {
                    Text(if (key.isActive) "활성" else "활성화")
                }
                OutlinedButton(
                    onClick = { onEdit(key) }
                ) {
                    Text("이름 수정")
                }
                TextButton(onClick = { onDelete(key.id) }) {
                    Text("삭제")
                }
            }
        }
    }
}

@Composable
private fun OverwriteDialog(
    fileName: String,
    onConfirmOverwrite: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("같은 파일명이 있습니다") },
        text = {
            Text("$fileName 파일을 덮어쓸까요? 다른 이름을 입력하려면 취소하고 파일명을 바꿔주세요.")
        },
        confirmButton = {
            Button(onClick = onConfirmOverwrite) {
                Text("덮어쓰기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("다른 파일명 입력")
            }
        }
    )
}

@Composable
private fun EditKeyLabelDialog(
    originalLabel: String,
    currentValue: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API 키 이름 수정") },
        text = {
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("새 키 이름") },
                placeholder = { Text(originalLabel) }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun CustomHintSection(
    customHint: String,
    onCustomHintChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "추가 요청사항 (선택)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${customHint.length}/100",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = customHint,
            onValueChange = onCustomHintChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 1,
            maxLines = 3,
            placeholder = { Text("예: 더 밝은 톤으로, 디테일한 묘사 추가 등") }
        )
    }
}
