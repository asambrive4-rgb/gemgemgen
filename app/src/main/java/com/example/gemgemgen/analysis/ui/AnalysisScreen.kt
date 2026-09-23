// 역할: AI 프롬프트 분석 화면의 전체 레이아웃과 액션 인터페이스 기반 사용자 인터랙션을 화면에 표시합니다.
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuPillChip
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
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary
import com.example.gemgemgen.ui.AppMultilineTextField
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.ui.theme.appTextFieldColors
import kotlin.math.roundToInt

@Composable
internal fun AnalysisScreen(
    uiState: AnalysisUiState,
    sourcePromptState: TextFieldState,
    actions: AnalysisScreenActions = AnalysisScreenActions.Empty,
    modifier: Modifier = Modifier
) {
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
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
                    onShowKeyDialog = { actions.onShowKeyDialog() },
                    onStartGrokLogin = { actions.onStartGrokLogin() },
                    onLogoutGrok = { actions.onLogoutGrok() }
                )

                SourcePromptAndMaskingRow(
                    sourcePromptState = sourcePromptState,
                    onSourcePromptChange = { actions.onSourcePromptChange(it) },
                    onImportFromAutomation = { actions.onImportFromAutomation() },
                    targetSegment = uiState.targetSegment,
                    isAnalyzing = uiState.status == AnalysisStatus.ANALYZING,
                    onClearTargetSegment = { actions.onClearTargetSegment() }
                )

                DirectionSection(
                    directions = uiState.directions,
                    selectedIds = uiState.selectedDirectionIds,
                    onToggleDirection = { actions.onToggleDirection(it) }
                )

                CustomHintSection(
                    customHint = uiState.customHint,
                    onCustomHintChange = { actions.onCustomHintChange(it) }
                )

                CountSection(
                    count = uiState.txtCount,
                    onCountChange = { actions.onTxtCountChange(it) }
                )

                FeedbackSection(uiState)

                ResultSection(
                    uiState = uiState,
                    onResultFileNameChange = { actions.onResultFileNameChange(it) },
                    onApplyCandidate = { actions.onApplyCandidate(it) },
                    onCopyCandidate = { actions.onCopyCandidate(it) },
                    onRestoreOriginalPrompt = { actions.onRestoreOriginalPrompt() }
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
                    onCategorySelected = { actions.onCategorySelected(it) },
                    onGenerate = { actions.onGenerate() },
                    onGenerateTxt = { actions.onGenerateTxt() },
                    onCancelWork = { actions.onCancelWork() },
                    onRequestResetSession = { actions.onRequestResetSession() },
                    onCopyResults = { actions.onCopyResults() },
                    onSaveResults = { actions.onSaveResults() }
                )
            }
        }
    }

    val activeDialog = deriveActiveAnalysisDialog(uiState)
    val dialogActions = AnalysisDialogActions(
        onDismissKeyDialog = { actions.onDismissKeyDialog() },
        onKeyLabelChange = { actions.onKeyLabelChange(it) },
        onKeyValueChange = { actions.onKeyValueChange(it) },
        onAddApiKey = { actions.onAddApiKey() },
        onDeleteApiKey = { actions.onDeleteApiKey(it) },
        onActivateApiKey = { actions.onActivateApiKey(it) },
        onStartEditApiKey = { actions.onStartEditApiKey(it) },
        onEditKeyLabelChange = { actions.onEditKeyLabelChange(it) },
        onCancelEditApiKey = { actions.onCancelEditApiKey() },
        onUpdateKeyLabel = { actions.onUpdateKeyLabel() },
        onConfirmResetSession = { actions.onConfirmResetSession() },
        onDismissResetSession = { actions.onDismissResetSession() },
        onConfirmOverwrite = { actions.onConfirmOverwrite() },
        onDismissOverwrite = { actions.onDismissOverwrite() },
        onOpenGrokLoginUrl = { actions.onOpenGrokLoginUrl(it) },
        onCancelGrokLogin = { actions.onCancelGrokLogin() }
    )
    AnalysisDialogHost(
        activeDialog = activeDialog,
        uiState = uiState,
        actions = dialogActions
    )
}

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
    AnalysisScreen(
        uiState = uiState,
        sourcePromptState = sourcePromptState,
        actions = object : AnalysisScreenActions {
            override fun onClearFocus() = onClearFocus()
            override fun onSourcePromptChange(value: String) = onSourcePromptChange(value)
            override fun onImportFromAutomation() = onImportFromAutomation()
            override fun onCategorySelected(category: AnalysisCategory) = onCategorySelected(category)
            override fun onClearTargetSegment() = onClearTargetSegment()
            override fun onGenerate() = onGenerate()
            override fun onGenerateTxt() = onGenerateTxt()
            override fun onCancelWork() = onCancelWork()
            override fun onRequestResetSession() = onRequestResetSession()
            override fun onConfirmResetSession() = onConfirmResetSession()
            override fun onDismissResetSession() = onDismissResetSession()
            override fun onTxtCountChange(value: Int) = onTxtCountChange(value)
            override fun onToggleDirection(id: String) = onToggleDirection(id)
            override fun onCustomHintChange(value: String) = onCustomHintChange(value)
            override fun onResultFileNameChange(value: String) = onResultFileNameChange(value)
            override fun onApplyCandidate(index: Int) = onApplyCandidate(index)
            override fun onCopyCandidate(index: Int) = onCopyCandidate(index)
            override fun onRestoreOriginalPrompt() = onRestoreOriginalPrompt()
            override fun onCopyResults() = onCopyResults()
            override fun onSaveResults() = onSaveResults()
            override fun onConfirmOverwrite() = onConfirmOverwrite()
            override fun onDismissOverwrite() = onDismissOverwrite()
            override fun onShowKeyDialog() = onShowKeyDialog()
            override fun onDismissKeyDialog() = onDismissKeyDialog()
            override fun onKeyLabelChange(value: String) = onKeyLabelChange(value)
            override fun onKeyValueChange(value: String) = onKeyValueChange(value)
            override fun onRoleProviderSelected(role: AnalysisModelRole, provider: AnalysisProvider) = onRoleProviderSelected(role, provider)
            override fun onRoleModelSelected(role: AnalysisModelRole, modelId: String) = onRoleModelSelected(role, modelId)
            override fun onStartGrokLogin() = onStartGrokLogin()
            override fun onCancelGrokLogin() = onCancelGrokLogin()
            override fun onLogoutGrok() = onLogoutGrok()
            override fun onOpenGrokLoginUrl(url: String) = onOpenGrokLoginUrl(url)
            override fun onAddApiKey() = onAddApiKey()
            override fun onDeleteApiKey(id: String) = onDeleteApiKey(id)
            override fun onActivateApiKey(id: String) = onActivateApiKey(id)
            override fun onStartEditApiKey(key: GeminiApiKeySummary) = onStartEditApiKey(key)
            override fun onEditKeyLabelChange(value: String) = onEditKeyLabelChange(value)
            override fun onCancelEditApiKey() = onCancelEditApiKey()
            override fun onUpdateKeyLabel() = onUpdateKeyLabel()
        }
    )
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
    val headerShape = RoundedCornerShape(18.dp)
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        shape = headerShape,
        elevation = 3.dp
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
        ModelSelectorChips(
            selectedProvider = provider,
            selectedModelId = modelId,
            onSelectProvider = onProviderSelected,
            onSelectModel = onModelSelected
        )
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

