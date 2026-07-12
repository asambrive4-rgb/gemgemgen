package com.example.gemgemgen.analysis.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisStartGate
import com.example.gemgemgen.analysis.domain.AnalysisStartPolicy
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegmentPolicy
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.domain.ManualTargetSegmentResult
import com.example.gemgemgen.analysis.usecase.AnalysisReportCache
import com.example.gemgemgen.analysis.usecase.AnalysisSaveAndReplaceResult
import com.example.gemgemgen.analysis.usecase.CopyAnalysisResultsUseCase
import com.example.gemgemgen.analysis.usecase.GenerateAnalysisTxtUseCase
import com.example.gemgemgen.analysis.usecase.GrokDeviceLoginChallenge
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.analysis.usecase.ManageGrokAuthUseCase
import com.example.gemgemgen.analysis.usecase.ResolveAnalysisTargetUseCase
import com.example.gemgemgen.analysis.usecase.SaveAnalysisWildcardFileUseCase
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    coroutineScope: CoroutineScope? = null
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

        val blanknessChanged = state.sourcePrompt.isBlank() != value.isBlank()
        val segment = state.targetSegment
        val segmentStillValid = segment == null ||
            AnalysisTargetSegmentPolicy.isStillValid(value, segment)
        val nextSegment = if (segmentStillValid) segment else null
        val shouldClearCandidates = state.generatedCandidates.isNotEmpty()

        // 핫패스: canAnalyze 경계·구간 무효·결과 정리가 없으면 화면 state 방출 생략
        val needsUiUpdate = blanknessChanged ||
            nextSegment != segment ||
            shouldClearCandidates
        if (!needsUiUpdate) return

        _uiState.update {
            it.copy(
                sourcePrompt = value,
                targetSegment = nextSegment,
                generatedCandidates = if (shouldClearCandidates) {
                    emptyList()
                } else {
                    it.generatedCandidates
                },
                error = "",
                message = if (nextSegment != segment) "" else it.message,
                warning = "",
                status = if (it.status == AnalysisStatus.ERROR) {
                    AnalysisStatus.IDLE
                } else {
                    it.status
                }
            )
        }
    }

    /**
     * 자동화 탭에 입력된 원본 프롬프트로 분석 원문을 통째로 교체한다.
     * 비어 있으면 원문은 유지하고 안내만 표시한다.
     */
    fun importSourcePromptFromAutomation(text: String) {
        if (text.isBlank()) {
            showError("자동화에 입력된 텍스트가 없습니다.")
            return
        }
        replaceSourcePrompt(text)
    }

    private fun replaceSourcePrompt(value: String) {
        val currentText = sourcePromptTextFieldState.text.toString()
        if (currentText != value) {
            sourcePromptTextFieldState.setTextAndPlaceCursorAtEnd(value)
        }

        analysisCache = null
        val state = _uiState.value
        if (state.sourcePrompt == value &&
            state.targetSegment == null &&
            state.generatedCandidates.isEmpty() &&
            state.error.isEmpty() &&
            state.warning.isEmpty()
        ) {
            return
        }

        val segment = state.targetSegment
        val segmentStillValid = segment == null ||
            AnalysisTargetSegmentPolicy.isStillValid(value, segment)
        val nextSegment = if (segmentStillValid) segment else null

        _uiState.update {
            it.copy(
                sourcePrompt = value,
                targetSegment = nextSegment,
                generatedCandidates = if (it.generatedCandidates.isNotEmpty()) {
                    emptyList()
                } else {
                    it.generatedCandidates
                },
                error = "",
                message = if (nextSegment != segment) "" else it.message,
                warning = "",
                status = if (it.status == AnalysisStatus.ERROR) {
                    AnalysisStatus.IDLE
                } else {
                    it.status
                }
            )
        }
    }

    fun onCategorySelected(category: AnalysisCategory) {
        analysisCache = null
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
        when (
            val result = AnalysisTargetSegmentPolicy.fromManual(
                source = source,
                start = selection.min,
                end = selection.max,
                category = category
            )
        ) {
            ManualTargetSegmentResult.EmptySelection -> {
                showError("원문에서 바꾸고 싶은 구간을 선택해주세요.")
            }
            is ManualTargetSegmentResult.Success -> {
                analysisCache = null
                _uiState.update {
                    it.copy(
                        sourcePrompt = source,
                        targetSegment = result.segment,
                        generatedCandidates = emptyList(),
                        error = "",
                        message = "수동 마스킹 구간을 지정했습니다.",
                        warning = "",
                        status = AnalysisStatus.IDLE
                    )
                }
            }
        }
    }

    fun clearTargetSegment() {
        analysisCache = null
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
        val source = currentSourcePrompt()
        when (
            val gate = AnalysisStartPolicy.evaluateInputs(
                source = source,
                category = snapshot.selectedCategory,
                hasActiveKey = snapshot.hasMaskingCredential
            )
        ) {
            is AnalysisStartGate.Blocked -> {
                showError(
                    AnalysisUiText.startBlockedMessage(
                        reason = gate.reason,
                        provider = snapshot.maskingProvider,
                        role = AnalysisModelRole.MASKING
                    )
                )
                return
            }
            AnalysisStartGate.Allowed -> Unit
        }
        val category = checkNotNull(snapshot.selectedCategory)

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
                analysisCache = null
                val result = resolveTarget.analyzeAndMask(source, category)
                analysisCache = result.cache
                rememberLastUsed(
                    role = AnalysisModelRole.MASKING,
                    provider = snapshot.maskingProvider,
                    modelId = snapshot.maskingModel
                )
                _uiState.update {
                    it.copy(
                        sourcePrompt = source,
                        targetSegment = result.targetSegment,
                        generatedCandidates = emptyList(),
                        status = AnalysisStatus.IDLE,
                        message = "자동 마스킹 구간을 찾았습니다.",
                        warning = result.warning
                    )
                }
                if (snapshot.maskingProvider == AnalysisProvider.GROK ||
                    _uiState.value.usesGrok
                ) {
                    refreshGrokQuotaIfLoggedIn()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                analysisCache = null
                showError(error.message ?: "분석에 실패했습니다.")
            }
        }
    }

    fun generateTxt() {
        val snapshot = _uiState.value
        val source = currentSourcePrompt()
        val needsMaskingAnalysis = willNeedMaskingAnalysis(
            source = source,
            category = snapshot.selectedCategory,
            existingTarget = snapshot.targetSegment,
            cache = analysisCache
        )
        // 캐시 미스 등으로 구간 분석이 필요하면 마스킹 자격증명도 먼저 확인
        if (needsMaskingAnalysis) {
            when (
                val gate = AnalysisStartPolicy.evaluateInputs(
                    source = source,
                    category = snapshot.selectedCategory,
                    hasActiveKey = snapshot.hasMaskingCredential
                )
            ) {
                is AnalysisStartGate.Blocked -> {
                    showError(
                        AnalysisUiText.startBlockedMessage(
                            reason = gate.reason,
                            provider = snapshot.maskingProvider,
                            role = AnalysisModelRole.MASKING
                        )
                    )
                    return
                }
                AnalysisStartGate.Allowed -> Unit
            }
        }
        when (
            val gate = AnalysisStartPolicy.evaluateInputs(
                source = source,
                category = snapshot.selectedCategory,
                hasActiveKey = snapshot.hasGenerationCredential
            )
        ) {
            is AnalysisStartGate.Blocked -> {
                showError(
                    AnalysisUiText.startBlockedMessage(
                        reason = gate.reason,
                        provider = snapshot.generationProvider,
                        role = AnalysisModelRole.GENERATION
                    )
                )
                return
            }
            AnalysisStartGate.Allowed -> Unit
        }
        val category = checkNotNull(snapshot.selectedCategory)

        runningJob?.cancel()
        runningJob = scope.launch {
            _uiState.update {
                it.copy(
                    status = AnalysisStatus.GENERATING,
                    error = "",
                    message = if (needsMaskingAnalysis) {
                        "자동 마스킹 중..."
                    } else {
                        "프롬프트 목록 생성 중..."
                    },
                    warning = ""
                )
            }
            try {
                val ensured = resolveTarget.ensureForGeneration(
                    source = source,
                    category = category,
                    existingTarget = _uiState.value.targetSegment,
                    cache = analysisCache
                )
                analysisCache = ensured.cache
                if (ensured.targetChanged) {
                    _uiState.update {
                        it.copy(
                            targetSegment = ensured.target,
                            warning = ensured.warning
                        )
                    }
                }
                // 마스킹 단계가 끝났으면 생성 단계 문구로 전환
                if (needsMaskingAnalysis) {
                    _uiState.update {
                        it.copy(message = "프롬프트 목록 생성 중...")
                    }
                }
                val selectedHints = _uiState.value.directions
                    .filter { it.id in _uiState.value.selectedDirectionIds }
                    .map { it.hint }
                val result = generateTxtUseCase.generate(
                    sourcePrompt = source,
                    category = category,
                    targetSegment = ensured.target,
                    analysisReport = ensured.report,
                    count = _uiState.value.txtCount,
                    selectedHints = selectedHints,
                    customHint = _uiState.value.customHint
                )
                if (ensured.didAnalyze) {
                    rememberLastUsed(
                        role = AnalysisModelRole.MASKING,
                        provider = snapshot.maskingProvider,
                        modelId = snapshot.maskingModel
                    )
                }
                rememberLastUsed(
                    role = AnalysisModelRole.GENERATION,
                    provider = snapshot.generationProvider,
                    modelId = snapshot.generationModel
                )
                _uiState.update {
                    it.copy(
                        sourcePrompt = source,
                        targetSegment = ensured.target,
                        generatedCandidates = result.candidates,
                        // 생성 완료 시 카테고리명(공백 제거)으로 저장 파일명 기본값 지정.
                        // 사용자가 이전에 수정했더라도 이번 생성 카테고리 기준으로 덮어쓴다.
                        resultFileName = category.defaultWildcardSaveFileName(),
                        status = AnalysisStatus.SUCCESS,
                        message = "${result.candidates.size}개 후보를 생성했습니다.",
                        warning = result.warning,
                        error = ""
                    )
                }
                val usedGrok =
                    snapshot.generationProvider == AnalysisProvider.GROK ||
                        (ensured.didAnalyze &&
                            snapshot.maskingProvider == AnalysisProvider.GROK) ||
                        _uiState.value.usesGrok
                if (usedGrok) {
                    refreshGrokQuotaIfLoggedIn()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                analysisCache = null
                showError(error.message ?: "TXT 생성에 실패했습니다.")
            }
        }
    }

    /**
     * TXT 생성 시 캐시로 분석 결과를 재사용하지 못하면 마스킹 모델 분석이 필요하다.
     * (ResolveAnalysisTargetUseCase.getOrAnalyzeReport 캐시 조건과 동일)
     */
    private fun willNeedMaskingAnalysis(
        source: String,
        category: AnalysisCategory?,
        existingTarget: AnalysisTargetSegment?,
        cache: AnalysisReportCache?
    ): Boolean {
        if (category == null) return false
        return cache == null ||
            cache.sourcePrompt != source ||
            cache.category != category ||
            cache.targetSegment != existingTarget
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

    fun trimForInactiveTab() {
        runningJob?.cancel()
        runningJob = null
        analysisCache = null
        _uiState.update {
            it.copy(
                generatedCandidates = emptyList(),
                targetSegment = null,
                status = AnalysisStatus.IDLE,
                warning = "",
                error = "",
                message = "",
                pendingOverwriteFileName = null
            )
        }
    }

    fun onTxtCountChange(value: Int) {
        _uiState.update {
            it.copy(txtCount = AnalysisTxtCountPolicy.coerce(value))
        }
    }

    fun onCustomHintChange(value: String) {
        if (value.length <= 100) {
            _uiState.update {
                it.copy(customHint = value)
            }
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

    /**
     * 와일드카드 파일 저장 후, 치환된 원문([replacedSource])을 호출측에 넘긴다.
     * 호출측(호스트)에서 자동화 템플릿 반영·탭 이동을 처리한다.
     */
    fun saveGeneratedResults(
        overwrite: Boolean = false,
        onSuccess: ((replacedSource: String) -> Unit)? = null
    ) {
        val state = _uiState.value
        val candidates = state.generatedCandidates
        if (candidates.isEmpty()) return
        scope.launch {
            try {
                when (
                    val result = saveWildcardFile.saveAndPrepareReplacedSource(
                        fileNameInput = state.resultFileName,
                        candidates = candidates,
                        overwrite = overwrite,
                        sourcePrompt = sourcePromptTextFieldState.text.toString(),
                        targetSegment = state.targetSegment
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
                        onSuccess?.invoke(result.replacedSource)
                    }
                }
            } catch (error: RuntimeException) {
                showError(error.message ?: "와일드카드 파일 저장에 실패했습니다.")
            }
        }
    }

    fun confirmOverwrite(onSuccess: ((replacedSource: String) -> Unit)? = null) {
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
        _uiState.update {
            when (role) {
                AnalysisModelRole.MASKING -> it.copy(
                    maskingProvider = provider,
                    maskingModel = modelId,
                    error = "",
                    message = ""
                )
                AnalysisModelRole.GENERATION -> it.copy(
                    generationProvider = provider,
                    generationModel = modelId,
                    error = "",
                    message = ""
                )
            }
        }
    }

    private suspend fun rememberLastUsed(
        role: AnalysisModelRole,
        provider: AnalysisProvider,
        modelId: String
    ) {
        keyManager.rememberLastUsed(role, provider, modelId)
    }

    private fun refreshGrokStatus() {
        scope.launch {
            val status = grokAuth.status()
            _uiState.update {
                it.copy(
                    isGrokLoggedIn = status.isLoggedIn,
                    grokAccountPreview = status.accountPreview,
                    grokRemainingPercent = if (status.isLoggedIn) {
                        it.grokRemainingPercent
                    } else {
                        null
                    }
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

    private fun currentSourcePrompt(): String {
        val text = sourcePromptTextFieldState.text.toString()
        if (text != _uiState.value.sourcePrompt) {
            _uiState.update { it.copy(sourcePrompt = text) }
        }
        return text
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
