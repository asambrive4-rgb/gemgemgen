// 역할: 와일드카드 파일 퀐색, 내용 편집, 저장 및 AI 분류 이벤트를 관리합니다.
package com.example.gemgemgen.wildcard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.wildcard.domain.WildcardDynamicPromptComposer
import com.example.gemgemgen.wildcard.domain.WildcardEditorSession
import com.example.gemgemgen.wildcard.domain.WildcardTextEditResult
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.ClassifyWildcardLinesUseCase
import com.example.gemgemgen.wildcard.usecase.ManageWildcardFilesUseCase
import com.example.gemgemgen.wildcard.usecase.SaveWildcardClassifyResultUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardClipboardPasteResult
import com.example.gemgemgen.wildcard.usecase.WildcardClipboardUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WildcardManagerViewModel(
    private val manageWildcardFiles: ManageWildcardFilesUseCase,
    private val wildcardClipboard: WildcardClipboardUseCase,
    classifyWildcardLines: ClassifyWildcardLinesUseCase? = null,
    saveWildcardClassifyResult: SaveWildcardClassifyResultUseCase? = null,
    analysisKeyManager: ManageGeminiApiKeysUseCase? = null,
    classifyCoordinator: WildcardClassifyCoordinator? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val _uiState = MutableStateFlow(WildcardManagerUiState())
    val uiState: StateFlow<WildcardManagerUiState> = _uiState.asStateFlow()

    private val classifyCoordinator: WildcardClassifyCoordinator =
        classifyCoordinator ?: WildcardClassifyCoordinator(
            classifyWildcardLines = classifyWildcardLines,
            saveWildcardClassifyResult = saveWildcardClassifyResult,
            analysisKeyManager = analysisKeyManager,
            scope = scope,
            host = ClassifyHost()
        )

    init {
        refreshFiles(openFirstFile = true)
    }

    fun onFolderAccessChanged(canModifyFiles: Boolean) {
        if (uiState.value.canModifyFiles == canModifyFiles) return
        _uiState.update { it.copy(canModifyFiles = canModifyFiles) }
    }

    fun trimForInactiveTab() {
        classifyCoordinator.cancelJob()
        _uiState.update { state ->
            val trimmed = state.editor.trimForInactiveTab()
            if (trimmed == state.editor && !state.isLineSelectionMode && !state.classify.isBusy) {
                state
            } else {
                state.copy(
                    editor = trimmed,
                    isLineSelectionMode = false,
                    selectedLineIndices = emptySet(),
                    classify = WildcardClassifyUiState(),
                    message = "",
                    error = ""
                )
            }
        }
    }

    fun enterLineSelectionMode() {
        val state = uiState.value
        if (state.isFileOperationInProgress || state.isLineSelectionMode) return
        if (state.selectedFile == null) return showError("먼저 txt 파일을 선택하거나 새로 만들어주세요.")
        _uiState.update {
            it.copy(isLineSelectionMode = true, selectedLineIndices = emptySet(), message = "", error = "")
        }
    }

    fun exitLineSelectionMode() {
        if (!uiState.value.isLineSelectionMode) return
        _uiState.update {
            it.copy(isLineSelectionMode = false, selectedLineIndices = emptySet(), message = "", error = "")
        }
    }

    fun toggleLineSelection(index: Int) {
        val state = uiState.value
        if (!state.isLineSelectionMode || state.isFileOperationInProgress || index !in state.selectableLines.indices) return
        _uiState.update {
            val next = if (index in it.selectedLineIndices) it.selectedLineIndices - index else it.selectedLineIndices + index
            it.copy(selectedLineIndices = next, message = "", error = "")
        }
    }

    fun selectAllLines() {
        val state = uiState.value
        if (!state.isLineSelectionMode || state.isFileOperationInProgress || state.selectableLines.isEmpty()) return
        _uiState.update { it.copy(selectedLineIndices = state.selectableLines.indices.toSet(), message = "", error = "") }
    }

    fun deselectAllLines() {
        val state = uiState.value
        if (!state.isLineSelectionMode || state.isFileOperationInProgress) return
        _uiState.update { it.copy(selectedLineIndices = emptySet(), message = "", error = "") }
    }

    fun composeDynamicPromptToClipboard() {
        val state = uiState.value
        if (state.isFileOperationInProgress || !state.isLineSelectionMode) return
        when (val result = WildcardDynamicPromptComposer.composeFromIndices(state.selectableLines, state.selectedLineIndices)) {
            WildcardDynamicPromptComposer.ComposeResult.NoSelection -> showError("한 줄 이상 선택하세요.")
            is WildcardDynamicPromptComposer.ComposeResult.InvalidCharacters -> showError("| 또는 <> 가 있는 줄은 다이나믹에 넣을 수 없습니다.")
            is WildcardDynamicPromptComposer.ComposeResult.Success -> scope.launch {
                if (!wildcardClipboard.copy(result.dynamicPrompt)) return@launch showError("클립보드에 복사하지 못했습니다.")
                _uiState.update { it.copy(message = "다이나믹 프롬프트를 클립보드에 복사했습니다.", error = "") }
            }
        }
    }

    // AI 줄 분류 관련 액션 위임 (WildcardClassifyCoordinator)
    fun requestClassify() = classifyCoordinator.requestClassify()
    fun onClassifyCriteriaChange(value: String) = classifyCoordinator.onClassifyCriteriaChange(value)
    fun onClassifyProviderSelected(provider: AnalysisProvider) = classifyCoordinator.onClassifyProviderSelected(provider)
    fun onClassifyModelSelected(modelId: String) = classifyCoordinator.onClassifyModelSelected(modelId)
    fun dismissClassifyCriteriaDialog() = classifyCoordinator.dismissClassifyCriteriaDialog()
    fun runClassify() = classifyCoordinator.runClassify()
    fun dismissClassifyPreview() = classifyCoordinator.dismissClassifyPreview()
    fun onClassifyFileNameChange(index: Int, value: String) = classifyCoordinator.onClassifyFileNameChange(index, value)
    fun onToggleClassifyFileNameEdit(index: Int) = classifyCoordinator.onToggleClassifyFileNameEdit(index)
    fun saveClassifyResult(overwrite: Boolean = false) = classifyCoordinator.saveClassifyResult(overwrite)
    fun confirmClassifyOverwrite() = classifyCoordinator.confirmClassifyOverwrite()
    fun dismissClassifyOverwrite() = classifyCoordinator.dismissClassifyOverwrite()

    fun onTabEntered() {
        val state = uiState.value
        val file = state.selectedFile ?: return
        if (state.hasUnsavedChanges || state.editingText.isNotEmpty() || state.savedText.isNotEmpty()) return
        if (state.isFileOperationInProgress) return
        openFile(file, keepMessage = true)
    }

    fun refreshFiles(openFirstFile: Boolean = false) {
        launchFileOperation(errorMessage = "파일 목록을 불러오지 못했습니다.", onError = { showFileListError(it) }) {
            val workspace = manageWildcardFiles.refreshWorkspace(
                selectedFile = uiState.value.selectedFile,
                openFirstFile = openFirstFile
            )
            val openedFile = workspace.selectedFile
            val openedText = workspace.selectedText
            _uiState.update {
                val editor = when {
                    openedFile != null && openedText != null -> it.editor.open(openedFile, openedText)
                    openedFile != null -> it.editor.rename(openedFile)
                    else -> it.editor
                }
                val clearedSelection = openedFile != null && openedText != null
                it.copy(
                    files = workspace.files,
                    editor = editor,
                    isLineSelectionMode = if (clearedSelection) false else it.isLineSelectionMode,
                    selectedLineIndices = if (clearedSelection) emptySet() else it.selectedLineIndices,
                    message = if (openedFile != null && openedText != null) "${openedFile.fileName} 열기 완료" else it.message,
                    error = ""
                )
            }
            if (workspace.previousSelectionMissing && workspace.selectedFile == null) {
                clearSelectedFile(if (workspace.files.isEmpty()) "txt 파일이 없습니다." else "선택했던 파일을 찾지 못했습니다.")
            }
        }
    }

    fun onFolderChanged() {
        classifyCoordinator.reset()
        _uiState.update {
            it.copy(
                files = emptyList(),
                editor = WildcardEditorSession(),
                isFileOperationInProgress = false,
                pendingAction = null,
                isLineSelectionMode = false,
                selectedLineIndices = emptySet(),
                classify = WildcardClassifyUiState(),
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
            it.copy(pendingAction = WildcardPendingAction.SelectFolder, message = "", error = "")
        }
        return false
    }

    fun selectFile(file: WildcardTextFile) {
        val state = uiState.value
        if (state.isFileOperationInProgress || state.selectedFile?.id == file.id) return
        if (state.hasUnsavedChanges) {
            _uiState.update {
                it.copy(pendingAction = WildcardPendingAction.OpenFile(file), message = "", error = "")
            }
            return
        }
        openFile(file)
    }

    fun onTextChange(value: String) {
        _uiState.update { it.copy(editor = it.editor.edit(value), message = "", error = "") }
    }

    fun saveCurrent(): Boolean = saveCurrent(afterSave = null)

    fun requestNewFile() {
        val state = uiState.value
        if (state.isFileOperationInProgress) return
        if (!state.canModifyFiles) return showError("새 파일을 만들려면 wildcard 폴더를 다시 선택해주세요.")
        if (state.hasUnsavedChanges) {
            _uiState.update {
                it.copy(pendingAction = WildcardPendingAction.CreateFile, message = "", error = "")
            }
            return
        }
        showNewFileDialog()
    }

    fun onNewFileNameChange(value: String) {
        _uiState.update { it.copy(newFileName = value, error = "") }
    }

    fun dismissNewFileDialog() {
        _uiState.update { it.copy(showNewFileDialog = false, newFileName = "", error = "") }
    }

    fun createNewFile() {
        val input = uiState.value.newFileName
        if (input.isBlank()) return showError("파일명을 입력해주세요.")
        launchFileOperation(errorMessage = "새 파일을 만들지 못했습니다.") {
            val workspace = manageWildcardFiles.createFile(input)
            val createdFile = checkNotNull(workspace.selectedFile)
            _uiState.update {
                it.copy(
                    files = workspace.files,
                    editor = it.editor.open(createdFile, workspace.selectedText.orEmpty()),
                    showNewFileDialog = false,
                    newFileName = "",
                    isLineSelectionMode = false,
                    selectedLineIndices = emptySet(),
                    message = "${createdFile.fileName} 생성 완료",
                    error = ""
                )
            }
        }
    }

    fun requestRenameSelectedFile() {
        val state = uiState.value
        if (state.isFileOperationInProgress) return
        if (!state.canModifyFiles) return showError("파일 이름을 수정하려면 wildcard 폴더를 다시 선택해주세요.")
        val file = state.selectedFile ?: return showError("수정할 파일을 선택해주세요.")
        val baseName = if (file.fileName.endsWith(".txt")) file.fileName.dropLast(4) else file.fileName
        _uiState.update {
            it.copy(showRenameDialog = true, renameFileName = baseName, message = "", error = "")
        }
    }

    fun onRenameFileNameChange(value: String) {
        _uiState.update { it.copy(renameFileName = value, error = "") }
    }

    fun dismissRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = false, renameFileName = "", error = "") }
    }

    fun renameSelectedFile() {
        val file = uiState.value.selectedFile ?: return showError("수정할 파일을 선택해주세요.")
        val newName = uiState.value.renameFileName
        if (newName.isBlank()) return showError("파일 이름을 입력해주세요.")
        launchFileOperation(errorMessage = "파일 이름을 수정하지 못했습니다.") {
            val workspace = manageWildcardFiles.renameFile(file, newName)
            val updatedFile = checkNotNull(workspace.selectedFile)
            _uiState.update {
                it.copy(
                    files = workspace.files,
                    editor = it.editor.rename(updatedFile),
                    showRenameDialog = false,
                    renameFileName = "",
                    message = "${updatedFile.fileName}으로 이름 수정 완료",
                    error = ""
                )
            }
        }
    }

    fun requestDeleteSelectedFile() {
        val state = uiState.value
        if (state.isFileOperationInProgress) return
        if (!state.canModifyFiles) return showError("파일을 삭제하려면 wildcard 폴더를 다시 선택해주세요.")
        if (state.selectedFile == null) return showError("삭제할 파일을 선택해주세요.")
        _uiState.update { it.copy(showDeleteConfirm = true, message = "", error = "") }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDeleteSelectedFile() {
        val file = uiState.value.selectedFile ?: return showError("삭제할 파일을 선택해주세요.")
        launchFileOperation(
            errorMessage = "파일을 삭제하지 못했습니다.",
            onError = { error ->
                _uiState.update {
                    it.copy(showDeleteConfirm = false, message = "", error = error.message ?: "파일을 삭제하지 못했습니다.")
                }
            }
        ) {
            val workspace = manageWildcardFiles.deleteFile(file, uiState.value.files)
            val nextFile = workspace.selectedFile
            _uiState.update {
                it.copy(files = workspace.files, showDeleteConfirm = false, message = "${file.fileName} 삭제 완료", error = "")
            }
            if (nextFile == null) {
                clearSelectedFile("txt 파일이 없습니다.")
            } else {
                _uiState.update {
                    it.copy(
                        editor = it.editor.open(nextFile, workspace.selectedText.orEmpty()),
                        isLineSelectionMode = false,
                        selectedLineIndices = emptySet()
                    )
                }
            }
        }
    }

    fun pasteFromClipboard() = pasteInternal { current, undo -> wildcardClipboard.paste(current, undo) }
    fun pasteBelowFromClipboard() = pasteInternal { current, undo -> wildcardClipboard.pasteBelow(current, undo) }

    private fun pasteInternal(action: suspend (String, List<String>) -> WildcardClipboardPasteResult) {
        if (uiState.value.isFileOperationInProgress || !ensureCanModifyFiles() || !ensureFileSelected()) return
        val state = uiState.value
        scope.launch {
            when (val result = action(state.editingText, state.undoStack)) {
                WildcardClipboardPasteResult.EmptyClipboard -> showError("클립보드가 비어 있습니다.")
                is WildcardClipboardPasteResult.Success -> applyTextEditResult(result.edit)
            }
        }
    }

    fun copyToClipboard() {
        if (uiState.value.isFileOperationInProgress) return
        val text = uiState.value.editingText
        if (text.isEmpty()) return showError("복사할 내용이 없습니다.")
        scope.launch {
            if (!wildcardClipboard.copy(text)) return@launch showError("복사할 내용이 없습니다.")
            _uiState.update { it.copy(message = "클립보드에 복사했습니다.", error = "") }
        }
    }

    fun undoClipboardEdit() {
        if (uiState.value.isFileOperationInProgress) return
        val result = wildcardClipboard.undo(uiState.value.undoStack) ?: return showError("되돌릴 붙여넣기 기록이 없습니다.")
        _uiState.update {
            it.copy(editor = it.editor.apply(result), message = "붙여넣기 전 상태로 되돌렸습니다.", error = "")
        }
    }

    fun confirmPendingWithSave(onSelectFolder: () -> Unit = {}): Boolean {
        val action = uiState.value.pendingAction ?: return false
        return saveCurrent {
            clearPendingAction()
            runPendingActionInCurrentOperation(action, onSelectFolder)
        }
    }

    fun confirmPendingWithDiscard(onSelectFolder: () -> Unit = {}): Boolean {
        val action = uiState.value.pendingAction ?: return false
        clearPendingAction()
        return runPendingAction(action, onSelectFolder)
    }

    fun cancelPendingAction() = clearPendingAction()

    private fun saveCurrent(afterSave: (suspend () -> Unit)?): Boolean {
        val state = uiState.value
        if (state.isFileOperationInProgress) return false
        if (!state.canModifyFiles) { showError("파일을 편집하려면 wildcard 폴더를 다시 선택해주세요."); return false }
        val file = state.selectedFile ?: run { showError("저장할 파일을 선택해주세요."); return false }
        launchFileOperation(errorMessage = "파일을 저장하지 못했습니다.") {
            manageWildcardFiles.saveFile(file, state.editingText)
            _uiState.update { it.copy(editor = it.editor.markSaved(), message = "${file.fileName} 저장 완료", error = "") }
            afterSave?.invoke()
        }
        return true
    }

    private fun openFile(file: WildcardTextFile, keepMessage: Boolean = false) {
        launchFileOperation(errorMessage = "파일을 열지 못했습니다.") {
            openFileInCurrentOperation(file, keepMessage)
        }
    }

    private suspend fun openFileInCurrentOperation(file: WildcardTextFile, keepMessage: Boolean = false) {
        try {
            val text = manageWildcardFiles.openFile(file)
            classifyCoordinator.reset()
            _uiState.update {
                it.copy(
                    editor = it.editor.open(file, text),
                    isLineSelectionMode = false,
                    selectedLineIndices = emptySet(),
                    classify = WildcardClassifyUiState(),
                    message = if (keepMessage) it.message else "${file.fileName} 열기 완료",
                    error = ""
                )
            }
        } catch (error: RuntimeException) {
            showError(error.message ?: "파일을 열지 못했습니다.")
        }
    }

    private fun runPendingAction(action: WildcardPendingAction, onSelectFolder: () -> Unit): Boolean = when (action) {
        is WildcardPendingAction.OpenFile -> { openFile(action.file); false }
        WildcardPendingAction.CreateFile -> { showNewFileDialog(); false }
        WildcardPendingAction.SelectFolder -> { onSelectFolder(); true }
    }

    private suspend fun runPendingActionInCurrentOperation(action: WildcardPendingAction, onSelectFolder: () -> Unit) = when (action) {
        is WildcardPendingAction.OpenFile -> openFileInCurrentOperation(action.file)
        WildcardPendingAction.CreateFile -> showNewFileDialog()
        WildcardPendingAction.SelectFolder -> onSelectFolder()
    }

    private fun showNewFileDialog() {
        _uiState.update { it.copy(showNewFileDialog = true, newFileName = "", message = "", error = "") }
    }

    private fun applyTextEditResult(result: WildcardTextEditResult) {
        _uiState.update { it.copy(editor = it.editor.apply(result), message = "클립보드 내용을 반영했습니다.", error = "") }
    }

    private fun clearSelectedFile(message: String) {
        classifyCoordinator.reset()
        _uiState.update {
            it.copy(
                editor = it.editor.clear(),
                isLineSelectionMode = false,
                selectedLineIndices = emptySet(),
                classify = WildcardClassifyUiState(),
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
        _uiState.update { it.copy(isFileOperationInProgress = true) }
        return true
    }

    private fun endFileOperation() {
        _uiState.update { it.copy(isFileOperationInProgress = false) }
    }

    private fun launchFileOperation(
        errorMessage: String,
        onError: ((RuntimeException) -> Unit)? = null,
        block: suspend () -> Unit
    ) {
        if (!beginFileOperation()) return
        scope.launch {
            try {
                block()
            } catch (error: RuntimeException) {
                if (onError != null) {
                    onError(error)
                } else {
                    showError(error.message ?: errorMessage)
                }
            } finally {
                endFileOperation()
            }
        }
    }

    private fun showFileListError(error: RuntimeException) {
        _uiState.update {
            it.copy(
                files = emptyList(),
                editor = it.editor.clear(),
                isLineSelectionMode = false,
                selectedLineIndices = emptySet(),
                error = error.message ?: "파일 목록을 불러오지 못했습니다."
            )
        }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(message = "", error = message) }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(message = message, error = "") }
    }

    private fun clearPendingAction() {
        _uiState.update { it.copy(pendingAction = null, message = "", error = "") }
    }

    private inner class ClassifyHost : WildcardClassifyCoordinator.Host {
        override val selectedFile get() = _uiState.value.selectedFile
        override val selectableLines get() = _uiState.value.selectableLines
        override val editingText get() = _uiState.value.editingText
        override val canModifyFiles get() = _uiState.value.canModifyFiles
        override val isFileOperationInProgress get() = _uiState.value.isFileOperationInProgress
        override val canRequestClassify get() = _uiState.value.canRequestClassify

        override fun onLineSelectionCleared() {
            _uiState.update { it.copy(isLineSelectionMode = false, selectedLineIndices = emptySet()) }
        }

        override fun showMessage(message: String) = this@WildcardManagerViewModel.showMessage(message)
        override fun showError(error: String) = this@WildcardManagerViewModel.showError(error)
        override fun clearError() = _uiState.update { it.copy(error = "") }
        override fun clearMessageAndError() = _uiState.update { it.copy(message = "", error = "") }

        override fun updateClassifyState(transform: (WildcardClassifyUiState) -> WildcardClassifyUiState) {
            _uiState.update { it.copy(classify = transform(it.classify)) }
        }

        override fun beginFileOperation(): Boolean = this@WildcardManagerViewModel.beginFileOperation()
        override fun endFileOperation() = this@WildcardManagerViewModel.endFileOperation()

        override suspend fun onFilesSaved() {
            val workspace = manageWildcardFiles.refreshWorkspace(
                selectedFile = _uiState.value.selectedFile,
                openFirstFile = false
            )
            _uiState.update { it.copy(files = workspace.files) }
        }
    }
}
