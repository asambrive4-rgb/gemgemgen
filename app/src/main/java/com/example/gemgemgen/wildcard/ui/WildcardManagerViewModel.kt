package com.example.gemgemgen.wildcard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.usecase.AnalysisException
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.wildcard.domain.WildcardClassifyFileName
import com.example.gemgemgen.wildcard.domain.WildcardDynamicPromptComposer
import com.example.gemgemgen.wildcard.domain.WildcardEditorSession
import com.example.gemgemgen.wildcard.domain.WildcardTextEditResult
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.ClassifyWildcardLinesUseCase
import com.example.gemgemgen.wildcard.usecase.ManageWildcardFilesUseCase
import com.example.gemgemgen.wildcard.usecase.SaveWildcardClassifyResultUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardClassifySaveResult
import com.example.gemgemgen.wildcard.usecase.WildcardClipboardPasteResult
import com.example.gemgemgen.wildcard.usecase.WildcardClipboardUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WildcardManagerViewModel(
    private val manageWildcardFiles: ManageWildcardFilesUseCase,
    private val wildcardClipboard: WildcardClipboardUseCase,
    private val classifyWildcardLines: ClassifyWildcardLinesUseCase? = null,
    private val saveWildcardClassifyResult: SaveWildcardClassifyResultUseCase? = null,
    private val analysisKeyManager: ManageGeminiApiKeysUseCase? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private var classifyJob: Job? = null
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

    fun trimForInactiveTab() {
        classifyJob?.cancel()
        classifyJob = null
        _uiState.update { state ->
            val trimmed = state.editor.trimForInactiveTab()
            if (
                trimmed == state.editor &&
                !state.isLineSelectionMode &&
                !state.showClassifyCriteriaDialog &&
                !state.isClassifying &&
                state.classifyPreview == null
            ) {
                state
            } else {
                state.copy(
                    editor = trimmed,
                    isLineSelectionMode = false,
                    selectedLineIndices = emptySet(),
                    showClassifyCriteriaDialog = false,
                    isClassifying = false,
                    classifyPreview = null,
                    classifySaveEntries = emptyList(),
                    classifyOverwriteConflicts = emptyList(),
                    message = "",
                    error = ""
                )
            }
        }
    }

    fun enterLineSelectionMode() {
        val state = uiState.value
        if (state.isFileOperationInProgress) return
        if (state.selectedFile == null) {
            showError("먼저 txt 파일을 선택하거나 새로 만들어주세요.")
            return
        }
        if (state.isLineSelectionMode) return

        _uiState.update {
            it.copy(
                isLineSelectionMode = true,
                selectedLineIndices = emptySet(),
                message = "",
                error = ""
            )
        }
    }

    fun exitLineSelectionMode() {
        if (!uiState.value.isLineSelectionMode) return
        _uiState.update {
            it.copy(
                isLineSelectionMode = false,
                selectedLineIndices = emptySet(),
                message = "",
                error = ""
            )
        }
    }

    fun toggleLineSelection(index: Int) {
        val state = uiState.value
        if (!state.isLineSelectionMode || state.isFileOperationInProgress) return
        if (index !in state.selectableLines.indices) return

        _uiState.update {
            val next = if (index in it.selectedLineIndices) {
                it.selectedLineIndices - index
            } else {
                it.selectedLineIndices + index
            }
            it.copy(
                selectedLineIndices = next,
                message = "",
                error = ""
            )
        }
    }

    fun selectAllLines() {
        val state = uiState.value
        if (!state.isLineSelectionMode || state.isFileOperationInProgress) return
        val lines = state.selectableLines
        if (lines.isEmpty()) return

        _uiState.update {
            it.copy(
                selectedLineIndices = lines.indices.toSet(),
                message = "",
                error = ""
            )
        }
    }

    fun deselectAllLines() {
        val state = uiState.value
        if (!state.isLineSelectionMode || state.isFileOperationInProgress) return

        _uiState.update {
            it.copy(
                selectedLineIndices = emptySet(),
                message = "",
                error = ""
            )
        }
    }

    fun composeDynamicPromptToClipboard() {
        if (uiState.value.isFileOperationInProgress) return
        val state = uiState.value
        if (!state.isLineSelectionMode) return

        when (
            val result = WildcardDynamicPromptComposer.composeFromIndices(
                allLines = state.selectableLines,
                selectedIndices = state.selectedLineIndices
            )
        ) {
            WildcardDynamicPromptComposer.ComposeResult.NoSelection -> {
                showError("한 줄 이상 선택하세요.")
            }
            is WildcardDynamicPromptComposer.ComposeResult.InvalidCharacters -> {
                showError("| 또는 <> 가 있는 줄은 다이나믹에 넣을 수 없습니다.")
            }
            is WildcardDynamicPromptComposer.ComposeResult.Success -> {
                scope.launch {
                    if (!wildcardClipboard.copy(result.dynamicPrompt)) {
                        showError("클립보드에 복사하지 못했습니다.")
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            message = "다이나믹 프롬프트를 클립보드에 복사했습니다.",
                            error = ""
                        )
                    }
                }
            }
        }
    }

    fun requestClassify() {
        val state = uiState.value
        if (!state.canRequestClassify) {
            when {
                state.selectedFile == null ->
                    showError("먼저 txt 파일을 선택해주세요.")
                state.selectableLines.isEmpty() ->
                    showError("분류할 줄이 없습니다.")
                !state.canModifyFiles ->
                    showError("파일을 저장하려면 wildcard 폴더를 다시 선택해주세요.")
                classifyWildcardLines == null ->
                    showError("분류 기능을 사용할 수 없습니다.")
                else -> Unit
            }
            return
        }
        if (classifyWildcardLines == null) {
            showError("분류 기능을 사용할 수 없습니다.")
            return
        }

        scope.launch {
            val generationSetting = analysisKeyManager
                ?.getRoleSetting(AnalysisModelRole.GENERATION)
            _uiState.update {
                it.copy(
                    showClassifyCriteriaDialog = true,
                    classifyCriteria = it.classifyCriteria,
                    classifyPreview = null,
                    classifySaveEntries = emptyList(),
                    classifyOverwriteConflicts = emptyList(),
                    classifyProvider = generationSetting?.provider ?: it.classifyProvider,
                    classifyModelId = generationSetting?.modelId ?: it.classifyModelId,
                    isLineSelectionMode = false,
                    selectedLineIndices = emptySet(),
                    message = "",
                    error = ""
                )
            }
        }
    }

    fun onClassifyCriteriaChange(value: String) {
        _uiState.update { it.copy(classifyCriteria = value, error = "") }
    }

    fun onClassifyProviderSelected(provider: AnalysisProvider) {
        val keyManager = analysisKeyManager ?: run {
            _uiState.update {
                it.copy(
                    classifyProvider = provider,
                    classifyModelId = AnalysisProvider.defaultModel(provider),
                    error = ""
                )
            }
            return
        }
        scope.launch {
            try {
                val setting = keyManager.setRoleProvider(AnalysisModelRole.GENERATION, provider)
                _uiState.update {
                    it.copy(
                        classifyProvider = setting.provider,
                        classifyModelId = setting.modelId,
                        error = ""
                    )
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "모델을 바꾸지 못했습니다.")
            }
        }
    }

    fun onClassifyModelSelected(modelId: String) {
        val keyManager = analysisKeyManager ?: run {
            _uiState.update { it.copy(classifyModelId = modelId, error = "") }
            return
        }
        scope.launch {
            try {
                val setting = keyManager.setRoleModel(AnalysisModelRole.GENERATION, modelId)
                _uiState.update {
                    it.copy(
                        classifyProvider = setting.provider,
                        classifyModelId = setting.modelId,
                        error = ""
                    )
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "모델을 바꾸지 못했습니다.")
            }
        }
    }

    fun dismissClassifyCriteriaDialog() {
        if (uiState.value.isClassifying) return
        _uiState.update {
            it.copy(
                showClassifyCriteriaDialog = false,
                error = ""
            )
        }
    }

    fun runClassify() {
        val classify = classifyWildcardLines ?: run {
            showError("분류 기능을 사용할 수 없습니다.")
            return
        }
        val state = uiState.value
        if (!state.canRunClassify) {
            if (state.classifyCriteria.isBlank()) {
                showError("분류 기준을 입력해주세요.")
            }
            return
        }

        val editingText = state.editingText
        val criteria = state.classifyCriteria
        classifyJob?.cancel()
        classifyJob = scope.launch {
            _uiState.update {
                it.copy(
                    isClassifying = true,
                    showClassifyCriteriaDialog = false,
                    // 다시 분류 시 이전 미리보기는 잠시 숨김
                    classifyPreview = null,
                    classifySaveEntries = emptyList(),
                    classifyOverwriteConflicts = emptyList(),
                    message = "분류 중…",
                    error = ""
                )
            }
            try {
                val result = classify.classify(
                    editingText = editingText,
                    criteria = criteria
                )
                val entries = WildcardClassifyFileName.buildSaveEntries(result.savableGroups)
                val dropNote = if (result.droppedLineCount > 0) {
                    " · 미배정 ${result.droppedLineCount}줄(저장 안 함)"
                } else {
                    ""
                }
                _uiState.update {
                    it.copy(
                        isClassifying = false,
                        classifyPreview = result,
                        classifySaveEntries = entries,
                        classifyOverwriteConflicts = emptyList(),
                        classifyCriteria = result.criteria,
                        message = "분류 미리보기: ${entries.size}개 파일$dropNote",
                        error = ""
                    )
                }
            } catch (error: AnalysisException) {
                _uiState.update {
                    it.copy(
                        isClassifying = false,
                        showClassifyCriteriaDialog = true,
                        message = "",
                        error = error.message ?: "분류에 실패했습니다."
                    )
                }
            } catch (error: RuntimeException) {
                _uiState.update {
                    it.copy(
                        isClassifying = false,
                        showClassifyCriteriaDialog = true,
                        message = "",
                        error = error.message ?: "분류에 실패했습니다."
                    )
                }
            }
        }
    }

    fun dismissClassifyPreview() {
        if (uiState.value.isClassifying) return
        _uiState.update {
            it.copy(
                classifyPreview = null,
                classifySaveEntries = emptyList(),
                classifyOverwriteConflicts = emptyList(),
                message = "",
                error = ""
            )
        }
    }

    fun onClassifyFileNameChange(index: Int, value: String) {
        _uiState.update { state ->
            val entries = state.classifySaveEntries.toMutableList()
            if (index !in entries.indices) return@update state
            entries[index] = entries[index].copy(fileNameInput = value)
            state.copy(classifySaveEntries = entries, error = "")
        }
    }

    fun onToggleClassifyFileNameEdit(index: Int) {
        _uiState.update { state ->
            val entries = state.classifySaveEntries.toMutableList()
            if (index !in entries.indices) return@update state
            val current = entries[index]
            entries[index] = current.copy(isEditingFileName = !current.isEditingFileName)
            state.copy(classifySaveEntries = entries, error = "")
        }
    }

    fun saveClassifyResult(overwrite: Boolean = false) {
        val saveUseCase = saveWildcardClassifyResult ?: run {
            showError("분류 저장 기능을 사용할 수 없습니다.")
            return
        }
        val entries = uiState.value.classifySaveEntries
        if (entries.isEmpty()) {
            showError("저장할 그룹이 없습니다.")
            return
        }
        if (uiState.value.isFileOperationInProgress || uiState.value.isClassifying) return
        if (!uiState.value.canModifyFiles) {
            showError("파일을 저장하려면 wildcard 폴더를 다시 선택해주세요.")
            return
        }
        if (!beginFileOperation()) return

        scope.launch {
            try {
                when (val result = saveUseCase.save(entries, overwrite = overwrite)) {
                    is WildcardClassifySaveResult.Success -> {
                        val workspace = manageWildcardFiles.refreshWorkspace(
                            selectedFile = uiState.value.selectedFile,
                            openFirstFile = false
                        )
                        _uiState.update {
                            it.copy(
                                files = workspace.files,
                                classifyPreview = null,
                                classifySaveEntries = emptyList(),
                                classifyOverwriteConflicts = emptyList(),
                                message = "${result.savedFileNames.size}개 파일로 저장했습니다.",
                                error = ""
                            )
                        }
                    }
                    is WildcardClassifySaveResult.FileExists -> {
                        _uiState.update {
                            it.copy(
                                classifyOverwriteConflicts = result.conflictingFileNames,
                                message = "",
                                error = "같은 이름의 파일이 있습니다. 덮어쓸까요?"
                            )
                        }
                    }
                    WildcardClassifySaveResult.NothingToSave -> {
                        showError("저장할 그룹이 없습니다.")
                    }
                    is WildcardClassifySaveResult.InvalidFileName -> {
                        showError("파일 이름이 올바르지 않습니다: ${result.groupName}")
                    }
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "분류 결과를 저장하지 못했습니다.")
            } finally {
                endFileOperation()
            }
        }
    }

    fun confirmClassifyOverwrite() {
        saveClassifyResult(overwrite = true)
    }

    fun dismissClassifyOverwrite() {
        _uiState.update {
            it.copy(
                classifyOverwriteConflicts = emptyList(),
                error = ""
            )
        }
    }

    fun onTabEntered() {
        val state = uiState.value
        val file = state.selectedFile ?: return
        if (state.hasUnsavedChanges) return
        if (state.editingText.isNotEmpty() || state.savedText.isNotEmpty()) return
        if (state.isFileOperationInProgress) return
        openFile(file, keepMessage = true)
    }

    fun refreshFiles(openFirstFile: Boolean = false) {
        if (!beginFileOperation()) return

        scope.launch {
            try {
                val state = uiState.value
                val workspace = manageWildcardFiles.refreshWorkspace(
                    selectedFile = state.selectedFile,
                    openFirstFile = openFirstFile
                )
                val openedFile = workspace.selectedFile
                val openedText = workspace.selectedText
                _uiState.update {
                    val editor = when {
                        openedFile != null && openedText != null ->
                            it.editor.open(openedFile, openedText)
                        openedFile != null -> it.editor.rename(openedFile)
                        else -> it.editor
                    }
                    val clearedSelection = openedFile != null && openedText != null
                    it.copy(
                        files = workspace.files,
                        editor = editor,
                        isLineSelectionMode = if (clearedSelection) false else it.isLineSelectionMode,
                        selectedLineIndices = if (clearedSelection) emptySet() else it.selectedLineIndices,
                        message = if (openedFile != null && openedText != null) {
                            "${openedFile.fileName} 열기 완료"
                        } else {
                            it.message
                        },
                        error = ""
                    )
                }

                if (workspace.previousSelectionMissing && workspace.selectedFile == null) {
                    if (workspace.files.isEmpty()) {
                        clearSelectedFile("txt 파일이 없습니다.")
                    } else {
                        clearSelectedFile("선택했던 파일을 찾지 못했습니다.")
                    }
                }
            } catch (error: RuntimeException) {
                showFileListError(error)
            } finally {
                endFileOperation()
            }
        }
    }

    fun onFolderChanged() {
        classifyJob?.cancel()
        classifyJob = null
        _uiState.update {
            it.copy(
                files = emptyList(),
                editor = WildcardEditorSession(),
                isFileOperationInProgress = false,
                pendingAction = null,
                isLineSelectionMode = false,
                selectedLineIndices = emptySet(),
                showClassifyCriteriaDialog = false,
                isClassifying = false,
                classifyPreview = null,
                classifySaveEntries = emptyList(),
                classifyOverwriteConflicts = emptyList(),
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
                editor = it.editor.edit(value),
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
                val workspace = manageWildcardFiles.deleteFile(file, state.files)
                val nextFile = workspace.selectedFile

                _uiState.update {
                    it.copy(
                        files = workspace.files,
                        showDeleteConfirm = false,
                        message = "${file.fileName} 삭제 완료",
                        error = ""
                    )
                }

                if (nextFile == null) {
                    clearSelectedFile("txt 파일이 없습니다.")
                } else {
                    _uiState.update {
                        it.copy(
                            editor = it.editor.open(
                                nextFile,
                                workspace.selectedText.orEmpty()
                            ),
                            isLineSelectionMode = false,
                            selectedLineIndices = emptySet()
                        )
                    }
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
        scope.launch {
            when (val result = wildcardClipboard.paste(
                currentText = state.editingText,
                undoStack = state.undoStack
            )) {
                WildcardClipboardPasteResult.EmptyClipboard -> showError("클립보드가 비어 있습니다.")
                is WildcardClipboardPasteResult.Success -> applyTextEditResult(result.edit)
            }
        }
    }

    fun pasteBelowFromClipboard() {
        if (uiState.value.isFileOperationInProgress) return
        if (!ensureCanModifyFiles()) return
        if (!ensureFileSelected()) return

        val state = uiState.value
        scope.launch {
            when (val result = wildcardClipboard.pasteBelow(
                currentText = state.editingText,
                undoStack = state.undoStack
            )) {
                WildcardClipboardPasteResult.EmptyClipboard -> showError("클립보드가 비어 있습니다.")
                is WildcardClipboardPasteResult.Success -> applyTextEditResult(result.edit)
            }
        }
    }

    fun copyToClipboard() {
        if (uiState.value.isFileOperationInProgress) return
        val text = uiState.value.editingText
        if (text.isEmpty()) {
            showError("복사할 내용이 없습니다.")
            return
        }

        scope.launch {
            if (!wildcardClipboard.copy(text)) {
                showError("복사할 내용이 없습니다.")
                return@launch
            }
            _uiState.update {
                it.copy(
                    message = "클립보드에 복사했습니다.",
                    error = ""
                )
            }
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
                editor = it.editor.apply(result),
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
                manageWildcardFiles.saveFile(file, textToSave)
                _uiState.update {
                    it.copy(
                        editor = it.editor.markSaved(),
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
            val text = manageWildcardFiles.openFile(file)
            _uiState.update {
                it.copy(
                    editor = it.editor.open(file, text),
                    isLineSelectionMode = false,
                    selectedLineIndices = emptySet(),
                    showClassifyCriteriaDialog = false,
                    isClassifying = false,
                    classifyPreview = null,
                    classifySaveEntries = emptyList(),
                    classifyOverwriteConflicts = emptyList(),
                    message = if (keepMessage) it.message else "${file.fileName} 열기 완료",
                    error = ""
                )
            }
        } catch (error: RuntimeException) {
            showError(error.message ?: "파일을 열지 못했습니다.")
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
                editor = it.editor.apply(result),
                message = "클립보드 내용을 반영했습니다.",
                error = ""
            )
        }
    }

    private fun clearSelectedFile(message: String) {
        classifyJob?.cancel()
        classifyJob = null
        _uiState.update {
            it.copy(
                editor = it.editor.clear(),
                isLineSelectionMode = false,
                selectedLineIndices = emptySet(),
                showClassifyCriteriaDialog = false,
                isClassifying = false,
                classifyPreview = null,
                classifySaveEntries = emptyList(),
                classifyOverwriteConflicts = emptyList(),
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
                editor = it.editor.clear(),
                isLineSelectionMode = false,
                selectedLineIndices = emptySet(),
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
