package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.wildcard.domain.WildcardSet
import com.example.gemgemgen.wildcard.usecase.NoOpWildcardSetRepository
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartAutomationUseCaseTest {

    @Test
    fun decideStart_requiresOverlayPermissionBeforeStarting() {
        val checkAutomationStart = CheckAutomationStartUseCase(
            OverlayPermissionGateway { false }
        )
        val (automation, _) = createAutomation()
        val useCase = StartAutomationUseCase(
            checkAutomationStart = checkAutomationStart,
            automationStartRecorder = FakeAutomationStartRecorder(),
            automation = automation
        )

        val decision = useCase.decideStart(canRun = true, isStartInProgress = false)

        assertEquals(AutomationStartDecision.PermissionRequired, decision)
    }

    @Test
    fun decideStart_startsOnlyWhenReadyAndNotAlreadyStarting() {
        val checkAutomationStart = CheckAutomationStartUseCase(
            OverlayPermissionGateway { true }
        )
        val (automation, _) = createAutomation()
        val useCase = StartAutomationUseCase(
            checkAutomationStart = checkAutomationStart,
            automationStartRecorder = FakeAutomationStartRecorder(),
            automation = automation
        )

        assertEquals(
            AutomationStartDecision.Started,
            useCase.decideStart(canRun = true, isStartInProgress = false)
        )
        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decideStart(canRun = false, isStartInProgress = false)
        )
        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decideStart(canRun = true, isStartInProgress = true)
        )
    }

    @Test
    fun start_callsRecordAndRunsAutomationWithGivenRequest() = runBlocking {
        val checkAutomationStart = CheckAutomationStartUseCase(
            OverlayPermissionGateway { true }
        )
        val recorder = FakeAutomationStartRecorder()
        val (automation, promptGateway) = createAutomation()
        val useCase = StartAutomationUseCase(
            checkAutomationStart = checkAutomationStart,
            automationStartRecorder = recorder,
            automation = automation
        )

        val request = AutomationRunRequest(
            promptTemplate = "test prompt template",
            repeatCountText = "1",
            targetApp = AutomationTargetApp.GEMINI
        )

        useCase.start(request)

        assertEquals(1, recorder.callCount)
        assertEquals(request, recorder.recordedRequest)
        assertTrue(promptGateway.sentPrompts.contains(RunAutomationUseCase.MARKER_PROMPT))
        assertEquals(AutomationRunState.Success, automation.runState.value)
    }

    private fun createAutomation(): Pair<RunAutomationUseCase, FakePromptAutomationGateway> {
        val promptGateway = FakePromptAutomationGateway()
        var defaultImeId = "com.example/.OriginalIme"
        val automation = RunAutomationUseCase(
            imeManager = ImeManager(
                settings = object : ImeSettings {
                    override fun getDefaultInputMethod(): String = defaultImeId
                    override fun setDefaultInputMethod(imeId: String): Boolean {
                        defaultImeId = imeId
                        return true
                    }
                },
                nullKeyboardImeId = "com.example/.NullKeyboard"
            ),
            lastRunSnapshotStore = LastRunSnapshotStore(FakeLastRunSnapshotStorage()),
            clipboardGateway = FakeClipboardGateway(),
            wildcardSetRepository = NoOpWildcardSetRepository,
            promptGatewayProvider = PromptAutomationGatewayProvider { promptGateway },
            targetAppLauncher = TargetAppLauncher { true },
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined),
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )
        return automation to promptGateway
    }

    private class FakeAutomationStartRecorder : AutomationStartRecorder {
        var callCount = 0
        var recordedRequest: AutomationRunRequest? = null

        override suspend fun record(request: AutomationRunRequest) {
            callCount++
            recordedRequest = request
        }
    }

    private class FakeLastRunSnapshotStorage : LastRunSnapshotRepository {
        var snapshot: LastRunSnapshot? = null
        override fun load(): LastRunSnapshot? = snapshot
        override fun save(snapshot: LastRunSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeClipboardGateway : ClipboardGateway {
        var text: String = ""
        override fun readText(): String = text
        override fun writeText(text: String) {
            this.text = text
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
            sentPrompts.add(prompt)
            onDone()
        }

        override fun cancelCurrentRun() = Unit
    }
}
