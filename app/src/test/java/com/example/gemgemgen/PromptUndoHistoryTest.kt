package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.PromptUndoHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptUndoHistoryTest {
    @Test
    fun typingSnapshots_commitAsSingleUndoStep() {
        val history = PromptUndoHistory()

        history.recordTypingSnapshot("")
        history.recordTypingSnapshot("a")
        history.recordTypingSnapshot("ab")
        history.commitPendingTyping("abc")

        assertEquals("", history.popUndo())
        assertTrue(!history.canUndo)
    }

    @Test
    fun immediateSnapshot_commitsPendingTypingFirst() {
        val history = PromptUndoHistory()

        history.recordTypingSnapshot("before typing")
        history.commitPendingTyping("typed text")
        history.recordImmediateSnapshot("before paste")

        assertEquals("before paste", history.popUndo())
        assertEquals("before typing", history.popUndo())
    }

    @Test
    fun duplicateSnapshot_isIgnoredAtStackHead() {
        val history = PromptUndoHistory()

        history.recordImmediateSnapshot("same")
        history.recordImmediateSnapshot("same")

        assertEquals("same", history.popUndo())
        assertNull(history.popUndo())
    }
}
