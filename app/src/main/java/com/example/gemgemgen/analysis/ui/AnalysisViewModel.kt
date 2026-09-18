// 역할: AI 프롬프트 분석 화면 상태를 관리하고 하위 UseCase를 통해 생성 및 세션을 조율합니다.
package com.example.gemgemgen.analysis.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisGenerationCountPolicy
import com.example.gemgemgen.analysis.domain.AnalysisMaskingPolicy
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisResultPresentation
import com.example.gemgemgen.analysis.domain.AnalysisStartPolicy
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegmentPolicy
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.usecase.AnalysisGenerationStep
import com.example.gemgemgen.analysis.usecase.AnalysisReportCache
import com.example.gemgemgen.analysis.usecase.AnalysisSaveAndReplaceResult
import com.example.gemgemgen.analysis.usecase.ApplyCandidateResult
import com.example.gemgemgen.analysis.usecase.CopyAnalysisResultsUseCase
import com.example.gemgemgen.analysis.usecase.ExecuteAnalysisGenerationRequest
import com.example.gemgemgen.analysis.usecase.ExecuteAnalysisGenerationResult
import com.example.gemgemgen.analysis.usecase.ExecuteAnalysisGenerationUseCase
import com.example.gemgemgen.analysis.usecase.GenerateAnalysisTxtUseCase
import com.example.gemgemgen.analysis.usecase.GrokDeviceLoginChallenge
import com.example.gemgemgen.analysis.usecase.ManageCandidateHandoffUseCase
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.analysis.usecase.ManageGrokAuthUseCase
import com.example.gemgemgen.analysis.usecase.ResolveAnalysisTargetUseCase
import com.example.gemgemgen.analysis.usecase.RestorePromptResult
import com.example.gemgemgen.analysis.usecase.SaveAnalysisWildcardFileUseCase
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.PromptWorkspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnalysisViewModel(
    private val resolveTarget: ResolveAnalysisTargetUseCase,
    private val generateTxtUseCase: GenerateAnalysisTxtUseCase,
    private val keyManager: ManageGeminiApiKeysUseCase,
    private val grokAuth: ManageGrokAuthUseCase,
    private val copyResults: CopyAnalysisResultsUseCase,
    private val saveWildcardFile: SaveAnalysisWildcardFileUseCase,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    private val promptWorkspace: PromptWorkspace? = null,
    coroutineScope: CoroutineScope? = null,
    private val executeGeneration: ExecuteAnalysisGenerationUseCase = ExecuteAnalysisGenerationUseCase(
        resolveTarget = resolveTarget,
        generateTxtUseCase = generateTxtUseCase,
        keyManager = keyManager,
        dispatchers = dispatchers
    ),
    private val candidateHandoff: ManageCandidateHandoffUseCase = ManageCandidateHandoffUseCase(
        copyResults = copyResults,
        promptWorkspace = promptWorkspace,
        dispatchers = dispatchers
    )
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private var runningJob: Job? = null
    private var grokLoginJob: Job? = null
    private var pendingGrokChallenge: GrokDeviceLoginChallenge? = null
    private var analysisCache: AnalysisReportCache? = null

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()
    val sourcePromptTextFieldState = TextFieldState()

    init {
        refreshKeys()
        refreshRoleSettings()
        refreshGrokStatus()
    }

    fun onSourcePromptChange(value: String) {
        val state = _uiState.value
        if (state.sourcePrompt == value) return

        // 분석 캐시는 항상 무효화. 실제 원문은 TextField + currentSourcePrompt() 가 기준.
        analysisCache = null

        val nextSegment = state.targetSegment?.takeIf {
            AnalysisTargetSegmentPolicy.isStillValid(value, it)
        }
        val shouldClearCandidates = state.generatedCandidates.isNotEmpty()
        val nextNeedsMasking = computeNeedsMaskingAnalysis(
            source = value,
            category = state.selectedCategory,
            targetSegment = nextSegment,
            cache = null,
            state = state
        )

        // 핫패스: canGenerate 경계·구간 무효·결과 정리가 없으면 화면 state 방출 생략
        val needsUiUpdate = (state.sourcePrompt.isBlank() != value.isBlank()) ||
            (state.needsMaskingAnalysis != nextNeedsMasking) ||
            (nextSegment != state.targetSegment) ||
            shouldClearCandidates
        if (!needsUiUpdate) return

        applyPromptStateUpdate(
            value = value,
            segment = state.targetSegment,
            nextSegment = nextSegment,
            nextNeedsMasking = nextNeedsMasking,
            clearCandidates = shouldClearCandidates
        )
    }

    /**
     * 자동화 탭에 입력된 원본 프롬프트로 분석 원문을 통째로 교체한다.
     * 비어 있으면 원문은 유지하고 안내만 표시한다.
     */
    fun importSourcePromptFromAutomation(text: String = promptWorkspace?.currentPrompt?.value.orEmpty()) {
        if (text.isBlank()) {
            showError("자동화에 입력된 텍스트가 없습니다.")
            return
        }
        replaceSourcePrompt(text)
    }

    private fun replaceSourcePrompt(value: String) {
        if (sourcePromptTextFieldState.text.toString() != value) {
            sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(value)
        }

        analysisCache = null
        val state = _uiState.value
        val hasNoPendingState = state.sourcePrompt == value &&
            state.targetSegment == null &&
            state.generatedCandidates.isEmpty() &&
            state.error.isEmpty() &&
            state.warning.isEmpty()
        if (hasNoPendingState) return

        val nextSegment = state.targetSegment?.takeIf {
            AnalysisTargetSegmentPolicy.isStillValid(value, it)
        }
        val clearCandidates = state.generatedCandidates.isNotEmpty()
        val nextNeedsMasking = computeNeedsMaskingAnalysis(
            source = value,
            category = state.selectedCategory,
            targetSegment = nextSegment,
            cache = null,
            state = state
        )

        applyPromptStateUpdate(
            value = value,
            segment = state.targetSegment,
            nextSegment = nextSegment,
            nextNeedsMasking = nextNeedsMasking,
            clearCandidates = clearCandidates
        )
    }

    private fun applyPromptStateUpdate(
        value: String,
        segment: AnalysisTargetSegment?,
        nextSegment: AnalysisTargetSegment?,
        nextNeedsMasking: Boolean,
        clearCandidates: Boolean
    ) {
        _uiState.update { state ->
            state.copy(
                sourcePrompt = value,
                targetSegment = nextSegment,
                needsMaskingAnalysis = nextNeedsMasking,
                generatedCandidates = if (clearCandidates) emptyList() else state.generatedCandidates,
                resultPresentation = if (clearCandidates) AnalysisResultPresentation.NONE else state.resultPresentation,
                selectedCandidateIndex = state.selectedCandidateIndex.takeUnless { clearCandidates },
                error = "",
                message = if (nextSegment != segment) "" else state.message,
                warning = "",
                status = if (state.status == AnalysisStatus.ERROR) AnalysisStatus.IDLE else state.status
            )
        }
    }

    fun onCategorySelected(category: AnalysisCategory) {
        analysisCache = null
        val nextNeedsMasking = computeNeedsMaskingAnalysis(
            category = category,
            targetSegment = null,
            cache = null
        )
        _uiState.update {
            it.copy(
                selectedCategory = category,
                targetSegment = null,
                needsMaskingAnalysis = nextNeedsMasking,
                generatedCandidates = emptyList(),
                resultPresentation = AnalysisResultPresentation.NONE,
                selectedCandidateIndex = null,
                error = "",
                message = "",
                warning = "",
                status = AnalysisStatus.IDLE
            )
        }
    }

    fun clearTargetSegment() {
        analysisCache = null
        val nextNeedsMasking = computeNeedsMaskingAnalysis(
            targetSegment = null,
            cache = null
        )
        _uiState.update {
            it.copy(
                targetSegment = null,
                needsMaskingAnalysis = nextNeedsMasking,
                generatedCandidates = emptyList(),
                resultPresentation = AnalysisResultPresentation.NONE,
                selectedCandidateIndex = null,
                message = "마스킹 구간을 해제했습니다.",
                warning = "",
                error = ""
            )
        }
    }

    /** 「생성」모드: 고정 개수 후보를 카드로 보여 준다. */
    fun generate() {
        startGeneration(
            count = AnalysisGenerationCountPolicy.FIXED_COUNT,
            presentation = AnalysisResultPresentation.CARDS,
            generatingMessage = "후보 생성 중...",
            failureFallback = "생성에 실패했습니다.",
            updateResultFileName = false
        )
    }

    /** 「TXT 생성」모드: 슬라이더 개수 후보를 목록으로 보여 주고 파일 저장에 쓴다. */
    fun generateTxt() {
        startGeneration(
            count = AnalysisTxtCountPolicy.coerce(_uiState.value.txtCount),
            presentation = AnalysisResultPresentation.TXT,
            generatingMessage = "프롬프트 목록 생성 중...",
            failureFallback = "TXT 생성에 실패했습니다.",
            updateResultFileName = true
        )
    }

    private fun startGeneration(
        count: Int,
        presentation: AnalysisResultPresentation,
        generatingMessage: String,
        failureFallback: String,
        updateResultFileName: Boolean
    ) {
        val snapshot = _uiState.value
        val source = currentSourcePrompt()
        val directionInput = currentDirectionInput(snapshot)

        val request = ExecuteAnalysisGenerationRequest(
            sourcePrompt = source,
            category = snapshot.selectedCategory,
            targetSegment = snapshot.targetSegment,
            cache = analysisCache,
            count = count,
            selectedHints = directionInput.selectedHints,
            customHint = directionInput.customHint,
            maskingProvider = snapshot.maskingProvider,
            hasMaskingCredential = snapshot.hasMaskingCredential,
            maskingModel = snapshot.maskingModel,
            generationProvider = snapshot.generationProvider,
            hasGenerationCredential = snapshot.hasGenerationCredential,
            generationModel = snapshot.generationModel,
            failureFallback = failureFallback
        )

        runningJob?.cancel()
        runningJob = scope.launch {
            runningJob = coroutineContext[Job]
            try {
                val result = executeGeneration.execute(
                    request = request,
                    onStep = { step ->
                        _uiState.update {
                            it.copy(
                                status = AnalysisStatus.GENERATING,
                                error = "",
                                message = if (step == AnalysisGenerationStep.MASKING) "자동 마스킹 중..." else generatingMessage,
                                warning = ""
                            )
                        }
                    },
                    onTargetChanged = { newTarget, warning ->
                        _uiState.update {
                            it.copy(
                                targetSegment = newTarget,
                                generatedCandidates = emptyList(),
                                resultPresentation = AnalysisResultPresentation.NONE,
                                selectedCandidateIndex = null,
                                warning = warning
                            )
                        }
                    }
                )
                when (result) {
                    is ExecuteAnalysisGenerationResult.Blocked -> {
                        showError(AnalysisUiText.startBlockedMessage(result.reason))
                    }
                    is ExecuteAnalysisGenerationResult.Failure -> {
                        analysisCache = null
                        showError(result.message)
                    }
                    is ExecuteAnalysisGenerationResult.Success -> {
                        analysisCache = result.cache
                        val category = checkNotNull(snapshot.selectedCategory)
                        _uiState.update {
                            it.copy(
                                sourcePrompt = source,
                                targetSegment = result.targetSegment,
                                needsMaskingAnalysis = false,
                                generatedCandidates = result.candidates,
                                resultPresentation = if (result.candidates.isEmpty()) {
                                    AnalysisResultPresentation.NONE
                                } else {
                                    presentation
                                },
                                selectedCandidateIndex = candidateHandoff.currentSession
                                    ?.appliedCandidate
                                    ?.let(result.candidates::indexOf)
                                    ?.takeIf { idx -> idx >= 0 },
                                // TXT 생성 완료 시 카테고리명(공백 제거)으로 저장 파일명 기본값 지정.
                                resultFileName = if (updateResultFileName) {
                                    category.defaultWildcardSaveFileName()
                                } else {
                                    it.resultFileName
                                },
                                status = AnalysisStatus.SUCCESS,
                                message = "${result.candidates.size}개 후보를 생성했습니다.",
                                warning = result.warning,
                                error = ""
                            )
                        }
                        val usedGrok = snapshot.generationProvider == AnalysisProvider.GROK ||
                            (result.didAnalyze && snapshot.maskingProvider == AnalysisProvider.GROK) ||
                            _uiState.value.usesGrok
                        if (usedGrok) {
                            refreshGrokQuotaIfLoggedIn()
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                analysisCache = null
                showError(error.message ?: failureFallback)
            } finally {
                if (runningJob === coroutineContext[Job]) {
                    runningJob = null
                }
            }
        }
    }

    /**
     * 「생성」카드 탭: 후보를 복사하고 자동화 프롬프트의 대상 구간에 반영한다.
     * 분석 원문은 다음 후보를 비교할 기준이므로 변경하지 않는다.
     */
    fun applyCandidate(
        index: Int,
        applyToAutomation: ((
            expectedSegment: String,
            replacement: String,
            preferredStartIndex: Int
        ) -> Int?)? = null
    ) {
        val state = _uiState.value
        if (state.resultPresentation != AnalysisResultPresentation.CARDS || state.isBusy) return
        val candidate = state.generatedCandidates.getOrNull(index) ?: return
        val source = sourcePromptTextFieldState.text.toString()

        scope.launch {
            when (val result = candidateHandoff.applyCandidate(
                candidate = candidate,
                sourcePrompt = source,
                targetSegment = state.targetSegment,
                applyToAutomation = applyToAutomation
            )) {
                is ApplyCandidateResult.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedCandidateIndex = index,
                            hasAppliedCandidateToAutomation = true,
                            message = "후보를 복사하고 자동화 프롬프트에 반영했습니다.",
                            error = "",
                            warning = ""
                        )
                    }
                }
                is ApplyCandidateResult.SegmentMissing -> showError(result.message)
                is ApplyCandidateResult.SegmentInvalid -> showError(result.message)
                is ApplyCandidateResult.SourceMismatch -> showError(result.message)
                is ApplyCandidateResult.ReplacementFailed -> showError(result.message)
                is ApplyCandidateResult.Failure -> showError(result.message)
            }
        }
    }

    /** 오른쪽 복사 버튼: 자동화 프롬프트를 바꾸지 않고 선택한 후보만 복사한다. */
    fun copyCandidate(index: Int) {
        val state = _uiState.value
        if (state.resultPresentation != AnalysisResultPresentation.CARDS || state.isBusy) return
        val candidate = state.generatedCandidates.getOrNull(index) ?: return

        scope.launch {
            try {
                copyResults.copyText(candidate)
                _uiState.update {
                    it.copy(
                        message = "${index + 1}번 후보를 복사했습니다.",
                        error = ""
                    )
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "후보 복사에 실패했습니다.")
            }
        }
    }

    /** 자동화 프롬프트에 마지막으로 적용한 후보를 최초 분석 원문으로 복원한다. */
    fun restoreOriginalPrompt(
        restoreInAutomation: ((
            expectedSegment: String,
            originalSegment: String,
            preferredStartIndex: Int
        ) -> Int?)? = null
    ) {
        if (_uiState.value.isBusy) return
        when (val result = candidateHandoff.restoreOriginalPrompt(restoreInAutomation)) {
            is RestorePromptResult.Success -> {
                _uiState.update {
                    it.copy(
                        selectedCandidateIndex = null,
                        hasAppliedCandidateToAutomation = false,
                        message = "자동화 프롬프트를 원본으로 되돌렸습니다.",
                        error = "",
                        warning = ""
                    )
                }
            }
            is RestorePromptResult.NoSession -> Unit
            is RestorePromptResult.ReplacementFailed -> showError(result.message)
            is RestorePromptResult.Failure -> showError(result.message)
        }
    }

    private fun computeNeedsMaskingAnalysis(
        source: String = currentSourcePrompt(),
        category: AnalysisCategory? = _uiState.value.selectedCategory,
        targetSegment: AnalysisTargetSegment? = _uiState.value.targetSegment,
        cache: AnalysisReportCache? = analysisCache,
        state: AnalysisUiState = _uiState.value
    ): Boolean {
        if (category == null) return false
        val directionInput = currentDirectionInput(state)
        return AnalysisMaskingPolicy.shouldAnalyzeMasking(
            source = source,
            category = category,
            targetSegment = targetSegment,
            cache = cache,
            selectedHints = directionInput.selectedHints,
            customHint = directionInput.customHint
        )
    }

    private data class DirectionInput(
        val selectedHints: List<String>,
        val customHint: String
    )

    private fun currentDirectionInput(
        state: AnalysisUiState = _uiState.value
    ): DirectionInput = DirectionInput(
        selectedHints = state.directions
            .filter { it.id in state.selectedDirectionIds }
            .map { it.hint },
        customHint = state.customHint.trim()
    )

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

    fun requestResetSession() {
        if (!_uiState.value.canResetSession) return
        _uiState.update { it.copy(showResetConfirmation = true) }
    }

    fun dismissResetSession() {
        _uiState.update { it.copy(showResetConfirmation = false) }
    }

    /** 계정·모델 설정과 자동화 프롬프트는 보존하고 현재 분석 작업만 초기화한다. */
    fun confirmResetSession() {
        runningJob?.cancel()
        runningJob = null
        analysisCache = null
        candidateHandoff.clearSession()
        sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd("")

        _uiState.update {
            it.copy(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                targetSegment = null,
                status = AnalysisStatus.IDLE,
                error = "",
                message = "",
                warning = "",
                txtCount = AnalysisTxtCountPolicy.DEFAULT_COUNT,
                selectedDirectionIds = emptySet(),
                customHint = "",
                generatedCandidates = emptyList(),
                resultPresentation = AnalysisResultPresentation.NONE,
                selectedCandidateIndex = null,
                hasAppliedCandidateToAutomation = false,
                resultFileName = DEFAULT_ANALYSIS_RESULT_FILE_NAME,
                pendingOverwriteFileName = null,
                showResetConfirmation = false,
                needsMaskingAnalysis = true
            )
        }
    }

    /**
     * 다른 탭으로 이동하거나 자동화를 시작하는 등 탭이 비활성화될 때 호출.
     * 진행 중인 AI 생성 작업(자동 마스킹 및 후보 생성), 결과, 원문, 설정, 타겟 구간은 모두 유지하고,
     * 탭 밖에서 방치될 수 있는 일시적 대화상자(덮어쓰기 확인, 세션 초기화 확인)만 닫는다.
     */
    fun trimForInactiveTab() {
        _uiState.update { state ->
            if (state.pendingOverwriteFileName == null && !state.showResetConfirmation) {
                return@update state
            }
            state.copy(
                pendingOverwriteFileName = null,
                showResetConfirmation = false
            )
        }
    }

    fun onTxtCountChange(value: Int) {
        _uiState.update {
            it.copy(txtCount = AnalysisTxtCountPolicy.coerce(value))
        }
    }

    fun onCustomHintChange(value: String) {
        if (value.length > 100) return
        _uiState.update { state ->
            val nextState = state.copy(customHint = value)
            nextState.copy(
                needsMaskingAnalysis = computeNeedsMaskingAnalysis(state = nextState)
            )
        }
    }

    fun toggleDirection(id: String) {
        _uiState.update { state ->
            val nextIds = if (id in state.selectedDirectionIds) {
                state.selectedDirectionIds - id
            } else {
                state.selectedDirectionIds + id
            }
            val nextState = state.copy(selectedDirectionIds = nextIds)
            nextState.copy(
                needsMaskingAnalysis = computeNeedsMaskingAnalysis(state = nextState)
            )
        }
    }

    fun onResultFileNameChange(value: String) {
        _uiState.update { it.copy(resultFileName = value, error = "", message = "") }
    }

    fun copyGeneratedResults() {
        val state = _uiState.value
        if (state.resultPresentation != AnalysisResultPresentation.TXT || state.generatedCandidates.isEmpty()) return
        scope.launch {
            try {
                copyResults.copy(state.generatedCandidates)
                _uiState.update { it.copy(message = "생성 결과를 복사했습니다.", error = "") }
            } catch (error: RuntimeException) {
                showError(error.message ?: "복사에 실패했습니다.")
            }
        }
    }

    /**
     * 와일드카드 파일 저장 후, 치환된 원문([replacedSource])을 호출측에 넘긴다.
     * 호출측(호스트)에서 자동화 템플릿 반영·탭 이동을 처리한다.
     */
    fun saveGeneratedResults(
        overwrite: Boolean = false,
        onSuccess: ((replacedSource: String) -> Unit)? = null
    ) {
        val state = _uiState.value
        if (state.resultPresentation != AnalysisResultPresentation.TXT) return
        if (state.generatedCandidates.isEmpty()) return

        val candidates = state.generatedCandidates
        val fileNameInput = state.resultFileName
        val sourcePrompt = sourcePromptTextFieldState.text.toString()
        val targetSegment = state.targetSegment

        scope.launch {
            try {
                when (
                    val result = saveWildcardFile.saveAndPrepareReplacedSource(
                        fileNameInput = fileNameInput,
                        candidates = candidates,
                        overwrite = overwrite,
                        sourcePrompt = sourcePrompt,
                        targetSegment = targetSegment
                    )
                ) {
                    AnalysisSaveAndReplaceResult.InvalidFileName ->
                        showError("저장할 파일명을 입력해주세요.")

                    is AnalysisSaveAndReplaceResult.FileExists ->
                        _uiState.update {
                            it.copy(
                                pendingOverwriteFileName = result.fileName,
                                error = "",
                                message = "같은 이름의 파일이 있습니다."
                            )
                        }

                    is AnalysisSaveAndReplaceResult.Success -> {
                        val message = if (result.clipboardCopied) {
                            "${result.fileName} 파일로 저장하고, 치환된 원문을 클립보드에 복사한 뒤 자동화 프롬프트에 반영했습니다."
                        } else {
                            "${result.fileName} 파일로 저장하고 자동화 프롬프트에 반영했습니다. " +
                                "(클립보드 복사 실패: ${result.clipboardError})"
                        }
                        _uiState.update {
                            it.copy(
                                pendingOverwriteFileName = null,
                                message = message,
                                error = ""
                            )
                        }
                        promptWorkspace?.handoffEntirely(result.replacedSource)
                        onSuccess?.invoke(result.replacedSource)
                    }
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "와일드카드 파일 저장에 실패했습니다.")
            }
        }
    }

    fun confirmOverwrite(onSuccess: ((replacedSource: String) -> Unit)? = null) {
        if (_uiState.value.pendingOverwriteFileName.isNullOrBlank()) return
        saveGeneratedResults(overwrite = true, onSuccess = onSuccess)
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
                analysisCache = null
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
                analysisCache = null
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
                analysisCache = null
                val keys = keyManager.activateKey(id)
                _uiState.update {
                    it.copy(apiKeys = keys, message = "활성 API 키를 선택했습니다.", error = "")
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "API 키 선택에 실패했습니다.")
            }
        }
    }

    fun startEditingApiKey(key: GeminiApiKeySummary) {
        _uiState.update {
            it.copy(
                editingApiKey = key,
                editingKeyLabelInput = key.label
            )
        }
    }

    fun onEditingKeyLabelChange(value: String) {
        _uiState.update { it.copy(editingKeyLabelInput = value) }
    }

    fun cancelEditingApiKey() {
        _uiState.update {
            it.copy(
                editingApiKey = null,
                editingKeyLabelInput = ""
            )
        }
    }

    fun updateApiKeyLabel() {
        val state = _uiState.value
        val keyToEdit = state.editingApiKey ?: return
        scope.launch {
            try {
                val keys = keyManager.updateKeyLabel(
                    id = keyToEdit.id,
                    newLabel = state.editingKeyLabelInput
                )
                _uiState.update {
                    it.copy(
                        apiKeys = keys,
                        editingApiKey = null,
                        editingKeyLabelInput = "",
                        message = "API 키 이름을 수정했습니다.",
                        error = ""
                    )
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "API 키 이름 수정에 실패했습니다.")
            }
        }
    }

    fun onRoleProviderSelected(role: AnalysisModelRole, provider: AnalysisProvider) {
        if (_uiState.value.providerFor(role) == provider) return
        analysisCache = null
        scope.launch {
            val setting = keyManager.setRoleProvider(role, provider)
            applyRoleSetting(setting.role, setting.provider, setting.modelId)
            if (provider == AnalysisProvider.GROK) {
                refreshGrokQuotaIfLoggedIn()
            }
        }
    }

    fun onRoleModelSelected(role: AnalysisModelRole, modelId: String) {
        if (_uiState.value.modelFor(role) == modelId) return
        scope.launch {
            val setting = keyManager.setRoleModel(role, modelId)
            applyRoleSetting(setting.role, setting.provider, setting.modelId)
        }
    }

    fun startGrokLogin() {
        if (_uiState.value.isGrokLoginPolling) return
        grokLoginJob?.cancel()
        grokLoginJob = scope.launch {
            try {
                _uiState.update {
                    it.copy(
                        showGrokLoginDialog = true,
                        isGrokLoginPolling = true,
                        grokLoginUserCode = "",
                        grokLoginVerificationUri = "",
                        error = "",
                        message = "Grok 로그인 준비 중..."
                    )
                }
                val challenge = grokAuth.startDeviceLogin()
                pendingGrokChallenge = challenge
                _uiState.update {
                    it.copy(
                        grokLoginUserCode = challenge.userCode,
                        grokLoginVerificationUri = challenge.verificationUriComplete
                            ?: challenge.verificationUri,
                        message = "Firefox에서 코드 승인 후 이 화면을 유지하세요."
                    )
                }
                val status = grokAuth.awaitDeviceLogin(challenge)
                pendingGrokChallenge = null
                val quota = grokAuth.fetchQuota()
                _uiState.update {
                    it.copy(
                        isGrokLoggedIn = status.isLoggedIn,
                        grokAccountPreview = status.accountPreview,
                        showGrokLoginDialog = false,
                        isGrokLoginPolling = false,
                        grokLoginUserCode = "",
                        grokLoginVerificationUri = "",
                        grokRemainingPercent = quota?.remainingPercent,
                        message = "Grok 로그인에 성공했습니다.",
                        error = ""
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                pendingGrokChallenge = null
                _uiState.update {
                    it.copy(
                        isGrokLoginPolling = false,
                        showGrokLoginDialog = false,
                        grokLoginUserCode = "",
                        grokLoginVerificationUri = ""
                    )
                }
                showError(error.message ?: "Grok 로그인에 실패했습니다.")
            }
        }
    }

    fun cancelGrokLogin() {
        grokLoginJob?.cancel()
        grokLoginJob = null
        pendingGrokChallenge = null
        _uiState.update {
            it.copy(
                showGrokLoginDialog = false,
                isGrokLoginPolling = false,
                grokLoginUserCode = "",
                grokLoginVerificationUri = "",
                message = "Grok 로그인을 취소했습니다."
            )
        }
    }

    fun logoutGrok() {
        scope.launch {
            try {
                analysisCache = null
                val status = grokAuth.logout()
                _uiState.update {
                    it.copy(
                        isGrokLoggedIn = status.isLoggedIn,
                        grokAccountPreview = "",
                        grokRemainingPercent = null,
                        message = "Grok 로그아웃했습니다.",
                        error = ""
                    )
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "Grok 로그아웃에 실패했습니다.")
            }
        }
    }

    private fun refreshRoleSettings() {
        scope.launch {
            val masking = keyManager.getRoleSetting(AnalysisModelRole.MASKING)
            val generation = keyManager.getRoleSetting(AnalysisModelRole.GENERATION)
            _uiState.update {
                it.copy(
                    maskingProvider = masking.provider,
                    maskingModel = masking.modelId,
                    generationProvider = generation.provider,
                    generationModel = generation.modelId
                )
            }
        }
    }

    private fun applyRoleSetting(
        role: AnalysisModelRole,
        provider: AnalysisProvider,
        modelId: String
    ) {
        _uiState.update { state ->
            when (role) {
                AnalysisModelRole.MASKING -> state.copy(
                    maskingProvider = provider,
                    maskingModel = modelId,
                    error = "",
                    message = ""
                )
                AnalysisModelRole.GENERATION -> state.copy(
                    generationProvider = provider,
                    generationModel = modelId,
                    error = "",
                    message = ""
                )
            }
        }
    }

    private fun refreshGrokStatus() {
        scope.launch {
            val status = grokAuth.status()
            _uiState.update {
                it.copy(
                    isGrokLoggedIn = status.isLoggedIn,
                    grokAccountPreview = status.accountPreview,
                    grokRemainingPercent = if (status.isLoggedIn) it.grokRemainingPercent else null
                )
            }
            if (status.isLoggedIn) {
                refreshGrokQuotaIfLoggedIn()
            }
        }
    }

    private suspend fun refreshGrokQuotaIfLoggedIn() {
        if (!_uiState.value.isGrokLoggedIn) return
        val quota = grokAuth.fetchQuota()
        _uiState.update {
            it.copy(grokRemainingPercent = quota?.remainingPercent)
        }
    }

    private fun refreshKeys() {
        scope.launch {
            val keys = keyManager.listKeys()
            _uiState.update { it.copy(apiKeys = keys) }
        }
    }

    private fun currentSourcePrompt(): String =
        sourcePromptTextFieldState.text.toString().also { text ->
            if (text != _uiState.value.sourcePrompt) {
                _uiState.update { it.copy(sourcePrompt = text) }
            }
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

    override fun onCleared() {
        super.onCleared()
        runningJob?.cancel()
        runningJob = null
        grokLoginJob?.cancel()
        grokLoginJob = null
    }
}
