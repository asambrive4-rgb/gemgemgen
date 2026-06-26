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
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.automation.domain.isTerminal
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.CloseGeminiAppUseCase
import com.example.gemgemgen.automation.usecase.GeminiAppCloser
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.RunAutomationUseCase
import com.example.gemgemgen.automation.usecase.RunLogger
import com.example.gemgemgen.automation.usecase.OverlayPermissionGateway
import com.example.gemgemgen.automation.ui.AutomationBarUiState
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
    private val runLogger: RunLogger,
    private val lastRunSnapshotStore: LastRunSnapshotStore,
    private val automation: RunAutomationUseCase,
    private val closeGeminiApp: CloseGeminiAppUseCase = CloseGeminiAppUseCase(
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
    private var promptUndoStack: List<String> = emptyList()
    private var pendingPromptUndoSnapshot: String? = null
    private var promptUndoDebounceJob: Job? = null
    private var ignoredPromptChangeText: String? = null
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
        val previous = _uiState.value.promptTemplate
        if (!promptTemplateTextFieldState.text.contentEquals(value)) {
            promptTemplateTextFieldState.setTextAndPlaceCursorAtEnd(value)
        }
        _uiState.update {
            if (it.promptTemplate == value) it else it.copy(promptTemplate = value)
        }
        if (ignoredPromptChangeText == value) {
            ignoredPromptChangeText = null
            return
        }
        if (previous != value) {
            schedulePromptTypingUndo(previous)
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
        scope.launch {
            val text = withContext(dispatchers.io) {
                clipboardGateway.readText()
            }
            val state = _uiState.value
            if (!state.isParagraphSelectionMode) {
                replaceWholePromptTemplate(text)
                return@launch
            }

            val range = state.selectedParagraphRange
            when {
                range == null -> {
                    _uiState.update {
                        it.copy(paragraphSelectionMessage = SELECT_PARAGRAPH_FIRST_MESSAGE)
                    }
                }
                text.isBlank() -> {
                    _uiState.update {
                        it.copy(paragraphSelectionMessage = EMPTY_CLIPBOARD_MESSAGE)
                    }
                }
                else -> replaceSelectedParagraph(range.start, range.endExclusive, text)
            }
        }
    }

    fun closeGeminiApp() {
        val state = _uiState.value
        if (!state.canCloseGemini) {
            _uiState.update {
                it.copy(geminiCloseMessage = geminiCloseUnavailableMessage(it))
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
                    geminiCloseMessage = geminiCloseResultMessage(result)
                )
            }
        }
    }

    fun undoPromptEdit() {
        if (_uiState.value.isRunning) return

        commitPendingPromptUndo()
        val previous = promptUndoStack.firstOrNull() ?: return
        promptUndoStack = promptUndoStack.drop(1)
        applyPromptTemplateText(previous)
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

    fun toggleRecentLogs() {
        refreshLogs()
        _uiState.update { it.copy(showRecentLogs = !it.showRecentLogs) }
    }

    private fun loadInitialState() {
        scope.launch {
            val snapshotAndLogs = withContext(dispatchers.io) {
                lastRunSnapshotStore.load() to runLogger.loadRecent()
            }
            val lastRunSnapshot = snapshotAndLogs.first
            _uiState.update {
                val defaultRepeatCountText = AppDefaults.DEFAULT_REPEAT_COUNT.toString()
                it.copy(
                    promptTemplate = if (it.promptTemplate.isBlank()) {
                        lastRunSnapshot?.promptTemplate.orEmpty()
                    } else {
                        it.promptTemplate
                    },
                    repeatCountText = if (it.repeatCountText == defaultRepeatCountText) {
                        lastRunSnapshot?.repeatCountText
                            ?.ifBlank { defaultRepeatCountText }
                            ?: defaultRepeatCountText
                    } else {
                        it.repeatCountText
                    },
                    selectedTargetApp = lastRunSnapshot?.targetApp ?: it.selectedTargetApp,
                    recentLogs = snapshotAndLogs.second
                )
            }
            applyPromptTemplateText(uiState.value.promptTemplate)
            val restoredState = uiState.value
            _automationBarUiState.value = AutomationBarUiState(
                repeatCountText = restoredState.repeatCountText,
                automationState = restoredState.automationState
            )
        }
    }

    private fun handleAutomationState(state: AutomationRunState) {
        var stateChanged = false
        _uiState.update {
            if (it.automationState == state) {
                it
            } else {
                stateChanged = true
                it.copy(automationState = state)
            }
        }
        if (stateChanged && state.isTerminal()) {
            refreshLogs()
        }
        _automationBarUiState.update { it.copy(automationState = state) }
    }

    private fun refreshLogs() {
        scope.launch {
            val logs = withContext(dispatchers.io) {
                runLogger.loadRecent()
            }
            _uiState.update { it.copy(recentLogs = logs) }
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

    private fun schedulePromptTypingUndo(previous: String) {
        if (pendingPromptUndoSnapshot == null) {
            pendingPromptUndoSnapshot = previous
        }
        promptUndoDebounceJob?.cancel()
        promptUndoDebounceJob = scope.launch {
            delay(PROMPT_UNDO_DEBOUNCE_MILLIS)
            commitPendingPromptUndo()
        }
        updatePromptUndoAvailability()
    }

    private fun recordImmediatePromptUndo(snapshot: String) {
        commitPendingPromptUndo()
        pushPromptUndo(snapshot)
        updatePromptUndoAvailability()
    }

    private fun commitPendingPromptUndo() {
        val snapshot = pendingPromptUndoSnapshot ?: return
        pendingPromptUndoSnapshot = null
        promptUndoDebounceJob?.cancel()
        promptUndoDebounceJob = null
        if (snapshot != _uiState.value.promptTemplate) {
            pushPromptUndo(snapshot)
        }
        updatePromptUndoAvailability()
    }

    private fun pushPromptUndo(snapshot: String) {
        if (promptUndoStack.firstOrNull() == snapshot) return
        promptUndoStack = (listOf(snapshot) + promptUndoStack).take(MAX_PROMPT_UNDO_COUNT)
    }

    private fun updatePromptUndoAvailability() {
        val canUndo = hasPromptUndo()
        _uiState.update {
            if (it.canUndoPromptEdit == canUndo) it else it.copy(canUndoPromptEdit = canUndo)
        }
    }

    private fun hasPromptUndo(): Boolean {
        return pendingPromptUndoSnapshot != null || promptUndoStack.isNotEmpty()
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

    private companion object {
        const val MAX_PROMPT_UNDO_COUNT = 5
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
