// 역할: 시작 조건 검사 및 로컬/원격 자동화 실행 경로를 조율하는 CoordinateAutomationExecutionUseCase 동작을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.usecase.AutomationHistoryRecorder
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.CoordinateAutomationExecutionUseCase
import com.example.gemgemgen.automation.usecase.ExecuteAutomationLoopUseCase
import com.example.gemgemgen.automation.usecase.ManageImeUseCase
import com.example.gemgemgen.automation.usecase.ImeSettings
import com.example.gemgemgen.automation.usecase.LastRunSnapshot
import com.example.gemgemgen.automation.usecase.LastRunSnapshotRepository
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.NewChatMode
import com.example.gemgemgen.automation.usecase.OverlayPermissionGateway
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway
import com.example.gemgemgen.automation.usecase.PromptAutomationGatewayProvider
import com.example.gemgemgen.automation.usecase.PromptHistoryRepository
import com.example.gemgemgen.automation.usecase.PromptHistoryStore
import com.example.gemgemgen.automation.usecase.TargetAppLauncher
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import com.example.gemgemgen.remote.usecase.RemoteAutomationGateway
import com.example.gemgemgen.wildcard.usecase.NoOpWildcardSetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateAutomationExecutionUseCaseTest {

    @Test
    fun decideStart_inReceiverMode_isAlwaysRejected() {
        val (useCase, _) = createUseCase(isOverlayGranted = true)

        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decideStart(canRun = true, isStartInProgress = false, mode = AutomationMode.RECEIVER)
        )
        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decideStart(canRun = false, isStartInProgress = true, mode = AutomationMode.RECEIVER)
        )
    }

    @Test
    fun decideStart_inSenderMode_startsOnlyWhenCanRunAndNotInProgress() {
        val (useCase, _) = createUseCase(isOverlayGranted = false)

        assertEquals(
            AutomationStartDecision.RemoteStarted,
            useCase.decideStart(canRun = true, isStartInProgress = false, mode = AutomationMode.SENDER)
        )
        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decideStart(canRun = false, isStartInProgress = false, mode = AutomationMode.SENDER)
        )
        assertEquals(
            AutomationStartDecision.Rejected,
            useCase.decideStart(canRun = true, isStartInProgress = true, mode = AutomationMode.SENDER)
        )
    }

    @Test
    fun decideStart_inNormalMode_checksOverlayPermissionAndStartStatus() {
        val (useCaseWithOverlay, _) = createUseCase(isOverlayGranted = true)
        val (useCaseWithoutOverlay, _) = createUseCase(isOverlayGranted = false)

        assertEquals(
            AutomationStartDecision.Started,
            useCaseWithOverlay.decideStart(canRun = true, isStartInProgress = false, mode = AutomationMode.NORMAL)
        )
        assertEquals(
            AutomationStartDecision.PermissionRequired,
            useCaseWithoutOverlay.decideStart(canRun = true, isStartInProgress = false, mode = AutomationMode.NORMAL)
        )
        assertEquals(
            AutomationStartDecision.Rejected,
            useCaseWithOverlay.decideStart(canRun = false, isStartInProgress = false, mode = AutomationMode.NORMAL)
        )
        assertEquals(
            AutomationStartDecision.Rejected,
            useCaseWithOverlay.decideStart(canRun = true, isStartInProgress = true, mode = AutomationMode.NORMAL)
        )
    }

    @Test
    fun executeRemote_recordsHistoryAndDelegatesToManageRemoteAutomation() = runBlocking {
        val historyRepo = FakePromptHistoryRepository()
        val historyStore = PromptHistoryStore(historyRepo)
        val (useCase, context) = createUseCase(promptHistoryStore = historyStore, isRemotePaired = true)

        val request = AutomationRunRequest(
            promptTemplate = "remote prompt",
            repeatCountText = "2",
            targetApp = AutomationTargetApp.GEMINI
        )

        val result = useCase.executeRemote(request) {}

        assertEquals(RemoteActionResult.Success, result)
        val recordedHistory = historyRepo.load()
        assertEquals(1, recordedHistory.size)
        assertEquals("remote prompt", recordedHistory.first().prompt)
        assertEquals(AutomationTargetApp.GEMINI, recordedHistory.first().targetApp)
        assertEquals("remote prompt", context.remoteGateway.sentRequest?.promptTemplate)
    }

    @Test
    fun executeRemote_withoutHistoryStore_delegatesSuccessfully() = runBlocking {
        val (useCase, context) = createUseCase(promptHistoryStore = null, isRemotePaired = true)

        val request = AutomationRunRequest(
            promptTemplate = "prompt without history",
            repeatCountText = "1",
            targetApp = AutomationTargetApp.GEMINI
        )

        val result = useCase.executeRemote(request) {}

        assertEquals(RemoteActionResult.Success, result)
        assertEquals("prompt without history", context.remoteGateway.sentRequest?.promptTemplate)
    }

    @Test
    fun executeLocal_recordsSnapshotAndRunsAutomation() = runBlocking {
        val (useCase, context) = createUseCase()

        val request = AutomationRunRequest(
            promptTemplate = "local prompt",
            repeatCountText = "1",
            targetApp = AutomationTargetApp.GEMINI
        )

        useCase.executeLocal(request)

        assertEquals(1, context.startRecorder.callCount)
        assertEquals(request, context.startRecorder.recordedRequest)
        assertEquals(AutomationRunState.Success, context.localAutomation.runState.value)
    }

    @Test
    fun cancel_inSenderMode_forceStopsRemoteAutomation() {
        val (useCase, context) = createUseCase()
        var localCanceled = false
        var stateChangeReported: AutomationRunState? = null

        useCase.cancel(
            mode = AutomationMode.SENDER,
            isRemoteRunActive = false,
            isPreparationActive = false,
            onStateChange = { stateChangeReported = it },
            onCancelLocal = { localCanceled = true }
        )

        assertTrue(context.remoteGateway.forceStopCalled)
        assertFalse(localCanceled)
        assertEquals(AutomationRunState.Stopped, stateChangeReported)
    }

    @Test
    fun cancel_whenRemoteRunActive_forceStopsRemoteAutomationRegardlessOfMode() {
        val (useCase, context) = createUseCase()
        var localCanceled = false
        var stateChangeReported: AutomationRunState? = null

        useCase.cancel(
            mode = AutomationMode.NORMAL,
            isRemoteRunActive = true,
            isPreparationActive = false,
            onStateChange = { stateChangeReported = it },
            onCancelLocal = { localCanceled = true }
        )

        assertTrue(context.remoteGateway.forceStopCalled)
        assertFalse(localCanceled)
        assertEquals(AutomationRunState.Stopped, stateChangeReported)
    }

    @Test
    fun cancel_whenPreparationActiveInNormalMode_updatesStateToStoppedWithoutLocalCancel() {
        val (useCase, context) = createUseCase()
        var localCanceled = false
        var stateChangeReported: AutomationRunState? = null

        useCase.cancel(
            mode = AutomationMode.NORMAL,
            isRemoteRunActive = false,
            isPreparationActive = true,
            onStateChange = { stateChangeReported = it },
            onCancelLocal = { localCanceled = true }
        )

        assertFalse(context.remoteGateway.forceStopCalled)
        assertFalse(localCanceled)
        assertEquals(AutomationRunState.Stopped, stateChangeReported)
    }

    @Test
    fun cancel_whenIdleInNormalMode_delegatesToOnCancelLocal() {
        val (useCase, context) = createUseCase()
        var localCanceled = false
        var stateChangeReported: AutomationRunState? = null

        useCase.cancel(
            mode = AutomationMode.NORMAL,
            isRemoteRunActive = false,
            isPreparationActive = false,
            onStateChange = { stateChangeReported = it },
            onCancelLocal = { localCanceled = true }
        )

        assertFalse(context.remoteGateway.forceStopCalled)
        assertTrue(localCanceled)
        assertEquals(null, stateChangeReported)
    }

    private fun createUseCase(
        isOverlayGranted: Boolean = true,
        isRemotePaired: Boolean = true,
        promptHistoryStore: PromptHistoryStore? = null
    ): Pair<CoordinateAutomationExecutionUseCase, TestContext> {
        val checkAutomationStart = CheckAutomationStartUseCase(OverlayPermissionGateway { isOverlayGranted })
        val startRecorder = FakeAutomationHistoryRecorder()
        val localAutomation = createLocalAutomation()

        val remoteGateway = FakeRemoteAutomationGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = isRemotePaired
            )
        )
        val manageRemoteAutomation = ManageRemoteAutomationUseCase(
            gateway = remoteGateway,
            automationHistoryRecorder = startRecorder,
            requestIdProvider = { "test-request-id" }
        )

        val useCase = CoordinateAutomationExecutionUseCase(
            checkAutomationStart = checkAutomationStart,
            automationHistoryRecorder = startRecorder,
            automation = localAutomation,
            manageRemoteAutomation = manageRemoteAutomation,
            promptHistoryStore = promptHistoryStore
        )

        return useCase to TestContext(
            startRecorder = startRecorder,
            localAutomation = localAutomation,
            remoteGateway = remoteGateway
        )
    }

    private fun createLocalAutomation(): ExecuteAutomationLoopUseCase {
        val promptGateway = FakePromptAutomationGateway()
        var defaultImeId = "com.example/.OriginalIme"
        return ExecuteAutomationLoopUseCase(
            manageImeUseCase = ManageImeUseCase(
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
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )
    }

    private data class TestContext(
        val startRecorder: FakeAutomationHistoryRecorder,
        val localAutomation: ExecuteAutomationLoopUseCase,
        val remoteGateway: FakeRemoteAutomationGateway
    )

    private class FakeAutomationHistoryRecorder : AutomationHistoryRecorder {
        var callCount = 0
        var recordedRequest: AutomationRunRequest? = null

        override suspend fun record(request: AutomationRunRequest) {
            callCount++
            recordedRequest = request
        }
    }

    private class FakePromptHistoryRepository : PromptHistoryRepository {
        private var items = listOf<PromptHistoryItem>()
        override fun load(): List<PromptHistoryItem> = items
        override fun save(items: List<PromptHistoryItem>) {
            this.items = items
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
        override fun sendPrompt(
            prompt: String,
            newChatMode: NewChatMode,
            onStateChange: (AutomationRunState) -> Unit,
            onDone: () -> Unit
        ) {
            onDone()
        }

        override fun cancelCurrentRun() = Unit
    }

    private class FakeRemoteAutomationGateway(
        initialStatus: RemoteAutomationStatus = RemoteAutomationStatus()
    ) : RemoteAutomationGateway {
        override val status = MutableStateFlow(initialStatus)
        var sentRequest: RemoteAutomationRequest? = null
        var forceStopCalled = false

        override fun selectMode(mode: AutomationMode) {
            status.value = status.value.copy(mode = mode)
        }

        override suspend fun pair(pairingCode: String): RemoteActionResult = RemoteActionResult.Success
        override suspend fun disconnect(): RemoteActionResult = RemoteActionResult.Success

        override suspend fun send(
            request: RemoteAutomationRequest,
            onStateChange: (AutomationRunState) -> Unit
        ) {
            sentRequest = request
        }

        override fun forceStop(requestId: String?) {
            forceStopCalled = true
        }

        override suspend fun cleanMemory(): RemoteActionResult = RemoteActionResult.Success
        override suspend fun switchGeminiAccount(id: String, alias: String, identifier: String): RemoteActionResult = RemoteActionResult.Success
    }
}
