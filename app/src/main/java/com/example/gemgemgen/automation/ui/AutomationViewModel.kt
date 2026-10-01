// 역할: 화면 액션 인터페이스 구현, 프롬프트 편집, 상용구/와일드카드, 일반/변주 자동화 및 유지보수 상태를 총괄 관리하는 뷰모델입니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.InstructionTab
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.domain.PromptInstructionConfig
import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.automation.domain.isTerminal
import com.example.gemgemgen.automation.usecase.AutomationHistoryRecorder
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.PromptHistoryStore
import com.example.gemgemgen.automation.usecase.PromptInstructionRepository
import com.example.gemgemgen.automation.usecase.PromptSnippetRepository
import com.example.gemgemgen.automation.usecase.VariationPromptRepository
import com.example.gemgemgen.automation.usecase.RunVariationPromptUseCase
import com.example.gemgemgen.automation.usecase.VariationStartDecision
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.LastRunSnapshot
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.RecordAutomationHistoryUseCase
import com.example.gemgemgen.automation.usecase.ExecuteAutomationLoopUseCase
import com.example.gemgemgen.automation.usecase.CoordinateAutomationExecutionUseCase
import com.example.gemgemgen.automation.usecase.OverlayPermissionGateway
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.environment.usecase.EnvironmentGateway
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import com.example.gemgemgen.core.SoundAlertGateway
import com.example.gemgemgen.core.PromptWorkspace
import com.example.gemgemgen.core.PromptHandoffEvent
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

import com.example.gemgemgen.automation.usecase.AppMaintenanceUseCase
import com.example.gemgemgen.automation.usecase.MaintenanceResult

private data class AutomationInitialState(
    val lastRunSnapshot: LastRunSnapshot?,
    val historyItems: List<PromptHistoryItem>,
    val instructionConfig: PromptInstructionConfig,
    val variationConfig: VariationPromptConfig
)

