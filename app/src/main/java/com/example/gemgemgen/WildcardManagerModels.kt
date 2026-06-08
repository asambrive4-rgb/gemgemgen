package com.example.gemgemgen

data class WildcardTextFile(
    val id: String,
    val fileName: String,
    val documentUri: String
)

data class WildcardFileUiItem(
    val file: WildcardTextFile,
    val displayName: String,
    val isSelected: Boolean
)

data class WildcardManagerUiState(
    val files: List<WildcardTextFile> = emptyList(),
    val selectedFile: WildcardTextFile? = null,
    val savedText: String = "",
    val editingText: String = "",
    val canModifyFiles: Boolean = false,
    val message: String = "",
    val error: String = "",
    val undoStack: List<String> = emptyList(),
    val pendingAction: WildcardPendingAction? = null,
    val showNewFileDialog: Boolean = false,
    val newFileName: String = "",
    val showDeleteConfirm: Boolean = false,
    val showRenameDialog: Boolean = false,
    val renameFileName: String = ""
) {
    val hasUnsavedChanges: Boolean
        get() = selectedFile != null && savedText != editingText

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
        get() = canModifyFiles

    val canSave: Boolean
        get() = canModifyFiles && selectedFile != null

    val canDelete: Boolean
        get() = canModifyFiles && selectedFile != null

    val canPaste: Boolean
        get() = canModifyFiles && selectedFile != null

    val canCopy: Boolean
        get() = selectedFile != null

    val canEditText: Boolean
        get() = canModifyFiles && selectedFile != null

    val canUndo: Boolean
        get() = canModifyFiles && undoStack.isNotEmpty()
}

sealed interface WildcardPendingAction {
    data class OpenFile(val file: WildcardTextFile) : WildcardPendingAction
    data object CreateFile : WildcardPendingAction
    data object SelectFolder : WildcardPendingAction
}

object WildcardFileName {
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        return if (trimmed.endsWith(".txt", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed.txt"
        }
    }
}

class WildcardFileException(message: String) : RuntimeException(message)
