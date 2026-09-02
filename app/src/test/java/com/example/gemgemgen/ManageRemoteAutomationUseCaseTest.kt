package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.AutomationStartRecorder
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import com.example.gemgemgen.remote.usecase.RemoteAutomationGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
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
        val recorder = RecordingAutomationStartRecorder()
        val useCase = ManageRemoteAutomationUseCase(
            gateway = gateway,
            requestIdProvider = { "request-1" },
            automationStartRecorder = recorder
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

        override fun selectMode(mode: AutomationMode) {
            status.value = status.value.copy(mode = mode)
        }

        override suspend fun pair(pairingCode: String): RemoteActionResult {
            pairedCode = pairingCode
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
    }

    private class RecordingAutomationStartRecorder : AutomationStartRecorder {
        val recordedRequests = mutableListOf<AutomationRunRequest>()

        override suspend fun record(request: AutomationRunRequest) {
            recordedRequests += request
        }
    }
}
