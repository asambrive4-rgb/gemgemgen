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
        val logger = RunLogger(FakeRunLogStorage())
        var loadCount = 0
        val generatedIndexes = mutableListOf<Int>()
        val automation = automation(
            service = service,
            logger = logger,
            loadWildcardSets = {
                loadCount += 1
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

        val log = logger.loadRecent().single()
        assertEquals(AutomationRunLogStatus.SUCCESS, log.status)
        assertEquals(3, log.repeatCount)
        assertEquals(3, log.completedCount)
        assertEquals(3, log.successCount)
        assertEquals(0, log.failureCount)
        assertEquals("성공", log.markerStatus)
        assertEquals("성공", log.imeRestoreMessage)
    }

    @Test
    fun cancel_cancelsServiceRestoresImeAndWritesStoppedLog() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = false)
        val logger = RunLogger(FakeRunLogStorage())
        val automation = automation(
            service = service,
            logger = logger,
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

        val log = logger.loadRecent().single()
        assertEquals(AutomationRunLogStatus.STOPPED, log.status)
        assertEquals("사용자 중지", log.message)
        assertEquals("성공", log.imeRestoreMessage)
    }

    @Test
    fun run_stopsAfterFirstPromptFailureAndWritesFailureLog() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = true)
        val logger = RunLogger(FakeRunLogStorage())
        val automation = automation(
            service = service,
            logger = logger,
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "base", "prompt $index", emptyMap())
            }
        )
        service.failOnPrompt = "prompt 1"

        automation.run(
            AutomationRunRequest(
                promptTemplate = "base",
                repeatCountText = "2",
                targetApp = AutomationTargetApp.GEMINI
            ),
            {}
        )

        val log = logger.loadRecent().single()
        assertEquals(AutomationRunLogStatus.FAILURE, log.status)
        assertEquals(2, log.repeatCount)
        assertEquals(0, log.completedCount)
        assertEquals(0, log.successCount)
        assertEquals(1, log.failureCount)
    }

    @Test
    fun run_usesSelectedTargetForGatewayLauncherAndLog() = runBlocking {
        val service = FakePromptAutomationGateway(autoComplete = true)
        val logger = RunLogger(FakeRunLogStorage())
        val requestedGatewayTargets = mutableListOf<AutomationTargetApp>()
        val launchedTargets = mutableListOf<AutomationTargetApp>()
        val automation = automation(
            service = service,
            logger = logger,
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
        assertEquals(
            AutomationTargetApp.CHATGPT.storageValue,
            logger.loadRecent().single().targetApp
        )
    }

    private var defaultImeId = ORIGINAL_IME_ID

    private fun automation(
        service: FakePromptAutomationGateway,
        logger: RunLogger,
        loadWildcardSets: () -> List<WildcardSet> = { emptyList() },
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
            runLogger = logger,
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
        private val autoComplete: Boolean
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

    private class FakeRunLogStorage : RunLogRepository {
        private var logs: List<AutomationRunLog> = emptyList()

        override fun load(): List<AutomationRunLog> = logs

        override fun save(logs: List<AutomationRunLog>) {
            this.logs = logs
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
        private val loadWildcardSets: () -> List<WildcardSet>
    ) : WildcardSetRepository {
        override fun load(): List<WildcardSet> = loadWildcardSets()
    }

    private companion object {
        const val ORIGINAL_IME_ID = "example.keyboard/.Ime"
        const val NULL_IME_ID = "example.nullkeyboard/.NullIme"
    }
}
