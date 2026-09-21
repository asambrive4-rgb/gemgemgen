// 역할: 메인 화면의 프롬프트 편집, 상용구 관리, 와일드카드 자동완성, 일반/변주 자동화, 유지보수 및 환경 상태를 관리하는 뷰모델입니다.
package com.example.gemgemgen.automation.ui

import android.util.Log
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
import com.example.gemgemgen.automation.domain.VariationStartPolicy
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.automation.domain.isTerminal
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.GetPromptSnippetCandidatesUseCase
import com.example.gemgemgen.automation.usecase.GetWildcardTokenCandidatesUseCase
import com.example.gemgemgen.automation.usecase.ManagePromptInstructionUseCase
import com.example.gemgemgen.automation.usecase.PromptHistoryStore
import com.example.gemgemgen.automation.usecase.PromptInstructionRepository
import com.example.gemgemgen.automation.usecase.PromptSnippetRepository
import com.example.gemgemgen.automation.usecase.VariationPromptRepository
import com.example.gemgemgen.automation.usecase.ResolveVariationPromptUseCase
import com.example.gemgemgen.automation.usecase.RunVariationPromptUseCase
import com.example.gemgemgen.automation.usecase.VariationStartDecision
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.GeminiAppCloser
import com.example.gemgemgen.automation.usecase.LastRunSnapshot
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.MemoryCleanupGateway
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult
import com.example.gemgemgen.automation.usecase.RecordAutomationHistoryUseCase
import com.example.gemgemgen.automation.usecase.ExecuteAutomationLoopUseCase
import com.example.gemgemgen.automation.android.AndroidGeminiAccountSwitcherGateway
import com.example.gemgemgen.automation.usecase.OpenGeminiAccountPickerUseCase
import com.example.gemgemgen.automation.usecase.OpenGeminiAccountPickerResult
import com.example.gemgemgen.automation.domain.GeminiAccountSwitchProgressPolicy
import com.example.gemgemgen.automation.usecase.CoordinateAutomationExecutionUseCase
import com.example.gemgemgen.automation.usecase.OverlayPermissionGateway
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.environment.usecase.CheckEnvironmentStatusUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import com.example.gemgemgen.remote.usecase.NoOpRemoteAutomationGateway
import com.example.gemgemgen.core.NoOpSoundAlertGateway
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
    private val checkEnvironmentStatus: CheckEnvironmentStatusUseCase,
    private val clipboardGateway: ClipboardGateway,
    private val lastRunSnapshotStore: LastRunSnapshotStore,
    private val automation: ExecuteAutomationLoopUseCase,
    private val appMaintenance: AppMaintenanceUseCase = AppMaintenanceUseCase(
        geminiAppCloser = object : GeminiAppCloser {
            override suspend fun closeGeminiApp(): CloseGeminiAppResult {
                return CloseGeminiAppResult.AccessibilityUnavailable
            }
        },
        memoryCleanupGateway = object : MemoryCleanupGateway {
            override suspend fun cleanMemory(): MemoryCleanupResult {
                return MemoryCleanupResult.AccessibilityUnavailable
            }
        }
    ),
    private val checkAutomationStart: CheckAutomationStartUseCase =
        CheckAutomationStartUseCase(OverlayPermissionGateway { true }),
    wildcardFileRepository: WildcardFileRepository? = null,
    private val getWildcardTokenCandidates: GetWildcardTokenCandidatesUseCase =
        GetWildcardTokenCandidatesUseCase(wildcardFileRepository),
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase =
        ManageRemoteAutomationUseCase(NoOpRemoteAutomationGateway()),
    private val soundAlertGateway: SoundAlertGateway = NoOpSoundAlertGateway,
    private val promptHistoryStore: PromptHistoryStore? = null,
    private val themePaletteStore: com.example.gemgemgen.ui.theme.ThemePaletteStore? = null,
    private val promptWorkspace: PromptWorkspace? = null,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    private val executeAutomation: CoordinateAutomationExecutionUseCase = CoordinateAutomationExecutionUseCase(
        checkAutomationStart = checkAutomationStart,
        automationHistoryRecorder = RecordAutomationHistoryUseCase(
            lastRunSnapshotStore = lastRunSnapshotStore,
            clipboardGateway = clipboardGateway,
            promptHistoryStore = promptHistoryStore,
            dispatchers = dispatchers
        ),
        automation = automation,
        manageRemoteAutomation = manageRemoteAutomation,
        promptHistoryStore = promptHistoryStore
    ),
    private val openGeminiAccountPicker: OpenGeminiAccountPickerUseCase = OpenGeminiAccountPickerUseCase(
        manageRemoteAutomation = manageRemoteAutomation,
        switcherGateway = AndroidGeminiAccountSwitcherGateway()
    ),
    promptInstructionRepository: PromptInstructionRepository =
        object : PromptInstructionRepository {
            private var current = PromptInstructionConfig.DEFAULT
            override fun load(): PromptInstructionConfig = current
            override fun save(config: PromptInstructionConfig) { current = config }
        },
    private val managePromptInstruction: ManagePromptInstructionUseCase =
        ManagePromptInstructionUseCase(promptInstructionRepository),
    private val variationPromptRepository: VariationPromptRepository? = null,
    private val promptSnippetRepository: PromptSnippetRepository? = null,
    private val getPromptSnippetCandidates: GetPromptSnippetCandidatesUseCase =
        GetPromptSnippetCandidatesUseCase(promptSnippetRepository),
    private val runVariationPrompt: RunVariationPromptUseCase? = null,
    private val resolveVariationPrompt: ResolveVariationPromptUseCase = ResolveVariationPromptUseCase(),
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
                        canNavigateHistoryBack = editorState.canNavigateHistoryBack,
                        canNavigateHistoryForward = editorState.canNavigateHistoryForward,
                        isHistoryIndicatorVisible = editorState.isHistoryIndicatorVisible,
                        historyDotCount = editorState.historyDotCount,
                        activeHistoryDotIndex = editorState.activeHistoryDotIndex,
                        activeSuggestionCandidates = editorState.activeSuggestionCandidates
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
        promptWorkspace?.let { workspace ->
            workspace.segmentReplacer = ::replacePromptTemplateSegment
            scope.launch {
                promptEditor.editorUiState.collect { editorState ->
                    workspace.updateCurrentPrompt(editorState.promptTemplate)
                }
            }
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
        refreshStatus()
    }

    fun onSelectThemePalette(palette: com.example.gemgemgen.ui.theme.AppThemePalette) {
        themePaletteStore?.setPalette(palette) ?: _uiState.update { it.copy(selectedThemePalette = palette) }
    }

    fun onSelectThemeMode(mode: com.example.gemgemgen.ui.theme.AppThemeMode) {
        themePaletteStore?.setThemeMode(mode) ?: _uiState.update { it.copy(selectedThemeMode = mode) }
    }

    fun onPromptTemplateChange(value: String, updateTextFieldState: Boolean = true) =
        promptEditor.onPromptTemplateChange(value, updateTextFieldState)

    fun onPromptTemplateFromEditor(value: String) =
        promptEditor.onPromptTemplateFromEditor(value)

    fun toggleParagraphSelectionMode() = promptEditor.toggleParagraphSelectionMode()

    fun selectPromptParagraphAt(offset: Int) = promptEditor.selectPromptParagraphAt(offset)

    fun deleteSelectedPromptParagraph() = promptEditor.deleteSelectedPromptParagraph()

    fun cancelParagraphSelection() = promptEditor.cancelParagraphSelection()

    fun onTargetAppSelected(targetApp: AutomationTargetApp) {
        _uiState.update {
            if (it.isRunning) it else it.copy(selectedTargetApp = targetApp)
        }
    }

    fun onFlowImageCountSelected(count: Int) {
        _uiState.update {
            if (it.isRunning) it else it.copy(flowImageCount = count)
        }
        persistLastRunSnapshot(flowImageCount = count)
    }

    private fun persistLastRunSnapshot(
        repeatCountText: String = _uiState.value.repeatCountText,
        flowImageCount: Int = _uiState.value.flowImageCount
    ) {
        val state = _uiState.value
        scope.launch {
            withContext(dispatchers.io) {
                lastRunSnapshotStore.save(
                    LastRunSnapshot(
                        promptTemplate = state.promptTemplate,
                        repeatCountText = repeatCountText,
                        targetApp = state.selectedTargetApp,
                        flowImageCount = flowImageCount
                    )
                )
            }
        }
    }

    fun onRepeatCountChange(value: String) {
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
        persistLastRunSnapshot(repeatCountText = applied)
    }

    private fun publishRepeatCountText(text: String) {
        _uiState.update { it.copy(repeatCountText = text) }
        _automationBarUiState.update { it.copy(repeatCountText = text) }
    }

    fun importPromptFromClipboard() = promptEditor.importPromptFromClipboard()

    /** TextField 최신 값을 반영한 현재 원본 프롬프트. 분석 탭 가져오기 등에서 사용. */
    fun currentPromptTemplateText(): String = promptEditor.currentPromptTemplateText()

    /** 외부(분석 저장 등)에서 프롬프트 템플릿 전체를 교체한다. Undo 가능. */
    fun replacePromptTemplateEntirely(replacement: String) =
        promptEditor.replacePromptTemplateEntirely(replacement)

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

    fun copyPromptToClipboard() = promptEditor.copyPromptToClipboard(_uiState.value.isRunning)

    fun pastePromptFromClipboard() = promptEditor.pastePromptFromClipboard()

    /**
     * 프롬프트 템플릿 맨 앞에 상단 인스트럭션을 붙인다.
     * 문구가 설정되어 있지 않으면 설정 다이얼로그를 띄운다.
     */
    fun insertTopInstruction() {
        val config = _uiState.value.promptInstructionConfig
        if (config.topInstruction.isBlank()) {
            openInstructionConfigDialog(InstructionTab.TOP)
            return
        }
        promptEditor.insertTopInstruction(config.topInstruction, _uiState.value.isRunning)
    }

    /**
     * 프롬프트 템플릿 맨 뒤에 하단 인스트럭션을 붙인다.
     * 문구가 설정되어 있지 않으면 설정 다이얼로그를 띄워 입력을 유도한다.
     */
    fun insertBottomInstruction() {
        val config = _uiState.value.promptInstructionConfig
        val bottom = config.bottomInstruction
        if (bottom.isNullOrBlank()) {
            openInstructionConfigDialog(InstructionTab.BOTTOM)
            return
        }
        promptEditor.insertBottomInstruction(bottom, _uiState.value.isRunning)
    }

    fun openInstructionConfigDialog(initialTab: InstructionTab = InstructionTab.TOP) =
        _uiState.update {
            it.copy(
                showInstructionConfigDialog = true,
                instructionConfigDialogInitialTab = initialTab
            )
        }

    fun closeInstructionConfigDialog() =
        _uiState.update { it.copy(showInstructionConfigDialog = false) }

    fun saveInstructionConfig(config: PromptInstructionConfig) {
        scope.launch {
            withContext(dispatchers.io) {
                managePromptInstruction.save(config)
            }
        }
        _uiState.update {
            it.copy(
                promptInstructionConfig = config,
                showInstructionConfigDialog = false
            )
        }
    }

    fun openVariationPromptConfigDialog() =
        _uiState.update { it.copy(showVariationPromptConfigDialog = true) }

    fun closeVariationPromptConfigDialog() =
        _uiState.update { it.copy(showVariationPromptConfigDialog = false) }

    fun saveVariationPromptConfig(config: VariationPromptConfig) {
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

        val resolvedPrompt = resolveVariationPrompt.buildPrompt(
            config = state.variationPromptConfig,
            fullText = promptEditor.currentPromptTemplateText(),
            explicitSelectedText = selectedText,
            isParagraphSelectionMode = state.isParagraphSelectionMode,
            selectedParagraphRange = state.selectedParagraphRange
        )

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
     * 추천 칩 탭: 커서 기준 현재 단어를 와일드카드 토큰으로 교체.
     * Undo 가능. 실행 중·문단 선택 모드에서는 무시.
     */
    fun applyWildcardTokenSuggestion(token: String) {
        val candidates = _uiState.value.allAutocompleteCandidates.ifEmpty { _uiState.value.wildcardTokenCandidates }
        promptEditor.applyWildcardTokenSuggestion(
            token = token,
            isBlocked = _uiState.value.isRunning,
            candidates = candidates
        )
    }

    /**
     * 추천 칩 탭 (Candidate 직접 전달): 커서 기준 현재 단어를 치환.
     */
    fun applySuggestion(candidate: WildcardTokenAutocomplete.Candidate) {
        val candidates = _uiState.value.allAutocompleteCandidates.ifEmpty { _uiState.value.wildcardTokenCandidates }
        promptEditor.applySuggestion(
            candidate = candidate,
            isBlocked = _uiState.value.isRunning,
            candidates = candidates
        )
    }

    /** 와일드카드 폴더 및 상용구 목록으로 추천 후보를 다시 읽는다. */
    fun refreshWildcardTokenCandidates() {
        scope.launch {
            val (wildcardCandidates, snippetCandidates, snippets) = withContext(dispatchers.io) {
                Triple(
                    loadWildcardTokenCandidates(),
                    loadSnippetCandidates(),
                    promptSnippetRepository?.load().orEmpty()
                )
            }
            val combined = (wildcardCandidates + snippetCandidates).sortedWith(
                compareBy<WildcardTokenAutocomplete.Candidate> { it.name.length }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            promptEditor.updateAutocompleteCandidates(combined)
            _uiState.update { state ->
                state.copy(
                    wildcardTokenCandidates = wildcardCandidates,
                    promptSnippets = snippets,
                    allAutocompleteCandidates = combined
                )
            }
        }
    }

    fun showPromptSnippetDialog() {
        _uiState.update { it.copy(showPromptSnippetDialog = true) }
    }

    fun dismissPromptSnippetDialog() {
        _uiState.update { it.copy(showPromptSnippetDialog = false) }
    }

    fun addPromptSnippet(shortcut: String, content: String) {
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
            refreshWildcardTokenCandidates()
        }
    }

    fun deletePromptSnippet(id: String) {
        scope.launch {
            withContext(dispatchers.io) {
                val current = promptSnippetRepository?.load().orEmpty().toMutableList()
                current.removeAll { it.id == id }
                promptSnippetRepository?.save(current)
            }
            refreshWildcardTokenCandidates()
        }
    }

    fun replaceSelectedPromptParagraph(replacement: String) =
        promptEditor.replaceSelectedPromptParagraph(replacement)

    fun closeGeminiApp() = executeMaintenance(
        canExecute = AutomationUiState::canCloseGemini,
        unavailableMessage = AutomationUiText::geminiRestartUnavailableMessage,
        startingText = AutomationUiText.geminiRestartStartingText(),
        canceledText = AutomationUiText.geminiRestartCanceledText(),
        action = appMaintenance::restartGemini
    )

    fun terminateGeminiApp() = executeMaintenance(
        canExecute = AutomationUiState::canCloseGemini,
        unavailableMessage = AutomationUiText::geminiTerminateUnavailableMessage,
        startingText = AutomationUiText.geminiTerminateStartingText(),
        canceledText = AutomationUiText.geminiTerminateCanceledText(),
        action = appMaintenance::terminateGemini
    )

    fun terminateSelfApp() = executeMaintenance(
        canExecute = AutomationUiState::canCloseSelfApp,
        unavailableMessage = AutomationUiText::selfAppTerminateUnavailableMessage,
        startingText = AutomationUiText.selfAppTerminateStartingText(),
        canceledText = AutomationUiText.selfAppTerminateCanceledText(),
        action = appMaintenance::terminateSelf
    )

    fun cleanDeviceMemory() {
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
                            AutomationUiText.memoryCleanupScheduledText()
                        } else {
                            AutomationUiText.memoryCleanupScheduleCanceledText()
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

    fun navigatePromptHistoryBack() =
        promptEditor.navigatePromptHistoryBack(_uiState.value.isRunning)

    fun navigatePromptHistoryForward() =
        promptEditor.navigatePromptHistoryForward(_uiState.value.isRunning)

    fun refreshStatus() {
        scope.launch {
            val (report, wildcardCandidates, snippetCandidates, snippets) = withContext(dispatchers.io) {
                val rep = checkEnvironmentStatus.check()
                val wildcards = loadWildcardTokenCandidates()
                val snippetCands = loadSnippetCandidates()
                val snips = promptSnippetRepository?.load().orEmpty()
                StatusCandidatesBundle(rep, wildcards, snippetCands, snips)
            }
            val combined = (wildcardCandidates + snippetCandidates).sortedWith(
                compareBy<WildcardTokenAutocomplete.Candidate> { it.name.length }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            promptEditor.updateAutocompleteCandidates(combined)
            _uiState.update {
                it.copy(
                    environmentStatus = report.status,
                    environmentSetupInfo = report.setupInfo,
                    wildcardTokenCandidates = wildcardCandidates,
                    promptSnippets = snippets,
                    allAutocompleteCandidates = combined
                )
            }
        }
    }

    private fun loadWildcardTokenCandidates(): List<WildcardTokenAutocomplete.Candidate> =
        getWildcardTokenCandidates()

    private fun loadSnippetCandidates(): List<WildcardTokenAutocomplete.Candidate> =
        getPromptSnippetCandidates()

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
    fun confirmAccessibilityPrompt() =
        _uiState.update { it.copy(showAccessibilityPrompt = false) }

    /** 접근성 확인 팝업에서 「취소」: 전체 설정 다이얼로그로 진입. */
    fun dismissAccessibilityPromptToSettings() =
        _uiState.update { it.copy(showAccessibilityPrompt = false, showSettings = true) }

    fun hideSettings() =
        _uiState.update { it.copy(showSettings = false, showAccessibilityPrompt = false) }

    fun runAutomation(): AutomationStartDecision {
        promptEditor.syncPromptTemplateFromTextField()
        val state = uiState.value
        val isStartInProgress = automationPreparationJob?.isActive == true
        val decision = executeAutomation.decideStart(
            canRun = state.canRun,
            isStartInProgress = isStartInProgress,
            mode = state.automationMode
        )
        if (decision !is AutomationStartDecision.Started && decision !is AutomationStartDecision.RemoteStarted) {
            return decision
        }

        val history = promptHistoryStore?.load().orEmpty()
        promptEditor.onAutomationStarted(state.promptTemplate, history.map { it.prompt })

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
            additionalUpdate = { it.copy(promptHistoryItems = history, isMemoryCleanupScheduled = false) }
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
        val result = executeAutomation.executeRemote(request, ::handleAutomationState)
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

    fun openPromptHistory() {
        val items = promptHistoryStore?.load().orEmpty()
        _uiState.update { it.copy(showPromptHistory = true, promptHistoryItems = items) }
    }

    fun closePromptHistory() = _uiState.update { it.copy(showPromptHistory = false) }

    fun clearPromptHistory() {
        promptHistoryStore?.clear()
        promptEditor.syncHistoryItems(emptyList())
        _uiState.update { it.copy(promptHistoryItems = emptyList()) }
    }

    fun selectPromptHistoryItem(item: PromptHistoryItem) {
        if (_uiState.value.isRunning) return
        promptEditor.restorePrompt(item.prompt)
        closePromptHistory()
    }

    fun cancelAutomation() {
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

    fun onAutomationModeSelected(mode: AutomationMode) {
        if (_uiState.value.isRunning || _uiState.value.isVariationRunning) return
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
            val initialState = withContext(dispatchers.io) {
                AutomationInitialState(
                    lastRunSnapshotStore.load(),
                    promptHistoryStore?.load().orEmpty(),
                    managePromptInstruction.load(),
                    variationPromptRepository?.load() ?: VariationPromptConfig.DEFAULT
                )
            }
            val snapshot = initialState.lastRunSnapshot
            val history = initialState.historyItems
            val defaultRepeat = AppDefaults.DEFAULT_REPEAT_COUNT.toString()
            val restoredPrompt = _uiState.value.promptTemplate.ifBlank { snapshot?.promptTemplate.orEmpty() }

            promptEditor.restorePrompt(restoredPrompt)
            promptEditor.syncHistoryItems(history.map { it.prompt })

            _uiState.update {
                it.copy(
                    promptTemplate = restoredPrompt,
                    repeatCountText = if (it.repeatCountText == defaultRepeat) snapshot?.repeatCountText?.ifBlank { defaultRepeat } ?: defaultRepeat else it.repeatCountText,
                    selectedTargetApp = snapshot?.targetApp ?: it.selectedTargetApp,
                    flowImageCount = snapshot?.flowImageCount ?: it.flowImageCount,
                    promptHistoryItems = history,
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

    fun openGeminiAccountPicker() {
        if (_uiState.value.isMaintenanceBusy) return
        scope.launch {
            val mode = _uiState.value.automationMode
            val startingMessage = GeminiAccountSwitchProgressPolicy.startingMaintenanceMessage(
                isSenderMode = mode == AutomationMode.SENDER
            )
            _uiState.update {
                it.copy(
                    maintenanceState = MaintenanceState(isBusy = true, message = startingMessage)
                )
            }

            val result = openGeminiAccountPicker.execute(
                mode = mode,
                onProgress = { phase, msg ->
                    _uiState.update { current ->
                        current.copy(
                            maintenanceState = MaintenanceState(isBusy = true, message = "[$phase] $msg")
                        )
                    }
                }
            )

            when (result) {
                is OpenGeminiAccountPickerResult.Success -> _uiState.update {
                    it.copy(
                        maintenanceState = MaintenanceState(isBusy = false, message = result.message)
                    )
                }
                is OpenGeminiAccountPickerResult.Failure -> {
                    val maintenanceMsg = GeminiAccountSwitchProgressPolicy.formatMaintenanceErrorMessage(result.message)
                    _uiState.update {
                        it.copy(
                            maintenanceState = MaintenanceState(isBusy = false, message = maintenanceMsg)
                        )
                    }
                }
            }
        }
    }

}

private data class StatusCandidatesBundle(
    val report: com.example.gemgemgen.environment.domain.EnvironmentReport,
    val wildcardCandidates: List<WildcardTokenAutocomplete.Candidate>,
    val snippetCandidates: List<WildcardTokenAutocomplete.Candidate>,
    val snippets: List<PromptSnippet>
)
