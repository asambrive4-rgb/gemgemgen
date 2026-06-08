package com.example.gemgemgen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(
    private val environmentStatusProvider: EnvironmentStatusProvider,
    private val clipboardTextProvider: ClipboardTextProvider,
    private val wildcardFolderSaver: WildcardFolderSaver,
    private val runLogger: RunLogger,
    private val lastRunSnapshotStore: LastRunSnapshotStore,
    private val automation: GeminiMvpAutomation
) : ViewModel() {
    private val lastRunSnapshot = lastRunSnapshotStore.load()
    private val _uiState = MutableStateFlow(
        MainUiState(
            promptTemplate = lastRunSnapshot?.promptTemplate.orEmpty(),
            repeatCountText = lastRunSnapshot?.repeatCountText
                ?.ifBlank { AppDefaults.DEFAULT_REPEAT_COUNT.toString() }
                ?: AppDefaults.DEFAULT_REPEAT_COUNT.toString(),
            recentLogs = runLogger.loadRecent()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
    }

    fun onPromptTemplateChange(value: String) {
        _uiState.update { it.copy(promptTemplate = value) }
    }

    fun onRepeatCountChange(value: String) {
        _uiState.update { it.copy(repeatCountText = RepeatCountParser.normalizeInput(value)) }
    }

    fun importPromptFromClipboard() {
        onPromptTemplateChange(clipboardTextProvider.readText())
    }

    fun refreshStatus() {
        _uiState.update { it.copy(environmentStatus = environmentStatusProvider.check()) }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun saveWildcardFolder(folderUri: String) {
        val result = wildcardFolderSaver.save(folderUri)
        _uiState.update {
            it.copy(
                settingsMessage = result.message,
                settingsError = result.error
            )
        }
        refreshStatus()
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

        lastRunSnapshotStore.save(
            LastRunSnapshot(
                promptTemplate = state.promptTemplate,
                repeatCountText = state.repeatCountText
            )
        )
        automation.run(
            promptTemplate = state.promptTemplate,
            repeatCountText = state.repeatCountText,
            onStateChange = ::handleAutomationState
        )
        return true
    }

    fun cancelAutomation() {
        automation.cancel(::handleAutomationState)
    }

    fun toggleRecentLogs() {
        refreshLogs()
        _uiState.update { it.copy(showRecentLogs = !it.showRecentLogs) }
    }

    private fun handleAutomationState(state: AutomationUiState) {
        _uiState.update { it.copy(automationState = state) }
        if (state.isTerminal()) {
            refreshLogs()
        }
    }

    private fun refreshLogs() {
        _uiState.update { it.copy(recentLogs = runLogger.loadRecent()) }
    }
}

interface EnvironmentStatusProvider {
    fun check(): EnvironmentStatus
}

interface ClipboardTextProvider {
    fun readText(): String
}

data class FolderSelectionResult(
    val message: String = "",
    val error: String = ""
)

interface WildcardFolderSaver {
    fun save(folderUri: String): FolderSelectionResult
}
