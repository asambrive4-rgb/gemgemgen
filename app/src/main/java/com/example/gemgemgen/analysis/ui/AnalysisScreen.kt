package com.example.gemgemgen.analysis.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSource
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.domain.AnalysisDirection
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary
import com.example.gemgemgen.ui.AppMultilineTextField
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
    onResultFileNameChange: (String) -> Unit,
    onCopyResults: () -> Unit,
    onSaveResults: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    onDismissOverwrite: () -> Unit,
    onShowKeyDialog: () -> Unit,
    onDismissKeyDialog: () -> Unit,
    onKeyLabelChange: (String) -> Unit,
    onKeyValueChange: (String) -> Unit,
    onAddApiKey: () -> Unit,
    onDeleteApiKey: (String) -> Unit,
    onActivateApiKey: (String) -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxWidth()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ApiKeyHeader(
                activePreview = uiState.activeKeyPreview,
                hasKeys = uiState.apiKeys.isNotEmpty(),
                onShowKeyDialog = onShowKeyDialog
            )

            SourcePromptSection(
                sourcePromptState = sourcePromptState,
                uiState = uiState,
                onSourcePromptChange = onSourcePromptChange,
                onCategorySelected = onCategorySelected,
                onApplyManualSelection = onApplyManualSelection,
                onClearTargetSegment = onClearTargetSegment,
                onAnalyzeAndMask = onAnalyzeAndMask,
                onGenerateTxt = onGenerateTxt,
                onCancelWork = onCancelWork,
                onClearFocus = onClearFocus
            )

            DirectionSection(
                directions = uiState.directions,
                selectedIds = uiState.selectedDirectionIds,
                onToggleDirection = onToggleDirection
            )

            CountSection(
                count = uiState.txtCount,
                onCountChange = onTxtCountChange
            )

            FeedbackSection(uiState)

            ResultSection(
                uiState = uiState,
                onResultFileNameChange = onResultFileNameChange,
                onCopyResults = onCopyResults,
                onSaveResults = onSaveResults
            )
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
            onActivate = onActivateApiKey
        )
    }

    uiState.pendingOverwriteFileName?.let { fileName ->
        OverwriteDialog(
            fileName = fileName,
            onConfirmOverwrite = onConfirmOverwrite,
            onDismiss = onDismissOverwrite
        )
    }
}

@Composable
private fun ApiKeyHeader(
    activePreview: String,
    hasKeys: Boolean,
    onShowKeyDialog: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Gemini API 키",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        activePreview.isNotBlank() -> "활성 키: $activePreview"
                        hasKeys -> "활성 키를 선택해주세요."
                        else -> "키를 추가해야 분석 생성이 가능합니다."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onShowKeyDialog) {
                Text("키 관리")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcePromptSection(
    sourcePromptState: TextFieldState,
    uiState: AnalysisUiState,
    onSourcePromptChange: (String) -> Unit,
    onCategorySelected: (AnalysisCategory) -> Unit,
    onApplyManualSelection: () -> Unit,
    onClearTargetSegment: () -> Unit,
    onAnalyzeAndMask: () -> Unit,
    onGenerateTxt: () -> Unit,
    onCancelWork: () -> Unit,
    onClearFocus: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "원문 입력",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        AppMultilineTextField(
            state = sourcePromptState,
            onValueChange = onSourcePromptChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            placeholder = "분석과 변주의 대상이 되는 전체 이미지 프롬프트를 입력하세요."
        )

        Text(
            text = "카테고리 선택",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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

        TargetSegmentPanel(
            targetSegment = uiState.targetSegment,
            onClearTargetSegment = onClearTargetSegment
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onApplyManualSelection()
                    onClearFocus()
                },
                enabled = !uiState.isBusy
            ) {
                Text("선택 구간 지정")
            }
            OutlinedButton(
                onClick = if (uiState.status == AnalysisStatus.ANALYZING) {
                    onCancelWork
                } else {
                    onAnalyzeAndMask
                },
                enabled = uiState.canAnalyze || uiState.status == AnalysisStatus.ANALYZING
            ) {
                Text(if (uiState.status == AnalysisStatus.ANALYZING) "중지" else "자동 분석")
            }
            Button(
                onClick = if (uiState.status == AnalysisStatus.GENERATING) {
                    onCancelWork
                } else {
                    onGenerateTxt
                },
                enabled = uiState.canGenerate || uiState.status == AnalysisStatus.GENERATING
            ) {
                Text(if (uiState.status == AnalysisStatus.GENERATING) "중지" else "TXT 생성")
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TargetSegmentPanel(
    targetSegment: AnalysisTargetSegment?,
    onClearTargetSegment: () -> Unit
) {
    if (targetSegment == null) {
        Text(
            text = "기본은 자동 마스킹입니다. 직접 고르려면 원문에서 구간을 선택한 뒤 선택 구간 지정을 누르세요.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

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

@OptIn(ExperimentalLayoutApi::class)
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            directions.forEach { direction ->
                DirectionChip(
                    direction = direction,
                    selected = direction.id in selectedIds,
                    onClick = { onToggleDirection(direction.id) }
                )
            }
        }
    }
}

@Composable
private fun DirectionChip(
    direction: AnalysisDirection,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
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
    onResultFileNameChange: (String) -> Unit,
    onCopyResults: () -> Unit,
    onSaveResults: () -> Unit
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
                .heightIn(min = 160.dp),
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
        FlowRowCompat(
            canCopyOrSave = uiState.canCopyOrSave,
            onCopyResults = onCopyResults,
            onSaveResults = onSaveResults
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCompat(
    canCopyOrSave: Boolean,
    onCopyResults: () -> Unit,
    onSaveResults: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onCopyResults,
            enabled = canCopyOrSave
        ) {
            Text("목록 복사")
        }
        Button(
            onClick = onSaveResults,
            enabled = canCopyOrSave
        ) {
            Text("와일드카드 파일 저장")
        }
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
    onActivate: (String) -> Unit
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
                            onDelete = onDelete
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
    onDelete: (String) -> Unit
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onActivate(key.id) },
                    enabled = !key.isActive
                ) {
                    Text(if (key.isActive) "활성" else "활성화")
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
