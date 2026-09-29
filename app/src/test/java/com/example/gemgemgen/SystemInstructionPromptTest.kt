// 역할: AI 모델 시스템 지침 프롬프트의 기본 문구를 검증합니다.
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
}
