// 역할: 와일드카드 관리 화면 표시용 UI 상태와 다이얼로그 모델을 정의합니다.
package com.example.gemgemgen.wildcard.ui

import com.example.gemgemgen.wildcard.domain.WildcardEditorSession
import com.example.gemgemgen.wildcard.domain.WildcardTextFile

data class WildcardFileUiItem(
    val file: WildcardTextFile,
    val displayName: String,
    val isSelected: Boolean
)

data class WildcardUiState(
    val files: List<WildcardTextFile> = emptyList(),
    val editor: WildcardEditorSession = WildcardEditorSession(),
    val canModifyFiles: Boolean = false,
    val message: String = "",
    val error: String = "",
    val isFileOperationInProgress: Boolean = false,
    val pendingAction: WildcardPendingAction? = null,
    val showNewFileDialog: Boolean = false,
    val newFileName: String = "",
    val showDeleteConfirm: Boolean = false,
    val showRenameDialog: Boolean = false,
    val renameFileName: String = "",
    /** 줄 선택 모드 (다이나믹 프롬프트 구성). */
    val isLineSelectionMode: Boolean = false,
    /** [selectableLines] 인덱스 집합. 파일 순서로 조립한다. */
    val selectedLineIndices: Set<Int> = emptySet(),
    val classify: WildcardClassifyUiState = WildcardClassifyUiState()
) {
    val selectableLines: List<String>
        get() = editor.selectableLines

    val canCreateFile: Boolean
        get() = canModifyFiles && !isFileOperationInProgress && !isLineSelectionMode && !classify.isBusy

    val canSave: Boolean
        get() = canModifyFiles && editor.selectedFile != null && !isFileOperationInProgress &&
            !isLineSelectionMode && !classify.isClassifying

    val canDelete: Boolean
        get() = canModifyFiles && editor.selectedFile != null && !isFileOperationInProgress &&
            !isLineSelectionMode && !classify.isBusy

    val canPaste: Boolean
        get() = canModifyFiles && editor.selectedFile != null && !isFileOperationInProgress &&
            !isLineSelectionMode && !classify.isClassifying

    val canCopy: Boolean
        get() = editor.selectedFile != null && !isFileOperationInProgress &&
            !isLineSelectionMode && !classify.isClassifying

    val canEditText: Boolean
        get() = canModifyFiles && editor.selectedFile != null && !isFileOperationInProgress &&
            !isLineSelectionMode && !classify.isClassifying && classify.classifyPreview == null

    val canUndo: Boolean
        get() = canModifyFiles && editor.undoStack.isNotEmpty() && !isFileOperationInProgress &&
            !isLineSelectionMode && !classify.isClassifying

    val canEnterLineSelectionMode: Boolean
        get() = editor.selectedFile != null && !isFileOperationInProgress &&
            !isLineSelectionMode && !classify.isBusy

    val canExitLineSelectionMode: Boolean
        get() = isLineSelectionMode && !isFileOperationInProgress && !classify.isClassifying

    val canSelectAllLines: Boolean
        get() = isLineSelectionMode &&
            selectableLines.isNotEmpty() &&
            selectedLineIndices.size < selectableLines.size &&
            !isFileOperationInProgress

    val canDeselectAllLines: Boolean
        get() = isLineSelectionMode && selectedLineIndices.isNotEmpty() && !isFileOperationInProgress

    val canComposeDynamicPrompt: Boolean
        get() = isLineSelectionMode && selectedLineIndices.isNotEmpty() && !isFileOperationInProgress

    val canRequestClassify: Boolean
        get() = canModifyFiles &&
            editor.selectedFile != null &&
            editor.hasSelectableLines &&
            !isFileOperationInProgress &&
            !isLineSelectionMode &&
            !classify.isBusy

    /** 기준 입력 다이얼로그 또는 미리보기에서 전체 재분류 가능 */
    val canRunClassify: Boolean
        get() = classify.canRunClassify(isFileOperationInProgress)

    val canRerunClassifyFromPreview: Boolean
        get() = classify.classifyPreview != null && canRunClassify

    val canSaveClassifyResult: Boolean
        get() = classify.canSaveClassifyResult(canModifyFiles, isFileOperationInProgress)
}

sealed interface WildcardPendingAction {
    data class OpenFile(val file: WildcardTextFile) : WildcardPendingAction
    data object CreateFile : WildcardPendingAction
    data object SelectFolder : WildcardPendingAction
}
