// 역할: 와일드카드 화면의 활성 다이얼로그 상태를 판별하고 파일 관리 및 AI 분류 다이얼로그를 통합 표시하는 호스트 컴포넌트입니다.
package com.example.gemgemgen.wildcard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.core.credential.domain.AnalysisProvider
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppConfirmDialogContent
import com.example.gemgemgen.ui.theme.AppDialogHostShell
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuConfigDialogContent
import com.example.gemgemgen.ui.theme.appTextFieldColors
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResult
import com.example.gemgemgen.wildcard.domain.WildcardClassifySaveEntry

sealed interface WildcardDialogType {
    data object None : WildcardDialogType
    data class NewFile(val fileName: String, val error: String) : WildcardDialogType
    data class DeriveFile(val fileName: String, val selectedCount: Int, val error: String) : WildcardDialogType
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
    val classify = uiState.classify
    val classifyPreview = classify.classifyPreview
    return when {
        classify.classifyOverwriteConflicts.isNotEmpty() ->
            WildcardDialogType.ClassifyOverwrite(classify.classifyOverwriteConflicts)
        classify.isClassifying ->
            WildcardDialogType.ClassifyLoading
        classifyPreview != null ->
            WildcardDialogType.ClassifyPreview(
                result = classifyPreview,
                criteria = classify.classifyCriteria,
                saveEntries = classify.classifySaveEntries,
                canSave = uiState.canSaveClassifyResult,
                canRerun = uiState.canRerunClassifyFromPreview,
                error = uiState.error
            )
        classify.showClassifyCriteriaDialog ->
            WildcardDialogType.ClassifyCriteria(
                criteria = classify.classifyCriteria,
                provider = classify.classifyProvider,
                modelId = classify.classifyModelId,
                error = uiState.error,
                canRun = uiState.canRunClassify
            )
        uiState.pendingAction != null ->
            WildcardDialogType.UnsavedChanges
        uiState.showDeleteConfirm ->
            WildcardDialogType.DeleteConfirm(uiState.editor.selectedFile?.fileName.orEmpty())
        uiState.showRenameDialog ->
            WildcardDialogType.RenameFile(uiState.renameFileName, uiState.error)
        uiState.showNewFileDialog ->
            WildcardDialogType.NewFile(uiState.newFileName, uiState.error)
        uiState.showDeriveFileDialog ->
            WildcardDialogType.DeriveFile(uiState.deriveFileName, uiState.selectedLineIndices.size, uiState.error)
        else ->
            WildcardDialogType.None
    }
}

