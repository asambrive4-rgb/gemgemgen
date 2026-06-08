package com.example.gemgemgen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class WildcardManagerViewModel(
    private val fileManager: WildcardFileManager,
    private val clipboardTextProvider: ClipboardTextProvider,
    private val clipboardTextWriter: ClipboardTextWriter
) : ViewModel() {
    private val _uiState = MutableStateFlow(WildcardManagerUiState())
    val uiState: StateFlow<WildcardManagerUiState> = _uiState.asStateFlow()

    init {
        refreshFiles(openFirstFile = true)
    }

    fun onFolderAccessChanged(canModifyFiles: Boolean) {
        if (uiState.value.canModifyFiles == canModifyFiles) return

        _uiState.update {
            it.copy(canModifyFiles = canModifyFiles)
        }
    }

    fun refreshFiles(openFirstFile: Boolean = false) {
        val state = uiState.value
        val files = loadFilesOrShowError() ?: return
        val currentFile = state.selectedFile?.let { selected ->
            files.firstOrNull { it.id == selected.id }
        }

        _uiState.update {
            it.copy(
                files = files,
                selectedFile = currentFile ?: it.selectedFile,
                error = ""
            )
        }

        when {
            currentFile != null -> Unit
            openFirstFile && files.isNotEmpty() -> openFile(files.first())
            state.selectedFile != null && files.isEmpty() -> clearSelectedFile("txt 파일이 없습니다.")
            state.selectedFile != null -> clearSelectedFile("선택했던 파일을 찾지 못했습니다.")
        }
    }

    fun onFolderChanged() {
        _uiState.update {
            it.copy(
                files = emptyList(),
                selectedFile = null,
                savedText = "",
                editingText = "",
                undoStack = emptyList(),
                pendingAction = null,
                message = "wildcard 폴더를 선택했습니다.",
                error = ""
            )
        }
        refreshFiles(openFirstFile = true)
    }

    fun requestFolderSelection(): Boolean {
        val state = uiState.value
        if (!state.hasUnsavedChanges) return true

        _uiState.update {
            it.copy(
                pendingAction = WildcardPendingAction.SelectFolder,
                message = "",
                error = ""
            )
        }
        return false
    }

    fun selectFile(file: WildcardTextFile) {
        val state = uiState.value
        if (state.selectedFile?.id == file.id) return

        if (state.hasUnsavedChanges) {
            _uiState.update {
                it.copy(
                    pendingAction = WildcardPendingAction.OpenFile(file),
                    message = "",
                    error = ""
                )
            }
            return
        }

        openFile(file)
    }

    fun onTextChange(value: String) {
        _uiState.update {
            it.copy(
                editingText = value,
                message = "",
                error = ""
            )
        }
    }

    fun saveCurrent(): Boolean {
        val state = uiState.value
        if (!state.canModifyFiles) {
            showError("파일을 편집하려면 wildcard 폴더를 다시 선택해주세요.")
            return false
        }
        val file = state.selectedFile ?: run {
            showError("저장할 파일을 선택해주세요.")
            return false
        }

        return try {
            fileManager.writeFile(file, state.editingText)
            _uiState.update {
                it.copy(
                    savedText = state.editingText,
                    message = "${file.fileName} 저장 완료",
                    error = ""
                )
            }
            true
        } catch (error: RuntimeException) {
            showError(error.message ?: "파일을 저장하지 못했습니다.")
            false
        }
    }

    fun requestNewFile() {
        val state = uiState.value
        if (!state.canModifyFiles) {
            showError("새 파일을 만들려면 wildcard 폴더를 다시 선택해주세요.")
            return
        }
        if (state.hasUnsavedChanges) {
            _uiState.update {
                it.copy(
                    pendingAction = WildcardPendingAction.CreateFile,
                    message = "",
                    error = ""
                )
            }
            return
        }

        showNewFileDialog()
    }

    fun onNewFileNameChange(value: String) {
        _uiState.update { it.copy(newFileName = value, error = "") }
    }

    fun dismissNewFileDialog() {
        _uiState.update {
            it.copy(
                showNewFileDialog = false,
                newFileName = "",
                error = ""
            )
        }
    }

    fun createNewFile() {
        val input = uiState.value.newFileName
        if (input.isBlank()) {
            showError("파일명을 입력해주세요.")
            return
        }

        try {
            val createdFile = fileManager.createFile(input)
            val files = fileManager.listFiles()
            _uiState.update {
                it.copy(
                    files = files,
                    selectedFile = createdFile,
                    savedText = "",
                    editingText = "",
                    undoStack = emptyList(),
                    showNewFileDialog = false,
                    newFileName = "",
                    message = "${createdFile.fileName} 생성 완료",
                    error = ""
                )
            }
        } catch (error: RuntimeException) {
            showError(error.message ?: "새 파일을 만들지 못했습니다.")
        }
    }

    fun requestRenameSelectedFile() {
        val state = uiState.value
        if (!state.canModifyFiles) {
            showError("파일 이름을 수정하려면 wildcard 폴더를 다시 선택해주세요.")
            return
        }
        val file = state.selectedFile ?: run {
            showError("수정할 파일을 선택해주세요.")
            return
        }

        val baseName = if (file.fileName.endsWith(".txt")) {
            file.fileName.dropLast(4)
        } else {
            file.fileName
        }

        _uiState.update {
            it.copy(
                showRenameDialog = true,
                renameFileName = baseName,
                message = "",
                error = ""
            )
        }
    }

    fun onRenameFileNameChange(value: String) {
        _uiState.update { it.copy(renameFileName = value, error = "") }
    }

    fun dismissRenameDialog() {
        _uiState.update {
            it.copy(
                showRenameDialog = false,
                renameFileName = "",
                error = ""
            )
        }
    }

    fun renameSelectedFile() {
        val state = uiState.value
        val file = state.selectedFile ?: run {
            showError("수정할 파일을 선택해주세요.")
            return
        }
        val newName = state.renameFileName
        if (newName.isBlank()) {
            showError("파일 이름을 입력해주세요.")
            return
        }

        try {
            val updatedFile = fileManager.renameFile(file, newName)
            val files = fileManager.listFiles()
            _uiState.update {
                it.copy(
                    files = files,
                    selectedFile = updatedFile,
                    showRenameDialog = false,
                    renameFileName = "",
                    message = "${updatedFile.fileName}으로 이름 수정 완료",
                    error = ""
                )
            }
        } catch (error: RuntimeException) {
            showError(error.message ?: "파일 이름을 수정하지 못했습니다.")
        }
    }

    fun requestDeleteSelectedFile() {
        val state = uiState.value
        if (!state.canModifyFiles) {
            showError("파일을 삭제하려면 wildcard 폴더를 다시 선택해주세요.")
            return
        }
        if (state.selectedFile == null) {
            showError("삭제할 파일을 선택해주세요.")
            return
        }

        _uiState.update {
            it.copy(
                showDeleteConfirm = true,
                message = "",
                error = ""
            )
        }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDeleteSelectedFile() {
        val state = uiState.value
        val file = state.selectedFile ?: run {
            showError("삭제할 파일을 선택해주세요.")
            return
        }

        try {
            fileManager.deleteFile(file)
            val files = fileManager.listFiles()
            val oldIndex = state.files.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
            val nextFile = files.getOrNull(oldIndex) ?: files.lastOrNull()

            _uiState.update {
                it.copy(
                    files = files,
                    showDeleteConfirm = false,
                    message = "${file.fileName} 삭제 완료",
                    error = ""
                )
            }

            if (nextFile == null) {
                clearSelectedFile("txt 파일이 없습니다.")
            } else {
                openFile(nextFile, keepMessage = true)
            }
        } catch (error: RuntimeException) {
            _uiState.update {
                it.copy(
                    showDeleteConfirm = false,
                    message = "",
                    error = error.message ?: "파일을 삭제하지 못했습니다."
                )
            }
        }
    }

    fun pasteFromClipboard() {
        if (!ensureCanModifyFiles()) return
        val text = clipboardTextProvider.readText()
        if (text.isEmpty()) {
            showError("클립보드가 비어 있습니다.")
            return
        }
        if (!ensureFileSelected()) return

        replaceEditingText(text)
    }

    fun pasteBelowFromClipboard() {
        if (!ensureCanModifyFiles()) return
        val text = clipboardTextProvider.readText()
        if (text.isEmpty()) {
            showError("클립보드가 비어 있습니다.")
            return
        }
        if (!ensureFileSelected()) return

        val state = uiState.value
        val baseText = when {
            state.editingText.isEmpty() -> ""
            state.editingText.endsWith("\n") -> state.editingText
            else -> "${state.editingText}\n"
        }
        replaceEditingText(baseText + text)
    }

    fun copyToClipboard() {
        val text = uiState.value.editingText
        if (text.isEmpty()) {
            showError("복사할 내용이 없습니다.")
            return
        }

        clipboardTextWriter.writeText(text)
        _uiState.update {
            it.copy(
                message = "클립보드에 복사했습니다.",
                error = ""
            )
        }
    }

    fun undoClipboardEdit() {
        val state = uiState.value
        val previous = state.undoStack.firstOrNull() ?: run {
            showError("되돌릴 붙여넣기 기록이 없습니다.")
            return
        }

        _uiState.update {
            it.copy(
                editingText = previous,
                undoStack = state.undoStack.drop(1),
                message = "붙여넣기 전 상태로 되돌렸습니다.",
                error = ""
            )
        }
    }

    fun confirmPendingWithSave(): Boolean {
        val action = uiState.value.pendingAction ?: return false
        if (!saveCurrent()) return false
        clearPendingAction()
        return runPendingAction(action)
    }

    fun confirmPendingWithDiscard(): Boolean {
        val action = uiState.value.pendingAction ?: return false
        clearPendingAction()
        return runPendingAction(action)
    }

    fun cancelPendingAction() {
        clearPendingAction()
    }

    private fun openFile(file: WildcardTextFile, keepMessage: Boolean = false) {
        try {
            val text = fileManager.readFile(file)
            _uiState.update {
                it.copy(
                    selectedFile = file,
                    savedText = text,
                    editingText = text,
                    undoStack = emptyList(),
                    message = if (keepMessage) it.message else "${file.fileName} 열기 완료",
                    error = ""
                )
            }
        } catch (error: RuntimeException) {
            showError(error.message ?: "파일을 열지 못했습니다.")
        }
    }

    private fun loadFilesOrShowError(): List<WildcardTextFile>? {
        return try {
            fileManager.listFiles()
        } catch (error: RuntimeException) {
            _uiState.update {
                it.copy(
                    files = emptyList(),
                    selectedFile = null,
                    savedText = "",
                    editingText = "",
                    undoStack = emptyList(),
                    error = error.message ?: "파일 목록을 불러오지 못했습니다."
                )
            }
            null
        }
    }

    private fun runPendingAction(action: WildcardPendingAction): Boolean {
        return when (action) {
            is WildcardPendingAction.OpenFile -> {
                openFile(action.file)
                false
            }
            WildcardPendingAction.CreateFile -> {
                showNewFileDialog()
                false
            }
            WildcardPendingAction.SelectFolder -> true
        }
    }

    private fun showNewFileDialog() {
        _uiState.update {
            it.copy(
                showNewFileDialog = true,
                newFileName = "",
                message = "",
                error = ""
            )
        }
    }

    private fun replaceEditingText(text: String) {
        val state = uiState.value
        _uiState.update {
            it.copy(
                editingText = text,
                undoStack = (listOf(state.editingText) + state.undoStack).take(MAX_UNDO_COUNT),
                message = "클립보드 내용을 반영했습니다.",
                error = ""
            )
        }
    }

    private fun clearSelectedFile(message: String) {
        _uiState.update {
            it.copy(
                selectedFile = null,
                savedText = "",
                editingText = "",
                undoStack = emptyList(),
                message = message,
                error = ""
            )
        }
    }

    private fun ensureFileSelected(): Boolean {
        if (uiState.value.selectedFile != null) return true
        showError("먼저 txt 파일을 선택하거나 새로 만들어주세요.")
        return false
    }

    private fun ensureCanModifyFiles(): Boolean {
        if (uiState.value.canModifyFiles) return true
        showError("파일을 편집하려면 wildcard 폴더를 다시 선택해주세요.")
        return false
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                message = "",
                error = message
            )
        }
    }

    private fun clearPendingAction() {
        _uiState.update {
            it.copy(
                pendingAction = null,
                message = "",
                error = ""
            )
        }
    }

    private companion object {
        const val MAX_UNDO_COUNT = 5
    }
}

class WildcardManagerViewModelFactory(
    private val fileManager: WildcardFileManager,
    private val clipboardTextProvider: ClipboardTextProvider,
    private val clipboardTextWriter: ClipboardTextWriter
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(WildcardManagerViewModel::class.java)) {
            error("Unknown ViewModel class: ${modelClass.name}")
        }

        return WildcardManagerViewModel(
            fileManager = fileManager,
            clipboardTextProvider = clipboardTextProvider,
            clipboardTextWriter = clipboardTextWriter
        ) as T
    }
}