/** 하단 주 액션(TXT 생성 / 생성) 높이 - 터치 규격 44~52dp 가이드 준수 (48dp) */
private val PrimaryActionButtonHeight = 48.dp

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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnalysisCategory.entries.forEach { category ->
                NeuPillChip(
                    text = category.label,
                    selected = uiState.selectedCategory == category,
                    enabled = !uiState.isBusy,
                    onClick = { onCategorySelected(category) },
                    modifier = Modifier.alpha(if (uiState.isBusy) 0.45f else 1f)
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.primary,
                        contentColor = AppTheme.colors.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 40.dp)
                ) {
                    Text("와일드카드 파일 저장", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        val hintMessage = uiState.preconditionHintMessage
        if (hintMessage != null && !uiState.isBusy) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AppTheme.colors.insetBed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = hintMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (uiState.canResetSession) AppTheme.colors.card else AppTheme.colors.card.copy(alpha = 0.5f),
                contentColor = if (uiState.canResetSession) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                modifier = Modifier
                    .size(PrimaryActionButtonHeight)
                    .shadow(
                        elevation = if (uiState.canResetSession) 3.dp else 0.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.3f),
                        spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.2f)
                    )
                    .clickable(
                        enabled = uiState.canResetSession,
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
            if (uiState.status == AnalysisStatus.GENERATING) {
                NeuButton(
                    onClick = onCancelWork,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(PrimaryActionButtonHeight),
                    isPrimary = true
                ) {
                    Text(
                        text = "중지",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                NeuButton(
                    onClick = onGenerateTxt,
                    enabled = uiState.canGenerate,
                    isPrimary = true,
                    shape = RoundedCornerShape(12.dp),
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
                Surface(
                    onClick = onGenerate,
                    enabled = uiState.canGenerate,
                    shape = RoundedCornerShape(14.dp),
                    color = if (uiState.canGenerate) AppTheme.colors.accent else AppTheme.colors.accent.copy(alpha = 0.5f),
                    contentColor = if (uiState.canGenerate) AppTheme.colors.onPrimary else AppTheme.colors.onPrimary.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, AppTheme.colors.accent),
                    modifier = Modifier
                        .weight(1f)
                        .height(PrimaryActionButtonHeight)
                        .shadow(
                            elevation = if (uiState.canGenerate) 6.dp else 0.dp,
                            shape = RoundedCornerShape(14.dp),
                            ambientColor = AppTheme.colors.accent.copy(alpha = 0.4f),
                            spotColor = AppTheme.colors.accent.copy(alpha = 0.3f)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
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
}

/** 마스킹 결과 본문 카드. 부모에서 높이를 채우도록 넘겨 원문 입력과 하단을 맞춘다. */
@Composable
private fun TargetSegmentBody(
    targetSegment: AnalysisTargetSegment?,
    isAnalyzing: Boolean,
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
    val shape = RoundedCornerShape(14.dp)
    val containerColor = if (selected) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.card
    }
    val contentColor = if (selected) {
        AppTheme.colors.onPrimary
    } else {
        AppTheme.colors.textPrimary
    }
    val borderColor = if (selected) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.cardBorder
    }

    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .shadow(
                elevation = if (selected) 4.dp else 2.dp,
                shape = shape,
                ambientColor = if (selected) AppTheme.colors.primary.copy(alpha = 0.4f) else AppTheme.colors.shadowDark.copy(alpha = 0.3f),
                spotColor = if (selected) AppTheme.colors.primary.copy(alpha = 0.3f) else AppTheme.colors.shadowDark.copy(alpha = 0.2f)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = AppTheme.colors.onPrimary
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
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
    val focusManager = LocalFocusManager.current
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
                .heightIn(min = 160.dp, max = 200.dp),
            readOnly = true,
            shape = RoundedCornerShape(12.dp),
            colors = appTextFieldColors(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
        OutlinedTextField(
            value = resultFileName,
            onValueChange = onResultFileNameChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = appTextFieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus(force = true) }
            ),
            label = { Text("저장할 와일드카드 파일명") },
            placeholder = { Text("옷.txt") }
        )
    }
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
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = "${customHint.length}/100",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textSecondary
            )
        }
        OutlinedTextField(
            value = customHint,
            onValueChange = onCustomHintChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = appTextFieldColors(),
            singleLine = false,
            minLines = 1,
            maxLines = 3,
            placeholder = { Text("예: 더 밝은 톤으로, 디테일한 묘사 추가 등") }
        )
    }
}
