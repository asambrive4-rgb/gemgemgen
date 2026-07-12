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

class RunAutomationUseCaseTest {
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
            generatePrompt = { _, _, index ->
                generatedIndexes += index
                GeneratedPrompt(
                    index = index,
                    basePrompt = "base",
                    finalPrompt = "prompt $index",
                    replacements = emptyMap()
                )
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
                RunAutomationUseCase.MARKER_PROMPT,
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
    fun run_withoutWildcardTokens_doesNotLoadWildcardSets() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = true)
        var loadCount = 0
        val automation = automation(
            service = service,
            loadWildcardSets = {
                loadCount += 1
                emptyList()
            },
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "plain prompt", "plain prompt", emptyMap())
            }
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
            listOf(RunAutomationUseCase.MARKER_PROMPT, "plain prompt"),
            service.sentPrompts
        )
    }

    @Test
    fun cancel_cancelsServiceRestoresImeAndWritesStoppedLog() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = false)
        val automation = automation(
            service = service,
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "base", "prompt $index", emptyMap())
            }
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
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "base", "prompt $index", emptyMap())
            }
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
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "base", "prompt $index", emptyMap())
            }
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
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "base", "prompt $index", emptyMap())
            }
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
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "base", "prompt $index", emptyMap())
            }
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

    private var defaultImeId = ORIGINAL_IME_ID

    private fun automation(
        service: FakePromptAutomationGateway,
        loadWildcardSets: (Set<String>) -> List<WildcardSet> = { emptyList() },
        onGatewayRequest: (AutomationTargetApp) -> Unit = {},
        onLaunch: (AutomationTargetApp) -> Boolean = { true },
        generatePrompt: (String, List<WildcardSet>, Int) -> GeneratedPrompt
    ): RunAutomationUseCase {
        defaultImeId = ORIGINAL_IME_ID
        return RunAutomationUseCase(
            imeManager = ImeManager(
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
            clock = { 1000L },
            promptGatewayProvider = PromptAutomationGatewayProvider { targetApp ->
                onGatewayRequest(targetApp)
                service
            },
            targetAppLauncher = TargetAppLauncher(onLaunch),
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined),
            generatePrompt = generatePrompt
        )
    }

    private class FakePromptAutomationGateway(
        var autoComplete: Boolean
    ) : PromptAutomationGateway {
        val sentPrompts = mutableListOf<String>()
        val newChatModes = mutableListOf<NewChatMode>()
        var failOnPrompt: String? = null
        var wasCancelled = false

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
            }
        }

        override fun cancelCurrentRun() {
            wasCancelled = true
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
