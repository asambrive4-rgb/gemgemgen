package com.example.gemgemgen.analysis.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisReport
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSource
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.usecase.AnalysisException
import com.example.gemgemgen.analysis.usecase.AnalysisWildcardSaveResult
import com.example.gemgemgen.analysis.usecase.AnalyzePromptForCategoryUseCase
import com.example.gemgemgen.analysis.usecase.CopyAnalysisResultsUseCase
import com.example.gemgemgen.analysis.usecase.GenerateAnalysisTxtUseCase
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.analysis.usecase.SaveAnalysisWildcardFileUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnalysisViewModel(
    private val analyzePrompt: AnalyzePromptForCategoryUseCase,
    private val generateTxtUseCase: GenerateAnalysisTxtUseCase,
    private val keyManager: ManageGeminiApiKeysUseCase,
    private val copyResults: CopyAnalysisResultsUseCase,
    private val saveWildcardFile: SaveAnalysisWildcardFileUseCase,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private var runningJob: Job? = null
    private var analysisReport: AnalysisReport? = null

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()
    val sourcePromptTextFieldState = TextFieldState()

    init {
        refreshKeys()
    }

    fun onSourcePromptChange(value: String) {
        _uiState.update {
            if (it.sourcePrompt == value) {
                it
            } else {
                analysisReport = null
                it.copy(
                    sourcePrompt = value,
                    targetSegment = null,
                    generatedCandidates = emptyList(),
                    error = "",
                    message = "",
                    warning = "",
                    status = AnalysisStatus.IDLE
                )
            }
        }
    }

    fun onCategorySelected(category: AnalysisCategory) {
        analysisReport = null
        _uiState.update {
            it.copy(
                selectedCategory = category,
                targetSegment = null,
                generatedCandidates = emptyList(),
                error = "",
                message = "",
                warning = "",
                status = AnalysisStatus.IDLE
            )
        }
    }

    fun applyManualSelection() {
        val source = sourcePromptTextFieldState.text.toString()
        val category = _uiState.value.selectedCategory ?: run {
            showError("카테고리를 먼저 선택해주세요.")
            return
        }
        val selection = sourcePromptTextFieldState.selection
        val start = selection.min.coerceIn(0, source.length)
        val end = selection.max.coerceIn(start, source.length)
        if (start == end) {
            showError("원문에서 바꾸고 싶은 구간을 선택해주세요.")
            return
        }
        val selectedText = source.substring(start, end)
        _uiState.update {
            it.copy(
                sourcePrompt = source,
                targetSegment = AnalysisTargetSegment(
                    text = selectedText,
                    startIndex = start,
                    endIndex = end,
                    source = AnalysisTargetSource.MANUAL,
                    category = category,
                    confidence = 1.0,
                    reason = "사용자가 직접 선택한 구간입니다."
                ),
                generatedCandidates = emptyList(),
                error = "",
                message = "수동 마스킹 구간을 지정했습니다.",
                warning = "",
                status = AnalysisStatus.IDLE
            )
        }
    }

    fun clearTargetSegment() {
        _uiState.update {
            it.copy(
                targetSegment = null,
                generatedCandidates = emptyList(),
                message = "마스킹 구간을 해제했습니다.",
                warning = "",
                error = ""
            )
        }
    }

    fun analyzeAndMask() {
        val snapshot = _uiState.value
        val category = snapshot.selectedCategory ?: run {
            showError("카테고리를 선택해주세요.")
            return
        }
        val source = currentSourcePrompt()
        if (source.isBlank()) {
            showError("원본 프롬프트를 입력해주세요.")
            return
        }
        if (!hasActiveKey()) {
            showError("활성 Gemini API 키를 먼저 선택해주세요.")
            return
        }

        runningJob?.cancel()
        runningJob = scope.launch {
            _uiState.update {
                it.copy(
                    status = AnalysisStatus.ANALYZING,
                    error = "",
                    message = "자동 마스킹 분석 중...",
                    warning = ""
                )
            }
            try {
                val report = analyzePrompt.analyze(source, category)
                val autoTarget = autoTargetFrom(report, category)
                    ?: throw AnalysisException(
                        "자동으로 변주 대상을 찾지 못했습니다. 원문에서 직접 구간을 선택해주세요."
                    )
                analysisReport = report
                _uiState.update {
                    it.copy(
                        sourcePrompt = source,
                        targetSegment = autoTarget,
                        generatedCandidates = emptyList(),
                        status = AnalysisStatus.IDLE,
                        message = "자동 마스킹 구간을 찾았습니다.",
                        warning = report.warnings.firstOrNull().orEmpty()
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: RuntimeException) {
                showError(error.message ?: "분석에 실패했습니다.")
            }
        }
    }

    fun generateTxt() {
        val snapshot = _uiState.value
        val category = snapshot.selectedCategory ?: run {
            showError("카테고리를 선택해주세요.")
            return
        }
        val source = currentSourcePrompt()
        if (source.isBlank()) {
            showError("원본 프롬프트를 입력해주세요.")
            return
        }
        if (!hasActiveKey()) {
            showError("활성 Gemini API 키를 먼저 선택해주세요.")
            return
        }

        runningJob?.cancel()
        runningJob = scope.launch {
            _uiState.update {
                it.copy(
                    status = AnalysisStatus.GENERATING,
                    error = "",
                    message = "TXT 후보 생성 중...",
                    warning = ""
                )
            }
            try {
                val target = ensureTargetSegment(source, category)
                val report = analysisReport ?: analyzePrompt.analyze(source, category)
                    .also { analysisReport = it }
                val selectedHints = _uiState.value.directions
                    .filter { it.id in _uiState.value.selectedDirectionIds }
                    .map { it.hint }
                val result = generateTxtUseCase.generate(
                    sourcePrompt = source,
                    category = category,
                    targetSegment = target,
                    analysisReport = report,
                    count = _uiState.value.txtCount,
                    selectedHints = selectedHints
                )
                _uiState.update {
                    it.copy(
                        sourcePrompt = source,
                        targetSegment = target,
                        generatedCandidates = result.candidates,
                        status = AnalysisStatus.SUCCESS,
                        message = "${result.candidates.size}개 후보를 생성했습니다.",
                        warning = result.warning,
                        error = ""
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: RuntimeException) {
                showError(error.message ?: "TXT 생성에 실패했습니다.")
            }
        }
    }

    fun cancelActiveWork() {
        runningJob?.cancel()
        runningJob = null
        _uiState.update {
            it.copy(
                status = AnalysisStatus.IDLE,
                message = "작업을 중지했습니다."
            )
        }
    }

    fun onTxtCountChange(value: Int) {
        _uiState.update {
            it.copy(txtCount = AnalysisTxtCountPolicy.coerce(value))
        }
    }

    fun toggleDirection(id: String) {
        _uiState.update { state ->
            val nextIds = if (id in state.selectedDirectionIds) {
                state.selectedDirectionIds - id
            } else {
                state.selectedDirectionIds + id
            }
            state.copy(selectedDirectionIds = nextIds)
        }
    }

    fun onResultFileNameChange(value: String) {
        _uiState.update { it.copy(resultFileName = value, error = "", message = "") }
    }

    fun copyGeneratedResults() {
        val candidates = _uiState.value.generatedCandidates
        if (candidates.isEmpty()) return
        scope.launch {
            try {
                copyResults.copy(candidates)
                _uiState.update { it.copy(message = "생성 결과를 복사했습니다.", error = "") }
            } catch (error: RuntimeException) {
                showError(error.message ?: "복사에 실패했습니다.")
            }
        }
    }

    fun saveGeneratedResults(overwrite: Boolean = false) {
        val state = _uiState.value
        val candidates = state.generatedCandidates
        if (candidates.isEmpty()) return
        scope.launch {
            try {
                when (
                    val result = saveWildcardFile.save(
                        fileNameInput = state.resultFileName,
                        candidates = candidates,
                        overwrite = overwrite
                    )
                ) {
                    AnalysisWildcardSaveResult.InvalidFileName ->
                        showError("저장할 파일명을 입력해주세요.")
                    is AnalysisWildcardSaveResult.FileExists ->
                        _uiState.update {
                            it.copy(
                                pendingOverwriteFileName = result.fileName,
                                error = "",
                                message = "같은 이름의 파일이 있습니다."
                            )
                        }
                    is AnalysisWildcardSaveResult.Success ->
                        _uiState.update {
                            it.copy(
                                pendingOverwriteFileName = null,
                                message = "${result.fileName} 파일로 저장했습니다.",
                                error = ""
                            )
                        }
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "와일드카드 파일 저장에 실패했습니다.")
            }
        }
    }

    fun confirmOverwrite() {
        saveGeneratedResults(overwrite = true)
    }

    fun dismissOverwrite() {
        _uiState.update { it.copy(pendingOverwriteFileName = null) }
    }

    fun showKeyDialog() {
        _uiState.update { it.copy(showKeyDialog = true, error = "", message = "") }
        refreshKeys()
    }

    fun dismissKeyDialog() {
        _uiState.update {
            it.copy(
                showKeyDialog = false,
                keyLabelInput = "",
                keyValueInput = ""
            )
        }
    }

    fun onKeyLabelChange(value: String) {
        _uiState.update { it.copy(keyLabelInput = value) }
    }

    fun onKeyValueChange(value: String) {
        _uiState.update { it.copy(keyValueInput = value) }
    }

    fun addApiKey() {
        val state = _uiState.value
        scope.launch {
            try {
                val keys = keyManager.addKey(
                    label = state.keyLabelInput,
                    rawKey = state.keyValueInput
                )
                _uiState.update {
                    it.copy(
                        apiKeys = keys,
                        keyLabelInput = "",
                        keyValueInput = "",
                        message = "API 키를 추가했습니다.",
                        error = ""
                    )
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "API 키 추가에 실패했습니다.")
            }
        }
    }

    fun deleteApiKey(id: String) {
        scope.launch {
            try {
                val keys = keyManager.deleteKey(id)
                _uiState.update {
                    it.copy(apiKeys = keys, message = "API 키를 삭제했습니다.", error = "")
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "API 키 삭제에 실패했습니다.")
            }
        }
    }

    fun activateApiKey(id: String) {
        scope.launch {
            try {
                val keys = keyManager.activateKey(id)
                _uiState.update {
                    it.copy(apiKeys = keys, message = "활성 API 키를 선택했습니다.", error = "")
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "API 키 선택에 실패했습니다.")
            }
        }
    }

    private fun refreshKeys() {
        scope.launch {
            val keys = keyManager.listKeys()
            _uiState.update { it.copy(apiKeys = keys) }
        }
    }

    private suspend fun ensureTargetSegment(
        source: String,
        category: AnalysisCategory
    ): AnalysisTargetSegment {
        val existingTarget = _uiState.value.targetSegment
        if (existingTarget?.source == AnalysisTargetSource.MANUAL && existingTarget.isValid) {
            if (analysisReport == null) {
                analysisReport = analyzePrompt.analyze(source, category)
            }
            return existingTarget
        }
        val report = analysisReport ?: analyzePrompt.analyze(source, category)
            .also { analysisReport = it }
        val autoTarget = autoTargetFrom(report, category)
            ?: throw AnalysisException(
                "자동으로 변주 대상을 찾지 못했습니다. 원문에서 직접 구간을 선택해주세요."
            )
        _uiState.update {
            it.copy(
                targetSegment = autoTarget,
                warning = report.warnings.firstOrNull().orEmpty()
            )
        }
        return autoTarget
    }

    private fun autoTargetFrom(
        report: AnalysisReport,
        category: AnalysisCategory
    ): AnalysisTargetSegment? {
        val detected = report.targetSegment?.takeIf { it.isValid } ?: return null
        return AnalysisTargetSegment(
            text = detected.exactText,
            startIndex = detected.startIndex,
            endIndex = detected.endIndex,
            source = AnalysisTargetSource.AUTO,
            category = category,
            confidence = detected.confidence,
            reason = detected.reason
        )
    }

    private fun currentSourcePrompt(): String {
        val text = sourcePromptTextFieldState.text.toString()
        if (text != _uiState.value.sourcePrompt) {
            _uiState.update { it.copy(sourcePrompt = text) }
        }
        return text
    }

    private fun hasActiveKey(): Boolean {
        return _uiState.value.apiKeys.any { it.isActive }
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                status = AnalysisStatus.ERROR,
                error = message,
                message = "",
                pendingOverwriteFileName = null
            )
        }
    }
}
