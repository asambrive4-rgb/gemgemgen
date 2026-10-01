// 역할: 도메인 비즈니스 불변식 검사 및 로컬/원격 자동화 실행 경로 조율 동작을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.AutomationHistoryRecorder
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.CoordinateAutomationExecutionUseCase
import com.example.gemgemgen.automation.usecase.ExecuteAutomationLoopUseCase
import com.example.gemgemgen.automation.usecase.ManageImeUseCase
import com.example.gemgemgen.automation.usecase.ImeSettings
import com.example.gemgemgen.automation.usecase.NewChatMode
import com.example.gemgemgen.automation.usecase.OverlayPermissionGateway
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway
import com.example.gemgemgen.automation.usecase.PromptAutomationGatewayProvider
import com.example.gemgemgen.automation.usecase.TargetAppLauncher
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import com.example.gemgemgen.remote.usecase.RemoteAutomationGateway
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateAutomationExecutionUseCaseTest {

    private fun readyEnvironment(): EnvironmentStatus = EnvironmentStatus(
        isGeminiInstalled = true,
        isChatGptInstalled = true,
        isAccessibilityServiceEnabled = true,
        hasWriteSecureSettingsPermission = true,
        isWildcardDirectoryAccessible = true
    )

    @Test
    fun decideStart_withDomainContext_normalMode_startsWhenRequirementsMet() {
        val (useCaseWithOverlay, _) = createUseCase(isOverlayGranted = true)
        val (useCaseWithoutOverlay, _) = createUseCase(isOverlayGranted = false)

        val successDecision = useCaseWithOverlay.decideStart(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment(),
            targetApp = AutomationTargetApp.GEMINI,
            promptTemplate = "test prompt"
        )
        assertEquals(AutomationStartDecision.Started, successDecision)

        val permissionDecision = useCaseWithoutOverlay.decideStart(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment(),
            targetApp = AutomationTargetApp.GEMINI,
            promptTemplate = "test prompt"
        )
        assertEquals(AutomationStartDecision.PermissionRequired, permissionDecision)

        val rejectedDecision = useCaseWithOverlay.decideStart(
            mode = AutomationMode.NORMAL,
            environmentStatus = readyEnvironment(),
            targetApp = AutomationTargetApp.GEMINI,
            promptTemplate = "",
            isRunning = false
        )
        assertEquals(AutomationStartDecision.Rejected, rejectedDecision)
    }

    @Test
    fun decideStart_withDomainContext_senderMode_evaluatesRemoteStatus() {
        val (useCase, _) = createUseCase(isOverlayGranted = false)
        val pairedStatus = RemoteAutomationStatus(
            mode = AutomationMode.SENDER,
            discoveredDeviceName = "S25 FE",
            isPaired = true
        )

        val startDecision = useCase.decideStart(
            mode = AutomationMode.SENDER,
            environmentStatus = EnvironmentStatus(),
            targetApp = AutomationTargetApp.GEMINI,
            promptTemplate = "remote prompt",
            remoteAutomationStatus = pairedStatus
        )
        assertEquals(AutomationStartDecision.RemoteStarted, startDecision)

        val rejectedDecision = useCase.decideStart(
            mode = AutomationMode.SENDER,
            environmentStatus = EnvironmentStatus(),
            targetApp = AutomationTargetApp.GEMINI,
            promptTemplate = "remote prompt",
            remoteAutomationStatus = RemoteAutomationStatus()
        )
        assertEquals(AutomationStartDecision.Rejected, rejectedDecision)
    }

    @Test
    fun executeRemote_delegatesToManageRemoteAutomation() = runBlocking {
        val (useCase, context) = createUseCase(isRemotePaired = true)

        val request = AutomationRunRequest(
            promptTemplate = "remote prompt",
            repeatCountText = "2",
            targetApp = AutomationTargetApp.GEMINI
        )

        val result = useCase.executeRemote(request)

        assertEquals(RemoteActionResult.Success, result)
        assertEquals("remote prompt", context.remoteGateway.sentRequest?.promptTemplate)
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
        isRemotePaired: Boolean = true
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
            automationHistoryRecorder = AutomationHistoryRecorder {},
            requestIdProvider = { "test-request-id" }
        )

        val useCase = CoordinateAutomationExecutionUseCase(
            checkAutomationStart = checkAutomationStart,
            automationHistoryRecorder = startRecorder,
            automation = localAutomation,
            manageRemoteAutomation = manageRemoteAutomation
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
                nullKeyboardCandidates = listOf("com.example/.NullKeyboard")
            ),
            wildcardSetRepository = WildcardSetRepository { emptyList() },
            promptGatewayProvider = PromptAutomationGatewayProvider { promptGateway },
            targetAppLauncher = TargetAppLauncher { true },
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined)
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

        override suspend fun send(request: RemoteAutomationRequest) {
            sentRequest = request
        }

        override fun forceStop(requestId: String?) {
            forceStopCalled = true
        }

        override suspend fun cleanMemory(): RemoteActionResult = RemoteActionResult.Success
    }
}
