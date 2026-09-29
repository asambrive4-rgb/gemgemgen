// 역할: 프롬프트 상단 및 하단 인스트럭션 설정의 기본값 도메인 규칙을 검증합니다.
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
}
