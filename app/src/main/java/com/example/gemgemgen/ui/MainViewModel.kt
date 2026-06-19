package com.example.gemgemgen.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.automation.domain.isTerminal
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.RunAutomationUseCase
import com.example.gemgemgen.automation.usecase.RunLogger
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.environment.usecase.CheckEnvironmentStatusUseCase
import com.example.gemgemgen.wildcard.usecase.SaveWildcardFolderUseCase
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
    private val runLogger: RunLogger,
    private val lastRunSnapshotStore: LastRunSnapshotStore,
    private val automation: RunAutomationUseCase,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private var automationPreparationJob: Job? = null
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
        refreshStatus()
    }

    fun onPromptTemplateChange(value: String) {
        _uiState.update { it.copy(promptTemplate = value) }
    }

    fun onTargetAppSelected(targetApp: AutomationTargetApp) {
        _uiState.update {
            if (it.isRunning) it else it.copy(selectedTargetApp = targetApp)
        }
    }

    fun onRepeatCountChange(value: String) {
        _uiState.update { it.copy(repeatCountText = RepeatCountParser.normalizeInput(value)) }
    }

    fun importPromptFromClipboard() {
        scope.launch {
            val text = withContext(dispatchers.io) {
                clipboardGateway.readText()
            }
            onPromptTemplateChange(text)
        }
    }

    fun refreshStatus() {
        scope.launch {
            val status = withContext(dispatchers.io) {
                checkEnvironmentStatus.check()
            }
            _uiState.update { it.copy(environmentStatus = status) }
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
                it.copy(
                    settingsMessage = result.message,
                    settingsError = result.error
                )
            }
            refreshStatus()
        }
    }

    fun showWildcardFolderSaveError(message: String) {
        _uiState.update {
            it.copy(
                settingsMessage = "",
                settingsError = message
            )
        }
    }

    fun runAutomation(): Boolean {
        val state = uiState.value
        if (!state.canRun) return false
        if (automationPreparationJob?.isActive == true) return false

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
        return true
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
    }

    private fun refreshLogs() {
        scope.launch {
            val logs = withContext(dispatchers.io) {
                runLogger.loadRecent()
            }
            _uiState.update { it.copy(recentLogs = logs) }
        }
    }
}

