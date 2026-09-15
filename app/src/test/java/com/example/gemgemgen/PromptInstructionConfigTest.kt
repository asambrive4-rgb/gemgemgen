// 역할: 프롬프트 상단 및 하단 인스트럭션 결합 및 기본값 처리 도메인 규칙을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.PromptInstructionConfig
import com.example.gemgemgen.automation.domain.SystemInstructionPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptInstructionConfigTest {

    @Test
    fun defaultValues_haveSystemInstructionAndNullBottom() {
        val config = PromptInstructionConfig.DEFAULT
        assertEquals(SystemInstructionPrompt.text, config.topInstruction)
        assertNull(config.bottomInstruction)
    }

    @Test
    fun prependTopTo_emptyPrompt_returnsTopInstructionOnly() {
        val config = PromptInstructionConfig(topInstruction = "Top Header", bottomInstruction = null)
        val result = config.prependTopTo("")
        assertEquals("Top Header", result)
    }

    @Test
    fun prependTopTo_existingPrompt_separatesWithDoubleNewline() {
        val config = PromptInstructionConfig(topInstruction = "Top Header", bottomInstruction = null)
        val result = config.prependTopTo("Original Prompt")
        assertEquals("Top Header\n\nOriginal Prompt", result)
    }

    @Test
    fun prependTopTo_blankTop_returnsPromptUnchanged() {
        val config = PromptInstructionConfig(topInstruction = "   ", bottomInstruction = null)
        val result = config.prependTopTo("Original Prompt")
        assertEquals("Original Prompt", result)
    }

    @Test
    fun appendBottomTo_emptyPrompt_returnsBottomInstructionOnly() {
        val config = PromptInstructionConfig(topInstruction = "", bottomInstruction = "Bottom Footer")
        val result = config.appendBottomTo("")
        assertEquals("Bottom Footer", result)
    }

    @Test
    fun appendBottomTo_existingPrompt_separatesWithDoubleNewline() {
        val config = PromptInstructionConfig(topInstruction = "", bottomInstruction = "Bottom Footer")
        val result = config.appendBottomTo("Original Prompt")
        assertEquals("Original Prompt\n\nBottom Footer", result)
    }

    @Test
    fun appendBottomTo_nullOrBlankBottom_returnsPromptUnchanged() {
        val configNull = PromptInstructionConfig(topInstruction = "", bottomInstruction = null)
        assertEquals("Original Prompt", configNull.appendBottomTo("Original Prompt"))

        val configBlank = PromptInstructionConfig(topInstruction = "", bottomInstruction = "   ")
        assertEquals("Original Prompt", configBlank.appendBottomTo("Original Prompt"))
    }
}
