package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        val environment = FakeEnvironmentStatusReader(EnvironmentStatus())
        val viewModel = viewModel(environmentStatusReader = environment)

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
        val environment = FakeEnvironmentStatusReader(readyEnvironment())
        val folderSaver = FakeWildcardFolderSaver(
            FolderSelectionResult(message = "폴더 선택 완료")
        )
        val viewModel = viewModel(
            environmentStatusReader = environment,
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
            automationRunner = automation(runLogger)
        )

        viewModel.onPromptTemplateChange("base")
        viewModel.runAutomation()

        assertEquals(AutomationRunState.Success, viewModel.uiState.value.automationState)
        assertEquals(1, viewModel.uiState.value.recentLogs.size)
        assertEquals(AutomationRunLogStatus.SUCCESS, viewModel.uiState.value.recentLogs.single().status)
    }

    @Test
    fun runAutomation_showsPreparingStateUntilWildcardPreparationFinishes() {
        val runLogger = RunLogger(FakeRunLogStorage())
        val prepareStarted = CountDownLatch(1)
        val allowPrepareToFinish = CountDownLatch(1)
        val service = FakeGeminiPromptGateway()
        val viewModel = viewModel(
            runLogger = runLogger,
            lastRunSnapshotStore = LastRunSnapshotStore(
                FakeLastRunSnapshotStorage(
                    promptTemplate = "base",
                    repeatCountText = "1"
                )
            ),
            automationRunner = automation(
                runLogger = runLogger,
                lastRunSnapshotStore = LastRunSnapshotStore(
                    FakeLastRunSnapshotStorage(
                        promptTemplate = "base",
                        repeatCountText = "1"
                    )
                ),
                service = service,
                loadWildcards = {
                    prepareStarted.countDown()
                    assertTrue(allowPrepareToFinish.await(2, TimeUnit.SECONDS))
                    emptyList()
                },
                dispatchers = AppDispatchers(io = Dispatchers.Default)
            ),
            dispatchers = AppDispatchers(io = Dispatchers.Default),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        waitUntil {
            viewModel.uiState.value.environmentStatus.isReady &&
                viewModel.uiState.value.promptTemplate == "base"
        }

        assertTrue(viewModel.runAutomation())

        assertEquals(
            AutomationRunState.Running("자동화 준비 중"),
            viewModel.uiState.value.automationState
        )
        assertTrue(prepareStarted.await(2, TimeUnit.SECONDS))
        assertEquals(emptyList<String>(), service.sentPrompts)

        allowPrepareToFinish.countDown()

        waitUntil { service.sentPrompts.isNotEmpty() }
        assertEquals(RunGeminiAutomationUseCase.MARKER_PROMPT, service.sentPrompts.first())
    }

    private fun viewModel(
        environmentStatusReader: FakeEnvironmentStatusReader = FakeEnvironmentStatusReader(readyEnvironment()),
        clipboardText: String = "",
        clipboardTextWriter: FakeClipboardTextWriter = FakeClipboardTextWriter(),
        wildcardFolderSaver: FakeWildcardFolderSaver = FakeWildcardFolderSaver(),
        runLogger: RunLogger = RunLogger(FakeRunLogStorage()),
        lastRunSnapshotStore: LastRunSnapshotStore = LastRunSnapshotStore(FakeLastRunSnapshotStorage()),
        automationRunner: RunGeminiAutomationUseCase? = null,
        dispatchers: AppDispatchers = AppDispatchers(io = Dispatchers.Unconfined),
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    ): MainViewModel {
        return MainViewModel(
            environmentStatusReader = environmentStatusReader,
            clipboardTextProvider = FakeClipboardTextProvider(clipboardText),
            wildcardFolderSaver = wildcardFolderSaver,
            runLogger = runLogger,
            lastRunSnapshotStore = lastRunSnapshotStore,
            automation = automationRunner ?: automation(
                runLogger = runLogger,
                lastRunSnapshotStore = lastRunSnapshotStore,
                clipboardTextWriter = clipboardTextWriter,
                dispatchers = dispatchers
            ),
            dispatchers = dispatchers,
            coroutineScope = coroutineScope
        )
    }

    private fun automation(
        runLogger: RunLogger,
        lastRunSnapshotStore: LastRunSnapshotStore = LastRunSnapshotStore(FakeLastRunSnapshotStorage()),
        clipboardTextWriter: FakeClipboardTextWriter = FakeClipboardTextWriter(),
        service: FakeGeminiPromptGateway = FakeGeminiPromptGateway(),
        loadWildcards: () -> List<WildcardSet> = { emptyList() },
        dispatchers: AppDispatchers = AppDispatchers(io = Dispatchers.Unconfined)
    ): RunGeminiAutomationUseCase {
        var defaultImeId = ORIGINAL_IME_ID
        return RunGeminiAutomationUseCase(
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
            lastRunSnapshotStore = lastRunSnapshotStore,
            clipboardTextWriter = clipboardTextWriter,
            wildcardSetLoader = FakeWildcardSetLoader(loadWildcards),
            clock = { 1000L },
            promptGatewayProvider = { service },
            launchGeminiApp = { true },
            dispatchers = dispatchers,
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "base", "prompt $index", emptyMap())
            }
        )
    }

    private class FakeEnvironmentStatusReader(
        var status: EnvironmentStatus
    ) : EnvironmentStatusReader {
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

    private class FakeWildcardSetLoader(
        private val loadWildcards: () -> List<WildcardSet>
    ) : WildcardSetLoader {
        override fun load(): List<WildcardSet> = loadWildcards()
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

    private class FakeGeminiPromptGateway : GeminiPromptGateway {
        val sentPrompts = mutableListOf<String>()

        override fun sendPrompt(
            prompt: String,
            newChatMode: GeminiNewChatMode,
            onStateChange: (AutomationRunState) -> Unit,
            onDone: () -> Unit
        ) {
            sentPrompts += prompt
            onDone()
        }

        override fun cancelCurrentRun() = Unit
    }

    private fun waitUntil(
        timeoutMillis: Long = 2000,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("Condition was not met within $timeoutMillis ms", condition())
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
