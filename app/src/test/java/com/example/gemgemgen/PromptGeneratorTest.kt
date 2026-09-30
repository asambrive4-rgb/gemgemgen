// 역할: 와일드카드 규칙과 다이나믹 구문에 따른 최종 프롬프트 생성 알고리즘을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.PromptGenerator
import com.example.gemgemgen.wildcard.domain.WildcardSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class PromptGeneratorTest {
    @Test
    fun extractTokens_returnsDistinctTokensInPromptOrder() {
        val tokens = PromptGenerator(Random(0)).extractTokens(
            "__hair__ portrait with __color__ ribbon and __hair__"
        )

        assertEquals(listOf("__hair__", "__color__"), tokens)
    }

    @Test
    fun generateFinalPrompt_keepsPromptWhenThereAreNoTokens() {
        val compiledPrompt = PromptGenerator(Random(0)).compile(
            basePrompt = "plain prompt",
            wildcardSets = emptyList()
        )
        val generated = (1..3).map { compiledPrompt.generateFinalPrompt() }

        assertEquals(3, generated.size)
        assertEquals(listOf("plain prompt", "plain prompt", "plain prompt"), generated)
    }

    @Test
    fun generateFinalPrompt_replacesSameTokenWithSameValueInOnePrompt() {
        val generated = PromptGenerator(Random(0)).compile(
            basePrompt = "__color__ dress with __color__ ribbon",
            wildcardSets = listOf(
                WildcardSet(
                    token = "__color__",
                    fileName = "color.txt",
                    items = listOf("red")
                )
            )
        ).generateFinalPrompt()

        assertEquals("red dress with red ribbon", generated)
    }

    @Test
    fun generateFinalPrompt_keepsMissingTokenAsOriginalText() {
        val generated = PromptGenerator(Random(0)).compile(
            basePrompt = "portrait with __hair__",
            wildcardSets = emptyList()
        ).generateFinalPrompt()

        assertEquals("portrait with __hair__", generated)
    }

    @Test
    fun generateFinalPrompt_keepsTokenWhenCandidateListIsEmpty() {
        val generated = PromptGenerator(Random(0)).compile(
            basePrompt = "portrait with __hair__",
            wildcardSets = listOf(
                WildcardSet(
                    token = "__hair__",
                    fileName = "hair.txt",
                    items = emptyList()
                )
            )
        ).generateFinalPrompt()

        assertEquals("portrait with __hair__", generated)
    }

    @Test
    fun compiledPrompt_reusesTokenPlanForRepeatedGeneration() {
        val compiledPrompt = PromptGenerator(Random(0)).compile(
            basePrompt = "__color__ dress with __color__ ribbon",
            wildcardSets = listOf(
                WildcardSet(
                    token = "__color__",
                    fileName = "color.txt",
                    items = listOf("red")
                )
            )
        )

        val generated = listOf(
            compiledPrompt.generateFinalPrompt(),
            compiledPrompt.generateFinalPrompt()
        )

        assertEquals(
            listOf("red dress with red ribbon", "red dress with red ribbon"),
            generated
        )
    }

    @Test
    fun generateFinalPrompt_expandsDynamicPromptWithTwoOptions() {
        val generated = PromptGenerator(Random(0)).compile(
            basePrompt = "a <cat|dog> on the sofa",
            wildcardSets = emptyList()
        ).generateFinalPrompt()

        assertTrue(
            generated == "a cat on the sofa" ||
                generated == "a dog on the sofa"
        )
        assertFalse(generated.contains('<'))
        assertFalse(generated.contains('|'))
    }

    @Test
    fun generateFinalPrompt_expandsDynamicPromptWithThreeOrMoreOptions() {
        val compiledPrompt = PromptGenerator(Random(1)).compile(
            basePrompt = "wear a <red|blue|green> dress",
            wildcardSets = emptyList()
        )
        val results = (1..30).map { compiledPrompt.generateFinalPrompt() }.toSet()

        assertTrue(results.contains("wear a red dress"))
        assertTrue(results.contains("wear a blue dress"))
        assertTrue(results.contains("wear a green dress"))
        assertEquals(3, results.size)
    }

    @Test
    fun generateFinalPrompt_keepsAngleBracketsWithoutPipeAsLiteral() {
        val generated = PromptGenerator(Random(0)).compile(
            basePrompt = "tag <red> and value",
            wildcardSets = emptyList()
        ).generateFinalPrompt()

        assertEquals("tag <red> and value", generated)
    }

    @Test
    fun generateFinalPrompt_trimsDynamicOptionsAndAllowsEmptyOption() {
        val compiledPrompt = PromptGenerator(Random(2)).compile(
            basePrompt = "prefix< A | >suffix",
            wildcardSets = emptyList()
        )
        val results = (1..40).map { compiledPrompt.generateFinalPrompt() }.toSet()

        assertTrue(results.contains("prefixAsuffix"))
        assertTrue(results.contains("prefixsuffix"))
    }

    @Test
    fun generateFinalPrompt_picksIndependentValuesForSeparateDynamicSegments() {
        val compiledPrompt = PromptGenerator(Random(3)).compile(
            basePrompt = "<a|b> and <a|b>",
            wildcardSets = emptyList()
        )
        val results = (1..50).map { compiledPrompt.generateFinalPrompt() }.toSet()

        // 위치마다 독립 선택이므로 혼합 결과도 나와야 한다.
        assertTrue(results.any { it == "a and b" || it == "b and a" })
    }

    @Test
    fun generateFinalPrompt_appliesWildcardsBeforeDynamicPrompts() {
        val compiledPrompt = PromptGenerator(Random(0)).compile(
            basePrompt = "<__color__|navy> shirt",
            wildcardSets = listOf(
                WildcardSet(
                    token = "__color__",
                    fileName = "color.txt",
                    items = listOf("crimson")
                )
            )
        )
        val generated = (1..20).map { compiledPrompt.generateFinalPrompt() }.toSet()

        // 와일드카드 먼저 → <crimson|navy> → 둘 중 하나
        assertTrue(generated.all { it == "crimson shirt" || it == "navy shirt" })
        assertTrue(generated.contains("crimson shirt") || generated.contains("navy shirt"))
    }

    @Test
    fun generateFinalPrompt_combinesWildcardTokenAndDynamicInSamePrompt() {
        val generated = PromptGenerator(Random(0)).compile(
            basePrompt = "__hair__ with <smile|serious> face",
            wildcardSets = listOf(
                WildcardSet(
                    token = "__hair__",
                    fileName = "hair.txt",
                    items = listOf("short black hair")
                )
            )
        ).generateFinalPrompt()

        assertTrue(
            generated == "short black hair with smile face" ||
                generated == "short black hair with serious face"
        )
    }

    @Test
    fun expandDynamicPrompts_leavesUnclosedOrEmptyAngleBrackets() {
        assertEquals(
            "open <a|b still open",
            PromptGenerator.expandDynamicPrompts("open <a|b still open", Random(0))
        )
        assertEquals(
            "empty <> here",
            PromptGenerator.expandDynamicPrompts("empty <> here", Random(0))
        )
    }
}
