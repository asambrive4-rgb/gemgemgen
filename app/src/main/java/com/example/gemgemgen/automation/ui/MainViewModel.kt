package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptParagraphEditPolicy
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.PromptUndoHistory
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.CloseGeminiAppUseCase
import com.example.gemgemgen.automation.usecase.GeminiAppCloser
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.RunAutomationUseCase
import com.example.gemgemgen.automation.usecase.OverlayPermissionGateway
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.environment.usecase.CheckEnvironmentStatusUseCase
import com.example.gemgemgen.wildcard.usecase.SaveWildcardFolderUseCase
import com.example.gemgemgen.wildcard.usecase.FolderSelectionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val checkEnvironmentStatus: CheckEnvironmentStatusUseCase,
    private val clipboardGateway: ClipboardGateway,
    private val saveWildcardFolder: SaveWildcardFolderUseCase,
    private val lastRunSnapshotStore: LastRunSnapshotStore,
    private val automation: RunAutomationUseCase,
    private val closeGeminiApp: CloseGeminiAppUseCase = CloseGeminiAppUseCase(
        object : GeminiAppCloser {
            override suspend fun closeGeminiApp(): CloseGeminiAppResult {
                return CloseGeminiAppResult.AccessibilityUnavailable
            }
        }
    ),
    private val terminateGeminiApp: CloseGeminiAppUseCase = CloseGeminiAppUseCase(
        object : GeminiAppCloser {
            override suspend fun closeGeminiApp(): CloseGeminiAppResult {
                return CloseGeminiAppResult.AccessibilityUnavailable
            }
        }
    ),
    private val checkAutomationStart: CheckAutomationStartUseCase =
        CheckAutomationStartUseCase(OverlayPermissionGateway { true }),
    private val dispatchers: AppDispatchers = AppDispatchers(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private var automationPreparationJob: Job? = null
    private val promptUndoHistory = PromptUndoHistory()
    private var promptUndoDebounceJob: Job? = null
    private var ignoredPromptChangeText: String? = null
    /** TextField / 비즈니스 로직용 최신 프롬프트. 타이핑 중 uiState 전체 방출을 줄이기 위해 분리. */
    private var promptTemplateValue: String = ""
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    val promptTemplateTextFieldState = TextFieldState()
    private val _automationBarUiState = MutableStateFlow(AutomationBarUiState())
    val automationBarUiState: StateFlow<AutomationBarUiState> =
        _automationBarUiState.asStateFlow()

    init {
        loadInitialState()
        refreshStatus()
    }

    fun onPromptTemplateChange(value: String) {
        onPromptTemplateChange(value, updateTextFieldState = true)
    }

    /**
     * 텍스트 필드 debounce 경로 전용. [updateTextFieldState] = false 와 같으며
     * 메서드 레퍼런스로 넘길 수 있어 Host 람다 재생성으로 인한 구독 재시작을 줄인다.
     */
    fun onPromptTemplateFromEditor(value: String) {
        onPromptTemplateChange(value, updateTextFieldState = false)
    }

    fun onPromptTemplateChange(value: String, updateTextFieldState: Boolean) {
        val previous = promptTemplateValue
        if (updateTextFieldState && !promptTemplateTextFieldState.text.contentEquals(value)) {
            promptTemplateTextFieldState.setTextAndPlaceCursorAtEnd(value)
        }
        if (ignoredPromptChangeText == value) {
            ignoredPromptChangeText = null
            promptTemplateValue = value
            // 외부에서 필드를 맞춘 경우(붙여넣기/삭제 등)에는 state도 즉시 동기화한다.
            publishPromptTemplateToUiState(value, force = true)
            return
        }
        if (previous == value) {
            return
        }
        promptTemplateValue = value
        schedulePromptTypingUndo(previous)
        // 타이핑(필드→VM): 빈/비어 있지 않음 경계일 때만 uiState 방출.
        // 테스트·직접 호출(updateTextFieldState=true): 기존처럼 즉시 반영.
        publishPromptTemplateToUiState(value, force = updateTextFieldState)
    }

    private fun publishPromptTemplateToUiState(value: String, force: Boolean) {
        _uiState.update { state ->
            if (state.promptTemplate == value) {
                state
            } else if (!force && state.promptTemplate.isBlank() == value.isBlank()) {
                // 같은 blankness면 hasPromptTemplate/canRun 이 변하지 않음 → 전체 화면 리컴포즈 생략
                state
            } else {
                state.copy(promptTemplate = value)
            }
        }
    }

    fun toggleParagraphSelectionMode() {
        _uiState.update {
            if (it.isParagraphSelectionMode) {
                it.copy(
                    isParagraphSelectionMode = false,
                    selectedParagraphRange = null,
                    paragraphSelectionMessage = ""
                )
            } else {
                it.copy(
                    isParagraphSelectionMode = true,
                    selectedParagraphRange = null,
                    paragraphSelectionMessage = PARAGRAPH_SELECTION_GUIDE
                )
            }
        }
    }

    fun selectPromptParagraphAt(offset: Int) {
        if (!_uiState.value.isParagraphSelectionMode) return

        val range = PromptParagraphEditPolicy.findParagraph(
            text = promptTemplateTextFieldState.text.toString(),
            offset = offset
        )
        _uiState.update {
            if (range == null) {
                it.copy(
                    selectedParagraphRange = null,
                    paragraphSelectionMessage = EMPTY_PARAGRAPH_MESSAGE
                )
            } else {
                it.copy(
                    selectedParagraphRange = range,
                    paragraphSelectionMessage = PARAGRAPH_SELECTED_MESSAGE
                )
            }
        }
    }

    fun deleteSelectedPromptParagraph() {
        val state = _uiState.value
        val range = state.selectedParagraphRange ?: return
        val currentText = promptTemplateTextFieldState.text.toString()
        if (range.endExclusive > currentText.length) {
            cancelParagraphSelection()
            return
        }

        recordImmediatePromptUndo(currentText)
        val newText = PromptParagraphEditPolicy.replace(currentText, range, "")
        ignoredPromptChangeText = newText
        promptTemplateTextFieldState.edit {
            replace(range.start, range.endExclusive, "")
            selection = TextRange(range.start)
        }
        promptTemplateValue = newText
        _uiState.update {
            it.copy(
                promptTemplate = newText,
                isParagraphSelectionMode = false,
                selectedParagraphRange = null,
                paragraphSelectionMessage = ""
            )
        }
    }

    fun cancelParagraphSelection() {
        _uiState.update {
            if (!it.isParagraphSelectionMode &&
                it.selectedParagraphRange == null &&
                it.paragraphSelectionMessage.isEmpty()
            ) {
                it
            } else {
                it.copy(
                    isParagraphSelectionMode = false,
                    selectedParagraphRange = null,
                    paragraphSelectionMessage = ""
                )
            }
        }
    }

    fun onTargetAppSelected(targetApp: AutomationTargetApp) {
        _uiState.update {
            if (it.isRunning) it else it.copy(selectedTargetApp = targetApp)
        }
    }

    fun onRepeatCountChange(value: String) {
        val normalized = RepeatCountParser.normalizeInput(value)
        _uiState.update { it.copy(repeatCountText = normalized) }
        _automationBarUiState.update { it.copy(repeatCountText = normalized) }
    }

    fun importPromptFromClipboard() {
        syncPromptTemplateFromTextField()
        scope.launch {
            val text = withContext(dispatchers.io) {
                clipboardGateway.readText()
            }
            val state = _uiState.value
            if (!state.isParagraphSelectionMode) {
                replaceWholePromptTemplate(text)
                return@launch
            }

            replaceSelectedPromptParagraph(text)
        }
    }

    fun copyPromptToClipboard() {
        syncPromptTemplateFromTextField()
        val state = _uiState.value
        val text = state.promptTemplate
        if (state.isRunning || text.isBlank()) return

        scope.launch {
            withContext(dispatchers.io) {
                clipboardGateway.writeText(text)
            }
        }
    }

    fun pastePromptFromClipboard() {
        syncPromptTemplateFromTextField()
        scope.launch {
            val text = withContext(dispatchers.io) {
                clipboardGateway.readText()
            }
            if (text.isEmpty()) return@launch

            val currentText = promptTemplateTextFieldState.text.toString()
            recordImmediatePromptUndo(currentText)

            val selection = promptTemplateTextFieldState.selection
            val start = selection.min
            val end = selection.max

            promptTemplateTextFieldState.edit {
                replace(start, end, text)
                this.selection = TextRange(start + text.length)
            }

            val newText = promptTemplateTextFieldState.text.toString()
            promptTemplateValue = newText
            _uiState.update {
                it.copy(
                    promptTemplate = newText,
                    canUndoPromptEdit = hasPromptUndo()
                )
            }
        }
    }

    fun replaceSelectedPromptParagraph(replacement: String) {
        val state = _uiState.value
        if (!state.isParagraphSelectionMode) return

        val range = state.selectedParagraphRange
        when {
            range == null -> {
                _uiState.update {
                    it.copy(paragraphSelectionMessage = SELECT_PARAGRAPH_FIRST_MESSAGE)
                }
            }
            replacement.isBlank() -> {
                _uiState.update {
                    it.copy(paragraphSelectionMessage = EMPTY_CLIPBOARD_MESSAGE)
                }
            }
            else -> replaceSelectedParagraph(range.start, range.endExclusive, replacement)
        }
    }

    fun closeGeminiApp() {
        val state = _uiState.value
        if (!state.canCloseGemini) {
            _uiState.update {
                it.copy(
                    geminiCloseMessage = AutomationUiText.geminiRestartUnavailableMessage(it)
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isClosingGemini = true,
                geminiCloseMessage = "Gemini 재시작 중..."
            )
        }
        scope.launch {
            val result = try {
                withContext(dispatchers.io) {
                    closeGeminiApp.close()
                }
            } catch (error: CancellationException) {
                _uiState.update {
                    it.copy(
                        isClosingGemini = false,
                        geminiCloseMessage = "Gemini 재시작을 취소했습니다."
                    )
                }
                throw error
            } catch (error: Exception) {
                CloseGeminiAppResult.Failure(
                    error.message ?: "알 수 없는 오류가 발생했습니다."
                )
            }

            _uiState.update {
                it.copy(
                    isClosingGemini = false,
                    geminiCloseMessage = AutomationUiText.geminiRestartResultMessage(result)
                )
            }
        }
    }

    fun terminateGeminiApp() {
        val state = _uiState.value
        if (!state.canCloseGemini) {
            _uiState.update {
                it.copy(
                    geminiCloseMessage = AutomationUiText.geminiTerminateUnavailableMessage(it)
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isClosingGemini = true,
                geminiCloseMessage = "Gemini 종료 중..."
            )
        }
        scope.launch {
            val result = try {
                withContext(dispatchers.io) {
                    terminateGeminiApp.close()
                }
            } catch (error: CancellationException) {
                _uiState.update {
                    it.copy(
                        isClosingGemini = false,
                        geminiCloseMessage = "Gemini 종료를 취소했습니다."
                    )
                }
                throw error
            } catch (error: Exception) {
                CloseGeminiAppResult.Failure(
                    error.message ?: "알 수 없는 오류가 발생했습니다."
                )
            }

            _uiState.update {
                it.copy(
                    isClosingGemini = false,
                    geminiCloseMessage = AutomationUiText.geminiTerminateResultMessage(result)
                )
            }
        }
    }

    fun undoPromptEdit() {
        if (_uiState.value.isRunning) return

        commitPendingPromptUndo()
        val previous = promptUndoHistory.popUndo() ?: return
        applyPromptTemplateText(previous)
        promptTemplateValue = previous
        _uiState.update {
            it.copy(
                promptTemplate = previous,
                isParagraphSelectionMode = false,
                selectedParagraphRange = null,
                paragraphSelectionMessage = "",
                canUndoPromptEdit = hasPromptUndo()
            )
        }
    }

    fun refreshStatus() {
        scope.launch {
            val report = withContext(dispatchers.io) {
                checkEnvironmentStatus.check()
            }
            _uiState.update {
                it.copy(
                    environmentStatus = report.status,
                    environmentSetupInfo = report.setupInfo
                )
            }
        }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun saveWildcardFolder(folderUri: String) {
        scope.launch {
            val result = withContext(dispatchers.io) {
                saveWildcardFolder.save(folderUri)
            }
            _uiState.update {
                when (result) {
                    FolderSelectionResult.Success -> it.copy(
                        settingsMessage = "wildcard 폴더를 선택했습니다.",
                        settingsError = ""
                    )
                    is FolderSelectionResult.Failure -> it.copy(
                        settingsMessage = "",
                        settingsError =
                            "폴더 권한 저장 실패: ${result.reason ?: "다시 선택해주세요."}"
                    )
                }
            }
            refreshStatus()
        }
    }

    fun runAutomation(): AutomationStartDecision {
        syncPromptTemplateFromTextField()
        val state = uiState.value
        val decision = checkAutomationStart.decide(
            canRun = state.canRun,
            isStartInProgress = automationPreparationJob?.isActive == true
        )
        if (decision != AutomationStartDecision.Started) return decision

        cancelParagraphSelection()
        handleAutomationState(AutomationRunState.Running("자동화 준비 중"))
        val request = AutomationRunRequest(
            promptTemplate = state.promptTemplate,
            repeatCountText = state.repeatCountText,
            targetApp = state.selectedTargetApp
        )
        val job = scope.launch {
            try {
                automation.run(request, ::handleAutomationState)
            } catch (error: CancellationException) {
                handleAutomationState(AutomationRunState.Stopped)
                throw error
            } catch (error: Exception) {
                handleAutomationState(
                    AutomationRunState.Failure(error.message ?: "자동화 준비 중 오류가 발생했습니다.")
                )
            }
        }
        automationPreparationJob = job
        job.invokeOnCompletion {
            if (automationPreparationJob == job) {
                automationPreparationJob = null
            }
        }
        return AutomationStartDecision.Started
    }

    fun cancelAutomation() {
        val preparationJob = automationPreparationJob
        if (preparationJob?.isActive == true) {
            preparationJob.cancel()
            handleAutomationState(AutomationRunState.Stopped)
            return
        }

        automation.cancel(::handleAutomationState)
    }

    private fun loadInitialState() {
        scope.launch {
            val lastRunSnapshot = withContext(dispatchers.io) {
                lastRunSnapshotStore.load()
            }
            val current = _uiState.value
            val defaultRepeatCountText = AppDefaults.DEFAULT_REPEAT_COUNT.toString()
            val restoredPrompt = if (current.promptTemplate.isBlank()) {
                lastRunSnapshot?.promptTemplate.orEmpty()
            } else {
                current.promptTemplate
            }
            promptTemplateValue = restoredPrompt
            _uiState.update {
                it.copy(
                    promptTemplate = restoredPrompt,
                    repeatCountText = if (it.repeatCountText == defaultRepeatCountText) {
                        lastRunSnapshot?.repeatCountText
                            ?.ifBlank { defaultRepeatCountText }
                            ?: defaultRepeatCountText
                    } else {
                        it.repeatCountText
                    },
                    selectedTargetApp = lastRunSnapshot?.targetApp ?: it.selectedTargetApp
                )
            }
            applyPromptTemplateText(promptTemplateValue)
            val restoredState = uiState.value
            _automationBarUiState.value = AutomationBarUiState(
                repeatCountText = restoredState.repeatCountText,
                automationState = restoredState.automationState
            )
        }
    }

    private fun handleAutomationState(state: AutomationRunState) {
        _uiState.update {
            val coarseState = it.automationState.coarseAutomationStateFor(state)
            if (it.automationState == coarseState) {
                it
            } else {
                it.copy(automationState = coarseState)
            }
        }
        _automationBarUiState.update {
            if (it.automationState == state) it else it.copy(automationState = state)
        }
    }

    private fun AutomationRunState.coarseAutomationStateFor(
        nextState: AutomationRunState
    ): AutomationRunState {
        return if (this is AutomationRunState.Running && nextState is AutomationRunState.Running) {
            this
        } else {
            nextState
        }
    }

    private fun replaceSelectedParagraph(
        start: Int,
        endExclusive: Int,
        replacement: String
    ) {
        val currentText = promptTemplateTextFieldState.text.toString()
        if (start !in 0..currentText.length || endExclusive !in start..currentText.length) {
            cancelParagraphSelection()
            return
        }

        recordImmediatePromptUndo(currentText)
        val range = PromptParagraphRange(
            start = start,
            endExclusive = endExclusive
        )
        val newText = PromptParagraphEditPolicy.replace(currentText, range, replacement)
        ignoredPromptChangeText = newText
        promptTemplateTextFieldState.edit {
            replace(start, endExclusive, replacement)
            selection = TextRange(start + replacement.length)
        }
        promptTemplateValue = newText
        _uiState.update {
            it.copy(
                promptTemplate = newText,
                isParagraphSelectionMode = false,
                selectedParagraphRange = null,
                paragraphSelectionMessage = ""
            )
        }
    }

    private fun replaceWholePromptTemplate(replacement: String) {
        val currentText = promptTemplateTextFieldState.text.toString()
        if (currentText == replacement) return

        recordImmediatePromptUndo(currentText)
        applyPromptTemplateText(replacement)
        promptTemplateValue = replacement
        _uiState.update {
            it.copy(
                promptTemplate = replacement,
                isParagraphSelectionMode = false,
                selectedParagraphRange = null,
                paragraphSelectionMessage = "",
                canUndoPromptEdit = hasPromptUndo()
            )
        }
    }

    private fun applyPromptTemplateText(text: String) {
        if (!promptTemplateTextFieldState.text.contentEquals(text)) {
            ignoredPromptChangeText = text
            promptTemplateTextFieldState.setTextAndPlaceCursorAtEnd(text)
        }
    }

    /**
     * 실행·복사 등 최신 문자열이 필요할 때 TextField → 내부 값·uiState 를 맞춘다.
     * Undo 스냅샷은 잡지 않는다(타이핑 중 state 미방출 보정용).
     */
    private fun syncPromptTemplateFromTextField() {
        val currentText = promptTemplateTextFieldState.text.toString()
        if (promptTemplateValue == currentText &&
            _uiState.value.promptTemplate == currentText
        ) {
            return
        }
        promptTemplateValue = currentText
        _uiState.update {
            if (it.promptTemplate == currentText) it else it.copy(promptTemplate = currentText)
        }
    }

    private fun schedulePromptTypingUndo(previous: String) {
        promptUndoHistory.recordTypingSnapshot(previous)
        promptUndoDebounceJob?.cancel()
        promptUndoDebounceJob = scope.launch {
            delay(PROMPT_UNDO_DEBOUNCE_MILLIS)
            commitPendingPromptUndo()
        }
        updatePromptUndoAvailability()
    }

    private fun recordImmediatePromptUndo(snapshot: String) {
        commitPendingPromptUndo()
        promptUndoHistory.recordImmediateSnapshot(snapshot)
        updatePromptUndoAvailability()
    }

    private fun commitPendingPromptUndo() {
        promptUndoHistory.commitPendingTyping(promptTemplateValue)
        promptUndoDebounceJob?.cancel()
        promptUndoDebounceJob = null
        updatePromptUndoAvailability()
    }

    private fun updatePromptUndoAvailability() {
        val canUndo = hasPromptUndo()
        _uiState.update {
            if (it.canUndoPromptEdit == canUndo) it else it.copy(canUndoPromptEdit = canUndo)
        }
    }

    private fun hasPromptUndo(): Boolean {
        return promptUndoHistory.canUndo
    }

    private fun geminiCloseUnavailableMessage(state: MainUiState): String {
        return when {
            state.isRunning -> "자동화 중에는 Gemini를 재시작할 수 없습니다."
            state.isClosingGemini -> "Gemini 재시작이 이미 진행 중입니다."
            !state.environmentStatus.isGeminiInstalled -> "Gemini 앱이 설치되어 있지 않습니다."
            !state.environmentStatus.isAccessibilityServiceEnabled ->
                "접근성 서비스를 먼저 켜주세요."
            else -> "Gemini 재시작을 지금 실행할 수 없습니다."
        }
    }

    private fun geminiTerminateUnavailableMessage(state: MainUiState): String {
        return when {
            state.isRunning -> "자동화 중에는 Gemini를 종료할 수 없습니다."
            state.isClosingGemini -> "Gemini 종료가 이미 진행 중입니다."
            !state.environmentStatus.isGeminiInstalled -> "Gemini 앱이 설치되어 있지 않습니다."
            !state.environmentStatus.isAccessibilityServiceEnabled ->
                "접근성 서비스를 먼저 켜주세요."
            else -> "Gemini 종료를 지금 실행할 수 없습니다."
        }
    }

    private fun geminiCloseResultMessage(result: CloseGeminiAppResult): String {
        return when (result) {
            is CloseGeminiAppResult.Success -> {
                if (result.closedCount <= 1) {
                    "Gemini 앱을 재시작했습니다."
                } else {
                    "Gemini 앱 ${result.closedCount}개를 종료한 뒤 재시작했습니다."
                }
            }
            CloseGeminiAppResult.AccessibilityUnavailable ->
                "접근성 서비스가 켜져 있지 않습니다."
            CloseGeminiAppResult.RecentsUnavailable ->
                "최근 앱 화면을 열지 못했습니다."
            CloseGeminiAppResult.NotFound ->
                "최근 앱에서 Gemini를 찾지 못했습니다."
            is CloseGeminiAppResult.Failure ->
                "Gemini 재시작 실패: ${result.message}"
        }
    }

    private fun geminiTerminateResultMessage(result: CloseGeminiAppResult): String {
        return when (result) {
            is CloseGeminiAppResult.Success -> {
                if (result.closedCount <= 1) {
                    "Gemini 앱을 종료했습니다."
                } else {
                    "Gemini 앱 ${result.closedCount}개를 종료했습니다."
                }
            }
            CloseGeminiAppResult.AccessibilityUnavailable ->
                "접근성 서비스가 켜져 있지 않습니다."
            CloseGeminiAppResult.RecentsUnavailable ->
                "최근 앱 화면을 열지 못했습니다."
            CloseGeminiAppResult.NotFound ->
                "최근 앱에서 Gemini를 찾지 못했습니다."
            is CloseGeminiAppResult.Failure ->
                "Gemini 종료 실패: ${result.message}"
        }
    }

    private companion object {
        const val PROMPT_UNDO_DEBOUNCE_MILLIS = 700L
        const val PARAGRAPH_SELECTION_GUIDE =
            "바꿀 문단을 터치하세요. 직접 입력은 제한되며 삭제키는 사용할 수 있습니다."
        const val PARAGRAPH_SELECTED_MESSAGE =
            "문단이 선택되었습니다. 가져오기 또는 삭제키를 사용하세요."
        const val EMPTY_PARAGRAPH_MESSAGE =
            "빈 줄은 선택할 수 없습니다. 텍스트가 있는 문단을 터치하세요."
        const val SELECT_PARAGRAPH_FIRST_MESSAGE = "먼저 바꿀 문단을 선택하세요."
        const val EMPTY_CLIPBOARD_MESSAGE =
            "클립보드가 비어 있어 선택한 문단을 바꾸지 않았습니다."
    }
}
