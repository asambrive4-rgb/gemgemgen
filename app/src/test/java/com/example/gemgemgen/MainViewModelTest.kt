package com.example.gemgemgen

import androidx.compose.ui.text.TextRange
import com.example.gemgemgen.automation.android.*
import com.example.gemgemgen.automation.domain.*
import com.example.gemgemgen.automation.usecase.*
import com.example.gemgemgen.core.*
import com.example.gemgemgen.environment.android.*
import com.example.gemgemgen.environment.domain.*
import com.example.gemgemgen.environment.usecase.*
import com.example.gemgemgen.ui.*
import com.example.gemgemgen.automation.ui.*
import com.example.gemgemgen.wildcard.ui.*
import com.example.gemgemgen.wildcard.domain.*
import com.example.gemgemgen.wildcard.usecase.*
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
        assertEquals(
            "clipboard prompt",
            viewModel.promptTemplateTextFieldState.text.toString()
        )
    }

    @Test
    fun onPromptTemplateChange_withSameText_preservesSelection() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("abcdef")
        viewModel.promptTemplateTextFieldState.edit {
            selection = TextRange(1, 4)
        }

        viewModel.onPromptTemplateChange("abcdef")

        assertEquals(
            TextRange(1, 4),
            viewModel.promptTemplateTextFieldState.selection
        )
    }

    @Test
    fun onPromptTemplateChange_withDifferentText_placesCursorAtEnd() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("before")
        viewModel.promptTemplateTextFieldState.edit {
            selection = TextRange(1, 3)
        }

        viewModel.onPromptTemplateChange("after")

        assertEquals(
            TextRange("after".length),
            viewModel.promptTemplateTextFieldState.selection
        )
    }

    @Test
    fun undoPromptEdit_revertsContinuousTypingAsSingleStep() {
        val viewModel = viewModel()

        viewModel.onPromptTemplateChange("인")
        viewModel.onPromptTemplateChange("인물")
        viewModel.onPromptTemplateChange("인물 설명")

        assertTrue(viewModel.uiState.value.canUndoPromptEdit)

        viewModel.undoPromptEdit()

        assertEquals("", viewModel.uiState.value.promptTemplate)
        assertEquals("", viewModel.promptTemplateTextFieldState.text.toString())
        assertTrue(!viewModel.uiState.value.canUndoPromptEdit)
    }

    @Test
    fun undoPromptEdit_afterDebounce_revertsOnlyLatestTypingGroup() {
        val viewModel = viewModel()

        viewModel.onPromptTemplateChange("인물")
        Thread.sleep(800)
        viewModel.onPromptTemplateChange("인물\n장소")

        viewModel.undoPromptEdit()

        assertEquals("인물", viewModel.uiState.value.promptTemplate)
        assertEquals("인물", viewModel.promptTemplateTextFieldState.text.toString())
        assertTrue(viewModel.uiState.value.canUndoPromptEdit)
    }

    @Test
    fun undoPromptEdit_afterWholeClipboardImport_restoresPreviousPrompt() {
        val viewModel = viewModel(
            clipboardText = "새 프롬프트",
            lastRunSnapshotStore = LastRunSnapshotStore(
                FakeLastRunSnapshotStorage(promptTemplate = "기존 프롬프트")
            )
        )

        viewModel.importPromptFromClipboard()
        viewModel.undoPromptEdit()

        assertEquals("기존 프롬프트", viewModel.uiState.value.promptTemplate)
        assertEquals("기존 프롬프트", viewModel.promptTemplateTextFieldState.text.toString())
    }

    @Test
    fun toggleParagraphSelectionMode_enablesAndClearsMode() {
        val viewModel = viewModel()

        viewModel.toggleParagraphSelectionMode()

        assertTrue(viewModel.uiState.value.isParagraphSelectionMode)
        assertTrue(viewModel.uiState.value.paragraphSelectionMessage.isNotBlank())

        viewModel.toggleParagraphSelectionMode()

        assertTrue(!viewModel.uiState.value.isParagraphSelectionMode)
        assertEquals(null, viewModel.uiState.value.selectedParagraphRange)
    }

    @Test
    fun selectPromptParagraphAt_selectsTouchedNonBlankLine() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("첫째\n둘째\n셋째")
        viewModel.toggleParagraphSelectionMode()

        viewModel.selectPromptParagraphAt(4)

        assertEquals(
            PromptParagraphRange(3, 5),
            viewModel.uiState.value.selectedParagraphRange
        )
    }

    @Test
    fun selectPromptParagraphAt_blankLine_clearsSelection() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("첫째\n\n셋째")
        viewModel.toggleParagraphSelectionMode()

        viewModel.selectPromptParagraphAt(3)

        assertEquals(null, viewModel.uiState.value.selectedParagraphRange)
        assertTrue(viewModel.uiState.value.paragraphSelectionMessage.contains("빈 줄"))
    }

    @Test
    fun importPromptFromClipboard_inSelectionMode_replacesOnlySelectedParagraph() {
        val viewModel = viewModel(clipboardText = "새 장소\n보조 설명")
        viewModel.onPromptTemplateChange("인물\n장소\n조명")
        viewModel.toggleParagraphSelectionMode()
        viewModel.selectPromptParagraphAt(4)

        viewModel.importPromptFromClipboard()

        assertEquals(
            "인물\n새 장소\n보조 설명\n조명",
            viewModel.uiState.value.promptTemplate
        )
        assertTrue(!viewModel.uiState.value.isParagraphSelectionMode)
        assertEquals(
            TextRange("인물\n새 장소\n보조 설명".length),
            viewModel.promptTemplateTextFieldState.selection
        )
    }

    @Test
    fun replaceSelectedPromptParagraph_replacesOnlySelectedParagraph() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("인물\n장소\n조명")
        viewModel.toggleParagraphSelectionMode()
        viewModel.selectPromptParagraphAt(4)

        viewModel.replaceSelectedPromptParagraph("새 장소\n보조 설명")

        assertEquals(
            "인물\n새 장소\n보조 설명\n조명",
            viewModel.uiState.value.promptTemplate
        )
        assertTrue(!viewModel.uiState.value.isParagraphSelectionMode)
        assertEquals(
            TextRange("인물\n새 장소\n보조 설명".length),
            viewModel.promptTemplateTextFieldState.selection
        )
    }

    @Test
    fun replaceSelectedPromptParagraph_blankText_keepsSelectedParagraph() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("인물\n장소")
        viewModel.toggleParagraphSelectionMode()
        viewModel.selectPromptParagraphAt(4)

        viewModel.replaceSelectedPromptParagraph("   ")

        assertEquals("인물\n장소", viewModel.uiState.value.promptTemplate)
        assertTrue(viewModel.uiState.value.isParagraphSelectionMode)
        assertEquals(
            PromptParagraphRange(3, 5),
            viewModel.uiState.value.selectedParagraphRange
        )
    }

    @Test
    fun importPromptFromClipboard_withoutSelection_keepsTextAndMode() {
        val viewModel = viewModel(clipboardText = "새 장소")
        viewModel.onPromptTemplateChange("인물\n장소")
        viewModel.toggleParagraphSelectionMode()

        viewModel.importPromptFromClipboard()

        assertEquals("인물\n장소", viewModel.uiState.value.promptTemplate)
        assertTrue(viewModel.uiState.value.isParagraphSelectionMode)
        assertTrue(viewModel.uiState.value.paragraphSelectionMessage.contains("먼저"))
    }

    @Test
    fun importPromptFromClipboard_blankClipboard_keepsSelectedParagraph() {
        val viewModel = viewModel(clipboardText = "   ")
        viewModel.onPromptTemplateChange("인물\n장소")
        viewModel.toggleParagraphSelectionMode()
        viewModel.selectPromptParagraphAt(4)

        viewModel.importPromptFromClipboard()

        assertEquals("인물\n장소", viewModel.uiState.value.promptTemplate)
        assertTrue(viewModel.uiState.value.isParagraphSelectionMode)
        assertEquals(
            PromptParagraphRange(3, 5),
            viewModel.uiState.value.selectedParagraphRange
        )
    }

    @Test
    fun deleteSelectedPromptParagraph_deletesTextButKeepsLineBreaks() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("인물\n장소\n조명")
        viewModel.toggleParagraphSelectionMode()
        viewModel.selectPromptParagraphAt(4)

        viewModel.deleteSelectedPromptParagraph()

        assertEquals("인물\n\n조명", viewModel.uiState.value.promptTemplate)
        assertEquals(
            TextRange("인물\n".length),
            viewModel.promptTemplateTextFieldState.selection
        )
        assertTrue(!viewModel.uiState.value.isParagraphSelectionMode)
    }

    @Test
    fun cancelParagraphSelection_clearsSelectionState() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("인물\n장소")
        viewModel.toggleParagraphSelectionMode()
        viewModel.selectPromptParagraphAt(4)

        viewModel.cancelParagraphSelection()

        assertTrue(!viewModel.uiState.value.isParagraphSelectionMode)
        assertEquals(null, viewModel.uiState.value.selectedParagraphRange)
        assertEquals("", viewModel.uiState.value.paragraphSelectionMessage)
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

        assertTrue(
            viewModel.uiState.value.environmentStatus.isReadyFor(AutomationTargetApp.GEMINI)
        )
    }

    @Test
    fun init_restoresLastRunSnapshot() {
        val snapshotStorage = FakeLastRunSnapshotStorage(
            promptTemplate = "saved prompt",
            repeatCountText = "12",
            targetApp = AutomationTargetApp.CHATGPT.storageValue
        )

        val viewModel = viewModel(
            lastRunSnapshotStore = LastRunSnapshotStore(snapshotStorage)
        )

        assertEquals("saved prompt", viewModel.uiState.value.promptTemplate)
        assertEquals("12", viewModel.uiState.value.repeatCountText)
        assertEquals(AutomationTargetApp.CHATGPT, viewModel.uiState.value.selectedTargetApp)
    }

    @Test
    fun refreshStatus_keepsSetupDisplayInfoSeparateFromReadinessStatus() {
        val setupInfo = EnvironmentSetupInfo(
            wildcardDirectoryPath = "content://wildcard",
            nullKeyboardTargetImeId = "null/.Ime",
            adbGrantCommand = "adb grant command"
        )
        val environment = FakeEnvironmentStatusReader(
            status = readyEnvironment(),
            setupInfo = setupInfo
        )
        val viewModel = viewModel(environmentStatusReader = environment)

        viewModel.refreshStatus()

        assertEquals(setupInfo, viewModel.uiState.value.environmentSetupInfo)
        assertTrue(
            viewModel.uiState.value.environmentStatus
                .isReadyFor(AutomationTargetApp.GEMINI)
        )
    }

    @Test
    fun init_missingTargetAppInOldSnapshotDefaultsToGemini() {
        val viewModel = viewModel(
            lastRunSnapshotStore = LastRunSnapshotStore(
                FakeLastRunSnapshotStorage(
                    promptTemplate = "saved prompt",
                    repeatCountText = "3",
                    targetApp = ""
                )
            )
        )

        assertEquals(AutomationTargetApp.GEMINI, viewModel.uiState.value.selectedTargetApp)
    }

    @Test
    fun saveWildcardFolder_updatesSettingsMessageAndRefreshesStatus() {
        val environment = FakeEnvironmentStatusReader(readyEnvironment())
        val folderSaver = FakeWildcardFolderSaver(
            FolderSelectionResult.Success
        )
        val viewModel = viewModel(
            environmentStatusReader = environment,
            wildcardFolderSaver = folderSaver
        )

        viewModel.saveWildcardFolder("content://wildcard")

        assertEquals("content://wildcard", folderSaver.savedFolderUri)
        assertEquals("wildcard 폴더를 선택했습니다.", viewModel.uiState.value.settingsMessage)
        assertEquals("", viewModel.uiState.value.settingsError)
        assertEquals(2, environment.checkCount)
    }

    @Test
    fun saveWildcardFolder_updatesSettingsErrorWhenSaveFails() {
        val folderSaver = FakeWildcardFolderSaver(
            FolderSelectionResult.Failure("저장 권한 없음")
        )
        val viewModel = viewModel(wildcardFolderSaver = folderSaver)

        viewModel.saveWildcardFolder("content://wildcard")

        assertEquals("", viewModel.uiState.value.settingsMessage)
        assertEquals(
            "폴더 권한 저장 실패: 저장 권한 없음",
            viewModel.uiState.value.settingsError
        )
    }

    @Test
    fun runAutomation_savesLastRunSnapshotWhenRunStarts() {
        val snapshotStorage = FakeLastRunSnapshotStorage()
        val viewModel = viewModel(
            lastRunSnapshotStore = LastRunSnapshotStore(snapshotStorage)
        )

        viewModel.onPromptTemplateChange("prompt to resume")
        viewModel.onRepeatCountChange("7")
        viewModel.onTargetAppSelected(AutomationTargetApp.CHATGPT)
        viewModel.runAutomation()

        assertEquals("prompt to resume", snapshotStorage.promptTemplate)
        assertEquals("7", snapshotStorage.repeatCountText)
        assertEquals(AutomationTargetApp.CHATGPT.storageValue, snapshotStorage.targetApp)
    }

    @Test
    fun runAutomation_copiesPromptTemplateToClipboardWhenRunStarts() {
        val clipboardGateway = FakeClipboardGateway()
        val viewModel = viewModel(
            clipboardGateway = clipboardGateway
        )

        viewModel.onPromptTemplateChange("base __hair__ prompt")
        viewModel.runAutomation()

        assertEquals("base __hair__ prompt", clipboardGateway.writtenText)
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
        val service = FakePromptAutomationGateway()
        val viewModel = viewModel(
            runLogger = runLogger,
            lastRunSnapshotStore = LastRunSnapshotStore(
                FakeLastRunSnapshotStorage(
                    promptTemplate = "base __hair__",
                    repeatCountText = "1"
                )
            ),
            automationRunner = automation(
                runLogger = runLogger,
                lastRunSnapshotStore = LastRunSnapshotStore(
                    FakeLastRunSnapshotStorage(
                        promptTemplate = "base __hair__",
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
            viewModel.uiState.value.environmentStatus.isReadyFor(AutomationTargetApp.GEMINI) &&
                viewModel.uiState.value.promptTemplate == "base __hair__"
        }

        assertEquals(AutomationStartDecision.Started, viewModel.runAutomation())

        assertEquals(
            AutomationRunState.Running("자동화 준비 중"),
            viewModel.uiState.value.automationState
        )
        assertTrue(prepareStarted.await(2, TimeUnit.SECONDS))
        assertEquals(emptyList<String>(), service.sentPrompts)

        allowPrepareToFinish.countDown()

        waitUntil { service.sentPrompts.isNotEmpty() }
        assertEquals(RunAutomationUseCase.MARKER_PROMPT, service.sentPrompts.first())
    }

    @Test
    fun closeGeminiApp_updatesResultMessage() {
        val closer = FakeGeminiAppCloser(CloseGeminiAppResult.Success(closedCount = 2))
        val viewModel = viewModel(
            closeGeminiApp = CloseGeminiAppUseCase(closer)
        )

        viewModel.closeGeminiApp()

        assertEquals(1, closer.closeCount)
        assertTrue(!viewModel.uiState.value.isClosingGemini)
        assertEquals(
            "Gemini 앱 2개를 종료한 뒤 재시작했습니다.",
            viewModel.uiState.value.geminiCloseMessage
        )
    }

    @Test
    fun terminateGeminiApp_updatesResultMessageWithoutRestartText() {
        val closer = FakeGeminiAppCloser(CloseGeminiAppResult.Success(closedCount = 2))
        val viewModel = viewModel(
            terminateGeminiApp = CloseGeminiAppUseCase(closer)
        )

        viewModel.terminateGeminiApp()

        assertEquals(1, closer.closeCount)
        assertTrue(!viewModel.uiState.value.isClosingGemini)
        assertEquals(
            "Gemini 앱 2개를 종료했습니다.",
            viewModel.uiState.value.geminiCloseMessage
        )
    }

    @Test
    fun closeGeminiApp_requiresAccessibilityAndDoesNotCallCloser() {
        val closer = FakeGeminiAppCloser(CloseGeminiAppResult.Success(closedCount = 1))
        val viewModel = viewModel(
            environmentStatusReader = FakeEnvironmentStatusReader(
                readyEnvironment().copy(isAccessibilityServiceEnabled = false)
            ),
            closeGeminiApp = CloseGeminiAppUseCase(closer)
        )

        viewModel.closeGeminiApp()

        assertEquals(0, closer.closeCount)
        assertTrue(viewModel.uiState.value.geminiCloseMessage.contains("접근성"))
    }
    private fun viewModel(
        environmentStatusReader: FakeEnvironmentStatusReader = FakeEnvironmentStatusReader(readyEnvironment()),
        clipboardText: String = "",
        clipboardGateway: FakeClipboardGateway = FakeClipboardGateway(clipboardText),
        wildcardFolderSaver: FakeWildcardFolderSaver = FakeWildcardFolderSaver(),
        runLogger: RunLogger = RunLogger(FakeRunLogStorage()),
        lastRunSnapshotStore: LastRunSnapshotStore = LastRunSnapshotStore(FakeLastRunSnapshotStorage()),
        automationRunner: RunAutomationUseCase? = null,
        closeGeminiApp: CloseGeminiAppUseCase = CloseGeminiAppUseCase(FakeGeminiAppCloser()),
        terminateGeminiApp: CloseGeminiAppUseCase = CloseGeminiAppUseCase(FakeGeminiAppCloser()),
        dispatchers: AppDispatchers = AppDispatchers(io = Dispatchers.Unconfined),
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    ): MainViewModel {
        return MainViewModel(
            checkEnvironmentStatus = CheckEnvironmentStatusUseCase(environmentStatusReader),
            clipboardGateway = clipboardGateway,
            saveWildcardFolder = SaveWildcardFolderUseCase(wildcardFolderSaver),
            runLogger = runLogger,
            lastRunSnapshotStore = lastRunSnapshotStore,
            automation = automationRunner ?: automation(
                runLogger = runLogger,
                lastRunSnapshotStore = lastRunSnapshotStore,
                clipboardGateway = clipboardGateway,
                dispatchers = dispatchers
            ),
            closeGeminiApp = closeGeminiApp,
            terminateGeminiApp = terminateGeminiApp,
            dispatchers = dispatchers,
            coroutineScope = coroutineScope
        )
    }

    private fun automation(
        runLogger: RunLogger,
        lastRunSnapshotStore: LastRunSnapshotStore = LastRunSnapshotStore(FakeLastRunSnapshotStorage()),
        clipboardGateway: ClipboardGateway = FakeClipboardGateway(),
        service: FakePromptAutomationGateway = FakePromptAutomationGateway(),
        loadWildcards: () -> List<WildcardSet> = { emptyList() },
        dispatchers: AppDispatchers = AppDispatchers(io = Dispatchers.Unconfined)
    ): RunAutomationUseCase {
        var defaultImeId = ORIGINAL_IME_ID
        return RunAutomationUseCase(
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
            clipboardGateway = clipboardGateway,
            wildcardSetRepository = FakeWildcardSetRepository(loadWildcards),
            clock = { 1000L },
            promptGatewayProvider = PromptAutomationGatewayProvider { service },
            targetAppLauncher = TargetAppLauncher { true },
            dispatchers = dispatchers,
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "base", "prompt $index", emptyMap())
            }
        )
    }

    private class FakeEnvironmentStatusReader(
        var status: EnvironmentStatus,
        var setupInfo: EnvironmentSetupInfo = EnvironmentSetupInfo()
    ) : EnvironmentGateway {
        var checkCount = 0

        override fun check(): EnvironmentReport {
            checkCount += 1
            return EnvironmentReport(status = status, setupInfo = setupInfo)
        }
    }

    private class FakeClipboardGateway(
        private val readableText: String = ""
    ) : ClipboardGateway {
        var writtenText: String = ""

        override fun readText(): String = readableText

        override fun writeText(text: String) {
            writtenText = text
        }
    }

    private class FakeWildcardSetRepository(
        private val loadWildcards: () -> List<WildcardSet>
    ) : WildcardSetRepository {
        override fun load(): List<WildcardSet> = loadWildcards()
    }

    private class FakeWildcardFolderSaver(
        private val result: FolderSelectionResult = FolderSelectionResult.Success
    ) : WildcardFolderRepository {
        var savedFolderUri: String = ""

        override fun save(folderUri: String): FolderSelectionResult {
            savedFolderUri = folderUri
            return result
        }
    }


    private class FakeGeminiAppCloser(
        private val result: CloseGeminiAppResult = CloseGeminiAppResult.Success(closedCount = 1)
    ) : GeminiAppCloser {
        var closeCount = 0

        override suspend fun closeGeminiApp(): CloseGeminiAppResult {
            closeCount += 1
            return result
        }
    }

    private class FakePromptAutomationGateway : PromptAutomationGateway {
        val sentPrompts = mutableListOf<String>()

        override fun sendPrompt(
            prompt: String,
            newChatMode: NewChatMode,
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

    private class FakeRunLogStorage : RunLogRepository {
        private var logs: List<AutomationRunLog> = emptyList()

        override fun load(): List<AutomationRunLog> = logs

        override fun save(logs: List<AutomationRunLog>) {
            this.logs = logs
        }
    }

    private class FakeLastRunSnapshotStorage(
        var promptTemplate: String = "",
        var repeatCountText: String = "",
        var targetApp: String = ""
    ) : LastRunSnapshotRepository {
        override fun load(): LastRunSnapshot? {
            if (promptTemplate.isBlank() && repeatCountText.isBlank()) return null
            return LastRunSnapshot(
                promptTemplate = promptTemplate,
                repeatCountText = repeatCountText,
                targetApp = AutomationTargetApp.fromStorageValue(targetApp)
            )
        }

        override fun save(snapshot: LastRunSnapshot) {
            promptTemplate = snapshot.promptTemplate
            repeatCountText = snapshot.repeatCountText
            targetApp = snapshot.targetApp.storageValue
        }
    }

    private companion object {
        const val ORIGINAL_IME_ID = "example.keyboard/.Ime"
        const val NULL_IME_ID = "example.nullkeyboard/.NullIme"

        fun readyEnvironment(): EnvironmentStatus {
            return EnvironmentStatus(
                isGeminiInstalled = true,
                isChatGptInstalled = true,
                isAccessibilityServiceEnabled = true,
                hasWriteSecureSettingsPermission = true,
                isWildcardDirectoryAccessible = true
            )
        }
    }
}
