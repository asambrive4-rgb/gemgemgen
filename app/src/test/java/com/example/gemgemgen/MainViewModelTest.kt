// 역할: 메인 뷰모델의 상태 변경 및 비즈니스 이벤트 흐름을 검증합니다.
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
import com.example.gemgemgen.remote.domain.*
import com.example.gemgemgen.remote.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    fun pastePromptFromClipboard_insertsTextAtCursorPosition() {
        val viewModel = viewModel(clipboardText = "INSERT")
        viewModel.onPromptTemplateChange("beforeafter")
        viewModel.promptTemplateTextFieldState.edit {
            selection = TextRange(6)
        }

        viewModel.pastePromptFromClipboard()

        assertEquals("beforeINSERTafter", viewModel.uiState.value.promptTemplate)
        assertEquals(
            "beforeINSERTafter",
            viewModel.promptTemplateTextFieldState.text.toString()
        )
        assertEquals(TextRange(12), viewModel.promptTemplateTextFieldState.selection)
    }

    @Test
    fun pastePromptFromClipboard_replacesSelectedText() {
        val viewModel = viewModel(clipboardText = "REPLACE")
        viewModel.onPromptTemplateChange("beforeTargetafter")
        viewModel.promptTemplateTextFieldState.edit {
            selection = TextRange(6, 12)
        }

        viewModel.pastePromptFromClipboard()

        assertEquals("beforeREPLACEafter", viewModel.uiState.value.promptTemplate)
        assertEquals(
            "beforeREPLACEafter",
            viewModel.promptTemplateTextFieldState.text.toString()
        )
        assertEquals(TextRange(13), viewModel.promptTemplateTextFieldState.selection)
    }

    @Test
    fun pastePromptFromClipboard_withEmptyClipboardDoesNothing() {
        val viewModel = viewModel(clipboardText = "")
        viewModel.onPromptTemplateChange("original")
        viewModel.promptTemplateTextFieldState.edit {
            selection = TextRange(4)
        }

        viewModel.pastePromptFromClipboard()

        assertEquals("original", viewModel.uiState.value.promptTemplate)
        assertEquals(
            "original",
            viewModel.promptTemplateTextFieldState.text.toString()
        )
        assertEquals(TextRange(4), viewModel.promptTemplateTextFieldState.selection)
    }

    @Test
    fun insertSystemInstruction_prependsToEmptyPrompt() {
        val viewModel = viewModel()

        viewModel.insertSystemInstruction()

        assertEquals(
            SystemInstructionPrompt.text,
            viewModel.uiState.value.promptTemplate
        )
        assertEquals(
            SystemInstructionPrompt.text,
            viewModel.promptTemplateTextFieldState.text.toString()
        )
        assertEquals(
            TextRange(SystemInstructionPrompt.text.length),
            viewModel.promptTemplateTextFieldState.selection
        )
        assertTrue(viewModel.uiState.value.canUndoPromptEdit)
    }

    @Test
    fun insertSystemInstruction_prependsWithBlankLineBeforeExistingBody() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("user body")

        viewModel.insertSystemInstruction()

        val expected = SystemInstructionPrompt.text + "\n\n" + "user body"
        assertEquals(expected, viewModel.uiState.value.promptTemplate)
        assertEquals(
            expected,
            viewModel.promptTemplateTextFieldState.text.toString()
        )
        assertEquals(
            TextRange(SystemInstructionPrompt.text.length + 2),
            viewModel.promptTemplateTextFieldState.selection
        )
    }

    @Test
    fun insertSystemInstruction_doubleTapPrependsAgainAndUndoRestores() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("body")

        viewModel.insertSystemInstruction()
        val once = viewModel.uiState.value.promptTemplate
        viewModel.insertSystemInstruction()
        val twice = viewModel.uiState.value.promptTemplate

        assertEquals(
            SystemInstructionPrompt.text + "\n\n" + once,
            twice
        )

        viewModel.undoPromptEdit()
        assertEquals(once, viewModel.uiState.value.promptTemplate)

        viewModel.undoPromptEdit()
        assertEquals("body", viewModel.uiState.value.promptTemplate)
    }

    @Test
    fun copyPromptToClipboard_writesPromptTemplate() {
        val clipboardGateway = FakeClipboardGateway()
        val viewModel = viewModel(clipboardGateway = clipboardGateway)

        viewModel.onPromptTemplateChange("prompt to copy")
        viewModel.copyPromptToClipboard()

        assertEquals("prompt to copy", clipboardGateway.writtenText)
    }

    @Test
    fun copyPromptToClipboard_withBlankPromptDoesNothing() {
        val clipboardGateway = FakeClipboardGateway()
        val viewModel = viewModel(clipboardGateway = clipboardGateway)

        viewModel.onPromptTemplateChange("   ")
        viewModel.copyPromptToClipboard()

        assertEquals("", clipboardGateway.writtenText)
    }

    @Test
    fun replacePromptTemplateSegment_preservesOtherEditsAndSupportsUndo() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("quality, red hair and blue dress, masterpiece")

        val replacedStart = viewModel.replacePromptTemplateSegment(
            expectedSegment = "blue dress",
            replacement = "검은 원피스",
            preferredStartIndex = 13
        )

        assertEquals(22, replacedStart)
        assertEquals(
            "quality, red hair and 검은 원피스, masterpiece",
            viewModel.uiState.value.promptTemplate
        )

        viewModel.undoPromptEdit()

        assertEquals(
            "quality, red hair and blue dress, masterpiece",
            viewModel.uiState.value.promptTemplate
        )
    }

    @Test
    fun replacePromptTemplateSegment_doesNotChangePromptWhenSegmentIsMissing() {
        val viewModel = viewModel()
        viewModel.onPromptTemplateChange("사용자가 수정한 프롬프트")

        val replacedStart = viewModel.replacePromptTemplateSegment(
            expectedSegment = "blue dress",
            replacement = "검은 원피스",
            preferredStartIndex = 13
        )

        assertEquals(null, replacedStart)
        assertEquals("사용자가 수정한 프롬프트", viewModel.uiState.value.promptTemplate)
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
    fun decideWildcardFolderAction_delegatesToPolicyBasedOnEnvironmentStatus() {
        val allFilesEnv = readyEnvironment().copy(hasAllFilesAccess = true, isWildcardDirectoryAccessible = true)
        val viewModelAllFiles = viewModel(environmentStatusReader = FakeEnvironmentStatusReader(allFilesEnv))
        assertEquals(WildcardFolderAction.OpenDirectFolder, viewModelAllFiles.decideWildcardFolderAction())

        val inaccessibleEnv = readyEnvironment().copy(hasAllFilesAccess = false, isWildcardDirectoryAccessible = false)
        val viewModelInaccessible = viewModel(environmentStatusReader = FakeEnvironmentStatusReader(inaccessibleEnv))
        assertEquals(WildcardFolderAction.OpenStorageSettings, viewModelInaccessible.decideWildcardFolderAction())

        val safEnv = readyEnvironment().copy(hasAllFilesAccess = false, isWildcardDirectoryAccessible = true)
        val viewModelSaf = viewModel(environmentStatusReader = FakeEnvironmentStatusReader(safEnv))
        assertEquals(WildcardFolderAction.LaunchSafPicker, viewModelSaf.decideWildcardFolderAction())
    }

    @Test
    fun getInitialWildcardFolderUri_returnsStoredFolderUri() {
        val folderSaver = FakeWildcardFolderSaver()
        folderSaver.savedFolderUri = "content://stored/uri"
        val viewModel = viewModel(wildcardFolderSaver = folderSaver)

        assertEquals("content://stored/uri", viewModel.getInitialWildcardFolderUri())
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
    fun onRepeatCountChange_whileRunning_updatesEngineBadgeAndLastRunDefault() {
        val snapshotStorage = FakeLastRunSnapshotStorage()
        val holdingService = HoldingPromptAutomationGateway(
            progressState = AutomationRunState.Running("전송 중", currentIndex = 1, totalCount = 5)
        )
        val snapshotStore = LastRunSnapshotStore(snapshotStorage)
        val viewModel = viewModel(
            lastRunSnapshotStore = snapshotStore,
            automationRunner = automation(
                lastRunSnapshotStore = snapshotStore,
                service = holdingService
            )
        )

        viewModel.onPromptTemplateChange("live prompt")
        viewModel.onRepeatCountChange("5")
        assertEquals(AutomationStartDecision.Started, viewModel.runAutomation())
        waitUntil { viewModel.uiState.value.isRunning }
        waitUntil { holdingService.sentPrompts.isNotEmpty() }

        viewModel.onRepeatCountChange("7")

        assertEquals("7", viewModel.uiState.value.repeatCountText)
        assertEquals("7", viewModel.automationBarUiState.value.repeatCountText)
        val barState = viewModel.automationBarUiState.value.automationState
        assertTrue(barState is AutomationRunState.Running)
        assertEquals(7, (barState as AutomationRunState.Running).totalCount)
        assertEquals("7", snapshotStorage.repeatCountText)
        assertEquals("live prompt", snapshotStorage.promptTemplate)
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
    fun runAutomation_showsPreparingStateUntilWildcardPreparationFinishes() {
        val prepareStarted = CountDownLatch(1)
        val allowPrepareToFinish = CountDownLatch(1)
        val service = FakePromptAutomationGateway()
        val viewModel = viewModel(
            lastRunSnapshotStore = LastRunSnapshotStore(
                FakeLastRunSnapshotStorage(
                    promptTemplate = "base __hair__",
                    repeatCountText = "1"
                )
            ),
            automationRunner = automation(
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
    fun runAutomation_keepsDetailedProgressInAutomationBarState() {
        val service = HoldingPromptAutomationGateway(
            AutomationRunState.Running(
                step = "typing prompt",
                currentIndex = 1,
                totalCount = 2
            )
        )
        val viewModel = viewModel(
            automationRunner = automation(service = service)
        )

        viewModel.onPromptTemplateChange("base prompt")

        assertEquals(AutomationStartDecision.Started, viewModel.runAutomation())

        assertTrue(viewModel.uiState.value.automationState is AutomationRunState.Running)
        assertEquals(
            AutomationRunState.Running("자동화 준비 중"),
            viewModel.uiState.value.automationState
        )
        val automationBarState = viewModel.automationBarUiState.value.automationState
        assertTrue(automationBarState is AutomationRunState.Running)
        assertEquals("typing prompt", (automationBarState as AutomationRunState.Running).step)
        assertTrue(viewModel.uiState.value.automationState != automationBarState)
        assertTrue(!viewModel.uiState.value.canRun)
    }

    @Test
    fun runAutomation_updatesMainAndBarStateWhenFinished() {
        val viewModel = viewModel()

        viewModel.onPromptTemplateChange("base prompt")

        assertEquals(AutomationStartDecision.Started, viewModel.runAutomation())

        assertEquals(AutomationRunState.Success, viewModel.uiState.value.automationState)
        assertEquals(
            AutomationRunState.Success,
            viewModel.automationBarUiState.value.automationState
        )
    }

    @Test
    fun closeGeminiApp_updatesResultMessage() {
        val closer = FakeGeminiAppCloser(CloseGeminiAppResult.Success(closedCount = 2))
        val viewModel = viewModel(
            closeGeminiApp = CloseGeminiAppUseCase(closer)
        )

        viewModel.closeGeminiApp()

        assertEquals(1, closer.closeCount)
        assertTrue(!viewModel.uiState.value.isMaintenanceBusy)
        assertEquals(
            "Gemini 앱 2개를 종료하고 재시작했습니다.",
            viewModel.uiState.value.maintenanceMessage
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
        assertTrue(!viewModel.uiState.value.isMaintenanceBusy)
        assertEquals(
            "Gemini 앱 2개를 종료했습니다.",
            viewModel.uiState.value.maintenanceMessage
        )
    }

    @Test
    fun cleanDeviceMemory_updatesSuccessResult() {
        val gateway = FakeMemoryCleanupGateway(MemoryCleanupResult.Success)
        val viewModel = viewModel(
            cleanDeviceMemoryUseCase = CleanDeviceMemoryUseCase(gateway)
        )

        viewModel.cleanDeviceMemory()

        assertEquals(1, gateway.cleanCount)
        assertTrue(!viewModel.uiState.value.isMaintenanceBusy)
        assertTrue(viewModel.uiState.value.maintenanceMessage.contains("메모리 정리"))
    }

    @Test
    fun cleanDeviceMemory_inSenderMode_delegatesToRemoteGateway() {
        val remoteGateway = object : com.example.gemgemgen.remote.usecase.RemoteAutomationGateway {
            var cleanCount = 0
            private val currentStatus = kotlinx.coroutines.flow.MutableStateFlow(
                com.example.gemgemgen.remote.domain.RemoteAutomationStatus(
                    mode = AutomationMode.SENDER,
                    discoveredDeviceName = "S24 FE",
                    isPaired = true
                )
            )
            override val status: kotlinx.coroutines.flow.StateFlow<com.example.gemgemgen.remote.domain.RemoteAutomationStatus> = currentStatus

            override fun selectMode(mode: AutomationMode) {
                currentStatus.value = currentStatus.value.copy(mode = mode)
            }
            override suspend fun pair(pairingCode: String): RemoteActionResult = RemoteActionResult.Success
            override suspend fun disconnect(): RemoteActionResult = RemoteActionResult.Success
            override suspend fun send(
                request: com.example.gemgemgen.remote.domain.RemoteAutomationRequest,
                onStateChange: (AutomationRunState) -> Unit
            ) = Unit
            override fun forceStop(requestId: String?) = Unit
            override suspend fun cleanMemory(): RemoteActionResult {
                cleanCount += 1
                return RemoteActionResult.Success
            }
        }
        val manageRemote = ManageRemoteAutomationUseCase(remoteGateway)
        val viewModel = viewModel(
            manageRemoteAutomation = manageRemote
        )

        viewModel.cleanDeviceMemory()

        assertEquals(1, remoteGateway.cleanCount)
        assertTrue(viewModel.uiState.value.maintenanceMessage.contains("수신 기기 메모리를 정리했습니다"))
    }

    @Test
    fun cleanDeviceMemory_updatesFailureAndAccessibilityResults() {
        val failureGateway = FakeMemoryCleanupGateway(
            MemoryCleanupResult.Failure("clean button missing")
        )
        val failureViewModel = viewModel(
            cleanDeviceMemoryUseCase = CleanDeviceMemoryUseCase(failureGateway)
        )

        failureViewModel.cleanDeviceMemory()

        assertTrue(
            failureViewModel.uiState.value.maintenanceMessage.contains("clean button missing")
        )

        val unavailableGateway = FakeMemoryCleanupGateway(
            MemoryCleanupResult.AccessibilityUnavailable
        )
        val unavailableViewModel = viewModel(
            cleanDeviceMemoryUseCase = CleanDeviceMemoryUseCase(unavailableGateway)
        )

        unavailableViewModel.cleanDeviceMemory()

        assertTrue(
            unavailableViewModel.uiState.value.maintenanceMessage.contains("접근성")
        )
    }

    @Test
    fun cleanDeviceMemory_isBlockedWhileAutomationRuns() {
        val gateway = FakeMemoryCleanupGateway(MemoryCleanupResult.Success)
        val runner = HoldingPromptAutomationGateway(
            AutomationRunState.Running("running")
        )
        val viewModel = viewModel(
            automationRunner = automation(service = runner),
            cleanDeviceMemoryUseCase = CleanDeviceMemoryUseCase(gateway)
        )
        viewModel.onPromptTemplateChange("base")
        assertEquals(AutomationStartDecision.Started, viewModel.runAutomation())

        viewModel.cleanDeviceMemory()

        assertEquals(0, gateway.cleanCount)
        assertTrue(viewModel.uiState.value.maintenanceMessage.contains("자동화"))
    }

    @Test
    fun cleanDeviceMemory_blocksDuplicateCallsUntilFirstCompletes() {
        val gateway = ControlledMemoryCleanupGateway()
        val viewModel = viewModel(
            cleanDeviceMemoryUseCase = CleanDeviceMemoryUseCase(gateway)
        )

        viewModel.cleanDeviceMemory()
        assertTrue(gateway.started.await(2, TimeUnit.SECONDS))
        viewModel.cleanDeviceMemory()

        assertEquals(1, gateway.cleanCount)
        assertTrue(viewModel.uiState.value.maintenanceMessage.contains("이미"))

        gateway.result.complete(MemoryCleanupResult.Success)
        waitUntil { !viewModel.uiState.value.isMaintenanceBusy }
    }

    @Test
    fun cleanDeviceMemory_restoresStateWhenCanceled() {
        val gateway = ControlledMemoryCleanupGateway()
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val viewModel = viewModel(
            cleanDeviceMemoryUseCase = CleanDeviceMemoryUseCase(gateway),
            coroutineScope = scope
        )

        viewModel.cleanDeviceMemory()
        assertTrue(gateway.started.await(2, TimeUnit.SECONDS))

        scope.cancel()

        waitUntil { !viewModel.uiState.value.isMaintenanceBusy }
        assertTrue(viewModel.uiState.value.maintenanceMessage.contains("취소"))
    }

    @Test
    fun terminateSelfApp_updatesResultMessage() {
        val closer = FakeGeminiAppCloser(CloseGeminiAppResult.Success(closedCount = 1))
        val viewModel = viewModel(
            terminateSelfApp = CloseGeminiAppUseCase(closer)
        )

        viewModel.terminateSelfApp()

        assertEquals(1, closer.closeCount)
        assertTrue(!viewModel.uiState.value.isMaintenanceBusy)
        assertEquals(
            "앱을 종료했습니다.",
            viewModel.uiState.value.maintenanceMessage
        )
    }

    @Test
    fun terminateSelfApp_requiresAccessibilityAndDoesNotCallCloser() {
        val closer = FakeGeminiAppCloser(CloseGeminiAppResult.Success(closedCount = 1))
        val viewModel = viewModel(
            environmentStatusReader = FakeEnvironmentStatusReader(
                readyEnvironment().copy(isAccessibilityServiceEnabled = false)
            ),
            terminateSelfApp = CloseGeminiAppUseCase(closer)
        )

        viewModel.terminateSelfApp()

        assertEquals(0, closer.closeCount)
        assertTrue(viewModel.uiState.value.maintenanceMessage.contains("접근성"))
    }

    @Test
    fun terminateSelfApp_blockedWhileAutomationRunning() {
        val closer = FakeGeminiAppCloser(CloseGeminiAppResult.Success(closedCount = 1))
        val runner = HoldingPromptAutomationGateway(
            AutomationRunState.Running("진행 중")
        )
        val viewModel = viewModel(
            automationRunner = automation(service = runner),
            terminateSelfApp = CloseGeminiAppUseCase(closer)
        )
        viewModel.onPromptTemplateChange("base")
        assertEquals(AutomationStartDecision.Started, viewModel.runAutomation())
        assertTrue(viewModel.uiState.value.isRunning)

        viewModel.terminateSelfApp()

        assertEquals(0, closer.closeCount)
        assertTrue(
            viewModel.uiState.value.maintenanceMessage.contains("자동화 중에는 앱을 종료")
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
        assertTrue(viewModel.uiState.value.maintenanceMessage.contains("접근성"))
    }

    @Test
    fun showSettings_whenAccessibilityOff_showsPromptInsteadOfSettings() {
        val viewModel = viewModel(
            environmentStatusReader = FakeEnvironmentStatusReader(
                readyEnvironment().copy(isAccessibilityServiceEnabled = false)
            )
        )

        viewModel.showSettings()

        assertTrue(viewModel.uiState.value.showAccessibilityPrompt)
        assertTrue(!viewModel.uiState.value.showSettings)
    }

    @Test
    fun showSettings_whenAccessibilityOn_opensSettingsDialog() {
        val viewModel = viewModel()

        viewModel.showSettings()

        assertTrue(viewModel.uiState.value.showSettings)
        assertTrue(!viewModel.uiState.value.showAccessibilityPrompt)
    }

    @Test
    fun confirmAccessibilityPrompt_hidesPrompt() {
        val viewModel = viewModel(
            environmentStatusReader = FakeEnvironmentStatusReader(
                readyEnvironment().copy(isAccessibilityServiceEnabled = false)
            )
        )
        viewModel.showSettings()

        viewModel.confirmAccessibilityPrompt()

        assertTrue(!viewModel.uiState.value.showAccessibilityPrompt)
        assertTrue(!viewModel.uiState.value.showSettings)
    }

    @Test
    fun dismissAccessibilityPromptToSettings_opensFullSettings() {
        val viewModel = viewModel(
            environmentStatusReader = FakeEnvironmentStatusReader(
                readyEnvironment().copy(isAccessibilityServiceEnabled = false)
            )
        )
        viewModel.showSettings()

        viewModel.dismissAccessibilityPromptToSettings()

        assertTrue(!viewModel.uiState.value.showAccessibilityPrompt)
        assertTrue(viewModel.uiState.value.showSettings)
    }

    @Test
    fun openPromptHistory_and_closePromptHistory_updatesUiState() {
        val repo = FakePromptHistoryRepository()
        val store = PromptHistoryStore(repo)
        store.record("test prompt 1", AutomationTargetApp.CHATGPT)
        val viewModel = viewModel(promptHistoryStore = store)

        viewModel.openPromptHistory()
        assertTrue(viewModel.uiState.value.showPromptHistory)
        assertEquals(1, viewModel.uiState.value.promptHistoryItems.size)
        assertEquals("test prompt 1", viewModel.uiState.value.promptHistoryItems[0].prompt)

        viewModel.closePromptHistory()
        assertTrue(!viewModel.uiState.value.showPromptHistory)
    }

    @Test
    fun selectPromptHistoryItem_replacesPromptText_and_backsUpPreviousToUndoStack() {
        val repo = FakePromptHistoryRepository()
        val store = PromptHistoryStore(repo)
        val viewModel = viewModel(promptHistoryStore = store)

        viewModel.onPromptTemplateChange("현재 작성 중이던 텍스트")
        val item = PromptHistoryItem(
            id = "1",
            prompt = "히스토리에서 고른 프롬프트",
            targetApp = AutomationTargetApp.GEMINI,
            createdAtMillis = 1000L
        )

        viewModel.selectPromptHistoryItem(item)

        assertEquals("히스토리에서 고른 프롬프트", viewModel.uiState.value.promptTemplate)
        assertEquals("히스토리에서 고른 프롬프트", viewModel.promptTemplateTextFieldState.text.toString())
        assertTrue(!viewModel.uiState.value.showPromptHistory)

        // 안전망 검증: undo 실행 시 원래 작성 중이던 텍스트로 복원되어야 함
        assertTrue(viewModel.uiState.value.canUndoPromptEdit)
        viewModel.undoPromptEdit()
        assertEquals("현재 작성 중이던 텍스트", viewModel.uiState.value.promptTemplate)
        assertEquals("현재 작성 중이던 텍스트", viewModel.promptTemplateTextFieldState.text.toString())
    }

    @Test
    fun clearPromptHistory_clearsRepository_and_updatesUiState() {
        val repo = FakePromptHistoryRepository()
        val store = PromptHistoryStore(repo)
        store.record("prompt to clear", AutomationTargetApp.GEMINI)
        val viewModel = viewModel(promptHistoryStore = store)

        viewModel.openPromptHistory()
        assertEquals(1, viewModel.uiState.value.promptHistoryItems.size)

        viewModel.clearPromptHistory()
        assertTrue(viewModel.uiState.value.promptHistoryItems.isEmpty())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun runAutomation_inSenderMode_whenFailsDuringRun_playsShortAlertOnce() {
        val soundAlert = FakeSoundAlertGateway()
        val remoteGateway = FakeRemoteGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = true
            )
        )
        remoteGateway.sendAction = { _, onStateChange ->
            onStateChange(AutomationRunState.Running("작업 진행 중"))
            onStateChange(AutomationRunState.Failure("S25 FE 연결 끊김"))
            onStateChange(AutomationRunState.Failure("추가 에러"))
        }
        val remoteUseCase = ManageRemoteAutomationUseCase(remoteGateway)
        val viewModel = viewModel(
            soundAlertGateway = soundAlert,
            manageRemoteAutomation = remoteUseCase
        )
        viewModel.onAutomationModeSelected(AutomationMode.SENDER)
        viewModel.onPromptTemplateChange("테스트 프롬프트")

        viewModel.runAutomation()

        // 시작 후 실패가 발생했으므로 알림음이 정확히 1회 울려야 함
        assertEquals(1, soundAlert.playCount)
    }

    @Test
    fun runAutomation_inSenderMode_whenSucceeds_doesNotPlayAlert() {
        val soundAlert = FakeSoundAlertGateway()
        val remoteGateway = FakeRemoteGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = true
            )
        )
        remoteGateway.sendAction = { _, onStateChange ->
            onStateChange(AutomationRunState.Running("작업 진행 중"))
            onStateChange(AutomationRunState.Success)
        }
        val remoteUseCase = ManageRemoteAutomationUseCase(remoteGateway)
        val viewModel = viewModel(
            soundAlertGateway = soundAlert,
            manageRemoteAutomation = remoteUseCase
        )
        viewModel.onAutomationModeSelected(AutomationMode.SENDER)
        viewModel.onPromptTemplateChange("테스트 프롬프트")

        viewModel.runAutomation()

        // 정상 성공 시에는 알림음이 울리지 않아야 함
        assertEquals(0, soundAlert.playCount)
    }

    @Test
    fun runAutomation_inSenderMode_whenCancelledByUser_doesNotPlayAlert() {
        val soundAlert = FakeSoundAlertGateway()
        val remoteGateway = FakeRemoteGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = true
            )
        )
        val remoteUseCase = ManageRemoteAutomationUseCase(remoteGateway)
        val viewModel = viewModel(
            soundAlertGateway = soundAlert,
            manageRemoteAutomation = remoteUseCase
        )
        viewModel.onAutomationModeSelected(AutomationMode.SENDER)
        viewModel.onPromptTemplateChange("테스트 프롬프트")

        viewModel.runAutomation()
        viewModel.cancelAutomation()

        // 사용자가 직접 중지한 경우 알림음 미재생
        assertEquals(0, soundAlert.playCount)
    }

    @Test
    fun failure_withoutStartingSenderRun_doesNotPlayAlert() {
        val soundAlert = FakeSoundAlertGateway()
        val remoteGateway = FakeRemoteGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = false
            )
        )
        val remoteUseCase = ManageRemoteAutomationUseCase(remoteGateway)
        val viewModel = viewModel(
            soundAlertGateway = soundAlert,
            manageRemoteAutomation = remoteUseCase
        )
        viewModel.onAutomationModeSelected(AutomationMode.SENDER)

        // 시작 버튼을 누르지 않은 상태에서 페어링 시도 실패
        viewModel.pairRemoteDevice("1234")

        assertEquals(0, soundAlert.playCount)
    }

    @Test
    fun disconnectRemoteDevice_whenSuccess_updatesMessageAndDisconnectedStatus() {
        val remoteGateway = FakeRemoteGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = true
            )
        )
        val remoteUseCase = ManageRemoteAutomationUseCase(remoteGateway)
        val viewModel = viewModel(manageRemoteAutomation = remoteUseCase)
        viewModel.onAutomationModeSelected(AutomationMode.SENDER)

        viewModel.disconnectRemoteDevice()

        assertEquals("원격 연결을 끊었습니다.", viewModel.uiState.value.remoteDisconnectMessage)
        assertEquals(false, viewModel.uiState.value.isDisconnectingRemote)
        assertEquals(1, remoteGateway.disconnectCallCount)
        assertEquals(false, viewModel.uiState.value.remoteAutomationStatus.isPaired)
    }

    @Test
    fun disconnectRemoteDevice_whenAutomationRunning_showsWarningAndBlocksDisconnect() {
        val remoteGateway = FakeRemoteGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = true,
                automationState = AutomationRunState.Running("원격 실행 중")
            )
        )
        val remoteUseCase = ManageRemoteAutomationUseCase(remoteGateway)
        val viewModel = viewModel(manageRemoteAutomation = remoteUseCase)
        viewModel.onAutomationModeSelected(AutomationMode.SENDER)

        viewModel.disconnectRemoteDevice()

        assertEquals("원격 자동화를 중지한 뒤 연결을 끊어주세요.", viewModel.uiState.value.remoteDisconnectMessage)
        assertEquals(false, viewModel.uiState.value.isDisconnectingRemote)
        assertEquals(0, remoteGateway.disconnectCallCount)
    }

    @Test
    fun disconnectRemoteDevice_whenGatewayFails_updatesErrorMessage() {
        val remoteGateway = FakeRemoteGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = true
            )
        )
        remoteGateway.disconnectResult = RemoteActionResult.Failure("네트워크 연결이 불안정합니다.")
        val remoteUseCase = ManageRemoteAutomationUseCase(remoteGateway)
        val viewModel = viewModel(manageRemoteAutomation = remoteUseCase)
        viewModel.onAutomationModeSelected(AutomationMode.SENDER)

        viewModel.disconnectRemoteDevice()

        assertEquals("네트워크 연결이 불안정합니다.", viewModel.uiState.value.remoteDisconnectMessage)
        assertEquals(false, viewModel.uiState.value.isDisconnectingRemote)
        assertEquals(1, remoteGateway.disconnectCallCount)
    }

    @Test
    fun runAutomation_inNormalMode_whenFails_doesNotPlayAlert() {
        val soundAlert = FakeSoundAlertGateway()
        val viewModel = viewModel(
            soundAlertGateway = soundAlert,
            automationRunner = automation(
                service = object : PromptAutomationGateway {
                    override fun sendPrompt(
                        prompt: String,
                        newChatMode: NewChatMode,
                        onStateChange: (AutomationRunState) -> Unit,
                        onDone: () -> Unit
                    ) {
                        onStateChange(AutomationRunState.Failure("일반 모드 오류"))
                    }

                    override fun cancelCurrentRun() = Unit
                }
            )
        )
        viewModel.onPromptTemplateChange("일반 모드 프롬프트")

        viewModel.runAutomation()

        // 일반 모드에서는 송신 모드 알림음이 울리지 않아야 함
        assertEquals(0, soundAlert.playCount)
    }

    private fun viewModel(
        environmentStatusReader: FakeEnvironmentStatusReader = FakeEnvironmentStatusReader(readyEnvironment()),
        clipboardText: String = "",
        clipboardGateway: FakeClipboardGateway = FakeClipboardGateway(clipboardText),
        wildcardFolderSaver: FakeWildcardFolderSaver = FakeWildcardFolderSaver(),
        lastRunSnapshotStore: LastRunSnapshotStore = LastRunSnapshotStore(FakeLastRunSnapshotStorage()),
        automationRunner: RunAutomationUseCase? = null,
        appMaintenance: AppMaintenanceUseCase? = null,
        closeGeminiApp: CloseGeminiAppUseCase? = null,
        terminateGeminiApp: CloseGeminiAppUseCase? = null,
        terminateSelfApp: CloseGeminiAppUseCase? = null,
        cleanDeviceMemoryUseCase: CleanDeviceMemoryUseCase? = null,
        promptHistoryStore: PromptHistoryStore? = null,
        manageRemoteAutomation: ManageRemoteAutomationUseCase? = null,
        soundAlertGateway: SoundAlertGateway = NoOpSoundAlertGateway,
        dispatchers: AppDispatchers = AppDispatchers(io = Dispatchers.Unconfined),
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    ): MainViewModel {
        val resolvedMaintenance = appMaintenance ?: AppMaintenanceUseCase(
            geminiRestartCloser = closeGeminiApp?.let { FakeGeminiCloserAdapter(it) } ?: FakeGeminiAppCloser(),
            geminiTerminateCloser = terminateGeminiApp?.let { FakeGeminiCloserAdapter(it) } ?: FakeGeminiAppCloser(),
            selfAppCloser = terminateSelfApp?.let { FakeGeminiCloserAdapter(it) } ?: FakeGeminiAppCloser(),
            memoryCleanupGateway = cleanDeviceMemoryUseCase?.let { FakeMemoryGatewayAdapter(it) } ?: FakeMemoryCleanupGateway()
        )
        return MainViewModel(
            checkEnvironmentStatus = CheckEnvironmentStatusUseCase(environmentStatusReader),
            clipboardGateway = clipboardGateway,
            saveWildcardFolder = SaveWildcardFolderUseCase(wildcardFolderSaver),
            lastRunSnapshotStore = lastRunSnapshotStore,
            automation = automationRunner ?: automation(
                lastRunSnapshotStore = lastRunSnapshotStore,
                clipboardGateway = clipboardGateway,
                dispatchers = dispatchers
            ),
            appMaintenance = resolvedMaintenance,
            manageRemoteAutomation = manageRemoteAutomation ?: ManageRemoteAutomationUseCase(
                NoOpRemoteAutomationGateway()
            ),
            soundAlertGateway = soundAlertGateway,
            promptHistoryStore = promptHistoryStore,
            dispatchers = dispatchers,
            coroutineScope = coroutineScope
        )
    }

    private class FakeGeminiCloserAdapter(private val useCase: CloseGeminiAppUseCase) : GeminiAppCloser {
        override suspend fun closeGeminiApp(): CloseGeminiAppResult = useCase.close()
    }

    private class FakeMemoryGatewayAdapter(private val useCase: CleanDeviceMemoryUseCase) : MemoryCleanupGateway {
        override suspend fun cleanMemory(): MemoryCleanupResult = useCase.clean()
    }

    private fun automation(
        lastRunSnapshotStore: LastRunSnapshotStore = LastRunSnapshotStore(FakeLastRunSnapshotStorage()),
        clipboardGateway: ClipboardGateway = FakeClipboardGateway(),
        service: PromptAutomationGateway = FakePromptAutomationGateway(),
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
            lastRunSnapshotStore = lastRunSnapshotStore,
            clipboardGateway = clipboardGateway,
            wildcardSetRepository = FakeWildcardSetRepository(loadWildcards),
            promptGatewayProvider = PromptAutomationGatewayProvider { service },
            targetAppLauncher = TargetAppLauncher { true },
            dispatchers = dispatchers,
            generateFinalPrompt = { _, _, index -> "prompt $index" }
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

        override fun getFolderUri(): String? {
            return savedFolderUri.ifEmpty { null }
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

    private class FakeMemoryCleanupGateway(
        private val result: MemoryCleanupResult = MemoryCleanupResult.Success
    ) : MemoryCleanupGateway {
        var cleanCount = 0

        override suspend fun cleanMemory(): MemoryCleanupResult {
            cleanCount += 1
            return result
        }
    }

    private class ControlledMemoryCleanupGateway : MemoryCleanupGateway {
        val started = CountDownLatch(1)
        val result = CompletableDeferred<MemoryCleanupResult>()
        var cleanCount = 0

        override suspend fun cleanMemory(): MemoryCleanupResult {
            cleanCount += 1
            started.countDown()
            return result.await()
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

    private class HoldingPromptAutomationGateway(
        private val progressState: AutomationRunState
    ) : PromptAutomationGateway {
        val sentPrompts = mutableListOf<String>()

        override fun sendPrompt(
            prompt: String,
            newChatMode: NewChatMode,
            onStateChange: (AutomationRunState) -> Unit,
            onDone: () -> Unit
        ) {
            sentPrompts += prompt
            onStateChange(progressState)
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

    private class FakeSoundAlertGateway : SoundAlertGateway {
        var playCount = 0
        override fun playShortAlert() {
            playCount += 1
        }
    }

    private class FakeRemoteGateway(
        initialStatus: RemoteAutomationStatus = RemoteAutomationStatus()
    ) : RemoteAutomationGateway {
        override val status = MutableStateFlow(initialStatus)
        var lastStateCallback: ((AutomationRunState) -> Unit)? = null
        var sendAction: (suspend (RemoteAutomationRequest, (AutomationRunState) -> Unit) -> Unit)? = null

        override fun selectMode(mode: AutomationMode) {
            status.value = status.value.copy(mode = mode)
        }

        override suspend fun pair(pairingCode: String): RemoteActionResult = RemoteActionResult.Success

        var disconnectResult: RemoteActionResult = RemoteActionResult.Success
        var disconnectCallCount = 0

        override suspend fun disconnect(): RemoteActionResult {
            disconnectCallCount++
            if (disconnectResult is RemoteActionResult.Success) {
                status.value = status.value.copy(
                    isPaired = false,
                    connectionMessage = "원격 연결을 끊었습니다."
                )
            }
            return disconnectResult
        }

        override suspend fun send(
            request: RemoteAutomationRequest,
            onStateChange: (AutomationRunState) -> Unit
        ) {
            lastStateCallback = onStateChange
            sendAction?.invoke(request, onStateChange)
        }

        override fun forceStop(requestId: String?) {
            lastStateCallback?.invoke(AutomationRunState.Stopped)
        }

        override suspend fun cleanMemory(): RemoteActionResult = RemoteActionResult.Success
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
