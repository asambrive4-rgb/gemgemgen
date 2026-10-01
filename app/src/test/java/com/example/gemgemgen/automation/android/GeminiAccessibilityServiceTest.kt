// 역할: 접근성 패키지 범위 게이트웨이 래퍼가 반복 루프 정상 완료·실패·취소 시점에 패키지 필터를 정확히 해제하는지 검증합니다.
package com.example.gemgemgen.automation.android

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.AutomationRunRequest
import com.example.gemgemgen.automation.usecase.ExecuteAutomationLoopUseCase
import com.example.gemgemgen.automation.usecase.FlowConfigurableGateway
import com.example.gemgemgen.automation.usecase.ImeSettings
import com.example.gemgemgen.automation.usecase.ManageImeUseCase
import com.example.gemgemgen.automation.usecase.NewChatMode
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway
import com.example.gemgemgen.automation.usecase.PromptAutomationGatewayProvider
import com.example.gemgemgen.automation.usecase.TargetAppLauncher
import com.example.gemgemgen.automation.usecase.VariationPromptAutomationGateway
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.wildcard.domain.WildcardSet
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAccessibilityServiceTest {

    @Test
    fun packageScopedPromptAutomation_maintainsPackageFilterDuringLoopAndClearsOnNormalCompletion() = runBlocking {
        var currentRestrictedTarget: AutomationTargetApp? = null
        var clearCount = 0
        val delegate = RecordingPromptGateway(autoComplete = false)
        lateinit var loopUseCase: ExecuteAutomationLoopUseCase

        val scopedGateway = PackageScopedPromptAutomation(
            delegate = delegate,
            targetApp = AutomationTargetApp.GEMINI,
            restrictPackages = { target -> currentRestrictedTarget = target },
            clearPackageRestriction = {
                currentRestrictedTarget = null
                clearCount += 1
            },
            isSessionRunning = { loopUseCase.isSessionRunning() }
        )

        loopUseCase = ExecuteAutomationLoopUseCase(
            manageImeUseCase = ManageImeUseCase(
                settings = object : ImeSettings {
                    private var currentIme = "orig.ime/.Service"
                    override fun getDefaultInputMethod(): String = currentIme
                    override fun setDefaultInputMethod(imeId: String): Boolean {
                        currentIme = imeId
                        return true
                    }
                },
                nullKeyboardCandidates = listOf("null.ime/.Service")
            ),
            wildcardSetRepository = object : WildcardSetRepository {
                override fun load(): List<WildcardSet> = emptyList()
                override fun load(tokens: Set<String>): List<WildcardSet> = emptyList()
            },
            promptGatewayProvider = PromptAutomationGatewayProvider { scopedGateway },
            targetAppLauncher = TargetAppLauncher { true },
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined)
        )

        loopUseCase.run(
            AutomationRunRequest(
                promptTemplate = "hello",
                repeatCountText = "2",
                targetApp = AutomationTargetApp.GEMINI
            )
        )

        // Marker sent -> package restriction active, not cleared yet
        assertEquals(AutomationTargetApp.GEMINI, currentRestrictedTarget)
        assertEquals(0, clearCount)

        // Marker completes -> prompt 1 starts immediately, package restriction stays active
        delegate.completeNext()
        assertEquals(AutomationTargetApp.GEMINI, currentRestrictedTarget)
        assertEquals(0, clearCount)

        // Prompt 1 completes -> prompt 2 starts immediately, package restriction stays active
        delegate.completeNext()
        assertEquals(AutomationTargetApp.GEMINI, currentRestrictedTarget)
        assertEquals(0, clearCount)

        // Prompt 2 (last) completes -> loop finishes and package restriction is cleared
        delegate.completeNext()
        assertNull(currentRestrictedTarget)
        assertEquals(1, clearCount)
        assertEquals(AutomationRunState.Success, loopUseCase.runState.value)
    }

    @Test
    fun packageScopedPromptAutomation_clearsPackageRestrictionOnFailureAndCancel() {
        var currentRestrictedTarget: AutomationTargetApp? = null
        val delegate = RecordingPromptGateway(autoComplete = false)
        val scopedGateway = PackageScopedPromptAutomation(
            delegate = delegate,
            targetApp = AutomationTargetApp.CHATGPT,
            restrictPackages = { target -> currentRestrictedTarget = target },
            clearPackageRestriction = { currentRestrictedTarget = null },
            isSessionRunning = { true }
        )

        scopedGateway.sendPrompt(
            prompt = "test",
            newChatMode = NewChatMode.Initial,
            onStateChange = {},
            onDone = {}
        )
        assertEquals(AutomationTargetApp.CHATGPT, currentRestrictedTarget)

        delegate.emitState(AutomationRunState.Failure("오류"))
        assertNull(currentRestrictedTarget)

        scopedGateway.sendPrompt(
            prompt = "test 2",
            newChatMode = NewChatMode.Subsequent,
            onStateChange = {},
            onDone = {}
        )
        assertEquals(AutomationTargetApp.CHATGPT, currentRestrictedTarget)

        scopedGateway.cancelCurrentRun()
        assertTrue(delegate.cancelled)
        assertNull(currentRestrictedTarget)
    }

    @Test
    fun packageScopedVariationPromptAutomation_clearsPackageRestrictionOnDoneAndFailure() {
        var currentRestrictedTarget: AutomationTargetApp? = null
        var lastOnDone: (() -> Unit)? = null
        var lastOnStateChange: ((AutomationRunState) -> Unit)? = null

        val variationDelegate = object : PromptAutomationGateway {
            override fun sendPrompt(
                prompt: String,
                newChatMode: NewChatMode,
                onStateChange: (AutomationRunState) -> Unit,
                onDone: () -> Unit
            ) = Unit

            override fun pastePromptOnly(
                prompt: String,
                onStateChange: (AutomationRunState) -> Unit,
                onDone: () -> Unit
            ) {
                lastOnStateChange = onStateChange
                lastOnDone = onDone
            }

            override fun cancelCurrentRun() = Unit
        }

        val scopedVariation = PackageScopedPromptAutomation(
            delegate = variationDelegate,
            targetApp = AutomationTargetApp.GEMINI,
            restrictPackages = { target -> currentRestrictedTarget = target },
            clearPackageRestriction = { currentRestrictedTarget = null },
            isSessionRunning = { false }
        )

        var doneCalled = false
        scopedVariation.pastePromptOnly(
            prompt = "variation",
            onStateChange = {},
            onDone = { doneCalled = true }
        )
        assertEquals(AutomationTargetApp.GEMINI, currentRestrictedTarget)

        lastOnDone?.invoke()
        assertTrue(doneCalled)
        assertNull(currentRestrictedTarget)

        scopedVariation.pastePromptOnly(
            prompt = "variation 2",
            onStateChange = {},
            onDone = {}
        )
        assertEquals(AutomationTargetApp.GEMINI, currentRestrictedTarget)

        lastOnStateChange?.invoke(AutomationRunState.Stopped)
        assertNull(currentRestrictedTarget)
    }

    private class RecordingPromptGateway(
        private val autoComplete: Boolean
    ) : PromptAutomationGateway, FlowConfigurableGateway {
        private val pendingDone = ArrayDeque<() -> Unit>()
        private var latestOnStateChange: ((AutomationRunState) -> Unit)? = null
        var cancelled = false

        override fun setFlowImageCount(count: Int) = Unit

        override fun sendPrompt(
            prompt: String,
            newChatMode: NewChatMode,
            onStateChange: (AutomationRunState) -> Unit,
            onDone: () -> Unit
        ) {
            latestOnStateChange = onStateChange
            if (autoComplete) {
                onDone()
            } else {
                pendingDone.addLast(onDone)
            }
        }

        fun completeNext() {
            pendingDone.removeFirst().invoke()
        }

        fun emitState(state: AutomationRunState) {
            latestOnStateChange?.invoke(state)
        }

        override fun cancelCurrentRun() {
            cancelled = true
            pendingDone.clear()
        }
    }
}
