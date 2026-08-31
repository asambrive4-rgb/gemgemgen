package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.remote.android.RemoteAutomationProtocol
import com.example.gemgemgen.remote.android.RemoteProtocolMessage
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteAutomationProtocolTest {
    @Test
    fun runRequest_roundTripsMultilinePrompt() {
        val message = RemoteProtocolMessage.RunRequest(
            senderId = "tablet",
            token = "token",
            request = RemoteAutomationRequest(
                requestId = "request-1",
                promptTemplate = "첫 줄\n두 번째 줄 __hair__",
                repeatCountText = "7",
                targetApp = AutomationTargetApp.GEMINI
            )
        )

        assertEquals(message, RemoteAutomationProtocol.decode(RemoteAutomationProtocol.encode(message)))
    }

    @Test
    fun stateUpdate_roundTripsDetailedFailureAndProgress() {
        val running = RemoteProtocolMessage.StateUpdate(
            requestId = "request-1",
            state = AutomationRunState.Running("프롬프트 전송 중", 3, 10)
        )
        val failure = RemoteProtocolMessage.StateUpdate(
            requestId = "request-1",
            state = AutomationRunState.Failure("휴대폰이 잠겨 있습니다.")
        )

        assertEquals(running, RemoteAutomationProtocol.decode(RemoteAutomationProtocol.encode(running)))
        assertEquals(failure, RemoteAutomationProtocol.decode(RemoteAutomationProtocol.encode(failure)))
    }
}
