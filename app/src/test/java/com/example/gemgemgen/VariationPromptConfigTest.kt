// 역할: 변주 생성 프롬프트 기본값 제공 및 선택 문구 결합 도메인 규칙을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.VariationPromptConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariationPromptConfigTest {

    @Test
    fun defaultValues_haveDefaultPrompt() {
        val config = VariationPromptConfig.DEFAULT
        assertEquals(VariationPromptConfig.DEFAULT_VARIATION_PROMPT, config.prompt)
    }

    @Test
    fun defaultPrompt_containsRequestedVariationInstructions() {
        val prompt = VariationPromptConfig.DEFAULT_VARIATION_PROMPT

        assertTrue(prompt.startsWith("당신은 자연어 기반 이미지 생성 모델에 최적화된 프롬프트 엔지니어입니다."))
        assertTrue(prompt.contains("### 1. 와일드카드"))
        assertTrue(prompt.contains("<항목1|항목2|...|항목20>"))
        assertTrue(prompt.contains("변주할 문구:"))
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

    @Test
    fun buildPrompt_trimsPromptTrailingWhitespaceBeforeAppendingSelectedText() {
        val config = VariationPromptConfig(prompt = "기본 변주 프롬프트\n\n")

        assertEquals(
            "기본 변주 프롬프트\n\n드래그 선택된 본문 내용",
            config.buildPrompt("드래그 선택된 본문 내용")
        )
    }
}
