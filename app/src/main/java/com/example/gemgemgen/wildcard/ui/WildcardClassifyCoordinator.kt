// 역할: AI 단어 분류 기능의 실행, 결과 검토 및 저장 단계를 조율합니다.
package com.example.gemgemgen.wildcard.ui

import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.MODEL_GROK_4_5
import com.example.gemgemgen.analysis.usecase.AnalysisRoleModelSetting
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.wildcard.domain.WildcardClassifyFileName
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResult
import com.example.gemgemgen.wildcard.domain.WildcardClassifySaveEntry
import com.example.gemgemgen.wildcard.usecase.ClassifyWildcardLinesUseCase
import com.example.gemgemgen.wildcard.usecase.SaveWildcardClassifyResultUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardClassifySaveResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class WildcardClassifyUiState(
    val showClassifyCriteriaDialog: Boolean = false,
    val classifyCriteria: String = "",
    val isClassifying: Boolean = false,
    val classifyPreview: WildcardClassifyResult? = null,
    val classifySaveEntries: List<WildcardClassifySaveEntry> = emptyList(),
    val classifyOverwriteConflicts: List<String> = emptyList(),
    /** 분석 탭 TXT 생성(generation)과 공유. 기준 입력 화면에서 변경 가능. */
    val classifyProvider: AnalysisProvider = AnalysisModelRole.defaultProvider(AnalysisModelRole.GENERATION),
    val classifyModelId: String = MODEL_GROK_4_5
) {
    val isBusy: Boolean
        get() = isClassifying || classifyPreview != null || showClassifyCriteriaDialog

    fun canRunClassify(isFileOperationInProgress: Boolean): Boolean =
        classifyCriteria.isNotBlank() &&
            !isClassifying &&
            !isFileOperationInProgress &&
            (showClassifyCriteriaDialog || classifyPreview != null)

    fun canSaveClassifyResult(canModifyFiles: Boolean, isFileOperationInProgress: Boolean): Boolean =
        classifyPreview != null &&
            classifySaveEntries.isNotEmpty() &&
            classifySaveEntries.all {
                WildcardClassifyFileName.normalizeUserInput(it.fileNameInput) != null
            } &&
            canModifyFiles &&
            !isClassifying &&
            !isFileOperationInProgress &&
            classifyOverwriteConflicts.isEmpty()
}

interface WildcardClassifyActions {
    fun requestClassify() {}
    fun onClassifyCriteriaChange(value: String) {}
    fun onClassifyProviderSelected(provider: AnalysisProvider) {}
    fun onClassifyModelSelected(modelId: String) {}
    fun dismissClassifyCriteriaDialog() {}
    fun runClassify() {}
    fun dismissClassifyPreview() {}
    fun onClassifyFileNameChange(index: Int, value: String) {}
    fun onToggleClassifyFileNameEdit(index: Int) {}
    fun saveClassifyResult(overwrite: Boolean = false) {}
    fun confirmClassifyOverwrite() {}
    fun dismissClassifyOverwrite() {}
}

