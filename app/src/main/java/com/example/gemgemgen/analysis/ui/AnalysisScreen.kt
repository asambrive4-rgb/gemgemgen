package com.example.gemgemgen.analysis.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.gemgemgen.analysis.domain.AnalysisDirection
import com.example.gemgemgen.analysis.domain.AnalysisGenerationCountPolicy
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisResultPresentation
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSource
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_1_FLASH_LITE
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_5_FLASH
import com.example.gemgemgen.analysis.domain.MODEL_GROK_4_5
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary
import com.example.gemgemgen.ui.AppMultilineTextField
import com.example.gemgemgen.ui.blockMainTabSwipe
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import kotlin.math.roundToInt

@Composable
internal fun AnalysisScreen(
    uiState: AnalysisUiState,
    sourcePromptState: TextFieldState,
    onClearFocus: () -> Unit,
    onSourcePromptChange: (String) -> Unit,
    onImportFromAutomation: () -> Unit,
    onCategorySelected: (AnalysisCategory) -> Unit,
    onClearTargetSegment: () -> Unit,
    onGenerate: () -> Unit,
    onGenerateTxt: () -> Unit,
    onCancelWork: () -> Unit,
    onRequestResetSession: () -> Unit,
    onConfirmResetSession: () -> Unit,
    onDismissResetSession: () -> Unit,
    onTxtCountChange: (Int) -> Unit,
    onToggleDirection: (String) -> Unit,
    onCustomHintChange: (String) -> Unit,
    onResultFileNameChange: (String) -> Unit,
    onApplyCandidate: (Int) -> Unit,
    onCopyCandidate: (Int) -> Unit,
    onRestoreOriginalPrompt: () -> Unit,
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

                SourcePromptAndMaskingRow(
                    sourcePromptState = sourcePromptState,
                    onSourcePromptChange = onSourcePromptChange,
                    onImportFromAutomation = onImportFromAutomation,
                    targetSegment = uiState.targetSegment,
                    isAnalyzing = uiState.status == AnalysisStatus.ANALYZING,
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
                    onResultFileNameChange = onResultFileNameChange,
                    onApplyCandidate = onApplyCandidate,
                    onCopyCandidate = onCopyCandidate,
                    onRestoreOriginalPrompt = onRestoreOriginalPrompt
                )

                // 하단 고정바에 가려지지 않도록 메인 스크롤 하단에 여백 Spacer 추가
                Spacer(modifier = Modifier.height(if (isKeyboardVisible) 260.dp else 160.dp))
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
                    onGenerate = onGenerate,
                    onGenerateTxt = onGenerateTxt,
                    onCancelWork = onCancelWork,
                    onRequestResetSession = onRequestResetSession,
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

    if (uiState.showResetConfirmation) {
        ResetAnalysisSessionDialog(
            onConfirm = onConfirmResetSession,
            onDismiss = onDismissResetSession
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
            // 세로 스크롤을 줄이기 위해 두 역할을 항상 좌우 1행으로 배치한다.
            // 좁은 폭에서는 칸 안 칩만 줄바꿈하고, 2행 세로 복귀는 하지 않는다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                RoleModelRow(
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1f),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleModelRow(
    label: String,
    provider: AnalysisProvider,
    modelId: String,
    onProviderSelected: (AnalysisProvider) -> Unit,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // 반폭 칸에서도 칩이 잘리지 않도록 FlowRow로 감싼다 (1행 레이아웃은 유지).
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
            when (provider) {
                AnalysisProvider.GEMINI -> {
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

/**
 * 원문 입력(좌)과 마스킹 결과(우)를 50:50으로 나란히 배치한다.
 * 분석 생성 탭에서는 프롬프트를 거의 수정하지 않으므로 입력 폭을 줄이고,
 * 비는 오른쪽에 마스킹 구간을 항상 보여 원문과 바로 비교할 수 있게 한다.
 *
 * 좌·우 헤더 높이(32dp)와 본문 하단을 맞춰 단차가 생기지 않게 한다.
 */
@Composable
private fun SourcePromptAndMaskingRow(
    sourcePromptState: TextFieldState,
    onSourcePromptChange: (String) -> Unit,
    onImportFromAutomation: () -> Unit,
    targetSegment: AnalysisTargetSegment?,
    isAnalyzing: Boolean,
    onClearTargetSegment: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 왼쪽: 헤더 + 원문 입력 (행 높이 기준)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
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
                    fontWeight = FontWeight.Bold
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
                // 분석 생성에서는 편집이 드물어 높이를 고정한다. 넘치면 칸 안에서 스크롤.
                minLines = 4,
                maxLines = 4,
                placeholder = "분석과 변주의 대상이 되는 전체 이미지 프롬프트를 입력하세요."
            )
        }
        // 오른쪽: 동일 헤더 높이 + 본문이 왼쪽 입력 높이까지 늘어남
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
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
                    text = when {
                        targetSegment == null -> "마스킹 결과"
                        targetSegment.source == AnalysisTargetSource.MANUAL -> "수동 마스킹"
                        else -> "자동 마스킹"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
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
                targetSegment = targetSegment,
                isAnalyzing = isAnalyzing
            )
        }
    }
}

/** 좌·우 섹션 제목 줄 공통 높이 (단차 정렬용). CompactOutlinedButton과 동일. */
private val SectionHeaderHeight = 32.dp

/** 하단 주 액션(TXT 생성 / 생성) 높이 */
private val PrimaryActionButtonHeight = 56.dp

@Composable
private fun StickyBottomActionPanel(
    uiState: AnalysisUiState,
    onCategorySelected: (AnalysisCategory) -> Unit,
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
            .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnalysisCategory.entries.forEach { category ->
                CategoryChip(
                    category = category,
                    selected = uiState.selectedCategory == category,
                    enabled = !uiState.isBusy,
                    onClick = { onCategorySelected(category) }
                )
            }
        }

        if (uiState.resultPresentation == AnalysisResultPresentation.TXT &&
            uiState.generatedCandidates.isNotEmpty()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCopyResults,
                    enabled = uiState.canCopyOrSave,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 40.dp)
                ) {
                    Text("목록 복사", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onSaveResults,
                    enabled = uiState.canCopyOrSave,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 40.dp)
                ) {
                    Text("와일드카드 파일 저장", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRequestResetSession,
                enabled = uiState.canResetSession,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(PrimaryActionButtonHeight)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "분석 세션 비우기"
                )
            }
            if (uiState.status == AnalysisStatus.GENERATING) {
                Button(
                    onClick = onCancelWork,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(PrimaryActionButtonHeight)
                ) {
                    Text(
                        text = "중지",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onGenerateTxt,
                    enabled = uiState.canGenerate,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(PrimaryActionButtonHeight)
                ) {
                    Text(
                        text = "TXT 생성",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                FilledTonalButton(
                    onClick = onGenerate,
                    enabled = uiState.canGenerate,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(PrimaryActionButtonHeight)
                ) {
                    Text(
                        text = "생성",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ResetAnalysisSessionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("분석 세션 비우기") },
        text = {
            Text(
                "원문, 카테고리, 마스킹, 생성 결과와 변주 조건이 모두 지워집니다. " +
                    "자동화 프롬프트와 계정 설정은 유지됩니다."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("비우기")
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

/** 마스킹 결과 본문 카드. 부모에서 높이를 채우도록 넘겨 원문 입력과 하단을 맞춘다. */
@Composable
private fun TargetSegmentBody(
    targetSegment: AnalysisTargetSegment?,
    isAnalyzing: Boolean,
    modifier: Modifier = Modifier
) {
    val hasSegment = targetSegment != null
    Surface(
        modifier = modifier,
        color = if (hasSegment) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
                Text(
                    text = "신뢰도 ${((targetSegment.confidence * 100).roundToInt()).coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = if (isAnalyzing) {
                        "자동 마스킹 분석 중..."
                    } else {
                        "자동 분석 후 마스킹 구간이 여기에 표시됩니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionSection(
    directions: List<AnalysisDirection>,
    selectedIds: Set<String>,
    onToggleDirection: (String) -> Unit
) {
    // 세로를 줄이기 위해 제목 + 한 줄 칩만 표시한다.
    // 더미 안내 문구·카드형 설명은 제거한다 (선택/힌트 로직은 유지).
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "추천 방향",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            directions.forEach { direction ->
                DirectionChip(
                    title = direction.title,
                    selected = direction.id in selectedIds,
                    onClick = { onToggleDirection(direction.id) }
                )
            }
        }
    }
}

@Composable
private fun DirectionChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
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
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
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
            steps = 27,
            // 가로 드래그가 탭 스와이프와 겹치지 않도록 분리
            modifier = Modifier.blockMainTabSwipe()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("10", style = MaterialTheme.typography.labelSmall)
            Text("기본 50", style = MaterialTheme.typography.labelSmall)
            Text("150", style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = "TXT 생성에만 적용 · 생성은 ${AnalysisGenerationCountPolicy.FIXED_COUNT}개 고정",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        HorizontalDivider()
        Text(
            text = "생성 결과 ${candidates.size}개",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "카드 본문을 누르면 자동화 프롬프트에 반영하고, 오른쪽 버튼을 누르면 해당 후보만 복사합니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (canRestoreOriginal) {
            OutlinedButton(
                onClick = onRestoreOriginalPrompt,
                enabled = enabled
            ) {
                Text("원본으로 되돌리기")
            }
        }
        candidates.forEachIndexed { index, candidate ->
            val selected = selectedIndex == index
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    }
                )
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
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = candidate,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(
                        onClick = { onCopyCandidate(index) },
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "${index + 1}번 후보 복사"
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(
            text = "생성 결과 ${candidates.size}개",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = candidates.joinToString(separator = "\n"),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 200.dp)
                .blockMainTabSwipe(),
            readOnly = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
        OutlinedTextField(
            value = resultFileName,
            onValueChange = onResultFileNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .blockMainTabSwipe(),
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
            modifier = Modifier
                .fillMaxWidth()
                .blockMainTabSwipe(),
            singleLine = false,
            minLines = 1,
            maxLines = 3,
            placeholder = { Text("예: 더 밝은 톤으로, 디테일한 묘사 추가 등") }
        )
    }
}
