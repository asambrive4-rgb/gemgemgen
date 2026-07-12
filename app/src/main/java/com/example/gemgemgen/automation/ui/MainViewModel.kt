package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptEditorSession
import com.example.gemgemgen.automation.domain.PromptParagraphActionResult
import com.example.gemgemgen.automation.domain.PromptTextMutation
import com.example.gemgemgen.automation.domain.PromptTypingChange
import com.example.gemgemgen.automation.domain.PromptUndoHistory
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.CloseGeminiAppUseCase
import com.example.gemgemgen.automation.usecase.GeminiAppCloser
import com.example.gemgemgen.automation.usecase.LastRunSnapshot
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
    private val terminateSelfApp: CloseGeminiAppUseCase = CloseGeminiAppUseCase(
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
    private var promptEditorSession = PromptEditorSession()
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    val promptTemplateTextFieldState = TextFieldState()
    private val _automationBarUiState = MutableStateFlow(AutomationBarUiState())
    val automationBarUiState: StateFlow<AutomationBarUiState> =
        _automationBarUiState.asStateFlow()

    init {
        scope.launch {
            automation.runState.collect { state ->
                handleAutomationState(state)
            }
        }
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
        if (updateTextFieldState && !promptTemplateTextFieldState.text.contentEquals(value)) {
            promptTemplateTextFieldState.setTextAndPlaceCursorAtEnd(value)
        }
        when (
            val change = PromptEditorSession.classifyTypingChange(
                previousText = promptTemplateValue,
                newText = value,
                programmaticEchoText = ignoredPromptChangeText
            )
        ) {
            PromptTypingChange.IgnoredEcho -> {
                ignoredPromptChangeText = null
                setPromptTextOnly(value)
                // 외부에서 필드를 맞춘 경우(붙여넣기/삭제 등)에는 state도 즉시 동기화한다.
                publishPromptTemplateToUiState(value, force = true)
            }
            PromptTypingChange.Unchanged -> Unit
            is PromptTypingChange.UserEdit -> {
                setPromptTextOnly(change.newText)
                schedulePromptTypingUndo(change.previousText)
                // 타이핑(필드→VM): 빈/비어 있지 않음 경계일 때만 uiState 방출.
                // 테스트·직접 호출(updateTextFieldState=true): 기존처럼 즉시 반영.
                publishPromptTemplateToUiState(change.newText, force = updateTextFieldState)
            }
        }
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
        syncEditorTextFromCurrent()
        publishEditorSession(promptEditorSession.toggleSelectionMode())
    }

    fun selectPromptParagraphAt(offset: Int) {
        syncEditorTextFromCurrent()
        publishEditorSession(promptEditorSession.selectAt(offset))
    }

    fun deleteSelectedPromptParagraph() {
        syncEditorTextFromCurrent()
        when (val result = promptEditorSession.prepareDeleteSelected()) {
            PromptParagraphActionResult.NoOp -> Unit
            is PromptParagraphActionResult.SessionOnly -> publishEditorSession(result.session)
            is PromptParagraphActionResult.Mutated -> applyTextMutation(result.mutation)
        }
    }

    fun cancelParagraphSelection() {
        publishEditorSession(promptEditorSession.cancelSelection())
    }

    fun onTargetAppSelected(targetApp: AutomationTargetApp) {
        _uiState.update {
            if (it.isRunning) it else it.copy(selectedTargetApp = targetApp)
        }
    }

    fun onRepeatCountChange(value: String) {
        val normalized = RepeatCountParser.normalizeInput(value)
        if (_uiState.value.isRunning) {
            if (normalized.isEmpty()) {
                publishRepeatCountText(normalized)
                return
            }
            val requested = RepeatCountParser.parse(normalized)
            val applied = automation.updateRepeatCount(requested) ?: requested
            val appliedText = applied.toString()
            publishRepeatCountText(appliedText)
            persistRepeatCountAsLastRunDefault(appliedText)
            return
        }
        publishRepeatCountText(normalized)
    }

    private fun publishRepeatCountText(text: String) {
        _uiState.update { it.copy(repeatCountText = text) }
        _automationBarUiState.update { it.copy(repeatCountText = text) }
    }

    private fun persistRepeatCountAsLastRunDefault(repeatCountText: String) {
        val state = _uiState.value
        scope.launch {
            withContext(dispatchers.io) {
                lastRunSnapshotStore.save(
                    LastRunSnapshot(
                        promptTemplate = state.promptTemplate,
                        repeatCountText = repeatCountText,
                        targetApp = state.selectedTargetApp
                    )
                )
            }
        }
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
            promptEditorSession = promptEditorSession.afterPaste(newText)
            _uiState.update {
                it.copy(
                    promptTemplate = newText,
                    canUndoPromptEdit = hasPromptUndo()
                )
            }
        }
    }

    fun replaceSelectedPromptParagraph(replacement: String) {
        syncEditorTextFromCurrent()
        when (val result = promptEditorSession.prepareReplaceSelected(replacement)) {
            PromptParagraphActionResult.NoOp -> Unit
            is PromptParagraphActionResult.SessionOnly -> publishEditorSession(result.session)
            is PromptParagraphActionResult.Mutated -> applyTextMutation(result.mutation)
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
                geminiCloseMessage = AutomationUiText.geminiRestartStartingText()
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
                        geminiCloseMessage = AutomationUiText.geminiRestartCanceledText()
                    )
                }
                throw error
            } catch (error: Exception) {
                CloseGeminiAppResult.Failure(
                    AutomationUiText.unknownCloseErrorMessage(error)
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
                geminiCloseMessage = AutomationUiText.geminiTerminateStartingText()
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
                        geminiCloseMessage = AutomationUiText.geminiTerminateCanceledText()
                    )
                }
                throw error
            } catch (error: Exception) {
                CloseGeminiAppResult.Failure(
                    AutomationUiText.unknownCloseErrorMessage(error)
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

    fun terminateSelfApp() {
        val state = _uiState.value
        if (!state.canCloseSelfApp) {
            _uiState.update {
                it.copy(
                    geminiCloseMessage = AutomationUiText.selfAppTerminateUnavailableMessage(it)
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isClosingGemini = true,
                geminiCloseMessage = AutomationUiText.selfAppTerminateStartingText()
            )
        }
        scope.launch {
            val result = try {
                withContext(dispatchers.io) {
                    terminateSelfApp.close()
                }
            } catch (error: CancellationException) {
                _uiState.update {
                    it.copy(
                        isClosingGemini = false,
                        geminiCloseMessage = AutomationUiText.selfAppTerminateCanceledText()
                    )
                }
                throw error
            } catch (error: Exception) {
                CloseGeminiAppResult.Failure(
                    AutomationUiText.unknownCloseErrorMessage(error)
                )
            }

            // 성공 시 프로세스가 이미 종료될 수 있어 UI 갱신이 안 될 수 있다.
            _uiState.update {
                it.copy(
                    isClosingGemini = false,
                    geminiCloseMessage = AutomationUiText.selfAppTerminateResultMessage(result)
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
        publishEditorSession(
            session = promptEditorSession.afterUndo(previous),
            canUndoPromptEdit = hasPromptUndo()
        )
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
        _uiState.update { state ->
            if (!state.environmentStatus.isAccessibilityServiceEnabled) {
                state.copy(showAccessibilityPrompt = true, showSettings = false)
            } else {
                state.copy(showSettings = true, showAccessibilityPrompt = false)
            }
        }
    }

    /** 접근성 확인 팝업에서 「이동」: 팝업만 닫고 시스템 설정 이동은 UI에서 처리. */
    fun confirmAccessibilityPrompt() {
        _uiState.update { it.copy(showAccessibilityPrompt = false) }
    }

    /** 접근성 확인 팝업에서 「취소」: 전체 설정 다이얼로그로 진입. */
    fun dismissAccessibilityPromptToSettings() {
        _uiState.update {
            it.copy(showAccessibilityPrompt = false, showSettings = true)
        }
    }

    fun hideSettings() {
        _uiState.update {
            it.copy(showSettings = false, showAccessibilityPrompt = false)
        }
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
                automation.run(request)
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

        automation.cancel()
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
            promptEditorSession = promptEditorSession.withText(restoredPrompt)
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

    private fun replaceWholePromptTemplate(replacement: String) {
        syncEditorTextFromCurrent()
        val currentText = promptTemplateValue
        if (currentText == replacement) return

        recordImmediatePromptUndo(currentText)
        applyPromptTemplateText(replacement)
        promptTemplateValue = replacement
        publishEditorSession(
            session = promptEditorSession.afterWholeReplace(replacement),
            canUndoPromptEdit = hasPromptUndo()
        )
    }

    private fun applyTextMutation(mutation: PromptTextMutation) {
        recordImmediatePromptUndo(mutation.previousTextForUndo)
        val newText = mutation.session.text
        ignoredPromptChangeText = newText
        val start = mutation.selectionStart.coerceIn(0, newText.length)
        val end = mutation.selectionEnd.coerceIn(start, newText.length)
        // 전체 치환 후 커서 위치를 Domain이 지정한 범위로 맞춘다.
        promptTemplateTextFieldState.edit {
            replace(0, length, newText)
            selection = TextRange(start, end)
        }
        promptTemplateValue = newText
        publishEditorSession(mutation.session)
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
            _uiState.value.promptTemplate == currentText &&
            promptEditorSession.text == currentText
        ) {
            return
        }
        setPromptTextOnly(currentText)
        _uiState.update {
            if (it.promptTemplate == currentText) it else it.copy(promptTemplate = currentText)
        }
    }

    private fun syncEditorTextFromCurrent() {
        val currentText = promptTemplateTextFieldState.text.toString()
        if (promptTemplateValue != currentText || promptEditorSession.text != currentText) {
            setPromptTextOnly(currentText)
        }
    }

    private fun setPromptTextOnly(text: String) {
        promptTemplateValue = text
        promptEditorSession = promptEditorSession.withText(text)
    }

    private fun publishEditorSession(
        session: PromptEditorSession,
        canUndoPromptEdit: Boolean? = null
    ) {
        promptEditorSession = session
        promptTemplateValue = session.text
        val message = AutomationUiText.paragraphMessage(session.messageKey)
        _uiState.update { state ->
            val nextCanUndo = canUndoPromptEdit ?: state.canUndoPromptEdit
            if (state.promptTemplate == session.text &&
                state.isParagraphSelectionMode == session.isParagraphSelectionMode &&
                state.selectedParagraphRange == session.selectedParagraphRange &&
                state.paragraphSelectionMessage == message &&
                state.canUndoPromptEdit == nextCanUndo
            ) {
                state
            } else {
                state.copy(
                    promptTemplate = session.text,
                    isParagraphSelectionMode = session.isParagraphSelectionMode,
                    selectedParagraphRange = session.selectedParagraphRange,
                    paragraphSelectionMessage = message,
                    canUndoPromptEdit = nextCanUndo
                )
            }
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

    private companion object {
        const val PROMPT_UNDO_DEBOUNCE_MILLIS = 700L
    }
}
