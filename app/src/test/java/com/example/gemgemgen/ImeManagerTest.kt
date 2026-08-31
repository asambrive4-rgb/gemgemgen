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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeManagerTest {
    @Test
    fun switchToNullKeyboard_usesTaskerDefaultIdWithoutEnabledInputMethodDetection() {
        val settings = FakeImeSettings(defaultImeId = ORIGINAL_IME_ID)
        val manager = ImeManager(settings)

        val result = manager.switchToNullKeyboard()

        assertTrue(result is ImeSwitchResult.Success)
        val session = (result as ImeSwitchResult.Success).session
        assertEquals(ORIGINAL_IME_ID, session.originalImeId)
        assertEquals(AppDefaults.NULL_KEYBOARD_IME_ID, session.targetImeId)
        assertEquals(AppDefaults.NULL_KEYBOARD_IME_ID, settings.defaultImeId)
        assertTrue(session.changed)
    }

    @Test
    fun switchToNullKeyboard_doesNothingWhenAlreadyUsingNullKeyboard() {
        val settings = FakeImeSettings(defaultImeId = NULL_IME_ID)
        val manager = ImeManager(settings, NULL_IME_ID)

        val result = manager.switchToNullKeyboard()

        assertTrue(result is ImeSwitchResult.Success)
        val session = (result as ImeSwitchResult.Success).session
        assertEquals(NULL_IME_ID, session.originalImeId)
        assertFalse(session.changed)
        assertTrue(settings.writes.isEmpty())
    }

    @Test
    fun switchToNullKeyboard_failsWhenCurrentImeCannotBeRead() {
        val settings = FakeImeSettings(defaultImeId = null)
        val manager = ImeManager(settings, NULL_IME_ID)

        val result = manager.switchToNullKeyboard()

        assertTrue(result is ImeSwitchResult.Failure)
        assertNull((result as ImeSwitchResult.Failure).originalImeId)
        assertTrue(settings.writes.isEmpty())
    }

    @Test
    fun switchToNullKeyboard_failsWhenSettingWriteFails() {
        val settings = FakeImeSettings(
            defaultImeId = ORIGINAL_IME_ID,
            writeResult = false
        )
        val manager = ImeManager(settings, NULL_IME_ID)

        val result = manager.switchToNullKeyboard()

        assertTrue(result is ImeSwitchResult.Failure)
        assertEquals(ORIGINAL_IME_ID, settings.defaultImeId)
        assertEquals(listOf(NULL_IME_ID), settings.writes)
    }

    @Test
    fun switchToNullKeyboard_failsWhenDefaultInputMethodDoesNotChangeAfterWrite() {
        val settings = FakeImeSettings(
            defaultImeId = ORIGINAL_IME_ID,
            applyWrites = false
        )
        val manager = ImeManager(settings, NULL_IME_ID)

        val result = manager.switchToNullKeyboard()

        assertTrue(result is ImeSwitchResult.Failure)
        assertEquals(ORIGINAL_IME_ID, settings.defaultImeId)
        assertEquals(listOf(NULL_IME_ID), settings.writes)
    }

    @Test
    fun restore_setsOriginalImeWhenSwitchChangedInputMethod() {
        val settings = FakeImeSettings(defaultImeId = NULL_IME_ID)
        val manager = ImeManager(settings, NULL_IME_ID)
        val session = ImeSwitchSession(
            originalImeId = ORIGINAL_IME_ID,
            targetImeId = NULL_IME_ID,
            changed = true
        )

        val result = manager.restore(session)

        assertEquals(ImeRestoreResult.Success, result)
        assertEquals(ORIGINAL_IME_ID, settings.defaultImeId)
    }

    @Test
    fun restore_doesNothingWhenSwitchDidNotChangeInputMethod() {
        val settings = FakeImeSettings(defaultImeId = NULL_IME_ID)
        val manager = ImeManager(settings, NULL_IME_ID)
        val session = ImeSwitchSession(
            originalImeId = NULL_IME_ID,
            targetImeId = NULL_IME_ID,
            changed = false
        )

        val result = manager.restore(session)

        assertEquals(ImeRestoreResult.Success, result)
        assertTrue(settings.writes.isEmpty())
    }

    @Test
    fun restore_returnsFailureWhenOriginalImeCannotBeRestored() {
        val settings = FakeImeSettings(
            defaultImeId = NULL_IME_ID,
            writeResult = false
        )
        val manager = ImeManager(settings, NULL_IME_ID)
        val session = ImeSwitchSession(
            originalImeId = ORIGINAL_IME_ID,
            targetImeId = NULL_IME_ID,
            changed = true
        )

        val result = manager.restore(session)

        assertTrue(result is ImeRestoreResult.Failure)
        assertEquals(NULL_IME_ID, settings.defaultImeId)
    }

    @Test
    fun switchToNullKeyboard_prioritizesEnabledCandidateWhenMultipleCandidatesExist() {
        val candidate1 = "com.wparam.nullkeyboard/.NullInputMethod"
        val candidate2 = "com.nilac.nullkeyboard/.NullKeyboardService"
        val settings = FakeImeSettings(
            defaultImeId = ORIGINAL_IME_ID,
            enabledImeList = listOf("example.keyboard/.Ime", candidate2)
        )
        val manager = ImeManager(settings, listOf(candidate1, candidate2))

        val result = manager.switchToNullKeyboard()

        assertTrue(result is ImeSwitchResult.Success)
        val session = (result as ImeSwitchResult.Success).session
        assertEquals(ORIGINAL_IME_ID, session.originalImeId)
        assertEquals(candidate2, session.targetImeId)
        assertEquals(candidate2, settings.defaultImeId)
        assertTrue(session.changed)
    }

    @Test
    fun switchToNullKeyboard_fallsBackToNextCandidateWhenFirstFails() {
        val candidate1 = "com.first.nullkeyboard/.Ime"
        val candidate2 = "com.second.nullkeyboard/.Ime"
        val settings = object : ImeSettings {
            var currentIme: String? = ORIGINAL_IME_ID
            val writes = mutableListOf<String>()

            override fun getDefaultInputMethod(): String? = currentIme

            override fun setDefaultInputMethod(imeId: String): Boolean {
                writes += imeId
                return if (imeId == candidate1) {
                    false
                } else {
                    currentIme = imeId
                    true
                }
            }
        }
        val manager = ImeManager(settings, listOf(candidate1, candidate2))

        val result = manager.switchToNullKeyboard()

        assertTrue(result is ImeSwitchResult.Success)
        val session = (result as ImeSwitchResult.Success).session
        assertEquals(candidate2, session.targetImeId)
        assertEquals(candidate2, settings.currentIme)
        assertEquals(listOf(candidate1, candidate2), settings.writes)
    }

    private class FakeImeSettings(
        var defaultImeId: String?,
        private val writeResult: Boolean = true,
        private val applyWrites: Boolean = true,
        private val enabledImeList: List<String> = emptyList()
    ) : ImeSettings {
        val writes = mutableListOf<String>()

        override fun getDefaultInputMethod(): String? = defaultImeId

        override fun setDefaultInputMethod(imeId: String): Boolean {
            writes += imeId
            if (!writeResult) return false

            if (applyWrites) {
                defaultImeId = imeId
            }
            return true
        }

        override fun getEnabledInputMethods(): List<String> = enabledImeList
    }

    private companion object {
        const val ORIGINAL_IME_ID = "example.keyboard/.Ime"
        const val NULL_IME_ID = "example.nullkeyboard/.NullIme"
    }
}
