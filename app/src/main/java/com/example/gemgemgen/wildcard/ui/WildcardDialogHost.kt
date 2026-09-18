// 역할: 와일드카드 화면의 생성, 이름 변경, 삭제, 분류 다이얼로그 팝업을 총괄 표시합니다.
package com.example.gemgemgen.wildcard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.ui.ModelSelectorChips
import com.example.gemgemgen.ui.theme.AppTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.ui.theme.appTextFieldColors
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResult
import com.example.gemgemgen.wildcard.domain.WildcardClassifySaveEntry

sealed interface WildcardDialogType {
    data object None : WildcardDialogType
    data class NewFile(val fileName: String, val error: String) : WildcardDialogType
    data class RenameFile(val fileName: String, val error: String) : WildcardDialogType
    data class DeleteConfirm(val fileName: String) : WildcardDialogType
    data object UnsavedChanges : WildcardDialogType
    data object ClassifyLoading : WildcardDialogType
    data class ClassifyCriteria(
        val criteria: String,
        val provider: AnalysisProvider,
        val modelId: String,
        val error: String,
        val canRun: Boolean
    ) : WildcardDialogType
    data class ClassifyPreview(
        val result: WildcardClassifyResult,
        val criteria: String,
        val saveEntries: List<WildcardClassifySaveEntry>,
        val canSave: Boolean,
        val canRerun: Boolean,
        val error: String
    ) : WildcardDialogType
    data class ClassifyOverwrite(val fileNames: List<String>) : WildcardDialogType
}

fun deriveActiveWildcardDialog(uiState: WildcardUiState): WildcardDialogType {
    val classifyPreview = uiState.classifyPreview
    return when {
        uiState.classifyOverwriteConflicts.isNotEmpty() ->
            WildcardDialogType.ClassifyOverwrite(uiState.classifyOverwriteConflicts)
        uiState.isClassifying ->
            WildcardDialogType.ClassifyLoading
        classifyPreview != null ->
            WildcardDialogType.ClassifyPreview(
                result = classifyPreview,
                criteria = uiState.classifyCriteria,
                saveEntries = uiState.classifySaveEntries,
                canSave = uiState.canSaveClassifyResult,
                canRerun = uiState.canRerunClassifyFromPreview,
                error = uiState.error
            )
        uiState.showClassifyCriteriaDialog ->
            WildcardDialogType.ClassifyCriteria(
                criteria = uiState.classifyCriteria,
                provider = uiState.classifyProvider,
                modelId = uiState.classifyModelId,
                error = uiState.error,
                canRun = uiState.canRunClassify
            )
        uiState.pendingAction != null ->
            WildcardDialogType.UnsavedChanges
        uiState.showDeleteConfirm ->
            WildcardDialogType.DeleteConfirm(uiState.selectedFile?.fileName.orEmpty())
        uiState.showRenameDialog ->
            WildcardDialogType.RenameFile(uiState.renameFileName, uiState.error)
        uiState.showNewFileDialog ->
            WildcardDialogType.NewFile(uiState.newFileName, uiState.error)
        else ->
            WildcardDialogType.None
    }
}

internal data class WildcardDialogActions(
    val onNewFileNameChange: (String) -> Unit,
    val onCreateNewFile: () -> Unit,
    val onDismissNewFile: () -> Unit,
    val onRenameFileNameChange: (String) -> Unit,
    val onConfirmRename: () -> Unit,
    val onDismissRename: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissDelete: () -> Unit,
    val onConfirmPendingSave: () -> Unit,
    val onConfirmPendingDiscard: () -> Unit,
    val onCancelPending: () -> Unit,
    val onClassifyCriteriaChange: (String) -> Unit,
    val onClassifyProviderSelected: (AnalysisProvider) -> Unit,
    val onClassifyModelSelected: (String) -> Unit,
    val onRunClassify: () -> Unit,
    val onDismissClassifyCriteria: () -> Unit,
    val onClassifyFileNameChange: (Int, String) -> Unit,
    val onToggleClassifyFileNameEdit: (Int) -> Unit,
    val onSaveClassifyResult: () -> Unit,
    val onDismissClassifyPreview: () -> Unit,
    val onConfirmClassifyOverwrite: () -> Unit,
    val onDismissClassifyOverwrite: () -> Unit
)

