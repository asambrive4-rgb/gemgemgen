// 역할: 변주 프롬프트 실행 유스케이스가 Gemini를 열고 붙여넣기를 조율하는지 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.RunVariationPromptUseCase
import com.example.gemgemgen.automation.usecase.TargetAppLauncher
import com.example.gemgemgen.automation.usecase.VariationPromptAutomationGateway
import com.example.gemgemgen.automation.usecase.VariationStartDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunVariationPromptUseCaseTest {

    @Test
    fun start_launchesGeminiAndPastesWithoutSending() {
        val launcher = RecordingTargetAppLauncher(launchResult = true)
        val gateway = RecordingVariationGateway()
        val useCase = RunVariationPromptUseCase(
            gatewayProvider = { gateway },
            targetAppLauncher = launcher
        )
        val states = mutableListOf<AutomationRunState>()

        val decision = useCase.start(
            prompt = "변주 프롬프트",
            onStateChange = states::add
        )

        assertEquals(VariationStartDecision.Started, decision)
        assertEquals(AutomationTargetApp.GEMINI, launcher.launchedTarget)
        assertEquals("변주 프롬프트", gateway.pastedPrompt)
        assertEquals(
            AutomationRunState.Running("Gemini 앱 실행 중"),
            states.first()
        )

        gateway.complete()

        assertEquals(AutomationRunState.Success, states.last())
    }

    @Test
    fun start_withoutAccessibilityGateway_isRejected() {
        val states = mutableListOf<AutomationRunState>()
        val useCase = RunVariationPromptUseCase(
            gatewayProvider = { null },
            targetAppLauncher = RecordingTargetAppLauncher(launchResult = true)
        )

        val decision = useCase.start(
            prompt = "변주 프롬프트",
            onStateChange = states::add
        )

        assertEquals(
            VariationStartDecision.Rejected("접근성 서비스를 먼저 켜주세요."),
            decision
        )
        assertTrue(states.single() is AutomationRunState.Failure)
    }

    private class RecordingTargetAppLauncher(
        private val launchResult: Boolean
    ) : TargetAppLauncher {
        var launchedTarget: AutomationTargetApp? = null

        override fun launch(targetApp: AutomationTargetApp): Boolean {
            launchedTarget = targetApp
            return launchResult
        }
    }

    private class RecordingVariationGateway : VariationPromptAutomationGateway {
        var pastedPrompt: String? = null
        private var onDone: (() -> Unit)? = null

        override fun pastePromptOnly(
            prompt: String,
            onStateChange: (AutomationRunState) -> Unit,
            onDone: () -> Unit
        ) {
            pastedPrompt = prompt
            this.onDone = onDone
        }

        override fun cancelCurrentRun() = Unit

        fun complete() {
            onDone?.invoke()
        }
    }
}
