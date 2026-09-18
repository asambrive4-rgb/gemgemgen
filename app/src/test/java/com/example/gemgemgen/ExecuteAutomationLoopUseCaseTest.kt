// 역할: 자동화 반복 루프 실행 파이프라인의 마커 전송, 반복 생성 및 환경(입력기/애니메이션) 제어를 검증하는 단위 테스트
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.*
import com.example.gemgemgen.automation.domain.*
import com.example.gemgemgen.automation.usecase.*
import com.example.gemgemgen.core.*
import com.example.gemgemgen.environment.android.*
import com.example.gemgemgen.environment.domain.*
import com.example.gemgemgen.environment.usecase.*
import com.example.gemgemgen.ui.*
import com.example.gemgemgen.wildcard.domain.*
import com.example.gemgemgen.wildcard.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteAutomationLoopUseCaseTest {
    @Test
    fun run_loadsWildcardsOnceSendsMarkerFirstAndGeneratesOnePromptPerRepeat() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = true)
        var loadCount = 0
        val generatedIndexes = mutableListOf<Int>()
        var loadedTokens: Set<String>? = null
        val automation = automation(
            service = service,
            loadWildcardSets = { tokens ->
                loadCount += 1
                loadedTokens = tokens
                listOf(WildcardSet("__hair__", "hair.txt", listOf("black hair")))
            },
            generateFinalPrompt = { _, _, index ->
                generatedIndexes += index
                "prompt $index"
            }
        )

        automation.run(
            request = AutomationRunRequest(
                promptTemplate = "base __hair__",
                repeatCountText = "3",
                targetApp = AutomationTargetApp.GEMINI
            ),
            onStateChange = {}
        )

        assertEquals(1, loadCount)
        assertEquals(setOf("__hair__"), loadedTokens)
        assertEquals(
            listOf(
                ExecuteAutomationLoopUseCase.MARKER_PROMPT,
                "prompt 1",
                "prompt 2",
                "prompt 3"
            ),
            service.sentPrompts
        )
        assertEquals(
            listOf(
                NewChatMode.Initial,
                NewChatMode.Subsequent,
                NewChatMode.Subsequent,
                NewChatMode.Subsequent
            ),
            service.newChatModes
        )
        assertEquals(listOf(1, 2, 3), generatedIndexes)
    }

    @Test
    fun run_withFlowTarget_skipsMarkerAndSendsInitialModeOnFirstPromptOnly() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = true)
        val automation = automation(
            service = service,
            generateFinalPrompt = { _, _, index -> "flow prompt $index" }
        )

        automation.run(
            request = AutomationRunRequest(
                promptTemplate = "test template",
                repeatCountText = "2",
                targetApp = AutomationTargetApp.FLOW,
                flowImageCount = 3
            ),
            onStateChange = {}
        )

        assertEquals(
            listOf("flow prompt 1", "flow prompt 2"),
            service.sentPrompts
        )
        assertEquals(
            listOf(NewChatMode.Initial, NewChatMode.Subsequent),
            service.newChatModes
        )
        assertEquals(3, service.configuredImageCount)
    }

    @Test
    fun run_withoutWildcardTokens_doesNotLoadWildcardSets() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = true)
        var loadCount = 0
        val automation = automation(
            service = service,
            loadWildcardSets = {
                loadCount += 1
                emptyList()
            },
            generateFinalPrompt = { _, _, _ -> "plain prompt" }
        )

        automation.run(
            request = AutomationRunRequest(
                promptTemplate = "plain prompt",
                repeatCountText = "1",
                targetApp = AutomationTargetApp.GEMINI
            ),
            onStateChange = {}
        )

        assertEquals(0, loadCount)
        assertEquals(
            listOf(ExecuteAutomationLoopUseCase.MARKER_PROMPT, "plain prompt"),
            service.sentPrompts
        )
    }

    @Test
    fun run_withInitialWildcards_bypassesRepositoryAndUsesProvidedSets() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = true)
        var loadCount = 0
        var receivedWildcards: List<WildcardSet>? = null
        val customWildcard = WildcardSet("__color__", "color.txt", listOf("cyan"))
        val automation = automation(
            service = service,
            loadWildcardSets = {
                loadCount += 1
                listOf(WildcardSet("__color__", "color.txt", listOf("magenta")))
            },
            generateFinalPrompt = { _, wildcards, _ ->
                receivedWildcards = wildcards
                "paint it ${wildcards.first().items.first()}"
            }
        )

        automation.run(
            request = AutomationRunRequest(
                promptTemplate = "paint it __color__",
                repeatCountText = "1",
                targetApp = AutomationTargetApp.GEMINI,
                initialWildcards = listOf(customWildcard)
            ),
            onStateChange = {}
        )

        assertEquals(0, loadCount)
        assertEquals(listOf(customWildcard), receivedWildcards)
        assertEquals(
            listOf(ExecuteAutomationLoopUseCase.MARKER_PROMPT, "paint it cyan"),
            service.sentPrompts
        )
    }

    @Test
    fun cancel_cancelsServiceRestoresImeAndWritesStoppedLog() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = false)
        val automation = automation(
            service = service,
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )
        val states = mutableListOf<AutomationRunState>()

        automation.run(
            AutomationRunRequest(
                promptTemplate = "base",
                repeatCountText = "2",
                targetApp = AutomationTargetApp.GEMINI
            ),
            states::add
        )
        automation.cancel(states::add)

        assertTrue(service.wasCancelled)
        assertEquals(ORIGINAL_IME_ID, defaultImeId)
        assertEquals(AutomationRunState.Stopped, states.last())
        assertEquals(AutomationRunState.Stopped, automation.runState.value)
    }

    @Test
    fun onAccessibilityLost_cancelsRestoresImeAndEmitsFailure() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = false)
        val automation = automation(
            service = service,
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )

        automation.run(
            AutomationRunRequest(
                promptTemplate = "base",
                repeatCountText = "2",
                targetApp = AutomationTargetApp.GEMINI
            )
        )
        automation.onAccessibilityLost()

        assertTrue(service.wasCancelled)
        assertEquals(ORIGINAL_IME_ID, defaultImeId)
        val failure = automation.runState.value
        assertTrue(failure is AutomationRunState.Failure)
        assertEquals(
            "접근성 서비스가 중단되었습니다.",
            (failure as AutomationRunState.Failure).message
        )

        service.autoComplete = true
        service.wasCancelled = false
        automation.run(
            AutomationRunRequest(
                promptTemplate = "base",
                repeatCountText = "1",
                targetApp = AutomationTargetApp.GEMINI
            )
        )
        assertEquals(AutomationRunState.Success, automation.runState.value)
    }

    @Test
    fun run_rejectsSecondStartWhileActiveWithoutClearingRunState() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = false)
        val automation = automation(
            service = service,
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )
        val rejected = mutableListOf<AutomationRunState>()

        automation.run(
            AutomationRunRequest(
                promptTemplate = "base",
                repeatCountText = "2",
                targetApp = AutomationTargetApp.GEMINI
            )
        )
        val activeState = automation.runState.value
        assertTrue(activeState is AutomationRunState.Running)

        automation.run(
            AutomationRunRequest(
                promptTemplate = "base",
                repeatCountText = "1",
                targetApp = AutomationTargetApp.GEMINI
            ),
            rejected::add
        )

        assertEquals(
            listOf(AutomationRunState.Failure("이미 실행 중입니다.")),
            rejected
        )
        assertEquals(activeState, automation.runState.value)

        automation.cancel()
        assertEquals(AutomationRunState.Stopped, automation.runState.value)
    }

    @Test
    fun run_stopsAfterFirstPromptFailureAndWritesFailureLog() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = true)
        val automation = automation(
            service = service,
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )
        service.failOnPrompt = "prompt 1"
        val states = mutableListOf<AutomationRunState>()

        automation.run(
            AutomationRunRequest(
                promptTemplate = "base",
                repeatCountText = "2",
                targetApp = AutomationTargetApp.GEMINI
            ),
            states::add
        )

        assertTrue(states.any { it is AutomationRunState.Failure })
    }

    @Test
    fun run_usesSelectedTargetForGatewayLauncherAndLog() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = true)
        val requestedGatewayTargets = mutableListOf<AutomationTargetApp>()
        val launchedTargets = mutableListOf<AutomationTargetApp>()
        val automation = automation(
            service = service,
            onGatewayRequest = requestedGatewayTargets::add,
            onLaunch = {
                launchedTargets += it
                true
            },
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )

        automation.run(
            AutomationRunRequest(
                promptTemplate = "base",
                repeatCountText = "1",
                targetApp = AutomationTargetApp.CHATGPT
            ),
            {}
        )

        assertEquals(listOf(AutomationTargetApp.CHATGPT), requestedGatewayTargets)
        assertEquals(listOf(AutomationTargetApp.CHATGPT), launchedTargets)
    }

    @Test
    fun updateRepeatCount_midRun_increasesTargetAndContinuesSending() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = false)
        val automation = automation(
            service = service,
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )

        automation.run(
            AutomationRunRequest(
                promptTemplate = "base",
                repeatCountText = "2",
                targetApp = AutomationTargetApp.GEMINI
            )
        )
        // marker is pending; complete marker then first prompt
        service.completeLatest()
        service.completeLatest()

        val applied = automation.updateRepeatCount(4)
        assertEquals(4, applied)
        val running = automation.runState.value as AutomationRunState.Running
        assertEquals(4, running.totalCount)

        service.completeLatest() // prompt 2
        service.completeLatest() // prompt 3
        service.completeLatest() // prompt 4

        assertEquals(
            listOf(
                ExecuteAutomationLoopUseCase.MARKER_PROMPT,
                "prompt 1",
                "prompt 2",
                "prompt 3",
                "prompt 4"
            ),
            service.sentPrompts
        )
        assertEquals(AutomationRunState.Success, automation.runState.value)
    }

    @Test
    fun updateRepeatCount_doesNotGoBelowAlreadySuccessfulCount() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = false)
        val automation = automation(
            service = service,
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )

        automation.run(
            AutomationRunRequest(
                promptTemplate = "base",
                repeatCountText = "5",
                targetApp = AutomationTargetApp.GEMINI
            )
        )
        service.completeLatest() // marker
        service.completeLatest() // prompt 1
        service.completeLatest() // prompt 2 → successCount = 2, prompt 3 pending

        val applied = automation.updateRepeatCount(1)
        assertEquals(2, applied)
        val running = automation.runState.value as AutomationRunState.Running
        assertEquals(2, running.totalCount)

        service.completeLatest() // finish in-flight prompt 3, then stop at successCount >= 2
        assertEquals(AutomationRunState.Success, automation.runState.value)
        assertEquals(
            listOf(
                ExecuteAutomationLoopUseCase.MARKER_PROMPT,
                "prompt 1",
                "prompt 2",
                "prompt 3"
            ),
            service.sentPrompts
        )
    }

    @Test
    fun updateRepeatCount_whenIdle_returnsNull() {
        val automation = automation(
            service = FakePromptAutomationGateway(autoComplete = true),
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )

        assertEquals(null, automation.updateRepeatCount(9))
    }

    @Test
    fun run_disablesAnimationScalesOnStartAndRestoresOnFinish() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = true)
        var currentScales = AnimationScales(1.0f, 1.0f, 1.0f)
        val scaleSettings = object : AnimationScaleSettings {
            override fun getScales(): AnimationScales = currentScales
            override fun setScales(scales: AnimationScales): Boolean {
                currentScales = scales
                return true
            }
        }
        val animManager = ManageAnimationScaleUseCase(scaleSettings)
        val automation = automation(
            service = service,
            manageAnimationScaleUseCase = animManager,
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )

        automation.run(
            request = AutomationRunRequest(
                promptTemplate = "test",
                repeatCountText = "1",
                targetApp = AutomationTargetApp.GEMINI
            )
        )

        assertEquals(AnimationScales(1.0f, 1.0f, 1.0f), currentScales)
    }

    @Test
    fun cancel_restoresAnimationScales() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = false)
        var currentScales = AnimationScales(0.5f, 0.5f, 0.5f)
        val scaleSettings = object : AnimationScaleSettings {
            override fun getScales(): AnimationScales = currentScales
            override fun setScales(scales: AnimationScales): Boolean {
                currentScales = scales
                return true
            }
        }
        val animManager = ManageAnimationScaleUseCase(scaleSettings)
        val automation = automation(
            service = service,
            manageAnimationScaleUseCase = animManager,
            generateFinalPrompt = { _, _, index -> "prompt $index" }
        )

        automation.run(
            request = AutomationRunRequest(
                promptTemplate = "test",
                repeatCountText = "2",
                targetApp = AutomationTargetApp.GEMINI
            )
        )
        assertEquals(AnimationScales.ZERO, currentScales)

        automation.cancel()

        assertEquals(AnimationScales(0.5f, 0.5f, 0.5f), currentScales)
    }

    private var defaultImeId = ORIGINAL_IME_ID

    private fun automation(
        service: FakePromptAutomationGateway,
        loadWildcardSets: (Set<String>) -> List<WildcardSet> = { emptyList() },
        onGatewayRequest: (AutomationTargetApp) -> Unit = {},
        onLaunch: (AutomationTargetApp) -> Boolean = { true },
        manageAnimationScaleUseCase: ManageAnimationScaleUseCase? = null,
        generateFinalPrompt: (String, List<WildcardSet>, Int) -> String
    ): ExecuteAutomationLoopUseCase {
        defaultImeId = ORIGINAL_IME_ID
        return ExecuteAutomationLoopUseCase(
            manageImeUseCase = ManageImeUseCase(
                settings = object : ImeSettings {
                    override fun getDefaultInputMethod(): String? = defaultImeId

                    override fun setDefaultInputMethod(imeId: String): Boolean {
                        defaultImeId = imeId
                        return true
                    }
                },
                nullKeyboardImeId = NULL_IME_ID
            ),
            lastRunSnapshotStore = LastRunSnapshotStore(FakeLastRunSnapshotStorage()),
            clipboardGateway = FakeClipboardGateway(),
            wildcardSetRepository = FakeWildcardSetRepository(loadWildcardSets),
            promptGatewayProvider = PromptAutomationGatewayProvider { targetApp ->
                onGatewayRequest(targetApp)
                service
            },
            targetAppLauncher = TargetAppLauncher(onLaunch),
            manageAnimationScaleUseCase = manageAnimationScaleUseCase,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined),
            generateFinalPrompt = generateFinalPrompt
        )
    }

    private class FakePromptAutomationGateway(
        var autoComplete: Boolean
    ) : PromptAutomationGateway, FlowConfigurableGateway {
        var configuredImageCount: Int? = null

        override fun setFlowImageCount(count: Int) {
            configuredImageCount = count
        }
        val sentPrompts = mutableListOf<String>()
        val newChatModes = mutableListOf<NewChatMode>()
        var failOnPrompt: String? = null
        var wasCancelled = false
        private val pendingDone = ArrayDeque<() -> Unit>()

        override fun sendPrompt(
            prompt: String,
            newChatMode: NewChatMode,
            onStateChange: (AutomationRunState) -> Unit,
            onDone: () -> Unit
        ) {
            sentPrompts += prompt
            newChatModes += newChatMode
            failOnPrompt?.let { failedPrompt ->
                if (prompt == failedPrompt) {
                    onStateChange(AutomationRunState.Failure("전송 실패"))
                    return
                }
            }
            if (autoComplete) {
                onDone()
            } else {
                pendingDone.addLast(onDone)
            }
        }

        fun completeLatest() {
            val done = pendingDone.removeFirstOrNull()
                ?: error("No pending prompt to complete. sent=${sentPrompts.size}")
            done()
        }

        override fun cancelCurrentRun() {
            wasCancelled = true
            pendingDone.clear()
        }
    }

    private class FakeLastRunSnapshotStorage : LastRunSnapshotRepository {
        override fun load(): LastRunSnapshot? = null

        override fun save(snapshot: LastRunSnapshot) = Unit
    }

    private class FakeClipboardGateway : ClipboardGateway {
        override fun readText(): String = ""

        override fun writeText(text: String) = Unit
    }

    private class FakeWildcardSetRepository(
        private val loadWildcardSets: (Set<String>) -> List<WildcardSet>
    ) : WildcardSetRepository {
        override fun load(): List<WildcardSet> = loadWildcardSets(emptySet())

        override fun load(tokens: Set<String>): List<WildcardSet> = loadWildcardSets(tokens)
    }

    private companion object {
        const val ORIGINAL_IME_ID = "example.keyboard/.Ime"
        const val NULL_IME_ID = "example.nullkeyboard/.NullIme"
    }
}