enum class WildcardDialogStage {
    NEW_FILE,
    RENAME_FILE,
    DELETE_CONFIRM,
    UNSAVED_CHANGES,
    CLASSIFY_LOADING,
    CLASSIFY_CRITERIA,
    CLASSIFY_PREVIEW,
    CLASSIFY_OVERWRITE
}

fun deriveWildcardDialogStage(dialog: WildcardDialogType): WildcardDialogStage? {
    return when (dialog) {
        WildcardDialogType.None -> null
        is WildcardDialogType.NewFile -> WildcardDialogStage.NEW_FILE
        is WildcardDialogType.RenameFile -> WildcardDialogStage.RENAME_FILE
        is WildcardDialogType.DeleteConfirm -> WildcardDialogStage.DELETE_CONFIRM
        WildcardDialogType.UnsavedChanges -> WildcardDialogStage.UNSAVED_CHANGES
        WildcardDialogType.ClassifyLoading -> WildcardDialogStage.CLASSIFY_LOADING
        is WildcardDialogType.ClassifyCriteria -> WildcardDialogStage.CLASSIFY_CRITERIA
        is WildcardDialogType.ClassifyPreview -> WildcardDialogStage.CLASSIFY_PREVIEW
        is WildcardDialogType.ClassifyOverwrite -> WildcardDialogStage.CLASSIFY_OVERWRITE
    }
}

@Composable
internal fun WildcardDialogHost(
    activeDialog: WildcardDialogType,
    actions: WildcardDialogActions
) {
    val activeStage = deriveWildcardDialogStage(activeDialog) ?: return

    val onDismissRequest: () -> Unit = {
        when (activeDialog) {
            WildcardDialogType.None -> Unit
            is WildcardDialogType.NewFile -> actions.onDismissNewFile()
            is WildcardDialogType.RenameFile -> actions.onDismissRename()
            is WildcardDialogType.DeleteConfirm -> actions.onDismissDelete()
            WildcardDialogType.UnsavedChanges -> actions.onCancelPending()
            WildcardDialogType.ClassifyLoading -> Unit // 로딩 중 dismiss 방지
            is WildcardDialogType.ClassifyCriteria -> actions.onDismissClassifyCriteria()
            is WildcardDialogType.ClassifyPreview -> actions.onDismissClassifyPreview()
            is WildcardDialogType.ClassifyOverwrite -> actions.onDismissClassifyOverwrite()
        }
    }

    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = activeStage != WildcardDialogStage.CLASSIFY_LOADING,
            dismissOnClickOutside = activeStage != WildcardDialogStage.CLASSIFY_LOADING,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .imePadding()
                .widthIn(min = 280.dp, max = 560.dp)
                .fillMaxWidth()
                .wrapContentHeight()
                .clearFocusOnOutsideTap { focusManager.clearFocus(force = true) },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            AnimatedContent(
                targetState = activeStage,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                },
                contentAlignment = Alignment.Center,
                label = "WildcardDialogHostCrossfade"
            ) { stage ->
                when (stage) {
                    WildcardDialogStage.NEW_FILE -> (activeDialog as? WildcardDialogType.NewFile)?.let { NewFileContent(it, actions) }
                    WildcardDialogStage.RENAME_FILE -> (activeDialog as? WildcardDialogType.RenameFile)?.let { RenameFileContent(it, actions) }
                    WildcardDialogStage.DELETE_CONFIRM -> (activeDialog as? WildcardDialogType.DeleteConfirm)?.let { DeleteConfirmContent(it, actions) }
                    WildcardDialogStage.UNSAVED_CHANGES -> UnsavedChangesContent(actions)
                    WildcardDialogStage.CLASSIFY_LOADING -> ClassifyLoadingContent()
                    WildcardDialogStage.CLASSIFY_CRITERIA -> (activeDialog as? WildcardDialogType.ClassifyCriteria)?.let { ClassifyCriteriaContent(it, actions) }
                    WildcardDialogStage.CLASSIFY_PREVIEW -> (activeDialog as? WildcardDialogType.ClassifyPreview)?.let { ClassifyPreviewContent(it, actions) }
                    WildcardDialogStage.CLASSIFY_OVERWRITE -> (activeDialog as? WildcardDialogType.ClassifyOverwrite)?.let { ClassifyOverwriteContent(it, actions) }
                }
            }
        }
    }
}