@Composable
internal fun WildcardDialogHost(
    activeDialog: WildcardDialogType,
    actions: WildcardScreenActions
) {
    if (activeDialog is WildcardDialogType.None) return

    val onDismissRequest: () -> Unit = {
        when (activeDialog) {
            WildcardDialogType.None -> Unit
            is WildcardDialogType.NewFile -> actions.dismissNewFileDialog()
            is WildcardDialogType.DeriveFile -> actions.dismissDeriveFileDialog()
            is WildcardDialogType.RenameFile -> actions.dismissRenameDialog()
            is WildcardDialogType.DeleteConfirm -> actions.dismissDeleteConfirm()
            WildcardDialogType.UnsavedChanges -> actions.cancelPendingAction()
            WildcardDialogType.ClassifyLoading -> Unit // 로딩 중 dismiss 방지
            is WildcardDialogType.ClassifyCriteria -> actions.classify.dismissClassifyCriteriaDialog()
            is WildcardDialogType.ClassifyPreview -> actions.classify.dismissClassifyPreview()
            is WildcardDialogType.ClassifyOverwrite -> actions.classify.dismissClassifyOverwrite()
        }
    }

    AppDialogHostShell(
        activeDialog = activeDialog,
        onDismissRequest = onDismissRequest,
        isDismissible = activeDialog !is WildcardDialogType.ClassifyLoading,
        label = "WildcardDialogHostCrossfade"
    ) { targetDialog ->
        when (targetDialog) {
            WildcardDialogType.None -> Unit
            is WildcardDialogType.NewFile -> FileNameInputDialogContent(
                title = "새 txt 파일",
                fileName = targetDialog.fileName,
                placeholder = "예: hair",
                error = targetDialog.error,
                confirmLabel = "생성",
                onFileNameChange = actions::onNewFileNameChange,
                onConfirm = actions::createNewFile,
                onDismiss = actions::dismissNewFileDialog
            )
            is WildcardDialogType.DeriveFile -> FileNameInputDialogContent(
                title = "선택 항목으로 새 파일 생성",
                description = "선택된 ${targetDialog.selectedCount}개 항목으로 새 와일드카드 파일을 만듭니다.",
                fileName = targetDialog.fileName,
                placeholder = "예: hair_short",
                error = targetDialog.error,
                confirmLabel = "생성",
                onFileNameChange = actions::onDeriveFileNameChange,
                onConfirm = actions::confirmDeriveFile,
                onDismiss = actions::dismissDeriveFileDialog
            )
            is WildcardDialogType.RenameFile -> FileNameInputDialogContent(
                title = "파일 이름 수정",
                fileName = targetDialog.fileName,
                placeholder = "예: new_hair",
                error = targetDialog.error,
                confirmLabel = "변경",
                onFileNameChange = actions::onRenameFileNameChange,
                onConfirm = actions::renameSelectedFile,
                onDismiss = actions::dismissRenameDialog
            )
            is WildcardDialogType.DeleteConfirm -> AppConfirmDialogContent(
                title = "파일 삭제",
                message = "${targetDialog.fileName} 파일을 삭제할까요?",
                confirmLabel = "삭제",
                onConfirm = actions::confirmDeleteSelectedFile,
                onDismiss = actions::dismissDeleteConfirm
            )
            WildcardDialogType.UnsavedChanges -> UnsavedChangesContent(actions)
            WildcardDialogType.ClassifyLoading -> ClassifyLoadingContent()
            is WildcardDialogType.ClassifyCriteria -> ClassifyCriteriaContent(targetDialog, actions.classify)
            is WildcardDialogType.ClassifyPreview -> ClassifyPreviewContent(targetDialog, actions.classify)
            is WildcardDialogType.ClassifyOverwrite -> ClassifyOverwriteContent(targetDialog, actions.classify)
        }
    }
}

@Composable
private fun FileNameInputDialogContent(
    title: String,
    fileName: String,
    placeholder: String,
    error: String,
    confirmLabel: String,
    description: String? = null,
    onFileNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val fileNameState = rememberSyncedTextFieldState(
        value = fileName,
        onValueChange = onFileNameChange,
        resetKey = title
    )
    val canConfirm = fileNameState.text.isNotBlank()
    NeuConfigDialogContent(
        title = title,
        subtitle = description,
        confirmLabel = confirmLabel,
        confirmEnabled = canConfirm,
        useNeuButtons = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state = fileNameState,
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = {
                    if (canConfirm) {
                        onConfirm()
                    }
                },
                label = { Text("파일명") },
                placeholder = { Text(placeholder) }
            )
            if (error.isNotBlank()) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun UnsavedChangesContent(
    actions: WildcardScreenActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "저장하지 않은 변경사항",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = "현재 파일의 변경사항을 어떻게 처리할까요?",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                lineHeight = 16.sp
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeuButton(
                    onClick = actions::onConfirmPendingDiscard,
                    isPrimary = false,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    contentModifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "변경 취소",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                NeuButton(
                    onClick = actions::onConfirmPendingSave,
                    isPrimary = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    contentModifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "저장",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            NeuButton(
                onClick = actions::cancelPendingAction,
                isPrimary = false,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                contentModifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "계속 편집",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

