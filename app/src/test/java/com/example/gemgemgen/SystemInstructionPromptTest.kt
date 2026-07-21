package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.SystemInstructionPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemInstructionPromptTest {
    @Test
    fun text_isNonBlankAndHasMultipleLines() {
        val lines = SystemInstructionPrompt.text.lines()
        assertTrue(SystemInstructionPrompt.text.isNotBlank())
        assertTrue(lines.size >= 10)
        assertEquals(
            "[System Instruction for Thought Process Strategy]",
            lines.first()
        )
        assertTrue(SystemInstructionPrompt.text.contains("POST-DISTORTION ENVIRONMENTAL SEPARATION"))
    }

    @Test
    fun prependTo_empty_returnsSiOnly() {
        assertEquals(SystemInstructionPrompt.text, SystemInstructionPrompt.prependTo(""))
    }

    @Test
    fun prependTo_existing_separatesWithOneBlankLine() {
        val result = SystemInstructionPrompt.prependTo("hello")
        assertEquals(
            SystemInstructionPrompt.text + "\n\n" + "hello",
            result
        )
    }
}
