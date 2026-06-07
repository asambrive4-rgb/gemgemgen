package com.example.gemgemgen

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

    private class FakeImeSettings(
        var defaultImeId: String?,
        private val writeResult: Boolean = true,
        private val applyWrites: Boolean = true
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
    }

    private companion object {
        const val ORIGINAL_IME_ID = "example.keyboard/.Ime"
        const val NULL_IME_ID = "example.nullkeyboard/.NullIme"
    }
}
