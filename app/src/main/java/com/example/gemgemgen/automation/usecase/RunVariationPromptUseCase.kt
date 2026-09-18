// 역할: 변주 프롬프트를 현재 기기의 Gemini 채팅방에 붙여넣고 실행을 조율하는 유스케이스입니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp

interface VariationPromptAutomationGateway {
    fun pastePromptOnly(
        prompt: String,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    )

    fun cancelCurrentRun()
}

fun interface VariationPromptAutomationGatewayProvider {
    fun current(): VariationPromptAutomationGateway?
}

sealed interface VariationStartDecision {
    data object Started : VariationStartDecision
    data class Rejected(val message: String) : VariationStartDecision
}

class RunVariationPromptUseCase(
    private val gatewayProvider: VariationPromptAutomationGatewayProvider,
    private val targetAppLauncher: TargetAppLauncher
) {
    private var activeGateway: VariationPromptAutomationGateway? = null

    fun start(
        prompt: String,
        onStateChange: (AutomationRunState) -> Unit
    ): VariationStartDecision {
        if (prompt.isBlank()) {
            return reject("변주 생성용 프롬프트를 먼저 입력해주세요.", onStateChange)
        }

        if (activeGateway != null) {
            return reject("변주 자동화가 이미 실행 중입니다.", onStateChange)
        }

        val gateway = gatewayProvider.current()
            ?: return reject("접근성 서비스를 먼저 켜주세요.", onStateChange)

        if (!targetAppLauncher.launch(AutomationTargetApp.GEMINI)) {
            return reject("Gemini 앱을 찾지 못했습니다.", onStateChange)
        }

        activeGateway = gateway
        onStateChange(AutomationRunState.Running("Gemini 앱 실행 중"))

        try {
            gateway.pastePromptOnly(
                prompt = prompt,
                onStateChange = { state ->
                    if (state is AutomationRunState.Failure ||
                        state is AutomationRunState.Stopped
                    ) {
                        activeGateway = null
                    }
                    onStateChange(state)
                },
                onDone = {
                    activeGateway = null
                    onStateChange(AutomationRunState.Success)
                }
            )
        } catch (error: Throwable) {
            activeGateway = null
            return reject(
                error.message ?: "변주 프롬프트 붙여넣기 중 오류가 발생했습니다.",
                onStateChange
            )
        }

        return VariationStartDecision.Started
    }

    fun cancel() {
        activeGateway?.cancelCurrentRun()
        activeGateway = null
    }

    private fun reject(
        message: String,
        onStateChange: (AutomationRunState) -> Unit
    ): VariationStartDecision {
        onStateChange(AutomationRunState.Failure(message))
        return VariationStartDecision.Rejected(message)
    }
}
