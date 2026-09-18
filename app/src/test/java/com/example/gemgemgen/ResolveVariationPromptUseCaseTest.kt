// 역할: 문단 선택 및 드래그 영역 기반 변주 프롬프트 결합 유스케이스의 정확성과 예외 안전성을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import com.example.gemgemgen.automation.usecase.ResolveVariationPromptUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveVariationPromptUseCaseTest {

    private val useCase = ResolveVariationPromptUseCase()
    private val config = VariationPromptConfig("기본 변주 템플릿")

    @Test
    fun `resolveTarget returns selected paragraph when paragraph mode is active`() {
        val fullText = "첫 번째 문단\n\n두 번째 문단\n\n세 번째 문단"
        val start = fullText.indexOf("두 번째 문단")
        val range = PromptParagraphRange(start = start, endExclusive = start + "두 번째 문단".length)

        val target = useCase.resolveTarget(
            fullText = fullText,
            explicitSelectedText = null,
            isParagraphSelectionMode = true,
            selectedParagraphRange = range
        )

        assertEquals("두 번째 문단", target)
        val prompt = useCase.buildPrompt(
            config = config,
            fullText = fullText,
            explicitSelectedText = null,
            isParagraphSelectionMode = true,
            selectedParagraphRange = range
        )
        assertEquals("기본 변주 템플릿\n\n두 번째 문단", prompt)
    }

    @Test
    fun `resolveTarget returns explicit drag selection when paragraph mode is inactive`() {
        val fullText = "전체 프롬프트 원문입니다."

        val target = useCase.resolveTarget(
            fullText = fullText,
            explicitSelectedText = "프롬프트 원문",
            isParagraphSelectionMode = false,
            selectedParagraphRange = null
        )

        assertEquals("프롬프트 원문", target)
        val prompt = useCase.buildPrompt(
            config = config,
            fullText = fullText,
            explicitSelectedText = "프롬프트 원문",
            isParagraphSelectionMode = false,
            selectedParagraphRange = null
        )
        assertEquals("기본 변주 템플릿\n\n프롬프트 원문", prompt)
    }

    @Test
    fun `resolveTarget prioritizes paragraph selection over drag text when paragraph mode is active`() {
        val fullText = "첫 번째 문단\n\n두 번째 문단"
        val start = fullText.indexOf("두 번째 문단")
        val range = PromptParagraphRange(start = start, endExclusive = start + "두 번째 문단".length)

        val target = useCase.resolveTarget(
            fullText = fullText,
            explicitSelectedText = "첫 번째",
            isParagraphSelectionMode = true,
            selectedParagraphRange = range
        )

        assertEquals("두 번째 문단", target)
    }

    @Test
    fun `resolveTarget falls back to explicit drag text if paragraph range is null in paragraph mode`() {
        val fullText = "전체 프롬프트 원문"

        val target = useCase.resolveTarget(
            fullText = fullText,
            explicitSelectedText = "프롬프트",
            isParagraphSelectionMode = true,
            selectedParagraphRange = null
        )

        assertEquals("프롬프트", target)
    }

    @Test
    fun `resolveTarget returns null when no selection exists`() {
        val fullText = "전체 프롬프트 원문"

        val target = useCase.resolveTarget(
            fullText = fullText,
            explicitSelectedText = null,
            isParagraphSelectionMode = false,
            selectedParagraphRange = null
        )

        assertNull(target)
        val prompt = useCase.buildPrompt(
            config = config,
            fullText = fullText,
            explicitSelectedText = null,
            isParagraphSelectionMode = false,
            selectedParagraphRange = null
        )
        assertEquals("기본 변주 템플릿", prompt)
    }

    @Test
    fun `resolveTarget safely clamps out-of-bounds paragraph range`() {
        val fullText = "짧은 본문"
        val outOfBoundsRange = PromptParagraphRange(start = 1, endExclusive = 100)

        val target = useCase.resolveTarget(
            fullText = fullText,
            explicitSelectedText = null,
            isParagraphSelectionMode = true,
            selectedParagraphRange = outOfBoundsRange
        )

        assertEquals("은 본문", target)
    }

    @Test
    fun `resolveTarget returns null when selected paragraph contains only blank characters`() {
        val fullText = "첫 문단\n\n   \n\n끝 문단"
        val blankRange = PromptParagraphRange(start = 5, endExclusive = 10)

        val target = useCase.resolveTarget(
            fullText = fullText,
            explicitSelectedText = null,
            isParagraphSelectionMode = true,
            selectedParagraphRange = blankRange
        )

        assertNull(target)
    }
}
