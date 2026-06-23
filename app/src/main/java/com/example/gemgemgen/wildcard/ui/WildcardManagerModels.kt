package com.example.gemgemgen.wildcard.ui

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
    val renameFileName: String = ""
) {
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

    val canCreateFile: Boolean
        get() = canModifyFiles && !isFileOperationInProgress

    val canSave: Boolean
        get() = canModifyFiles && selectedFile != null && !isFileOperationInProgress

    val canDelete: Boolean
        get() = canModifyFiles && selectedFile != null && !isFileOperationInProgress

    val canPaste: Boolean
        get() = canModifyFiles && selectedFile != null && !isFileOperationInProgress

    val canCopy: Boolean
        get() = selectedFile != null && !isFileOperationInProgress

    val canEditText: Boolean
        get() = canModifyFiles && selectedFile != null && !isFileOperationInProgress

    val canUndo: Boolean
        get() = canModifyFiles && undoStack.isNotEmpty() && !isFileOperationInProgress
}

sealed interface WildcardPendingAction {
    data class OpenFile(val file: WildcardTextFile) : WildcardPendingAction
    data object CreateFile : WildcardPendingAction
    data object SelectFolder : WildcardPendingAction
}

