package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiOneShotAutomationTest {
    @Test
    fun cancel_cancelsAccessibilityRunRestoresImeAndWritesStoppedLog() {
        val imeSettings = FakeImeSettings(defaultImeId = ORIGINAL_IME_ID)
        val service = FakeOneShotAutomationService()
        val logger = RunLogger(FakeRunLogStorage())
        var now = 1000L
        val automation = GeminiOneShotAutomation(
            imeManager = ImeManager(imeSettings, NULL_IME_ID),
            runLogger = logger,
            clock = {
                now += 1000L
                now
            },
            serviceProvider = { service },
            launchGeminiApp = { true }
        )
        val states = mutableListOf<AutomationUiState>()

        automation.run(states::add)
        automation.cancel(states::add)

        assertTrue(service.wasCancelled)
        assertEquals(ORIGINAL_IME_ID, imeSettings.defaultImeId)
        assertEquals(AutomationUiState.Stopped, states.last())

        val log = logger.loadRecent().single()
        assertEquals(AutomationRunLogStatus.STOPPED, log.status)
        assertEquals("Gemini 앱 실행 중", log.lastStep)
        assertEquals("사용자 중지", log.message)
        assertEquals("성공", log.imeRestoreMessage)
    }

    private class FakeOneShotAutomationService : OneShotAutomationService {
        var wasCancelled = false

        override fun sendPrompt(
            prompt: String,
            onStateChange: (AutomationUiState) -> Unit,
            onDone: () -> Unit,
            startDelayMillis: Long
        ) = Unit

        override fun runOneShot(
            prompt: String,
            onStateChange: (AutomationUiState) -> Unit
        ) = Unit

        override fun cancelCurrentRun() {
            wasCancelled = true
        }
    }

    private class FakeImeSettings(
        var defaultImeId: String?
    ) : ImeSettings {
        override fun getDefaultInputMethod(): String? = defaultImeId

        override fun setDefaultInputMethod(imeId: String): Boolean {
            defaultImeId = imeId
            return true
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
