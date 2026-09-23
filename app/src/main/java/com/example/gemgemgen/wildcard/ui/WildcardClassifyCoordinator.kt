// 역할: AI 단어 분류 기능의 실행, 결과 검토 및 저장 단계를 조율합니다.
package com.example.gemgemgen.wildcard.ui

import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.MODEL_GROK_4_5
import com.example.gemgemgen.analysis.usecase.AnalysisException
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.wildcard.domain.WildcardClassifyFileName
import com.example.gemgemgen.wildcard.domain.WildcardClassifyPolicy
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResult
import com.example.gemgemgen.wildcard.domain.WildcardClassifySaveEntry
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.ClassifyWildcardLinesUseCase
import com.example.gemgemgen.wildcard.usecase.SaveWildcardClassifyResultUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardClassifySaveResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        WildcardClassifyPolicy.canRunClassify(
            criteria = classifyCriteria,
            isClassifying = isClassifying,
            isFileOperationInProgress = isFileOperationInProgress,
            showCriteriaDialog = showClassifyCriteriaDialog,
            hasPreview = classifyPreview != null
        )

    fun canSaveClassifyResult(canModifyFiles: Boolean, isFileOperationInProgress: Boolean): Boolean =
        WildcardClassifyPolicy.canSaveClassifyResult(
            hasPreview = classifyPreview != null,
            saveEntries = classifySaveEntries,
            canModifyFiles = canModifyFiles,
            isClassifying = isClassifying,
            isFileOperationInProgress = isFileOperationInProgress,
            hasOverwriteConflicts = classifyOverwriteConflicts.isNotEmpty()
        )
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
    private val classifyWildcardLines: ClassifyWildcardLinesUseCase? = null,
    private val saveWildcardClassifyResult: SaveWildcardClassifyResultUseCase? = null,
    private val analysisKeyManager: ManageGeminiApiKeysUseCase? = null,
    private val scope: CoroutineScope,
    private val host: Host
) : WildcardClassifyActions {
    interface Host {
        val selectedFile: WildcardTextFile?
        val selectableLines: List<String>
        val editingText: String
        val canModifyFiles: Boolean
        val isFileOperationInProgress: Boolean
        val canRequestClassify: Boolean

        fun onLineSelectionCleared()
        fun showMessage(message: String)
        fun showError(error: String)
        fun clearError()
        fun clearMessageAndError()
        fun updateClassifyState(transform: (WildcardClassifyUiState) -> WildcardClassifyUiState)
        fun beginFileOperation(): Boolean
        fun endFileOperation()
        suspend fun onFilesSaved()
    }

    private var classifyJob: Job? = null
    private val _classifyUiState = MutableStateFlow(WildcardClassifyUiState())
    val classifyUiState: StateFlow<WildcardClassifyUiState> = _classifyUiState.asStateFlow()

    private fun updateState(transform: (WildcardClassifyUiState) -> WildcardClassifyUiState) {
        val next = transform(_classifyUiState.value)
        _classifyUiState.value = next
        host.updateClassifyState { next }
    }

    fun cancelJob() {
        classifyJob?.cancel()
        classifyJob = null
    }

    fun reset() {
        cancelJob()
        updateState { WildcardClassifyUiState() }
    }

    override fun requestClassify() {
        val classify = classifyWildcardLines
        if (!host.canRequestClassify || classify == null) {
            when {
                host.selectedFile == null -> host.showError("먼저 txt 파일을 선택해주세요.")
                host.selectableLines.isEmpty() -> host.showError("분류할 줄이 없습니다.")
                !host.canModifyFiles -> host.showError("파일을 저장하려면 wildcard 폴더를 다시 선택해주세요.")
                else -> host.showError("분류 기능을 사용할 수 없습니다.")
            }
            return
        }

        scope.launch {
            val generationSetting = analysisKeyManager?.getRoleSetting(AnalysisModelRole.GENERATION)
            host.onLineSelectionCleared()
            updateState {
                it.copy(
                    showClassifyCriteriaDialog = true,
                    classifyCriteria = it.classifyCriteria,
                    classifyPreview = null,
                    classifySaveEntries = emptyList(),
                    classifyOverwriteConflicts = emptyList(),
                    classifyProvider = generationSetting?.provider ?: it.classifyProvider,
                    classifyModelId = generationSetting?.modelId ?: it.classifyModelId
                )
            }
            host.clearMessageAndError()
        }
    }

    override fun onClassifyCriteriaChange(value: String) {
        updateState { it.copy(classifyCriteria = value) }
        host.clearError()
    }

    override fun onClassifyProviderSelected(provider: AnalysisProvider) {
        val keyManager = analysisKeyManager ?: run {
            updateState {
                it.copy(
                    classifyProvider = provider,
                    classifyModelId = AnalysisProvider.defaultModel(provider)
                )
            }
            host.clearError()
            return
        }
        updateRoleSetting { keyManager.setRoleProvider(AnalysisModelRole.GENERATION, provider) }
    }

    override fun onClassifyModelSelected(modelId: String) {
        val keyManager = analysisKeyManager ?: run {
            updateState { it.copy(classifyModelId = modelId) }
            host.clearError()
            return
        }
        updateRoleSetting { keyManager.setRoleModel(AnalysisModelRole.GENERATION, modelId) }
    }

    private fun updateRoleSetting(block: suspend () -> com.example.gemgemgen.analysis.usecase.AnalysisRoleModelSetting) {
        scope.launch {
            try {
                val setting = block()
                updateState {
                    it.copy(
                        classifyProvider = setting.provider,
                        classifyModelId = setting.modelId
                    )
                }
                host.clearError()
            } catch (error: RuntimeException) {
                host.showError(error.message ?: "모델을 바꾸지 못했습니다.")
            }
        }
    }

    override fun dismissClassifyCriteriaDialog() {
        if (_classifyUiState.value.isClassifying) return
        updateState { it.copy(showClassifyCriteriaDialog = false) }
        host.clearError()
    }

    override fun runClassify() {
        val classify = classifyWildcardLines ?: run {
            host.showError("분류 기능을 사용할 수 없습니다.")
            return
        }
        val currentState = _classifyUiState.value
        if (!currentState.canRunClassify(host.isFileOperationInProgress)) {
            if (currentState.classifyCriteria.isBlank()) {
                host.showError("분류 기준을 입력해주세요.")
            }
            return
        }

        val editingText = host.editingText
        val criteria = currentState.classifyCriteria
        cancelJob()
        classifyJob = scope.launch {
            updateState {
                it.copy(
                    isClassifying = true,
                    showClassifyCriteriaDialog = false,
                    classifyPreview = null,
                    classifySaveEntries = emptyList(),
                    classifyOverwriteConflicts = emptyList()
                )
            }
            host.showMessage("분류 중…")
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
                updateState {
                    it.copy(
                        isClassifying = false,
                        classifyPreview = result,
                        classifySaveEntries = entries,
                        classifyOverwriteConflicts = emptyList(),
                        classifyCriteria = result.criteria
                    )
                }
                host.showMessage("분류 미리보기: ${entries.size}개 파일$dropNote")
            } catch (error: RuntimeException) {
                updateState {
                    it.copy(
                        isClassifying = false,
                        showClassifyCriteriaDialog = true
                    )
                }
                host.showError(error.message ?: "분류에 실패했습니다.")
            }
        }
    }

    override fun dismissClassifyPreview() {
        if (_classifyUiState.value.isClassifying) return
        updateState {
            it.copy(
                classifyPreview = null,
                classifySaveEntries = emptyList(),
                classifyOverwriteConflicts = emptyList()
            )
        }
        host.clearMessageAndError()
    }

    override fun onClassifyFileNameChange(index: Int, value: String) {
        mutateSaveEntry(index) { it.copy(fileNameInput = value) }
    }

    override fun onToggleClassifyFileNameEdit(index: Int) {
        mutateSaveEntry(index) { it.copy(isEditingFileName = !it.isEditingFileName) }
    }

    private fun mutateSaveEntry(index: Int, transform: (WildcardClassifySaveEntry) -> WildcardClassifySaveEntry) {
        val entries = _classifyUiState.value.classifySaveEntries
        if (index !in entries.indices) return
        val updated = entries.toMutableList().also { it[index] = transform(it[index]) }
        updateState { it.copy(classifySaveEntries = updated) }
        host.clearError()
    }

    override fun saveClassifyResult(overwrite: Boolean) {
        val saveUseCase = saveWildcardClassifyResult ?: run {
            host.showError("분류 저장 기능을 사용할 수 없습니다.")
            return
        }
        val entries = _classifyUiState.value.classifySaveEntries
        if (entries.isEmpty()) {
            host.showError("저장할 그룹이 없습니다.")
            return
        }
        if (host.isFileOperationInProgress || _classifyUiState.value.isClassifying) return
        if (!host.canModifyFiles) {
            host.showError("파일을 저장하려면 wildcard 폴더를 다시 선택해주세요.")
            return
        }
        if (!host.beginFileOperation()) return

        scope.launch {
            try {
                when (val result = saveUseCase.save(entries, overwrite = overwrite)) {
                    is WildcardClassifySaveResult.Success -> {
                        host.onFilesSaved()
                        updateState {
                            it.copy(
                                classifyPreview = null,
                                classifySaveEntries = emptyList(),
                                classifyOverwriteConflicts = emptyList()
                            )
                        }
                        host.showMessage("${result.savedFileNames.size}개 파일로 저장했습니다.")
                    }
                    is WildcardClassifySaveResult.FileExists -> {
                        updateState {
                            it.copy(
                                classifyOverwriteConflicts = result.conflictingFileNames
                            )
                        }
                        host.showError("같은 이름의 파일이 있습니다. 덮어쓸까요?")
                    }
                    WildcardClassifySaveResult.NothingToSave -> {
                        host.showError("저장할 그룹이 없습니다.")
                    }
                    is WildcardClassifySaveResult.InvalidFileName -> {
                        host.showError("파일 이름이 올바르지 않습니다: ${result.groupName}")
                    }
                }
            } catch (error: RuntimeException) {
                host.showError(error.message ?: "분류 결과를 저장하지 못했습니다.")
            } finally {
                host.endFileOperation()
            }
        }
    }

    override fun confirmClassifyOverwrite() {
        saveClassifyResult(overwrite = true)
    }

    override fun dismissClassifyOverwrite() {
        updateState {
            it.copy(classifyOverwriteConflicts = emptyList())
        }
        host.clearError()
    }
}
