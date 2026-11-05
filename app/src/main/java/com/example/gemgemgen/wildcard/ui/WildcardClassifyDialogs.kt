// 역할: 와일드카드 AI 분류 기능의 진행 중 로딩, 기준 설정, 결과 미리보기 및 파일 덮어쓰기 확인 다이얼로그 컨텐츠를 제공합니다.
package com.example.gemgemgen.wildcard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.ui.components.ModelSelectorChips
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuConfigDialogContent
import com.example.gemgemgen.ui.theme.appTextFieldColors

@Composable
internal fun ClassifyLoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "분류 중",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textPrimary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = AppTheme.colors.primary
            )
            Text(
                text = "분석 설정(Gemini/Grok)으로 분류하고 있습니다…",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary
            )
        }
    }
}

@Composable
internal fun ClassifyCriteriaContent(
    dialog: WildcardDialogType.ClassifyCriteria,
    actions: WildcardClassifyActions
) {
    val criteriaState = rememberSyncedTextFieldState(
        value = dialog.criteria,
        onValueChange = actions::onClassifyCriteriaChange
    )

    NeuConfigDialogContent(
        title = "와일드카드 분류",
        confirmLabel = "분류 실행",
        confirmEnabled = dialog.canRun,
        useNeuButtons = true,
        onConfirm = actions::runClassify,
        onDismiss = actions::dismissClassifyCriteriaDialog
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "지금 연 파일의 모든 줄을 기준에 따라 나눕니다. 모델 기본값은 분석 탭「TXT 생성」과 같고, 여기서 바꾸면 함께 저장됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary
            )
            Text(
                text = "모델",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            ModelSelectorChips(
                selectedProvider = dialog.provider,
                selectedModelId = dialog.modelId,
                onSelectProvider = actions::onClassifyProviderSelected,
                onSelectModel = actions::onClassifyModelSelected,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            )

            OutlinedTextField(
                state = criteriaState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3),
                label = { Text("분류 기준 (필수)") },
                placeholder = {
                    Text("예: 머리색상별(블론드, 흑발, 은발 등)로 나누어줘. 영문 파일명 권장")
                }
            )

            if (dialog.error.isNotBlank()) {
                Text(
                    text = dialog.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun ClassifyPreviewContent(
    dialog: WildcardDialogType.ClassifyPreview,
    actions: WildcardClassifyActions
) {
    val criteriaState = rememberSyncedTextFieldState(
        value = dialog.criteria,
        onValueChange = actions::onClassifyCriteriaChange
    )

    NeuConfigDialogContent(
        title = "분류 미리보기",
        confirmLabel = "파일로 저장",
        dismissLabel = "닫기",
        confirmEnabled = dialog.canSave,
        useNeuButtons = true,
        onConfirm = { actions.saveClassifyResult() },
        onDismiss = actions::dismissClassifyPreview
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "전체 목록을 확인한 뒤, 기준을 고쳐 다시 분류하거나 저장하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary
            )
            OutlinedTextField(
                state = criteriaState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
                label = { Text("분류 기준 (다시 분류용)") },
                placeholder = { Text("기준을 수정한 뒤 다시 분류") }
            )
            NeuButton(
                onClick = actions::runClassify,
                enabled = dialog.canRerun,
                isPrimary = false,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                contentModifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "다시 분류 (전체)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            if (dialog.error.isNotBlank()) {
                Text(
                    text = dialog.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val dropNote = if (dialog.result.droppedLineCount > 0) {
                " · 미배정 ${dialog.result.droppedLineCount}줄(저장 안 함)"
            } else {
                ""
            }
            Text(
                text = "원본 ${dialog.result.sourceLines.size}줄 → ${dialog.saveEntries.size}개 파일 예정$dropNote",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            dialog.saveEntries.forEachIndexed { index, entry ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${entry.groupName} (${entry.items.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AppTheme.colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (entry.isEditingFileName) {
                            val entryFileNameState = rememberSyncedTextFieldState(
                                value = entry.fileNameInput,
                                onValueChange = { actions.onClassifyFileNameChange(index, it) },
                                resetKey = index
                            )
                            OutlinedTextField(
                                state = entryFileNameState,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = appTextFieldColors(),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                onKeyboardAction = { actions.onToggleClassifyFileNameEdit(index) },
                                label = { Text("파일명") },
                                trailingIcon = {
                                    Text(
                                        text = ".txt",
                                        color = AppTheme.colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            )
                            IconButton(
                                onClick = { actions.onToggleClassifyFileNameEdit(index) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "파일명 확정",
                                    tint = AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "${entry.fileNameInput.trim().removeSuffix(".txt")}.txt",
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = AppTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { actions.onToggleClassifyFileNameEdit(index) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "파일명 수정",
                                    tint = AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    entry.items.forEach { line ->
                        Text(
                            text = "· $line",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
            }
            if (dialog.result.droppedLines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "미배정 (${dialog.result.droppedLines.size}) · 저장 안 함",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    dialog.result.droppedLines.forEach { line ->
                        Text(
                            text = "· $line",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ClassifyOverwriteContent(
    dialog: WildcardDialogType.ClassifyOverwrite,
    actions: WildcardClassifyActions
) {
    NeuConfigDialogContent(
        title = "파일 덮어쓰기",
        subtitle = "같은 이름의 파일이 있습니다. 덮어쓸까요?",
        confirmLabel = "덮어쓰기",
        useNeuButtons = true,
        onConfirm = actions::confirmClassifyOverwrite,
        onDismiss = actions::dismissClassifyOverwrite
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            dialog.fileNames.forEach { name ->
                Text("· $name", fontSize = 13.sp, color = AppTheme.colors.textSecondary)
            }
        }
    }
}
