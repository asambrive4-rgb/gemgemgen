// 역할: 와일드카드 관리 화면의 파일 목록 아이템 및 UI 상태 모델을 정의합니다.
package com.example.gemgemgen.wildcard.ui

import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.MODEL_GROK_4_5
import com.example.gemgemgen.wildcard.domain.WildcardClassifyFileName
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResult
import com.example.gemgemgen.wildcard.domain.WildcardClassifySaveEntry
import com.example.gemgemgen.wildcard.domain.WildcardDynamicPromptComposer
import com.example.gemgemgen.wildcard.domain.WildcardEditorSession
import com.example.gemgemgen.wildcard.domain.WildcardTextFile

data class WildcardFileUiItem(
    val file: WildcardTextFile,
    val displayName: String,
    val isSelected: Boolean
)

data class WildcardManagerUiState(
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
    val showClassifyCriteriaDialog: Boolean get() = classify.showClassifyCriteriaDialog
    val classifyCriteria: String get() = classify.classifyCriteria
    val isClassifying: Boolean get() = classify.isClassifying
    val classifyPreview: WildcardClassifyResult? get() = classify.classifyPreview
    val classifySaveEntries: List<WildcardClassifySaveEntry> get() = classify.classifySaveEntries
    val classifyOverwriteConflicts: List<String> get() = classify.classifyOverwriteConflicts
    val classifyProvider: AnalysisProvider get() = classify.classifyProvider
    val classifyModelId: String get() = classify.classifyModelId

    val selectedFile: WildcardTextFile?
        get() = editor.selectedFile

    val savedText: String
        get() = editor.savedText

    val editingText: String
        get() = editor.editingText

    val undoStack: List<String>
        get() = editor.undoStack

    val hasUnsavedChanges: Boolean
        get() = editor.hasUnsavedChanges

    val selectableLines: List<String>
        get() = WildcardDynamicPromptComposer.selectableLines(editingText)

    val fileItems: List<WildcardFileUiItem>
        get() = files.map { file ->
            val isSelected = selectedFile?.id == file.id
            WildcardFileUiItem(
                file = file,
                displayName = if (isSelected && hasUnsavedChanges) {
                    "${file.fileName} *"
                } else {
                    file.fileName
                },
                isSelected = isSelected
            )
        }

    val selectedFileDisplayName: String
        get() = selectedFile?.let { file ->
            if (hasUnsavedChanges) "${file.fileName} *" else file.fileName
        } ?: "No file selected"

    private val classifyBusy: Boolean
        get() = classify.isBusy

    val canCreateFile: Boolean
        get() = canModifyFiles && !isFileOperationInProgress && !isLineSelectionMode && !classifyBusy

    val canSave: Boolean
        get() = canModifyFiles && selectedFile != null && !isFileOperationInProgress &&
            !isLineSelectionMode && !isClassifying

    val canDelete: Boolean
        get() = canModifyFiles && selectedFile != null && !isFileOperationInProgress &&
            !isLineSelectionMode && !classifyBusy

    val canPaste: Boolean
        get() = canModifyFiles && selectedFile != null && !isFileOperationInProgress &&
            !isLineSelectionMode && !isClassifying

    val canCopy: Boolean
        get() = selectedFile != null && !isFileOperationInProgress && !isLineSelectionMode && !isClassifying

    val canEditText: Boolean
        get() = canModifyFiles && selectedFile != null && !isFileOperationInProgress &&
            !isLineSelectionMode && !isClassifying && classifyPreview == null

    val canUndo: Boolean
        get() = canModifyFiles && undoStack.isNotEmpty() && !isFileOperationInProgress &&
            !isLineSelectionMode && !isClassifying

    val canEnterLineSelectionMode: Boolean
        get() = selectedFile != null && !isFileOperationInProgress && !isLineSelectionMode && !classifyBusy

    val canExitLineSelectionMode: Boolean
        get() = isLineSelectionMode && !isFileOperationInProgress && !isClassifying

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
            selectedFile != null &&
            selectableLines.isNotEmpty() &&
            !isFileOperationInProgress &&
            !isLineSelectionMode &&
            !classifyBusy

    /** 기준 입력 다이얼로그 또는 미리보기에서 전체 재분류 가능 */
    val canRunClassify: Boolean
        get() = classify.canRunClassify(isFileOperationInProgress)

    val canRerunClassifyFromPreview: Boolean
        get() = classifyPreview != null && canRunClassify

    val canSaveClassifyResult: Boolean
        get() = classify.canSaveClassifyResult(canModifyFiles, isFileOperationInProgress)

}

sealed interface WildcardPendingAction {
    data class OpenFile(val file: WildcardTextFile) : WildcardPendingAction
    data object CreateFile : WildcardPendingAction
    data object SelectFolder : WildcardPendingAction
}
