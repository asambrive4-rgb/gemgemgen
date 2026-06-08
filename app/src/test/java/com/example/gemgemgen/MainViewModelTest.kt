package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {
    @Test
    fun importPromptFromClipboard_updatesPromptTemplate() {
        val viewModel = viewModel(clipboardText = "clipboard prompt")

        viewModel.importPromptFromClipboard()

        assertEquals("clipboard prompt", viewModel.uiState.value.promptTemplate)
    }

    @Test
    fun onRepeatCountChange_usesSharedNormalizationRule() {
        val viewModel = viewModel()

        viewModel.onRepeatCountChange("a12b")

        assertEquals("12", viewModel.uiState.value.repeatCountText)
    }

    @Test
    fun refreshStatus_updatesEnvironmentStatus() {
        val environment = FakeEnvironmentStatusProvider(EnvironmentStatus())
        val viewModel = viewModel(environmentStatusProvider = environment)

        environment.status = readyEnvironment()
        viewModel.refreshStatus()

        assertTrue(viewModel.uiState.value.environmentStatus.isReady)
    }

    @Test
    fun init_restoresLastRunSnapshot() {
        val snapshotStorage = FakeLastRunSnapshotStorage(
            promptTemplate = "saved prompt",
            repeatCountText = "12"
        )

        val viewModel = viewModel(
            lastRunSnapshotStore = LastRunSnapshotStore(snapshotStorage)
        )

        assertEquals("saved prompt", viewModel.uiState.value.promptTemplate)
        assertEquals("12", viewModel.uiState.value.repeatCountText)
    }

    @Test
    fun saveWildcardFolder_updatesSettingsMessageAndRefreshesStatus() {
        val environment = FakeEnvironmentStatusProvider(readyEnvironment())
        val folderSaver = FakeWildcardFolderSaver(
            FolderSelectionResult(message = "폴더 선택 완료")
        )
        val viewModel = viewModel(
            environmentStatusProvider = environment,
            wildcardFolderSaver = folderSaver
        )

        viewModel.saveWildcardFolder("content://wildcard")

        assertEquals("content://wildcard", folderSaver.savedFolderUri)
        assertEquals("폴더 선택 완료", viewModel.uiState.value.settingsMessage)
        assertEquals("", viewModel.uiState.value.settingsError)
        assertEquals(2, environment.checkCount)
    }

    @Test
    fun runAutomation_savesLastRunSnapshotWhenRunStarts() {
        val snapshotStorage = FakeLastRunSnapshotStorage()
        val viewModel = viewModel(
            lastRunSnapshotStore = LastRunSnapshotStore(snapshotStorage)
        )

        viewModel.onPromptTemplateChange("prompt to resume")
        viewModel.onRepeatCountChange("7")
        viewModel.runAutomation()

        assertEquals("prompt to resume", snapshotStorage.promptTemplate)
        assertEquals("7", snapshotStorage.repeatCountText)
    }

    @Test
    fun runAutomation_copiesPromptTemplateToClipboardWhenRunStarts() {
        val clipboardTextWriter = FakeClipboardTextWriter()
        val viewModel = viewModel(
            clipboardTextWriter = clipboardTextWriter
        )

        viewModel.onPromptTemplateChange("base __hair__ prompt")
        viewModel.runAutomation()

        assertEquals("base __hair__ prompt", clipboardTextWriter.text)
    }

    @Test
    fun runAutomation_refreshesRecentLogsWhenRunFinishes() {
        val runLogger = RunLogger(FakeRunLogStorage())
        val viewModel = viewModel(
            runLogger = runLogger,
            automation = automation(runLogger)
        )

        viewModel.onPromptTemplateChange("base")
        viewModel.runAutomation()

        assertEquals(AutomationUiState.Success, viewModel.uiState.value.automationState)
        assertEquals(1, viewModel.uiState.value.recentLogs.size)
        assertEquals(AutomationRunLogStatus.SUCCESS, viewModel.uiState.value.recentLogs.single().status)
    }

    private fun viewModel(
        environmentStatusProvider: FakeEnvironmentStatusProvider = FakeEnvironmentStatusProvider(readyEnvironment()),
        clipboardText: String = "",
        clipboardTextWriter: FakeClipboardTextWriter = FakeClipboardTextWriter(),
        wildcardFolderSaver: FakeWildcardFolderSaver = FakeWildcardFolderSaver(),
        runLogger: RunLogger = RunLogger(FakeRunLogStorage()),
        lastRunSnapshotStore: LastRunSnapshotStore = LastRunSnapshotStore(FakeLastRunSnapshotStorage()),
        automation: GeminiMvpAutomation = automation(runLogger)
    ): MainViewModel {
        return MainViewModel(
            environmentStatusProvider = environmentStatusProvider,
            clipboardTextProvider = FakeClipboardTextProvider(clipboardText),
            clipboardTextWriter = clipboardTextWriter,
            wildcardFolderSaver = wildcardFolderSaver,
            runLogger = runLogger,
            lastRunSnapshotStore = lastRunSnapshotStore,
            automation = automation
        )
    }

    private fun automation(runLogger: RunLogger): GeminiMvpAutomation {
        var defaultImeId = ORIGINAL_IME_ID
        return GeminiMvpAutomation(
            imeManager = ImeManager(
                settings = object : ImeSettings {
                    override fun getDefaultInputMethod(): String? = defaultImeId

                    override fun setDefaultInputMethod(imeId: String): Boolean {
                        defaultImeId = imeId
                        return true
                    }
                },
                nullKeyboardImeId = NULL_IME_ID
            ),
            runLogger = runLogger,
            clock = { 1000L },
            serviceProvider = { FakeGeminiPromptSender() },
            launchGeminiApp = { true },
            loadWildcards = { emptyList() },
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "base", "prompt $index", emptyMap())
            }
        )
    }

    private class FakeEnvironmentStatusProvider(
        var status: EnvironmentStatus
    ) : EnvironmentStatusProvider {
        var checkCount = 0

        override fun check(): EnvironmentStatus {
            checkCount += 1
            return status
        }
    }

    private class FakeClipboardTextProvider(
        private val text: String
    ) : ClipboardTextProvider {
        override fun readText(): String = text
    }

    private class FakeClipboardTextWriter : ClipboardTextWriter {
        var text: String = ""

        override fun writeText(text: String) {
            this.text = text
        }
    }

    private class FakeWildcardFolderSaver(
        private val result: FolderSelectionResult = FolderSelectionResult()
    ) : WildcardFolderSaver {
        var savedFolderUri: String = ""

        override fun save(folderUri: String): FolderSelectionResult {
            savedFolderUri = folderUri
            return result
        }
    }

    private class FakeGeminiPromptSender : GeminiPromptSender {
        override fun sendPrompt(
            prompt: String,
            onStateChange: (AutomationUiState) -> Unit,
            onDone: () -> Unit
        ) {
            onDone()
        }

        override fun cancelCurrentRun() = Unit
    }

    private class FakeRunLogStorage : RunLogStorage {
        private var value: String = ""

        override fun read(): String = value

        override fun write(value: String) {
            this.value = value
        }
    }

    private class FakeLastRunSnapshotStorage(
        var promptTemplate: String = "",
        var repeatCountText: String = ""
    ) : LastRunSnapshotStorage {
        override fun readPromptTemplate(): String = promptTemplate

        override fun readRepeatCountText(): String = repeatCountText

        override fun write(promptTemplate: String, repeatCountText: String) {
            this.promptTemplate = promptTemplate
            this.repeatCountText = repeatCountText
        }
    }

    private companion object {
        const val ORIGINAL_IME_ID = "example.keyboard/.Ime"
        const val NULL_IME_ID = "example.nullkeyboard/.NullIme"

        fun readyEnvironment(): EnvironmentStatus {
            return EnvironmentStatus(
                isGeminiInstalled = true,
                isAccessibilityServiceEnabled = true,
                hasWriteSecureSettingsPermission = true,
                isWildcardDirectoryAccessible = true
            )
        }
    }
}