@Composable
private fun NewFileContent(
    dialog: WildcardDialogType.NewFile,
    actions: WildcardDialogActions
) {
    val canCreate = dialog.fileName.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "새 txt 파일",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = dialog.fileName,
                onValueChange = actions.onNewFileNameChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canCreate) {
                            actions.onCreateNewFile()
                        }
                    }
                ),
                label = { Text("파일명") },
                placeholder = { Text("예: hair") }
            )
            if (dialog.error.isNotBlank()) {
                Text(
                    text = dialog.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions.onDismissNewFile) {
                Text("취소")
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = actions.onCreateNewFile,
                enabled = canCreate
            ) {
                Text("생성")
            }
        }
    }
}

@Composable
private fun RenameFileContent(
    dialog: WildcardDialogType.RenameFile,
    actions: WildcardDialogActions
) {
    val canRename = dialog.fileName.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "파일 이름 수정",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = dialog.fileName,
                onValueChange = actions.onRenameFileNameChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canRename) {
                            actions.onConfirmRename()
                        }
                    }
                ),
                label = { Text("파일명") },
                placeholder = { Text("예: new_hair") }
            )
            if (dialog.error.isNotBlank()) {
                Text(
                    text = dialog.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions.onDismissRename) {
                Text("취소")
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = actions.onConfirmRename,
                enabled = canRename
            ) {
                Text("변경")
            }
        }
    }
}

@Composable
private fun DeleteConfirmContent(
    dialog: WildcardDialogType.DeleteConfirm,
    actions: WildcardDialogActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "파일 삭제",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${dialog.fileName} 파일을 삭제할까요?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions.onDismissDelete) {
                Text("취소")
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = actions.onConfirmDelete) {
                Text("삭제")
            }
        }
    }
}

@Composable
private fun UnsavedChangesContent(
    actions: WildcardDialogActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "저장하지 않은 변경사항",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "현재 파일의 변경사항을 어떻게 처리할까요?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = actions.onConfirmPendingSave) {
                Text("Save")
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = actions.onConfirmPendingDiscard) {
                Text("Discard")
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = actions.onCancelPending) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ClassifyLoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "분류 중",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Text(
                text = "분석 설정(Gemini/Grok)으로 분류하고 있습니다…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ClassifyCriteriaContent(
    dialog: WildcardDialogType.ClassifyCriteria,
    actions: WildcardDialogActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "와일드카드 분류",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
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
                onSelectProvider = actions.onClassifyProviderSelected,
                onSelectModel = actions.onClassifyModelSelected,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            )

            OutlinedTextField(
                value = dialog.criteria,
                onValueChange = actions.onClassifyCriteriaChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                minLines = 3,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions.onDismissClassifyCriteria) {
                Text("취소")
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = actions.onRunClassify,
                enabled = dialog.canRun
            ) {
                Text("분류 실행")
            }
        }
    }
}

@Composable
private fun ClassifyPreviewContent(
    dialog: WildcardDialogType.ClassifyPreview,
    actions: WildcardDialogActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "분류 미리보기",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

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
                value = dialog.criteria,
                onValueChange = actions.onClassifyCriteriaChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                minLines = 2,
                label = { Text("분류 기준 (다시 분류용)") },
                placeholder = { Text("기준을 수정한 뒤 다시 분류") }
            )
            OutlinedButton(
                onClick = actions.onRunClassify,
                enabled = dialog.canRerun,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
            ) {
                Text("다시 분류 (전체)", fontWeight = FontWeight.Bold)
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
                            OutlinedTextField(
                                value = entry.fileNameInput,
                                onValueChange = { actions.onClassifyFileNameChange(index, it) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = appTextFieldColors(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { actions.onToggleClassifyFileNameEdit(index) }
                                ),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions.onDismissClassifyPreview) {
                Text("닫기")
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = actions.onSaveClassifyResult,
                enabled = dialog.canSave
            ) {
                Text("파일로 저장")
            }
        }
    }
}

@Composable
private fun ClassifyOverwriteContent(
    dialog: WildcardDialogType.ClassifyOverwrite,
    actions: WildcardDialogActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "파일 덮어쓰기",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("같은 이름의 파일이 있습니다. 덮어쓸까요?")
            dialog.fileNames.forEach { name ->
                Text("· $name", fontSize = 13.sp, color = AppTheme.colors.textSecondary)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions.onDismissClassifyOverwrite) {
                Text("취소")
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = actions.onConfirmClassifyOverwrite) {
                Text("덮어쓰기")
            }
        }
    }
}

