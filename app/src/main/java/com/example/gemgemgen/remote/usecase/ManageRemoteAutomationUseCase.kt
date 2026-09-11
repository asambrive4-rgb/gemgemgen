// 역할: 원격 연결 수신 서비스의 시작과 종료 생명주기를 제어합니다.
package com.example.gemgemgen.remote.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptGenerator
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.AutomationStartRecorder
import com.example.gemgemgen.automation.usecase.NoOpAutomationStartRecorder
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.wildcard.usecase.NoOpWildcardSetRepository
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.StateFlow

class ManageRemoteAutomationUseCase(
    private val gateway: RemoteAutomationGateway,
    private val automationStartRecorder: AutomationStartRecorder = NoOpAutomationStartRecorder,
    private val wildcardSetRepository: WildcardSetRepository = NoOpWildcardSetRepository,
    private val promptGenerator: PromptGenerator = PromptGenerator(),
    private val requestIdProvider: () -> String = { UUID.randomUUID().toString() }
) {
    private val activeRequestId = AtomicReference<String?>(null)

    val status: StateFlow<RemoteAutomationStatus>
        get() = gateway.status

    fun selectMode(mode: AutomationMode) {
        gateway.selectMode(mode)
    }

    suspend fun pair(pairingCode: String): RemoteActionResult {
        val normalizedCode = pairingCode.filter(Char::isDigit).take(PAIRING_CODE_LENGTH)
        if (normalizedCode.length != PAIRING_CODE_LENGTH) {
            return RemoteActionResult.Failure("S25 FE에 표시된 4자리 번호를 입력해주세요.")
        }
        return gateway.pair(normalizedCode)
    }

    suspend fun disconnect(): RemoteActionResult {
        val currentStatus = status.value
        if (currentStatus.automationState is AutomationRunState.Running) {
            return RemoteActionResult.Failure("원격 자동화를 중지한 뒤 연결을 끊어주세요.")
        }
        return gateway.disconnect()
    }

    suspend fun cleanMemory(): RemoteActionResult {
        if (!status.value.canSend) {
            return RemoteActionResult.Failure("연결된 수신 기기를 찾지 못했습니다.")
        }
        return gateway.cleanMemory()
    }

    suspend fun start(
        request: AutomationRunRequest,
        onStateChange: (AutomationRunState) -> Unit
    ): RemoteActionResult {
        if (!status.value.canSend) {
            return RemoteActionResult.Failure("연결된 수신 기기를 찾지 못했습니다.")
        }
        if (request.promptTemplate.isBlank()) {
            return RemoteActionResult.Failure("원본 프롬프트를 입력해주세요.")
        }
        try {
            automationStartRecorder.record(request)
        } catch (error: Exception) {
            return RemoteActionResult.Failure(
                "송신 기기에서 시작 정보를 저장하지 못했습니다. ${
                    error.message ?: "다시 시도해주세요."
                }"
            )
        }

        val requestId = requestIdProvider()
        val previousRequestId = activeRequestId.getAndSet(requestId)
        if (previousRequestId != null) {
            gateway.forceStop(previousRequestId)
        }

        val wildcardTokens = promptGenerator.extractTokens(request.promptTemplate).toSet()
        val wildcards = if (wildcardTokens.isEmpty()) {
            emptyList()
        } else {
            runCatching { wildcardSetRepository.load(wildcardTokens) }.getOrDefault(emptyList())
        }

        try {
            gateway.send(
                request = RemoteAutomationRequest(
                    requestId = requestId,
                    promptTemplate = request.promptTemplate,
                    repeatCountText = request.repeatCountText,
                    targetApp = request.targetApp,
                    flowImageCount = request.flowImageCount,
                    wildcards = wildcards
                ),
                onStateChange = { state ->
                    if (activeRequestId.get() == requestId) {
                        onStateChange(state)
                    }
                }
            )
        } finally {
            activeRequestId.compareAndSet(requestId, null)
        }
        return RemoteActionResult.Success
    }

    fun forceStop(onStateChange: (AutomationRunState) -> Unit) {
        val requestId = activeRequestId.getAndSet(null)
        gateway.forceStop(requestId)
        onStateChange(AutomationRunState.Stopped)
    }

    companion object {
        const val PAIRING_CODE_LENGTH = 4
    }
}
