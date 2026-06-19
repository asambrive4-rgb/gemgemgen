package com.example.gemgemgen.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.wildcard.domain.WildcardTextEditResult
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.ManageWildcardFilesUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardClipboardPasteResult
import com.example.gemgemgen.wildcard.usecase.WildcardClipboardUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WildcardManagerViewModel(
    private val manageWildcardFiles: ManageWildcardFilesUseCase,
    private val wildcardClipboard: WildcardClipboardUseCase,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
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
        if (!beginFileOperation()) return

        scope.launch {
            try {
                val state = uiState.value
                val files = listFiles()
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
                    openFirstFile && files.isNotEmpty() -> openFileInCurrentOperation(files.first())
                    state.selectedFile != null && files.isEmpty() -> clearSelectedFile("txt 파일이 없습니다.")
                    state.selectedFile != null -> clearSelectedFile("선택했던 파일을 찾지 못했습니다.")
                }
            } catch (error: RuntimeException) {
                showFileListError(error)
            } finally {
                endFileOperation()
            }
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
                isFileOperationInProgress = false,
                pendingAction = null,
                message = "wildcard 폴더를 선택했습니다.",
                error = ""
            )
        }
        refreshFiles(openFirstFile = true)
    }

    fun requestFolderSelection(): Boolean {
        val state = uiState.value
        if (state.isFileOperationInProgress) return false
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
        if (state.isFileOperationInProgress) return
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
        return saveCurrent(afterSave = null)
    }

    fun requestNewFile() {
        val state = uiState.value
        if (state.isFileOperationInProgress) return
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
        if (!beginFileOperation()) return

        scope.launch {
            try {
                val createdFile = createFile(input)
                val files = listFiles()
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
            } finally {
                endFileOperation()
            }
        }
    }

    fun requestRenameSelectedFile() {
        val state = uiState.value
        if (state.isFileOperationInProgress) return
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
        if (!beginFileOperation()) return

        scope.launch {
            try {
                val updatedFile = renameFile(file, newName)
                val files = listFiles()
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
            } finally {
                endFileOperation()
            }
        }
    }

    fun requestDeleteSelectedFile() {
        val state = uiState.value
        if (state.isFileOperationInProgress) return
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
        if (!beginFileOperation()) return

        scope.launch {
            try {
                deleteFile(file)
                val files = listFiles()
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
                    openFileInCurrentOperation(nextFile, keepMessage = true)
                }
            } catch (error: RuntimeException) {
                _uiState.update {
                    it.copy(
                        showDeleteConfirm = false,
                        message = "",
                        error = error.message ?: "파일을 삭제하지 못했습니다."
                    )
                }
            } finally {
                endFileOperation()
            }
        }
    }

    fun pasteFromClipboard() {
        if (uiState.value.isFileOperationInProgress) return
        if (!ensureCanModifyFiles()) return
        if (!ensureFileSelected()) return

        val state = uiState.value
        when (val result = wildcardClipboard.paste(
                currentText = state.editingText,
                undoStack = state.undoStack
            )
        ) {
            WildcardClipboardPasteResult.EmptyClipboard -> showError("클립보드가 비어 있습니다.")
            is WildcardClipboardPasteResult.Success -> applyTextEditResult(result.edit)
        }
    }

    fun pasteBelowFromClipboard() {
        if (uiState.value.isFileOperationInProgress) return
        if (!ensureCanModifyFiles()) return
        if (!ensureFileSelected()) return

        val state = uiState.value
        when (val result = wildcardClipboard.pasteBelow(
                currentText = state.editingText,
                undoStack = state.undoStack
            )
        ) {
            WildcardClipboardPasteResult.EmptyClipboard -> showError("클립보드가 비어 있습니다.")
            is WildcardClipboardPasteResult.Success -> applyTextEditResult(result.edit)
        }
    }

    fun copyToClipboard() {
        if (uiState.value.isFileOperationInProgress) return
        val text = uiState.value.editingText
        if (text.isEmpty()) {
            showError("복사할 내용이 없습니다.")
            return
        }

        if (!wildcardClipboard.copy(text)) {
            showError("복사할 내용이 없습니다.")
            return
        }
        _uiState.update {
            it.copy(
                message = "클립보드에 복사했습니다.",
                error = ""
            )
        }
    }

    fun undoClipboardEdit() {
        if (uiState.value.isFileOperationInProgress) return
        val state = uiState.value
        val result = wildcardClipboard.undo(state.undoStack) ?: run {
            showError("되돌릴 붙여넣기 기록이 없습니다.")
            return
        }

        _uiState.update {
            it.copy(
                editingText = result.text,
                undoStack = result.undoStack,
                message = "붙여넣기 전 상태로 되돌렸습니다.",
                error = ""
            )
        }
    }

    fun confirmPendingWithSave(onSelectFolder: () -> Unit = {}): Boolean {
        val action = uiState.value.pendingAction ?: return false
        return saveCurrent(
            afterSave = {
                clearPendingAction()
                runPendingActionInCurrentOperation(action, onSelectFolder)
            }
        )
    }

    fun confirmPendingWithDiscard(onSelectFolder: () -> Unit = {}): Boolean {
        val action = uiState.value.pendingAction ?: return false
        clearPendingAction()
        return runPendingAction(action, onSelectFolder)
    }

    fun cancelPendingAction() {
        clearPendingAction()
    }

    private fun saveCurrent(afterSave: (suspend () -> Unit)?): Boolean {
        val state = uiState.value
        if (state.isFileOperationInProgress) return false
        if (!state.canModifyFiles) {
            showError("파일을 편집하려면 wildcard 폴더를 다시 선택해주세요.")
            return false
        }
        val file = state.selectedFile ?: run {
            showError("저장할 파일을 선택해주세요.")
            return false
        }
        if (!beginFileOperation()) return false

        scope.launch {
            try {
                val textToSave = state.editingText
                writeFile(file, textToSave)
                _uiState.update {
                    it.copy(
                        savedText = textToSave,
                        message = "${file.fileName} 저장 완료",
                        error = ""
                    )
                }
                afterSave?.invoke()
            } catch (error: RuntimeException) {
                showError(error.message ?: "파일을 저장하지 못했습니다.")
            } finally {
                endFileOperation()
            }
        }
        return true
    }

    private fun openFile(file: WildcardTextFile, keepMessage: Boolean = false) {
        if (!beginFileOperation()) return

        scope.launch {
            try {
                openFileInCurrentOperation(file, keepMessage)
            } finally {
                endFileOperation()
            }
        }
    }

    private suspend fun openFileInCurrentOperation(
        file: WildcardTextFile,
        keepMessage: Boolean = false
    ) {
        try {
            val text = readFile(file)
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

    private suspend fun listFiles(): List<WildcardTextFile> {
        return withContext(dispatchers.io) {
            manageWildcardFiles.listFiles()
        }
    }

    private suspend fun readFile(file: WildcardTextFile): String {
        return withContext(dispatchers.io) {
            manageWildcardFiles.readFile(file)
        }
    }

    private suspend fun writeFile(file: WildcardTextFile, text: String) {
        withContext(dispatchers.io) {
            manageWildcardFiles.writeFile(file, text)
        }
    }

    private suspend fun createFile(fileName: String): WildcardTextFile {
        return withContext(dispatchers.io) {
            manageWildcardFiles.createFile(fileName)
        }
    }

    private suspend fun renameFile(
        file: WildcardTextFile,
        newName: String
    ): WildcardTextFile {
        return withContext(dispatchers.io) {
            manageWildcardFiles.renameFile(file, newName)
        }
    }

    private suspend fun deleteFile(file: WildcardTextFile) {
        withContext(dispatchers.io) {
            manageWildcardFiles.deleteFile(file)
        }
    }

    private fun runPendingAction(
        action: WildcardPendingAction,
        onSelectFolder: () -> Unit
    ): Boolean {
        return when (action) {
            is WildcardPendingAction.OpenFile -> {
                openFile(action.file)
                false
            }
            WildcardPendingAction.CreateFile -> {
                showNewFileDialog()
                false
            }
            WildcardPendingAction.SelectFolder -> {
                onSelectFolder()
                true
            }
        }
    }

    private suspend fun runPendingActionInCurrentOperation(
        action: WildcardPendingAction,
        onSelectFolder: () -> Unit
    ) {
        when (action) {
            is WildcardPendingAction.OpenFile -> openFileInCurrentOperation(action.file)
            WildcardPendingAction.CreateFile -> showNewFileDialog()
            WildcardPendingAction.SelectFolder -> onSelectFolder()
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

    private fun applyTextEditResult(result: WildcardTextEditResult) {
        _uiState.update {
            it.copy(
                editingText = result.text,
                undoStack = result.undoStack,
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

    private fun beginFileOperation(): Boolean {
        if (uiState.value.isFileOperationInProgress) return false
        _uiState.update {
            it.copy(isFileOperationInProgress = true)
        }
        return true
    }

    private fun endFileOperation() {
        _uiState.update {
            it.copy(isFileOperationInProgress = false)
        }
    }

    private fun showFileListError(error: RuntimeException) {
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
}

class WildcardManagerViewModelFactory(
    private val fileRepository: WildcardFileRepository,
    private val clipboardGateway: ClipboardGateway
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(WildcardManagerViewModel::class.java)) {
            error("Unknown ViewModel class: ${modelClass.name}")
        }

        return WildcardManagerViewModel(
            manageWildcardFiles = ManageWildcardFilesUseCase(fileRepository),
            wildcardClipboard = WildcardClipboardUseCase(clipboardGateway)
        ) as T
    }
}

