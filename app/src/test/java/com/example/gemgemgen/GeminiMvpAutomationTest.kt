package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiMvpAutomationTest {
    @Test
    fun run_loadsWildcardsOnceSendsMarkerFirstAndGeneratesOnePromptPerRepeat() {
        val service = FakeGeminiPromptSender(autoComplete = true)
        val logger = RunLogger(FakeRunLogStorage())
        var loadCount = 0
        val generatedIndexes = mutableListOf<Int>()
        val automation = automation(
            service = service,
            logger = logger,
            loadWildcards = {
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
            promptTemplate = "base __hair__",
            repeatCountText = "3",
            onStateChange = {}
        )

        assertEquals(1, loadCount)
        assertEquals(
            listOf(
                GeminiMvpAutomation.MARKER_PROMPT,
                "prompt 1",
                "prompt 2",
                "prompt 3"
            ),
            service.sentPrompts
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
    fun cancel_cancelsServiceRestoresImeAndWritesStoppedLog() {
        val service = FakeGeminiPromptSender(autoComplete = false)
        val logger = RunLogger(FakeRunLogStorage())
        val automation = automation(
            service = service,
            logger = logger,
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "base", "prompt $index", emptyMap())
            }
        )
        val states = mutableListOf<AutomationUiState>()

        automation.run("base", "2", states::add)
        automation.cancel(states::add)

        assertTrue(service.wasCancelled)
        assertEquals(ORIGINAL_IME_ID, defaultImeId)
        assertEquals(AutomationUiState.Stopped, states.last())

        val log = logger.loadRecent().single()
        assertEquals(AutomationRunLogStatus.STOPPED, log.status)
        assertEquals("사용자 중지", log.message)
        assertEquals("성공", log.imeRestoreMessage)
    }

    @Test
    fun run_stopsAfterFirstPromptFailureAndWritesFailureLog() {
        val service = FakeGeminiPromptSender(autoComplete = true)
        val logger = RunLogger(FakeRunLogStorage())
        val automation = automation(
            service = service,
            logger = logger,
            generatePrompt = { _, _, index ->
                GeneratedPrompt(index, "base", "prompt $index", emptyMap())
            }
        )
        service.failOnPrompt = "prompt 1"

        automation.run("base", "2", {})

        val log = logger.loadRecent().single()
        assertEquals(AutomationRunLogStatus.FAILURE, log.status)
        assertEquals(2, log.repeatCount)
        assertEquals(0, log.completedCount)
        assertEquals(0, log.successCount)
        assertEquals(1, log.failureCount)
    }

    private var defaultImeId = ORIGINAL_IME_ID

    private fun automation(
        service: FakeGeminiPromptSender,
        logger: RunLogger,
        loadWildcards: () -> List<WildcardSet> = { emptyList() },
        generatePrompt: (String, List<WildcardSet>, Int) -> GeneratedPrompt
    ): GeminiMvpAutomation {
        defaultImeId = ORIGINAL_IME_ID
        return GeminiMvpAutomation(
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
            clock = { 1000L },
            serviceProvider = { service },
            launchGeminiApp = { true },
            loadWildcards = loadWildcards,
            generatePrompt = generatePrompt
        )
    }

    private class FakeGeminiPromptSender(
        private val autoComplete: Boolean
    ) : GeminiPromptSender {
        val sentPrompts = mutableListOf<String>()
        var failOnPrompt: String? = null
        var wasCancelled = false

        override fun sendPrompt(
            prompt: String,
            onStateChange: (AutomationUiState) -> Unit,
            onDone: () -> Unit
        ) {
            sentPrompts += prompt
            failOnPrompt?.let { failedPrompt ->
                if (prompt == failedPrompt) {
                    onStateChange(AutomationUiState.Failure("전송 실패"))
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

    private class FakeRunLogStorage : RunLogStorage {
        private var value: String = ""

        override fun read(): String = value

        override fun write(value: String) {
            this.value = value
        }
    }

    private companion object {
        const val ORIGINAL_IME_ID = "example.keyboard/.Ime"
        const val NULL_IME_ID = "example.nullkeyboard/.NullIme"
    }
}
