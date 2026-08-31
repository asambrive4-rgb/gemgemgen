package com.example.gemgemgen.remote.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface RemoteAutomationGateway {
    val status: StateFlow<RemoteAutomationStatus>

    fun selectMode(mode: AutomationMode)
    suspend fun pair(pairingCode: String): RemoteActionResult
    suspend fun send(
        request: RemoteAutomationRequest,
        onStateChange: (AutomationRunState) -> Unit
    )
    fun forceStop(requestId: String?)
}

class NoOpRemoteAutomationGateway : RemoteAutomationGateway {
    private val currentStatus = MutableStateFlow(RemoteAutomationStatus())
    override val status: StateFlow<RemoteAutomationStatus> = currentStatus

    override fun selectMode(mode: AutomationMode) {
        currentStatus.value = currentStatus.value.copy(mode = mode)
    }

    override suspend fun pair(pairingCode: String): RemoteActionResult {
        return RemoteActionResult.Failure("연결할 수신 기기를 찾지 못했습니다.")
    }

    override suspend fun send(
        request: RemoteAutomationRequest,
        onStateChange: (AutomationRunState) -> Unit
    ) {
        onStateChange(AutomationRunState.Failure("연결할 수신 기기를 찾지 못했습니다."))
    }

    override fun forceStop(requestId: String?) {
        currentStatus.value = currentStatus.value.copy(
            automationState = AutomationRunState.Stopped
        )
    }
}
