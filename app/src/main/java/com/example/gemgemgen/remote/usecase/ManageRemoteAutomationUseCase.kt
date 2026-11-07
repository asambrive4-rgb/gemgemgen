// 역할: 원격 기기 자동 연결, 연결 해제, 연결 새로고침(오류 상태 초기화), 자원 회수, 자동화 요청 송신, 메모리 정리, Gemini 계정 전환 및 강제 중지를 조율하며 RemoteAutomationSenderGateway를 구현합니다.
package com.example.gemgemgen.remote.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.PromptGenerator
import com.example.gemgemgen.automation.usecase.AutomationHistoryRecorder
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.RemoteAutomationSenderGateway
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.automation.domain.AutomationMode
import com.example.gemgemgen.automation.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class ManageRemoteAutomationUseCase(
    private val gateway: RemoteAutomationGateway,
    private val automationHistoryRecorder: AutomationHistoryRecorder = AutomationHistoryRecorder {},
    private val wildcardSetRepository: WildcardSetRepository = WildcardSetRepository { emptyList() },
    private val promptGenerator: PromptGenerator = PromptGenerator(),
    private val dispatchers: AppDispatchers = AppDispatchers(),
    private val requestIdProvider: () -> String = { UUID.randomUUID().toString() }
) : RemoteAutomationSenderGateway, AutoCloseable {
    private val activeRequestId = AtomicReference<String?>(null)

    val status: StateFlow<RemoteAutomationStatus>
        get() = gateway.status

    fun selectMode(mode: AutomationMode) {
        gateway.selectMode(mode)
    }

    suspend fun disconnect(): RemoteActionResult {
        val currentStatus = status.value
        if (currentStatus.automationState is AutomationRunState.Running) {
            return RemoteActionResult.Failure("원격 자동화를 중지한 뒤 연결을 끊어주세요.")
        }
        return gateway.disconnect()
    }

    suspend fun retryConnection(): RemoteActionResult {
        val requestId = activeRequestId.getAndSet(null)
        if (requestId != null) {
            gateway.forceStop(requestId)
        }
        return gateway.retryConnection()
    }

    override suspend fun cleanMemory(): RemoteActionResult {
        if (!status.value.canSend) {
            return RemoteActionResult.Failure("연결된 수신 기기를 찾지 못했습니다.")
        }
        return gateway.cleanMemory()
    }

    override suspend fun switchGeminiAccount(email: String, alias: String): RemoteActionResult {
        if (!status.value.canSend) {
            return RemoteActionResult.Failure("연결된 수신 기기를 찾지 못했습니다.")
        }
        return gateway.switchGeminiAccount(email = email, alias = alias)
    }

    override suspend fun start(request: AutomationRunRequest): RemoteActionResult {
        if (!status.value.canSend) {
            return RemoteActionResult.Failure("연결된 수신 기기를 찾지 못했습니다.")
        }
        if (request.promptTemplate.isBlank()) {
            return RemoteActionResult.Failure("원본 프롬프트를 입력해주세요.")
        }
        try {
            automationHistoryRecorder.record(request)
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
            withContext(dispatchers.io) {
                runCatching { wildcardSetRepository.load(wildcardTokens) }.getOrDefault(emptyList())
            }
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
                )
            )
        } finally {
            activeRequestId.compareAndSet(requestId, null)
        }
        return RemoteActionResult.Success
    }

    override fun forceStop() {
        val requestId = activeRequestId.getAndSet(null)
        gateway.forceStop(requestId)
    }

    fun updateRepeatCount(repeatCount: Int) {
        val requestId = activeRequestId.get() ?: return
        gateway.updateRepeatCount(requestId, repeatCount.coerceIn(1, 999))
    }

    override fun close() {
        gateway.close()
    }
}
