package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.PromptHistoryStore
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.CloseGeminiAppUseCase
import com.example.gemgemgen.automation.usecase.CleanDeviceMemoryUseCase
import com.example.gemgemgen.automation.usecase.GeminiAppCloser
import com.example.gemgemgen.automation.usecase.LastRunSnapshot
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.MemoryCleanupGateway
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult
import com.example.gemgemgen.automation.usecase.RecordAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.RunAutomationUseCase
import com.example.gemgemgen.automation.usecase.ExecuteAutomationUseCase
import com.example.gemgemgen.automation.usecase.StartAutomationUseCase
import com.example.gemgemgen.automation.usecase.OverlayPermissionGateway
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.environment.usecase.CheckEnvironmentStatusUseCase
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.SaveWildcardFolderUseCase
import com.example.gemgemgen.wildcard.usecase.FolderSelectionResult
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import com.example.gemgemgen.remote.usecase.NoOpRemoteAutomationGateway
import com.example.gemgemgen.core.NoOpSoundAlertGateway
import com.example.gemgemgen.core.SoundAlertGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val cleanDeviceMemoryUseCase: CleanDeviceMemoryUseCase = CleanDeviceMemoryUseCase(
        object : MemoryCleanupGateway {
            override suspend fun cleanMemory(): MemoryCleanupResult {
                return MemoryCleanupResult.AccessibilityUnavailable
            }
        }
    ),
    private val checkAutomationStart: CheckAutomationStartUseCase =
        CheckAutomationStartUseCase(OverlayPermissionGateway { true }),
    private val wildcardFileRepository: WildcardFileRepository = EmptyWildcardFileRepository,
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase =
        ManageRemoteAutomationUseCase(NoOpRemoteAutomationGateway()),
    private val soundAlertGateway: SoundAlertGateway = NoOpSoundAlertGateway,
    private val promptHistoryStore: PromptHistoryStore? = null,
    private val themePaletteStore: com.example.gemgemgen.ui.theme.ThemePaletteStore? = null,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    private val startAutomation: StartAutomationUseCase = StartAutomationUseCase(
        checkAutomationStart = checkAutomationStart,
        automationStartRecorder = RecordAutomationStartUseCase(
            lastRunSnapshotStore = lastRunSnapshotStore,
            clipboardGateway = clipboardGateway,
            promptHistoryStore = promptHistoryStore,
            dispatchers = dispatchers
        ),
        automation = automation
    ),
    private val executeAutomation: ExecuteAutomationUseCase = ExecuteAutomationUseCase(
        startAutomation = startAutomation,
        manageRemoteAutomation = manageRemoteAutomation,
        promptHistoryStore = promptHistoryStore
    ),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private var automationPreparationJob: Job? = null
    private var isRemoteRunActive = false
    private val promptEditor = PromptEditorCoordinator(
        clipboardGateway = clipboardGateway,
        scope = scope,
        dispatchers = dispatchers
    )
    val promptTemplateTextFieldState: TextFieldState
        get() = promptEditor.textFieldState

    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedThemePalette = themePaletteStore?.currentPalette?.value
                ?: com.example.gemgemgen.ui.theme.AppThemePalette.DEFAULT,
            selectedThemeMode = themePaletteStore?.currentMode?.value
                ?: com.example.gemgemgen.ui.theme.AppThemeMode.DEFAULT
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val _automationBarUiState = MutableStateFlow(AutomationBarUiState())
    val automationBarUiState: StateFlow<AutomationBarUiState> =
        _automationBarUiState.asStateFlow()

    init {
        themePaletteStore?.let { store ->
            scope.launch {
                store.currentPalette.collect { palette ->
                    _uiState.update { it.copy(selectedThemePalette = palette) }
                }
            }
            scope.launch {
                store.currentMode.collect { mode ->
                    _uiState.update { it.copy(selectedThemeMode = mode) }
                }
            }
        }
        scope.launch {
            promptEditor.editorUiState.collect { editorState ->
                _uiState.update { current ->
                    current.copy(
                        promptTemplate = editorState.promptTemplate,
                        isParagraphSelectionMode = editorState.isParagraphSelectionMode,
                        selectedParagraphRange = editorState.selectedParagraphRange,
                        paragraphSelectionMessage = editorState.paragraphSelectionMessage,
                        canUndoPromptEdit = editorState.canUndoPromptEdit
                    )
                }
            }
        }
        scope.launch {
            automation.runState.collect { state ->
                handleAutomationState(state)
            }
        }
        scope.launch {
            manageRemoteAutomation.status.collect { status ->
                _uiState.update { current ->
                    current.copy(
                        automationMode = status.mode,
                        remoteAutomationStatus = status,
                        automationState = if (status.mode == AutomationMode.NORMAL) {
                            current.automationState
                        } else {
                            status.automationState
                        }
                    )
                }
                if (status.mode != AutomationMode.NORMAL) {
                    _automationBarUiState.update {
                        it.copy(automationState = status.automationState)
                    }
                }
            }
        }
        loadInitialState()
        refreshStatus()
    }

    fun onSelectThemePalette(palette: com.example.gemgemgen.ui.theme.AppThemePalette) {
        themePaletteStore?.setPalette(palette) ?: _uiState.update { it.copy(selectedThemePalette = palette) }
    }

    fun onSelectThemeMode(mode: com.example.gemgemgen.ui.theme.AppThemeMode) {
        themePaletteStore?.setThemeMode(mode) ?: _uiState.update { it.copy(selectedThemeMode = mode) }
    }

    fun onPromptTemplateChange(value: String) {
        promptEditor.onPromptTemplateChange(value)
    }

    fun onPromptTemplateFromEditor(value: String) {
        promptEditor.onPromptTemplateFromEditor(value)
    }

    fun onPromptTemplateChange(value: String, updateTextFieldState: Boolean) {
        promptEditor.onPromptTemplateChange(value, updateTextFieldState)
    }

    fun toggleParagraphSelectionMode() {
        promptEditor.toggleParagraphSelectionMode()
    }

    fun selectPromptParagraphAt(offset: Int) {
        promptEditor.selectPromptParagraphAt(offset)
    }

    fun deleteSelectedPromptParagraph() {
        promptEditor.deleteSelectedPromptParagraph()
    }

    fun cancelParagraphSelection() {
        promptEditor.cancelParagraphSelection()
    }

    fun onTargetAppSelected(targetApp: AutomationTargetApp) {
        _uiState.update {
            if (it.isRunning) it else it.copy(selectedTargetApp = targetApp)
        }
    }

    fun onRepeatCountChange(value: String) {
        val normalized = RepeatCountParser.normalizeInput(value)
        if (_uiState.value.isRunning) {
            if (_uiState.value.automationMode == AutomationMode.SENDER) return
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
        promptEditor.importPromptFromClipboard()
    }

    /** TextField 최신 값을 반영한 현재 원본 프롬프트. 분석 탭 가져오기 등에서 사용. */
    fun currentPromptTemplateText(): String {
        return promptEditor.currentPromptTemplateText()
    }

    /** 외부(분석 저장 등)에서 프롬프트 템플릿 전체를 교체한다. Undo 가능. */
    fun replacePromptTemplateEntirely(replacement: String) {
        promptEditor.replacePromptTemplateEntirely(replacement)
    }

    /** 현재 프롬프트의 나머지 내용은 보존하고 일치하는 대상 구간만 교체한다. */
    fun replacePromptTemplateSegment(
        expectedSegment: String,
        replacement: String,
        preferredStartIndex: Int
    ): Int? {
        return promptEditor.replacePromptTemplateSegment(
            expectedSegment = expectedSegment,
            replacement = replacement,
            preferredStartIndex = preferredStartIndex
        )
    }

    fun copyPromptToClipboard() {
        promptEditor.copyPromptToClipboard(_uiState.value.isRunning)
    }

    fun pastePromptFromClipboard() {
        promptEditor.pastePromptFromClipboard()
    }

    /**
     * 프롬프트 템플릿 맨 앞에 System Instruction을 붙인다.
     * 본문이 있으면 SI와 본문 사이에 빈 줄 1개. Undo 가능. 실행 중에는 무시.
     * 연속 탭 시 SI가 다시 앞에 붙는다.
     */
    fun insertSystemInstruction() {
        promptEditor.insertSystemInstruction(_uiState.value.isRunning)
    }

    /**
     * 추천 칩 탭: 커서 기준 현재 단어를 와일드카드 토큰으로 교체.
     * Undo 가능. 실행 중·문단 선택 모드에서는 무시.
     */
    fun applyWildcardTokenSuggestion(token: String) {
        promptEditor.applyWildcardTokenSuggestion(
            token = token,
            isBlocked = _uiState.value.isRunning,
            candidates = _uiState.value.wildcardTokenCandidates
        )
    }

    /** 와일드카드 폴더의 txt 파일명으로 추천 후보를 다시 읽는다. */
    fun refreshWildcardTokenCandidates() {
        scope.launch {
            val candidates = withContext(dispatchers.io) {
                loadWildcardTokenCandidates()
            }
            _uiState.update { state ->
                if (state.wildcardTokenCandidates == candidates) {
                    state
                } else {
                    state.copy(wildcardTokenCandidates = candidates)
                }
            }
        }
    }

    fun replaceSelectedPromptParagraph(replacement: String) {
        promptEditor.replaceSelectedPromptParagraph(replacement)
    }

    fun closeGeminiApp() {
        runMaintenanceAction(
            canExecute = { it.canCloseGemini },
            unavailableMessage = { AutomationUiText.geminiRestartUnavailableMessage(it) },
            startingText = { AutomationUiText.geminiRestartStartingText() },
            canceledText = { AutomationUiText.geminiRestartCanceledText() },
            action = { closeGeminiApp.close() },
            onFailure = { CloseGeminiAppResult.Failure(AutomationUiText.unknownCloseErrorMessage(it)) },
            resultMessage = { AutomationUiText.geminiRestartResultMessage(it) }
        )
    }

    fun terminateGeminiApp() {
        runMaintenanceAction(
            canExecute = { it.canCloseGemini },
            unavailableMessage = { AutomationUiText.geminiTerminateUnavailableMessage(it) },
            startingText = { AutomationUiText.geminiTerminateStartingText() },
            canceledText = { AutomationUiText.geminiTerminateCanceledText() },
            action = { terminateGeminiApp.close() },
            onFailure = { CloseGeminiAppResult.Failure(AutomationUiText.unknownCloseErrorMessage(it)) },
            resultMessage = { AutomationUiText.geminiTerminateResultMessage(it) }
        )
    }

    fun terminateSelfApp() {
        runMaintenanceAction(
            canExecute = { it.canCloseSelfApp },
            unavailableMessage = { AutomationUiText.selfAppTerminateUnavailableMessage(it) },
            startingText = { AutomationUiText.selfAppTerminateStartingText() },
            canceledText = { AutomationUiText.selfAppTerminateCanceledText() },
            action = { terminateSelfApp.close() },
            onFailure = { CloseGeminiAppResult.Failure(AutomationUiText.unknownCloseErrorMessage(it)) },
            resultMessage = { AutomationUiText.selfAppTerminateResultMessage(it) }
        )
    }

    fun cleanDeviceMemory() {
        runMaintenanceAction(
            canExecute = { it.canCleanMemory },
            unavailableMessage = { AutomationUiText.memoryCleanupUnavailableMessage(it) },
            startingText = { AutomationUiText.memoryCleanupStartingText() },
            canceledText = { AutomationUiText.memoryCleanupCanceledText() },
            action = { cleanDeviceMemoryUseCase.clean() },
            onFailure = { MemoryCleanupResult.Failure(AutomationUiText.unknownMemoryCleanupErrorMessage(it)) },
            resultMessage = { AutomationUiText.memoryCleanupResultMessage(it) }
        )
    }

    private fun <T> runMaintenanceAction(
        canExecute: (MainUiState) -> Boolean,
        unavailableMessage: (MainUiState) -> String,
        startingText: () -> String,
        canceledText: () -> String,
        action: suspend () -> T,
        onFailure: (Exception) -> T,
        resultMessage: (T) -> String
    ) {
        val state = _uiState.value
        if (!canExecute(state)) {
            _uiState.update { it.copy(maintenanceState = MaintenanceState(isBusy = false, message = unavailableMessage(it))) }
            return
        }

        _uiState.update { it.copy(maintenanceState = MaintenanceState(isBusy = true, message = startingText())) }
        scope.launch {
            val result = try {
                withContext(dispatchers.io) {
                    action()
                }
            } catch (error: CancellationException) {
                _uiState.update { it.copy(maintenanceState = MaintenanceState(isBusy = false, message = canceledText())) }
                throw error
            } catch (error: Exception) {
                onFailure(error)
            }

            _uiState.update { it.copy(maintenanceState = MaintenanceState(isBusy = false, message = resultMessage(result))) }
        }
    }

    fun undoPromptEdit() {
        promptEditor.undoPromptEdit(_uiState.value.isRunning)
    }

    fun refreshStatus() {
        scope.launch {
            val (report, candidates) = withContext(dispatchers.io) {
                checkEnvironmentStatus.check() to loadWildcardTokenCandidates()
            }
            _uiState.update {
                it.copy(
                    environmentStatus = report.status,
                    environmentSetupInfo = report.setupInfo,
                    wildcardTokenCandidates = candidates
                )
            }
        }
    }

    private fun loadWildcardTokenCandidates(): List<WildcardTokenAutocomplete.Candidate> {
        return try {
            val fileNames = wildcardFileRepository.listFiles().map { it.fileName }
            WildcardTokenAutocomplete.candidatesFromFileNames(fileNames)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun showSettings() {
        _uiState.update { state ->
            val base = state.copy(remoteDisconnectMessage = "")
            if (!base.environmentStatus.isAccessibilityServiceEnabled) {
                base.copy(showAccessibilityPrompt = true, showSettings = false)
            } else {
                base.copy(showSettings = true, showAccessibilityPrompt = false)
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
        promptEditor.syncPromptTemplateFromTextField()
        val state = uiState.value
        val isStartInProgress = automationPreparationJob?.isActive == true
        val decision = executeAutomation.decideStart(
            canRun = state.canRun,
            isStartInProgress = isStartInProgress,
            mode = state.automationMode
        )

        when (decision) {
            AutomationStartDecision.RemoteStarted -> {
                cancelParagraphSelection()
                isRemoteRunActive = true
                handleAutomationState(AutomationRunState.Running("S25 FE로 요청 전송 중"))
                val request = AutomationRunRequest(
                    promptTemplate = state.promptTemplate,
                    repeatCountText = state.repeatCountText,
                    targetApp = state.selectedTargetApp
                )
                val job = scope.launch {
                    val result = executeAutomation.executeRemote(request, ::handleAutomationState)
                    if (result is RemoteActionResult.Failure) {
                        handleAutomationState(AutomationRunState.Failure(result.message))
                    }
                }
                automationPreparationJob = job
                job.invokeOnCompletion {
                    if (automationPreparationJob == job) automationPreparationJob = null
                }
                return AutomationStartDecision.RemoteStarted
            }
            AutomationStartDecision.Started -> {
                cancelParagraphSelection()
                handleAutomationState(AutomationRunState.Running("자동화 준비 중"))
                val request = AutomationRunRequest(
                    promptTemplate = state.promptTemplate,
                    repeatCountText = state.repeatCountText,
                    targetApp = state.selectedTargetApp
                )
                val job = scope.launch {
                    try {
                        executeAutomation.executeLocal(request)
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
            AutomationStartDecision.PermissionRequired,
            AutomationStartDecision.Rejected -> return decision
        }
    }

    fun openPromptHistory() {
        val items = promptHistoryStore?.load().orEmpty()
        _uiState.update { it.copy(showPromptHistory = true, promptHistoryItems = items) }
    }

    fun closePromptHistory() {
        _uiState.update { it.copy(showPromptHistory = false) }
    }

    fun clearPromptHistory() {
        promptHistoryStore?.clear()
        _uiState.update { it.copy(promptHistoryItems = emptyList()) }
    }

    fun selectPromptHistoryItem(item: PromptHistoryItem) {
        if (_uiState.value.isRunning) return
        promptEditor.restorePrompt(item.prompt)
        closePromptHistory()
    }

    fun cancelAutomation() {
        val wasRemoteRunActive = isRemoteRunActive
        isRemoteRunActive = false
        val preparationJob = automationPreparationJob
        val isPreparationActive = preparationJob?.isActive == true
        automationPreparationJob = null
        preparationJob?.cancel()

        executeAutomation.cancel(
            mode = _uiState.value.automationMode,
            isRemoteRunActive = wasRemoteRunActive,
            isPreparationActive = isPreparationActive,
            onStateChange = ::handleAutomationState,
            onCancelLocal = { automation.cancel() }
        )
    }

    fun onAutomationModeSelected(mode: AutomationMode) {
        if (_uiState.value.isRunning) return
        isRemoteRunActive = false
        handleAutomationState(AutomationRunState.Idle)
        manageRemoteAutomation.selectMode(mode)
    }

    fun pairRemoteDevice(pairingCode: String) {
        if (_uiState.value.automationMode != AutomationMode.SENDER) return
        scope.launch {
            val result = manageRemoteAutomation.pair(pairingCode)
            if (result is RemoteActionResult.Failure) {
                _uiState.update { state ->
                    state.copy(
                        remoteAutomationStatus = state.remoteAutomationStatus.copy(
                            connectionMessage = result.message
                        )
                    )
                }
            }
        }
    }

    fun disconnectRemoteDevice() {
        if (_uiState.value.isDisconnectingRemote) return
        if (_uiState.value.remoteAutomationStatus.automationState is AutomationRunState.Running) {
            _uiState.update {
                it.copy(remoteDisconnectMessage = "원격 자동화를 중지한 뒤 연결을 끊어주세요.")
            }
            return
        }
        scope.launch {
            _uiState.update {
                it.copy(isDisconnectingRemote = true, remoteDisconnectMessage = "")
            }
            val result = manageRemoteAutomation.disconnect()
            _uiState.update { state ->
                when (result) {
                    is RemoteActionResult.Success -> state.copy(
                        isDisconnectingRemote = false,
                        remoteDisconnectMessage = "원격 연결을 끊었습니다."
                    )
                    is RemoteActionResult.Failure -> state.copy(
                        isDisconnectingRemote = false,
                        remoteDisconnectMessage = result.message
                    )
                }
            }
        }
    }

    private fun loadInitialState() {
        scope.launch {
            val (lastRunSnapshot, historyItems) = withContext(dispatchers.io) {
                lastRunSnapshotStore.load() to (promptHistoryStore?.load().orEmpty())
            }
            val current = _uiState.value
            val defaultRepeatCountText = AppDefaults.DEFAULT_REPEAT_COUNT.toString()
            val restoredPrompt = if (current.promptTemplate.isBlank()) {
                lastRunSnapshot?.promptTemplate.orEmpty()
            } else {
                current.promptTemplate
            }
            promptEditor.restorePrompt(restoredPrompt)
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
                    selectedTargetApp = lastRunSnapshot?.targetApp ?: it.selectedTargetApp,
                    promptHistoryItems = historyItems
                )
            }
            val restoredState = uiState.value
            _automationBarUiState.value = AutomationBarUiState(
                repeatCountText = restoredState.repeatCountText,
                automationState = restoredState.automationState
            )
        }
    }

    private fun handleAutomationState(state: AutomationRunState) {
        if (_uiState.value.automationMode == AutomationMode.SENDER && isRemoteRunActive) {
            when (state) {
                is AutomationRunState.Failure -> {
                    isRemoteRunActive = false
                    soundAlertGateway.playShortAlert()
                }
                AutomationRunState.Success,
                AutomationRunState.Stopped -> {
                    isRemoteRunActive = false
                }
                else -> Unit
            }
        }
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

    private companion object {
        private object EmptyWildcardFileRepository : WildcardFileRepository {
            override fun listFiles(): List<WildcardTextFile> = emptyList()
            override fun readFile(file: WildcardTextFile): String = ""
            override fun createFile(fileName: String): WildcardTextFile =
                WildcardTextFile(id = fileName, fileName = fileName)
            override fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile =
                file.copy(fileName = newName)
            override fun writeFile(file: WildcardTextFile, text: String) = Unit
            override fun deleteFile(file: WildcardTextFile) = Unit
        }
    }
}
