// 역할: 원격 자동화 서비스 제어 및 상태 관리 유스케이스를 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.AutomationHistoryRecorder
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import com.example.gemgemgen.remote.usecase.RemoteAutomationGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ManageRemoteAutomationUseCaseTest {
    @Test
    fun pair_requiresFourDigitCode() = runBlocking {
        val gateway = FakeRemoteAutomationGateway()
        val useCase = ManageRemoteAutomationUseCase(gateway)

        assertEquals(
            RemoteActionResult.Failure("S25 FE에 표시된 4자리 번호를 입력해주세요."),
            useCase.pair("12")
        )
        assertEquals(RemoteActionResult.Success, useCase.pair("12-34"))
        assertEquals("1234", gateway.pairedCode)
    }

    @Test
    fun start_buildsSharedRemoteRequestAndDelegatesTransport() = runBlocking {
        val gateway = FakeRemoteAutomationGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = true
            )
        )
        val recorder = RecordingAutomationHistoryRecorder()
        val useCase = ManageRemoteAutomationUseCase(
            gateway = gateway,
            requestIdProvider = { "request-1" },
            automationHistoryRecorder = recorder
        )

        assertEquals(
            RemoteActionResult.Success,
            useCase.start(
                AutomationRunRequest("prompt", "3", AutomationTargetApp.GEMINI),
                onStateChange = {}
            )
        )
        assertEquals(
            RemoteAutomationRequest("request-1", "prompt", "3", AutomationTargetApp.GEMINI),
            gateway.sentRequest
        )
        assertEquals(
            listOf(AutomationRunRequest("prompt", "3", AutomationTargetApp.GEMINI)),
            recorder.recordedRequests
        )
    }

    @Test
    fun start_bundlesMatchingWildcardsFromRepository() = runBlocking {
        val gateway = FakeRemoteAutomationGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = true
            )
        )
        val expectedWildcard = com.example.gemgemgen.wildcard.domain.WildcardSet(
            token = "__flower__",
            fileName = "flower.txt",
            items = listOf("rose", "tulip")
        )
        val fakeRepo = object : com.example.gemgemgen.wildcard.usecase.WildcardSetRepository {
            override fun load(): List<com.example.gemgemgen.wildcard.domain.WildcardSet> =
                listOf(expectedWildcard)
        }
        val useCase = ManageRemoteAutomationUseCase(
            gateway = gateway,
            wildcardSetRepository = fakeRepo,
            requestIdProvider = { "request-wildcard-1" }
        )

        assertEquals(
            RemoteActionResult.Success,
            useCase.start(
                AutomationRunRequest("draw a __flower__", "2", AutomationTargetApp.CHATGPT),
                onStateChange = {}
            )
        )
        assertEquals(
            listOf(expectedWildcard),
            gateway.sentRequest?.wildcards
        )
    }

    @Test
    fun forceStop_stopsLocallyAndIgnoresLateStateFromStoppedRequest() = runBlocking {
        val gateway = FakeRemoteAutomationGateway(
            initialStatus = RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = true
            ),
            holdSend = true
        )
        val useCase = ManageRemoteAutomationUseCase(gateway) { "request-1" }
        val states = mutableListOf<AutomationRunState>()
        val startJob = launch {
            useCase.start(
                AutomationRunRequest("prompt", "3", AutomationTargetApp.GEMINI),
                states::add
            )
        }
        gateway.sendStarted.await()

        useCase.forceStop(states::add)
        gateway.sentStateCallback?.invoke(AutomationRunState.Success)

        assertEquals(listOf(AutomationRunState.Stopped), states)
        assertEquals("request-1", gateway.forceStoppedRequestId)
        startJob.cancelAndJoin()
    }

    @Test
    fun disconnect_whenAutomationIsRunning_returnsFailureWithoutCallingGateway() = runBlocking {
        val gateway = FakeRemoteAutomationGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = true,
                automationState = AutomationRunState.Running("작업 진행 중")
            )
        )
        val useCase = ManageRemoteAutomationUseCase(gateway)

        val result = useCase.disconnect()

        assertEquals(
            RemoteActionResult.Failure("원격 자동화를 중지한 뒤 연결을 끊어주세요."),
            result
        )
        assertEquals(false, gateway.disconnectCalled)
    }

    @Test
    fun disconnect_whenIdle_delegatesToGateway() = runBlocking {
        val gateway = FakeRemoteAutomationGateway(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = "S25 FE",
                isPaired = true,
                automationState = AutomationRunState.Idle
            )
        )
        val useCase = ManageRemoteAutomationUseCase(gateway)

        val result = useCase.disconnect()

        assertEquals(RemoteActionResult.Success, result)
        assertEquals(true, gateway.disconnectCalled)
    }

    private class FakeRemoteAutomationGateway(
        initialStatus: RemoteAutomationStatus = RemoteAutomationStatus(),
        private val holdSend: Boolean = false
    ) : RemoteAutomationGateway {
        override val status = MutableStateFlow(initialStatus)
        val sendStarted = CompletableDeferred<Unit>()
        var pairedCode = ""
        var sentRequest: RemoteAutomationRequest? = null
        var sentStateCallback: ((AutomationRunState) -> Unit)? = null
        var forceStoppedRequestId: String? = null
        var disconnectCalled = false

        override fun selectMode(mode: AutomationMode) {
            status.value = status.value.copy(mode = mode)
        }

        override suspend fun pair(pairingCode: String): RemoteActionResult {
            pairedCode = pairingCode
            return RemoteActionResult.Success
        }

        override suspend fun disconnect(): RemoteActionResult {
            disconnectCalled = true
            return RemoteActionResult.Success
        }

        override suspend fun send(
            request: RemoteAutomationRequest,
            onStateChange: (AutomationRunState) -> Unit
        ) {
            sentRequest = request
            sentStateCallback = onStateChange
            sendStarted.complete(Unit)
            if (holdSend) awaitCancellation()
        }

        override fun forceStop(requestId: String?) {
            forceStoppedRequestId = requestId
        }

        override suspend fun cleanMemory(): RemoteActionResult = RemoteActionResult.Success
        override suspend fun switchGeminiAccount(id: String, alias: String, identifier: String): RemoteActionResult = RemoteActionResult.Success
    }

    private class RecordingAutomationHistoryRecorder : AutomationHistoryRecorder {
        val recordedRequests = mutableListOf<AutomationRunRequest>()

        override suspend fun record(request: AutomationRunRequest) {
            recordedRequests += request
        }
    }
}
