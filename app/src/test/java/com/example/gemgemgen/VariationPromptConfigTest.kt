// 역할: 변주 생성 프롬프트 기본값 제공 및 선택 문구 결합 도메인 규칙을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.VariationPromptConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class VariationPromptConfigTest {

    @Test
    fun defaultValues_haveDefaultPrompt() {
        val config = VariationPromptConfig.DEFAULT
        assertEquals(VariationPromptConfig.DEFAULT_VARIATION_PROMPT, config.prompt)
    }

    @Test
    fun buildPrompt_withNullOrEmptySelectedText_returnsPromptOnly() {
        val config = VariationPromptConfig(prompt = "기본 변주 프롬프트")
        assertEquals("기본 변주 프롬프트", config.buildPrompt(null))
        assertEquals("기본 변주 프롬프트", config.buildPrompt(""))
        assertEquals("기본 변주 프롬프트", config.buildPrompt("   "))
    }

    @Test
    fun buildPrompt_withSelectedText_appendsDoubleNewlineAndTrimmedText() {
        val config = VariationPromptConfig(prompt = "기본 변주 프롬프트")
        val result = config.buildPrompt("  드래그 선택된 본문 내용  ")
        assertEquals("기본 변주 프롬프트\n\n드래그 선택된 본문 내용", result)
    }

    @Test
    fun buildPrompt_whenPromptIsBlank_returnsSelectedTextOnly() {
        val config = VariationPromptConfig(prompt = "   ")
        val result = config.buildPrompt("드래그 선택된 본문 내용")
        assertEquals("드래그 선택된 본문 내용", result)
    }
}