class WildcardClassifyCoordinator(
    private val classifyWildcardLines: ClassifyWildcardLinesUseCase,
    private val saveWildcardClassifyResult: SaveWildcardClassifyResultUseCase,
    private val analysisKeyManager: ManageGeminiApiKeysUseCase,
    private val scope: CoroutineScope,
    private val currentState: () -> WildcardUiState,
    private val updateState: ((WildcardUiState) -> WildcardUiState) -> Unit,
    private val onFilesSaved: suspend () -> Unit = {},
    private val beginFileOperation: () -> Boolean = { true },
    private val endFileOperation: () -> Unit = {}
) : WildcardClassifyActions {
    private var classifyJob: Job? = null

    private fun updateClassifyState(transform: (WildcardClassifyUiState) -> WildcardClassifyUiState) {
        updateState { it.copy(classify = transform(it.classify)) }
    }

    private fun showMessage(message: String) {
        updateState { it.copy(message = message, error = "") }
    }

    private fun showError(error: String) {
        updateState { it.copy(message = "", error = error) }
    }

    private fun clearError() {
        updateState { it.copy(error = "") }
    }

    private fun clearMessageAndError() {
        updateState { it.copy(message = "", error = "") }
    }

    fun cancelJob() {
        classifyJob?.cancel()
        classifyJob = null
        if (currentState().classify.isClassifying) {
            updateClassifyState { it.copy(isClassifying = false) }
        }
    }

    fun reset() {
        cancelJob()
        updateClassifyState { WildcardClassifyUiState() }
    }

    override fun requestClassify() {
        val state = currentState()
        if (!state.canRequestClassify) {
            when {
                state.editor.selectedFile == null -> showError("먼저 txt 파일을 선택해주세요.")
                state.selectableLines.isEmpty() -> showError("분류할 줄이 없습니다.")
                !state.canModifyFiles -> showError("파일을 저장하려면 wildcard 폴더를 다시 선택해주세요.")
                else -> showError("분류 기능을 사용할 수 없습니다.")
            }
            return
        }

        scope.launch {
            val generationSetting = analysisKeyManager.getRoleSetting(AnalysisModelRole.GENERATION)
            updateState {
                it.copy(
                    isLineSelectionMode = false,
                    selectedLineIndices = emptySet(),
                    classify = it.classify.copy(
                        showClassifyCriteriaDialog = true,
                        classifyCriteria = it.classify.classifyCriteria,
                        classifyPreview = null,
                        classifySaveEntries = emptyList(),
                        classifyOverwriteConflicts = emptyList(),
                        classifyProvider = generationSetting.provider,
                        classifyModelId = generationSetting.modelId
                    ),
                    message = "",
                    error = ""
                )
            }
        }
    }

    override fun onClassifyCriteriaChange(value: String) {
        updateClassifyState { it.copy(classifyCriteria = value) }
        clearError()
    }

    override fun onClassifyProviderSelected(provider: AnalysisProvider) {
        updateRoleSetting { analysisKeyManager.setRoleProvider(AnalysisModelRole.GENERATION, provider) }
    }

    override fun onClassifyModelSelected(modelId: String) {
        updateRoleSetting { analysisKeyManager.setRoleModel(AnalysisModelRole.GENERATION, modelId) }
    }

    private fun updateRoleSetting(block: suspend () -> AnalysisRoleModelSetting) {
        scope.launch {
            try {
                val setting = block()
                updateClassifyState {
                    it.copy(
                        classifyProvider = setting.provider,
                        classifyModelId = setting.modelId
                    )
                }
                clearError()
            } catch (error: RuntimeException) {
                showError(error.message ?: "모델을 바꾸지 못했습니다.")
            }
        }
    }

    override fun dismissClassifyCriteriaDialog() {
        if (currentState().classify.isClassifying) return
        updateClassifyState { it.copy(showClassifyCriteriaDialog = false) }
        clearError()
    }

    override fun runClassify() {
        val state = currentState()
        if (!state.canRunClassify) {
            if (state.classify.classifyCriteria.isBlank()) {
                showError("분류 기준을 입력해주세요.")
            }
            return
        }

        val editingText = state.editor.editingText
        val criteria = state.classify.classifyCriteria
        cancelJob()
        classifyJob = scope.launch {
            updateClassifyState {
                it.copy(
                    isClassifying = true,
                    showClassifyCriteriaDialog = false,
                    classifyPreview = null,
                    classifySaveEntries = emptyList(),
                    classifyOverwriteConflicts = emptyList()
                )
            }
            showMessage("분류 중…")
            try {
                val result = classifyWildcardLines.classify(
                    editingText = editingText,
                    criteria = criteria
                )
                val entries = WildcardClassifyFileName.buildSaveEntries(result.savableGroups)
                val dropNote = if (result.droppedLineCount > 0) {
                    " · 미배정 ${result.droppedLineCount}줄(저장 안 함)"
                } else {
                    ""
                }
                updateClassifyState {
                    it.copy(
                        isClassifying = false,
                        classifyPreview = result,
                        classifySaveEntries = entries,
                        classifyOverwriteConflicts = emptyList(),
                        classifyCriteria = result.criteria
                    )
                }
                showMessage("분류 미리보기: ${entries.size}개 파일$dropNote")
            } catch (error: RuntimeException) {
                updateClassifyState {
                    it.copy(
                        isClassifying = false,
                        showClassifyCriteriaDialog = true
                    )
                }
                showError(error.message ?: "분류에 실패했습니다.")
            }
        }
    }

    override fun dismissClassifyPreview() {
        if (currentState().classify.isClassifying) return
        updateClassifyState {
            it.copy(
                classifyPreview = null,
                classifySaveEntries = emptyList(),
                classifyOverwriteConflicts = emptyList()
            )
        }
        clearMessageAndError()
    }

    override fun onClassifyFileNameChange(index: Int, value: String) {
        mutateSaveEntry(index) { it.copy(fileNameInput = value) }
    }

    override fun onToggleClassifyFileNameEdit(index: Int) {
        mutateSaveEntry(index) { it.copy(isEditingFileName = !it.isEditingFileName) }
    }

    private fun mutateSaveEntry(index: Int, transform: (WildcardClassifySaveEntry) -> WildcardClassifySaveEntry) {
        val entries = currentState().classify.classifySaveEntries
        if (index !in entries.indices) return
        val updated = entries.toMutableList().also { it[index] = transform(it[index]) }
        updateClassifyState { it.copy(classifySaveEntries = updated) }
        clearError()
    }

    override fun saveClassifyResult(overwrite: Boolean) {
        val state = currentState()
        val entries = state.classify.classifySaveEntries
        if (entries.isEmpty()) {
            showError("저장할 그룹이 없습니다.")
            return
        }
        if (state.isFileOperationInProgress || state.classify.isClassifying) return
        if (!state.canModifyFiles) {
            showError("파일을 저장하려면 wildcard 폴더를 다시 선택해주세요.")
            return
        }
        if (!beginFileOperation()) return

        scope.launch {
            try {
                when (val result = saveWildcardClassifyResult.save(entries, overwrite = overwrite)) {
                    is WildcardClassifySaveResult.Success -> {
                        onFilesSaved()
                        updateClassifyState {
                            it.copy(
                                classifyPreview = null,
                                classifySaveEntries = emptyList(),
                                classifyOverwriteConflicts = emptyList()
                            )
                        }
                        showMessage("${result.savedFileNames.size}개 파일로 저장했습니다.")
                    }
                    is WildcardClassifySaveResult.FileExists -> {
                        updateClassifyState {
                            it.copy(
                                classifyOverwriteConflicts = result.conflictingFileNames
                            )
                        }
                        showError("같은 이름의 파일이 있습니다. 덮어쓸까요?")
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

    override fun confirmClassifyOverwrite() {
        saveClassifyResult(overwrite = true)
    }

    override fun dismissClassifyOverwrite() {
        updateClassifyState {
            it.copy(classifyOverwriteConflicts = emptyList())
        }
        clearError()
    }
}
