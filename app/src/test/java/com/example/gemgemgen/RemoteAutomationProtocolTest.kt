// 역할: 원격 기기 간 통신 패킷 직렬화 및 역직렬화 프로토콜을 검증합니다.
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
    fun runRequest_roundTripsFlowImageCount() {
        val message = RemoteProtocolMessage.RunRequest(
            senderId = "tablet",
            token = "token",
            request = RemoteAutomationRequest(
                requestId = "request-flow",
                promptTemplate = "flow prompt",
                repeatCountText = "3",
                targetApp = AutomationTargetApp.FLOW,
                flowImageCount = 2
            )
        )

        val decoded = RemoteAutomationProtocol.decode(RemoteAutomationProtocol.encode(message)) as? RemoteProtocolMessage.RunRequest
        assertEquals(2, decoded?.request?.flowImageCount)
    }

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
    fun runRequest_roundTripsWithWildcards() {
        val message = RemoteProtocolMessage.RunRequest(
            senderId = "tablet",
            token = "token",
            request = RemoteAutomationRequest(
                requestId = "request-2",
                promptTemplate = "a photo of __color__ __flower__",
                repeatCountText = "5",
                targetApp = AutomationTargetApp.CHATGPT,
                wildcards = listOf(
                    com.example.gemgemgen.wildcard.domain.WildcardSet(
                        token = "__color__",
                        fileName = "color.txt",
                        items = listOf("red", "blue", "yellow")
                    ),
                    com.example.gemgemgen.wildcard.domain.WildcardSet(
                        token = "__flower__",
                        fileName = "flower.txt",
                        items = listOf("rose", "tulip")
                    )
                )
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

    @Test
    fun disconnectRequest_roundTrips() {
        val message = RemoteProtocolMessage.DisconnectRequest(
            senderId = "tablet-id",
            token = "sample-token-123"
        )
        assertEquals(message, RemoteAutomationProtocol.decode(RemoteAutomationProtocol.encode(message)))
    }

    @Test
    fun disconnectResult_roundTrips() {
        val success = RemoteProtocolMessage.DisconnectResult(success = true)
        val failure = RemoteProtocolMessage.DisconnectResult(
            success = false,
            message = "등록되지 않은 송신 기기입니다."
        )
        assertEquals(success, RemoteAutomationProtocol.decode(RemoteAutomationProtocol.encode(success)))
        assertEquals(failure, RemoteAutomationProtocol.decode(RemoteAutomationProtocol.encode(failure)))
    }

    @Test
    fun cleanMemoryRequest_roundTrips() {
        val message = RemoteProtocolMessage.CleanMemoryRequest(
            senderId = "tablet-id",
            token = "sample-token-123"
        )
        assertEquals(message, RemoteAutomationProtocol.decode(RemoteAutomationProtocol.encode(message)))
    }

    @Test
    fun cleanMemoryResult_roundTrips() {
        val success = RemoteProtocolMessage.CleanMemoryResult(
            success = true,
            message = "수신 기기 메모리를 정리했습니다."
        )
        val failure = RemoteProtocolMessage.CleanMemoryResult(
            success = false,
            message = "접근성 서비스가 꺼져 있습니다."
        )
        assertEquals(success, RemoteAutomationProtocol.decode(RemoteAutomationProtocol.encode(success)))
    }
}