class AutomationViewModel(
    private val checkEnvironmentStatus: EnvironmentGateway,
    private val clipboardGateway: ClipboardGateway,
    private val lastRunSnapshotStore: LastRunSnapshotStore,
    private val automation: ExecuteAutomationLoopUseCase,
    private val appMaintenance: AppMaintenanceUseCase,
    checkAutomationStart: CheckAutomationStartUseCase? = null,
    private val wildcardFileRepository: WildcardFileRepository? = null,
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase,
    private val soundAlertGateway: SoundAlertGateway = SoundAlertGateway {},
    private val promptHistoryStore: PromptHistoryStore? = null,
    private val themePaletteStore: com.example.gemgemgen.ui.theme.ThemePaletteStore? = null,
    private val promptWorkspace: PromptWorkspace? = null,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    private val executeAutomation: CoordinateAutomationExecutionUseCase,
    private val promptInstructionRepository: PromptInstructionRepository? = null,
    private val variationPromptRepository: VariationPromptRepository? = null,
    private val promptSnippetRepository: PromptSnippetRepository? = null,
    private val runVariationPrompt: RunVariationPromptUseCase? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel(), AutomationScreenActions {
    private val scope = coroutineScope ?: viewModelScope
    private var automationPreparationJob: Job? = null
    private var isRemoteRunActive = false
    private val promptEditor = PromptEditorCoordinator(
        clipboardGateway = clipboardGateway,
        scope = scope,
        dispatchers = dispatchers,
        onPromptTextChanged = promptWorkspace?.let { it::updateCurrentPrompt }
    )
    val promptTemplateTextFieldState: TextFieldState
        get() = promptEditor.textFieldState

    private val _uiState = MutableStateFlow(
        AutomationUiState(
            selectedThemePalette = themePaletteStore?.currentPalette?.value
                ?: com.example.gemgemgen.ui.theme.AppThemePalette.DEFAULT,
            selectedThemeMode = themePaletteStore?.currentMode?.value
                ?: com.example.gemgemgen.ui.theme.AppThemeMode.DEFAULT
        )
    )
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()
    private val _automationBarUiState = MutableStateFlow(AutomationBarUiState())
    val automationBarUiState: StateFlow<AutomationBarUiState> =
        _automationBarUiState.asStateFlow()

    init {
        scope.launch {
            promptEditor.editorUiState.collect { editorState ->
                _uiState.update { current ->
                    current.copy(
                        promptTemplate = editorState.promptTemplate,
                        editor = editorState
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
            var lastRemoteMode: AutomationMode? = null
            var lastRemoteRunState: AutomationRunState? = null
            manageRemoteAutomation.status.collect { status ->
                val shouldSyncRunState = status.mode != AutomationMode.NORMAL &&
                    (status.mode != lastRemoteMode || status.automationState != lastRemoteRunState)
                lastRemoteMode = status.mode
                lastRemoteRunState = status.automationState
                _uiState.update { current ->
                    current.copy(
                        automationMode = status.mode,
                        remoteAutomationStatus = status
                    )
                }
                if (shouldSyncRunState) {
                    handleAutomationState(status.automationState)
                }
            }
        }
        loadInitialState()
        promptWorkspace?.let { workspace ->
            workspace.segmentReplacer = ::replacePromptTemplateSegment
            scope.launch {
                workspace.handoffEvents.collect { event ->
                    when (event) {
                        is PromptHandoffEvent.ReplaceEntirely -> {
                            replacePromptTemplateEntirely(event.replacement)
                        }
                    }
                }
            }
        }
    }

    // AutomationScreenActions 인터페이스 위임 구현
    override fun onRunAutomation() { runAutomation() }
    override fun onPromptTemplateChange(value: String) { promptEditor.onPromptTemplateChange(value, updateTextFieldState = true) }
    override fun onRunVariation(selectedText: String?) { runVariation(selectedText) }
    override fun onConfirmAccessibilityPrompt() { confirmAccessibilityPrompt() }

    override fun onSelectThemePalette(palette: com.example.gemgemgen.ui.theme.AppThemePalette) {
        themePaletteStore?.setPalette(palette)
        _uiState.update { it.copy(selectedThemePalette = palette) }
    }

    override fun onSelectThemeMode(mode: com.example.gemgemgen.ui.theme.AppThemeMode) {
        themePaletteStore?.setThemeMode(mode)
        _uiState.update { it.copy(selectedThemeMode = mode) }
    }

    fun onPromptTemplateChange(value: String, updateTextFieldState: Boolean = true) =
        promptEditor.onPromptTemplateChange(value, updateTextFieldState)

    fun onPromptTemplateFromEditor(value: String) =
        promptEditor.onPromptTemplateFromEditor(value)

    override fun toggleParagraphSelectionMode() = promptEditor.toggleParagraphSelectionMode()

    override fun selectPromptParagraphAt(offset: Int) = promptEditor.selectPromptParagraphAt(offset)

    override fun deleteSelectedPromptParagraph() = promptEditor.deleteSelectedPromptParagraph()

    override fun cancelParagraphSelection() = promptEditor.cancelParagraphSelection()

    override fun onTargetAppSelected(targetApp: AutomationTargetApp) {
        _uiState.update {
            if (it.isRunning) it else it.copy(selectedTargetApp = targetApp)
        }
    }

    override fun onFlowImageCountSelected(count: Int) {
        _uiState.update {
            if (it.isRunning) it else it.copy(flowImageCount = count)
        }
    }

    override fun onRepeatCountChange(value: String) {
        val normalized = RepeatCountParser.normalizeInput(value)
        val state = _uiState.value
        if (!state.isRunning) {
            publishRepeatCountText(normalized)
            return
        }
        if (state.automationMode == AutomationMode.SENDER) return
        if (normalized.isEmpty()) {
            publishRepeatCountText(normalized)
            return
        }
        val requested = RepeatCountParser.parse(normalized)
        val applied = (automation.updateRepeatCount(requested) ?: requested).toString()
        publishRepeatCountText(applied)
        scope.launch {
            withContext(dispatchers.io) {
                lastRunSnapshotStore.save(
                    LastRunSnapshot(
                        promptTemplate = state.promptTemplate,
                        repeatCountText = applied,
                        targetApp = state.selectedTargetApp,
                        flowImageCount = state.flowImageCount
                    )
                )
            }
        }
    }

    private fun publishRepeatCountText(text: String) {
        _uiState.update { it.copy(repeatCountText = text) }
        _automationBarUiState.update { it.copy(repeatCountText = text) }
    }

    override fun importPromptFromClipboard() = promptEditor.importPromptFromClipboard()

    /** 외부(분석 저장 등)에서 프롬프트 템플릿 전체를 교체한다. Undo 가능. */
    fun replacePromptTemplateEntirely(replacement: String) =
        promptEditor.replaceWholePromptTemplate(replacement)

    /** 현재 프롬프트의 나머지 내용은 보존하고 일치하는 대상 구간만 교체한다. */
    fun replacePromptTemplateSegment(
        expectedSegment: String,
        replacement: String,
        preferredStartIndex: Int
    ): Int? = promptEditor.replacePromptTemplateSegment(
        expectedSegment = expectedSegment,
        replacement = replacement,
        preferredStartIndex = preferredStartIndex
    )

    override fun copyPromptToClipboard() = promptEditor.copyPromptToClipboard()

    /**
     * 프롬프트 템플릿 맨 앞에 상단 인스트럭션을 붙인다.
     * 문구가 설정되어 있지 않으면 설정 다이얼로그를 띄운다.
     */
    override fun insertTopInstruction() {
        val config = _uiState.value.promptInstructionConfig
        if (config.topInstruction.isBlank()) {
            openInstructionConfigDialog(InstructionTab.TOP)
            return
        }
        promptEditor.insertTopInstruction(config.topInstruction)
    }

    /**
     * 프롬프트 템플릿 맨 뒤에 하단 인스트럭션을 붙인다.
     * 문구가 설정되어 있지 않으면 설정 다이얼로그를 띄워 입력을 유도한다.
     */
    override fun insertBottomInstruction() {
        val config = _uiState.value.promptInstructionConfig
        val bottom = config.bottomInstruction
        if (bottom.isNullOrBlank()) {
            openInstructionConfigDialog(InstructionTab.BOTTOM)
            return
        }
        promptEditor.insertBottomInstruction(bottom)
    }

    override fun openInstructionConfigDialog(initialTab: InstructionTab) =
        _uiState.update {
            it.copy(
                showInstructionConfigDialog = true,
                instructionConfigDialogInitialTab = initialTab
            )
        }

    override fun closeInstructionConfigDialog() =
        _uiState.update { it.copy(showInstructionConfigDialog = false) }

    override fun saveInstructionConfig(config: PromptInstructionConfig) {
        scope.launch {
            withContext(dispatchers.io) {
                promptInstructionRepository?.save(config)
            }
        }
        _uiState.update {
            it.copy(
                promptInstructionConfig = config,
                showInstructionConfigDialog = false
            )
        }
    }

    override fun openVariationPromptConfigDialog() =
        _uiState.update { it.copy(showVariationPromptConfigDialog = true) }

    override fun closeVariationPromptConfigDialog() =
        _uiState.update { it.copy(showVariationPromptConfigDialog = false) }

    override fun saveVariationPromptConfig(config: VariationPromptConfig) {
        scope.launch {
            withContext(dispatchers.io) {
                variationPromptRepository?.save(config)
            }
        }
        _uiState.update {
            it.copy(
                variationPromptConfig = config,
                showVariationPromptConfigDialog = false
            )
        }
    }

    /** 현재 기기에서 Gemini 새 채팅을 열고 변주 프롬프트를 붙여넣기만 합니다. */
    fun runVariation(selectedText: String? = null): VariationStartDecision {
        val state = _uiState.value
        if (!state.canRunVariation) {
            val reason = state.variationUnavailableReason ?: "변주를 지금 실행할 수 없습니다."
            return rejectVariation(reason)
        }

        val useCase = runVariationPrompt ?: return rejectVariation("변주 자동화가 준비되지 않았습니다.")

        val targetText = state.variationPromptConfig.resolveTarget(
            fullText = promptEditor.currentPromptTemplateText(),
            explicitSelectedText = selectedText,
            isParagraphSelectionMode = state.editor.isParagraphSelectionMode,
            selectedParagraphRange = state.editor.selectedParagraphRange
        )
        val resolvedPrompt = state.variationPromptConfig.buildPrompt(targetText)

        return useCase.start(
            prompt = resolvedPrompt,
            onStateChange = ::handleVariationState
        )
    }

    private fun rejectVariation(message: String): VariationStartDecision.Rejected {
        handleVariationState(AutomationRunState.Failure(message))
        return VariationStartDecision.Rejected(message)
    }

    /**
     * 추천 칩 탭 (Candidate 직접 전달): 커서 기준 현재 단어를 치환.
     */
    override fun applySuggestion(candidate: WildcardTokenAutocomplete.Candidate) =
        promptEditor.applySuggestion(candidate)

    /** 와일드카드 폴더 및 상용구 목록으로 추천 후보를 다시 읽는다. */
    fun refreshAutocompleteCandidates() {
        scope.launch {
            val (combined, snippets) = withContext(dispatchers.io) {
                loadSortedCandidatesAndSnippets()
            }
            promptEditor.updateAutocompleteCandidates(combined)
            _uiState.update { state ->
                state.copy(promptSnippets = snippets)
            }
        }
    }

    private fun loadSortedCandidatesAndSnippets(): Pair<List<WildcardTokenAutocomplete.Candidate>, List<PromptSnippet>> {
        val wildcardCandidates = runCatching {
            WildcardTokenAutocomplete.candidatesFromFileNames(
                wildcardFileRepository?.listFiles().orEmpty().map { it.fileName }
            )
        }.getOrDefault(emptyList())
        val snippets = runCatching { promptSnippetRepository?.load().orEmpty() }.getOrDefault(emptyList())
        val snippetCandidates = WildcardTokenAutocomplete.candidatesFromSnippets(snippets)
        val combined = (wildcardCandidates + snippetCandidates).sortedWith(
            compareBy<WildcardTokenAutocomplete.Candidate> { it.name.length }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
        return combined to snippets
    }

    override fun showPromptSnippetDialog() {
        _uiState.update { it.copy(showPromptSnippetDialog = true) }
    }

    override fun dismissPromptSnippetDialog() {
        _uiState.update { it.copy(showPromptSnippetDialog = false) }
    }

    override fun addPromptSnippet(shortcut: String, content: String) {
        scope.launch {
            withContext(dispatchers.io) {
                val current = promptSnippetRepository?.load().orEmpty().toMutableList()
                current.removeAll { it.shortcut.equals(shortcut.trim(), ignoreCase = true) }
                current.add(
                    PromptSnippet(
                        shortcut = shortcut.trim(),
                        content = content.trim()
                    )
                )
                promptSnippetRepository?.save(current)
            }
            refreshAutocompleteCandidates()
        }
    }

    override fun deletePromptSnippet(id: String) {
        scope.launch {
            withContext(dispatchers.io) {
                val current = promptSnippetRepository?.load().orEmpty().toMutableList()
                current.removeAll { it.id == id }
                promptSnippetRepository?.save(current)
            }
            refreshAutocompleteCandidates()
        }
    }

    override fun updatePromptSnippet(id: String, shortcut: String, content: String) {
        scope.launch {
            withContext(dispatchers.io) {
                val current = promptSnippetRepository?.load().orEmpty().toMutableList()
                val index = current.indexOfFirst { it.id == id }
                if (index != -1) {
                    current[index] = current[index].copy(
                        shortcut = shortcut.trim(),
                        content = content.trim()
                    )
                    promptSnippetRepository?.save(current)
                }
            }
            refreshAutocompleteCandidates()
        }
    }

    override fun replaceSelectedPromptParagraph(replacement: String) =
        promptEditor.replaceSelectedPromptParagraph(replacement)

    override fun closeGeminiApp() = executeMaintenance(
        canExecute = AutomationUiState::canCloseGemini,
        unavailableMessage = AutomationUiText::geminiRestartUnavailableMessage,
        startingText = AutomationUiText.GEMINI_RESTART_STARTING_TEXT,
        canceledText = AutomationUiText.GEMINI_RESTART_CANCELED_TEXT,
        action = appMaintenance::restartGemini
    )

    override fun terminateSelfApp() = executeMaintenance(
        canExecute = AutomationUiState::canCloseSelfApp,
        unavailableMessage = AutomationUiText::selfAppTerminateUnavailableMessage,
        startingText = AutomationUiText.SELF_APP_TERMINATE_STARTING_TEXT,
        canceledText = AutomationUiText.SELF_APP_TERMINATE_CANCELED_TEXT,
        action = appMaintenance::terminateSelf
    )

    override fun cleanDeviceMemory() {
        val state = _uiState.value
        if (!state.canCleanMemory) {
            _uiState.update {
                it.copy(maintenanceState = MaintenanceState(isBusy = false, message = AutomationUiText.memoryCleanupUnavailableMessage(it)))
            }
            return
        }

        if (state.isRunning || state.isVariationRunning) {
            val nextScheduled = !state.isMemoryCleanupScheduled
            _uiState.update {
                it.copy(
                    isMemoryCleanupScheduled = nextScheduled,
                    maintenanceState = MaintenanceState(
                        isBusy = false,
                        message = if (nextScheduled) {
                            AutomationUiText.MEMORY_CLEANUP_SCHEDULED_TEXT
                        } else {
                            AutomationUiText.MEMORY_CLEANUP_SCHEDULE_CANCELED_TEXT
                        }
                    )
                )
            }
            return
        }

        executeCleanDeviceMemory()
    }

    private fun executeCleanDeviceMemory() {
        val mode = _uiState.value.automationMode
        executeMaintenance(
            canExecute = AutomationUiState::canCleanMemory,
            unavailableMessage = AutomationUiText::memoryCleanupUnavailableMessage,
            startingText = AutomationUiText.memoryCleanupStartingText(mode),
            canceledText = AutomationUiText.memoryCleanupCanceledText(mode),
            action = { performMemoryCleanup(mode) }
        )
    }

    private suspend fun performMemoryCleanup(mode: AutomationMode): MaintenanceResult =
        appMaintenance.cleanMemory(mode)

    private fun executeMaintenance(
        canExecute: (AutomationUiState) -> Boolean,
        unavailableMessage: (AutomationUiState) -> String,
        startingText: String,
        canceledText: String,
        action: suspend () -> MaintenanceResult
    ) {
        val state = _uiState.value
        if (!canExecute(state)) {
            _uiState.update { it.copy(maintenanceState = MaintenanceState(isBusy = false, message = unavailableMessage(it))) }
            return
        }

        _uiState.update { it.copy(maintenanceState = MaintenanceState(isBusy = true, message = startingText)) }
        scope.launch {
            val result = try {
                withContext(dispatchers.io) {
                    action()
                }
            } catch (error: CancellationException) {
                _uiState.update { it.copy(maintenanceState = MaintenanceState(isBusy = false, message = canceledText)) }
                throw error
            } catch (error: Exception) {
                MaintenanceResult.Failure(error.message ?: "작업 중 오류가 발생했습니다.")
            }

            _uiState.update { it.copy(maintenanceState = MaintenanceState(isBusy = false, message = result.displayMessage)) }
        }
    }

    override fun navigatePromptHistoryBack() =
        promptEditor.navigatePromptHistoryBack()

    override fun navigatePromptHistoryForward() =
        promptEditor.navigatePromptHistoryForward()

    override fun refreshStatus() {
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

    override fun showSettings() {
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
    fun confirmAccessibilityPrompt() =
        _uiState.update { it.copy(showAccessibilityPrompt = false) }

    /** 접근성 확인 팝업에서 「취소」: 전체 설정 다이얼로그로 진입. */
    override fun dismissAccessibilityPromptToSettings() =
        _uiState.update { it.copy(showAccessibilityPrompt = false, showSettings = true) }

    override fun hideSettings() =
        _uiState.update { it.copy(showSettings = false, showAccessibilityPrompt = false) }

    fun runAutomation(): AutomationStartDecision {
        promptEditor.syncPromptTemplateFromTextField()
        val state = uiState.value
        val isStartInProgress = automationPreparationJob?.isActive == true
        val decision = executeAutomation.decideStart(
            mode = state.automationMode,
            environmentStatus = state.environmentStatus,
            targetApp = state.selectedTargetApp,
            promptTemplate = state.promptTemplate,
            isRunning = state.isRunning,
            remoteAutomationStatus = state.remoteAutomationStatus,
            isVariationRunning = state.isVariationRunning,
            isMaintenanceBusy = state.isMaintenanceBusy,
            isStartInProgress = isStartInProgress
        )
        if (decision !is AutomationStartDecision.Started && decision !is AutomationStartDecision.RemoteStarted) {
            return decision
        }

        promptEditor.onAutomationStarted(state.promptTemplate)

        cancelParagraphSelection()
        val request = AutomationRunRequest(
            promptTemplate = state.promptTemplate,
            repeatCountText = state.repeatCountText,
            targetApp = state.selectedTargetApp,
            flowImageCount = state.flowImageCount
        )

        val isRemote = decision is AutomationStartDecision.RemoteStarted
        if (isRemote) isRemoteRunActive = true
        val initialStep = if (isRemote) "S25 FE로 요청 전송 중" else "자동화 준비 중"
        handleAutomationState(
            AutomationRunState.Running(initialStep),
            additionalUpdate = { it.copy(isMemoryCleanupScheduled = false) }
        )

        val job = scope.launch {
            if (isRemote) {
                executeRemoteAutomationRequest(request)
            } else {
                executeLocalAutomationRequest(request)
            }
        }
        automationPreparationJob = job
        job.invokeOnCompletion {
            if (automationPreparationJob == job) {
                automationPreparationJob = null
            }
        }
        return decision
    }

    private suspend fun executeRemoteAutomationRequest(request: AutomationRunRequest) {
        val result = executeAutomation.executeRemote(request)
        if (result is RemoteActionResult.Failure) {
            handleAutomationState(AutomationRunState.Failure(result.message))
        }
    }

    private suspend fun executeLocalAutomationRequest(request: AutomationRunRequest) {
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

    override fun toggleSearch(active: Boolean?) = promptEditor.toggleSearch(active)
    override fun setSearchQuery(query: String) = promptEditor.setSearchQuery(query)
    override fun navigateSearchNext() = promptEditor.navigateSearchNext()
    override fun navigateSearchPrevious() = promptEditor.navigateSearchPrevious()
    override fun closeSearch() = promptEditor.closeSearch()

    override fun cancelAutomation() {
        _uiState.update { it.copy(isMemoryCleanupScheduled = false) }
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

    override fun onAutomationModeSelected(mode: AutomationMode) {
        if (_uiState.value.isRunning || _uiState.value.isVariationRunning) return
        isRemoteRunActive = false
        handleAutomationState(AutomationRunState.Idle)
        manageRemoteAutomation.selectMode(mode)
    }

    override fun pairRemoteDevice(pairingCode: String) {
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

    override fun disconnectRemoteDevice() {
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
            val message = when (result) {
                is RemoteActionResult.Success -> "원격 연결을 끊었습니다."
                is RemoteActionResult.Failure -> result.message
            }
            _uiState.update {
                it.copy(isDisconnectingRemote = false, remoteDisconnectMessage = message)
            }
        }
    }

    private fun loadInitialState() {
        scope.launch {
            val (initialState, report, candidatesAndSnippets) = withContext(dispatchers.io) {
                Triple(
                    AutomationInitialState(
                        lastRunSnapshotStore.load(),
                        promptHistoryStore?.load().orEmpty(),
                        promptInstructionRepository?.load() ?: PromptInstructionConfig.DEFAULT,
                        variationPromptRepository?.load() ?: VariationPromptConfig.DEFAULT
                    ),
                    checkEnvironmentStatus.check(),
                    loadSortedCandidatesAndSnippets()
                )
            }
            val (combined, snippets) = candidatesAndSnippets
            val snapshot = initialState.lastRunSnapshot
            val history = initialState.historyItems
            val defaultRepeat = AppDefaults.DEFAULT_REPEAT_COUNT.toString()
            val restoredPrompt = _uiState.value.promptTemplate.ifBlank { snapshot?.promptTemplate.orEmpty() }

            promptEditor.updateAutocompleteCandidates(combined)
            promptEditor.restorePrompt(restoredPrompt)
            promptEditor.syncHistoryItems(history.map { it.prompt })

            _uiState.update {
                it.copy(
                    promptTemplate = restoredPrompt,
                    repeatCountText = if (it.repeatCountText == defaultRepeat) snapshot?.repeatCountText?.ifBlank { defaultRepeat } ?: defaultRepeat else it.repeatCountText,
                    selectedTargetApp = snapshot?.targetApp ?: it.selectedTargetApp,
                    flowImageCount = snapshot?.flowImageCount ?: it.flowImageCount,
                    environmentStatus = report.status,
                    environmentSetupInfo = report.setupInfo,
                    promptSnippets = snippets,
                    promptInstructionConfig = initialState.instructionConfig,
                    variationPromptConfig = initialState.variationConfig
                )
            }
            val restoredState = uiState.value
            _automationBarUiState.value = AutomationBarUiState(
                repeatCountText = restoredState.repeatCountText,
                automationState = restoredState.automationState
            )
        }
    }

    private fun handleAutomationState(
        state: AutomationRunState,
        additionalUpdate: ((AutomationUiState) -> AutomationUiState)? = null
    ) {
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
        _uiState.update { current ->
            val withAdditional = additionalUpdate?.invoke(current) ?: current
            val coarseState = withAdditional.automationState.coarseAutomationStateFor(state)
            if (withAdditional.automationState == coarseState && additionalUpdate == null) {
                withAdditional
            } else {
                withAdditional.copy(automationState = coarseState)
            }
        }
        _automationBarUiState.update {
            if (it.automationState == state) it else it.copy(automationState = state)
        }

        var shouldExecuteScheduledCleanup = false
        if (state.isTerminal()) {
            _uiState.update { current ->
                if (current.isMemoryCleanupScheduled) {
                    if (state == AutomationRunState.Success || state is AutomationRunState.Failure) {
                        shouldExecuteScheduledCleanup = true
                    }
                    current.copy(isMemoryCleanupScheduled = false)
                } else {
                    current
                }
            }
        }

        if (shouldExecuteScheduledCleanup) {
            scope.launch {
                delay(300L)
                executeCleanDeviceMemory()
            }
        }
    }

    private fun handleVariationState(state: AutomationRunState) {
        _uiState.update { it.copy(variationAutomationState = state) }
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

}
