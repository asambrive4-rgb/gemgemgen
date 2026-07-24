package com.example.gemgemgen

import com.example.gemgemgen.automation.android.*
import com.example.gemgemgen.automation.domain.*
import com.example.gemgemgen.automation.usecase.*
import com.example.gemgemgen.core.*
import com.example.gemgemgen.environment.android.*
import com.example.gemgemgen.environment.domain.*
import com.example.gemgemgen.environment.usecase.*
import com.example.gemgemgen.ui.*
import com.example.gemgemgen.wildcard.domain.*
import com.example.gemgemgen.wildcard.usecase.*
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
    fun generate_keepsPromptWhenThereAreNoTokens() {
        val generated = PromptGenerator(Random(0)).generate(
            basePrompt = "plain prompt",
            wildcardSets = emptyList(),
            repeatCount = 3
        )

        assertEquals(3, generated.size)
        assertEquals(listOf("plain prompt", "plain prompt", "plain prompt"), generated.map { it.finalPrompt })
    }

    @Test
    fun generate_replacesSameTokenWithSameValueInOnePrompt() {
        val generated = PromptGenerator(Random(0)).generate(
            basePrompt = "__color__ dress with __color__ ribbon",
            wildcardSets = listOf(
                WildcardSet(
                    token = "__color__",
                    fileName = "color.txt",
                    items = listOf("red")
                )
            ),
            repeatCount = 1
        ).single()

        assertEquals("red dress with red ribbon", generated.finalPrompt)
        assertEquals(mapOf("__color__" to "red"), generated.replacements)
    }

    @Test
    fun generate_keepsMissingTokenAsOriginalText() {
        val generated = PromptGenerator(Random(0)).generate(
            basePrompt = "portrait with __hair__",
            wildcardSets = emptyList(),
            repeatCount = 1
        ).single()

        assertEquals("portrait with __hair__", generated.finalPrompt)
        assertEquals(emptyMap<String, String>(), generated.replacements)
    }

    @Test
    fun generate_keepsTokenWhenCandidateListIsEmpty() {
        val generated = PromptGenerator(Random(0)).generate(
            basePrompt = "portrait with __hair__",
            wildcardSets = listOf(
                WildcardSet(
                    token = "__hair__",
                    fileName = "hair.txt",
                    items = emptyList()
                )
            ),
            repeatCount = 1
        ).single()

        assertEquals("portrait with __hair__", generated.finalPrompt)
        assertEquals(emptyMap<String, String>(), generated.replacements)
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
            compiledPrompt.generate(index = 1),
            compiledPrompt.generate(index = 2)
        )

        assertEquals(listOf(1, 2), generated.map { it.index })
        assertEquals(
            listOf("red dress with red ribbon", "red dress with red ribbon"),
            generated.map { it.finalPrompt }
        )
        assertEquals(
            listOf(mapOf("__color__" to "red"), mapOf("__color__" to "red")),
            generated.map { it.replacements }
        )
    }

    @Test
    fun generateFinalPrompt_returnsReplacedStringWithoutWrapper() {
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

        assertEquals(
            "red dress with red ribbon",
            compiledPrompt.generateFinalPrompt(index = 1)
        )
    }

    @Test
    fun generate_expandsDynamicPromptWithTwoOptions() {
        val generated = PromptGenerator(Random(0)).generate(
            basePrompt = "a <cat|dog> on the sofa",
            wildcardSets = emptyList(),
            repeatCount = 1
        ).single()

        assertTrue(
            generated.finalPrompt == "a cat on the sofa" ||
                generated.finalPrompt == "a dog on the sofa"
        )
        assertFalse(generated.finalPrompt.contains('<'))
        assertFalse(generated.finalPrompt.contains('|'))
    }

    @Test
    fun generate_expandsDynamicPromptWithThreeOrMoreOptions() {
        val results = PromptGenerator(Random(1)).generate(
            basePrompt = "wear a <red|blue|green> dress",
            wildcardSets = emptyList(),
            repeatCount = 30
        ).map { it.finalPrompt }.toSet()

        assertTrue(results.contains("wear a red dress"))
        assertTrue(results.contains("wear a blue dress"))
        assertTrue(results.contains("wear a green dress"))
        assertEquals(3, results.size)
    }

    @Test
    fun generate_keepsAngleBracketsWithoutPipeAsLiteral() {
        val generated = PromptGenerator(Random(0)).generate(
            basePrompt = "tag <red> and value",
            wildcardSets = emptyList(),
            repeatCount = 1
        ).single()

        assertEquals("tag <red> and value", generated.finalPrompt)
    }

    @Test
    fun generate_trimsDynamicOptionsAndAllowsEmptyOption() {
        val results = PromptGenerator(Random(2)).generate(
            basePrompt = "prefix< A | >suffix",
            wildcardSets = emptyList(),
            repeatCount = 40
        ).map { it.finalPrompt }.toSet()

        assertTrue(results.contains("prefixAsuffix"))
        assertTrue(results.contains("prefixsuffix"))
    }

    @Test
    fun generate_picksIndependentValuesForSeparateDynamicSegments() {
        val results = PromptGenerator(Random(3)).generate(
            basePrompt = "<a|b> and <a|b>",
            wildcardSets = emptyList(),
            repeatCount = 50
        ).map { it.finalPrompt }.toSet()

        // 위치마다 독립 선택이므로 혼합 결과도 나와야 한다.
        assertTrue(results.any { it == "a and b" || it == "b and a" })
    }

    @Test
    fun generate_appliesWildcardsBeforeDynamicPrompts() {
        val generated = PromptGenerator(Random(0)).generate(
            basePrompt = "<__color__|navy> shirt",
            wildcardSets = listOf(
                WildcardSet(
                    token = "__color__",
                    fileName = "color.txt",
                    items = listOf("crimson")
                )
            ),
            repeatCount = 20
        ).map { it.finalPrompt }.toSet()

        // 와일드카드 먼저 → <crimson|navy> → 둘 중 하나
        assertTrue(generated.all { it == "crimson shirt" || it == "navy shirt" })
        assertTrue(generated.contains("crimson shirt") || generated.contains("navy shirt"))
    }

    @Test
    fun generate_combinesWildcardTokenAndDynamicInSamePrompt() {
        val generated = PromptGenerator(Random(0)).generate(
            basePrompt = "__hair__ with <smile|serious> face",
            wildcardSets = listOf(
                WildcardSet(
                    token = "__hair__",
                    fileName = "hair.txt",
                    items = listOf("short black hair")
                )
            ),
            repeatCount = 1
        ).single()

        assertTrue(
            generated.finalPrompt == "short black hair with smile face" ||
                generated.finalPrompt == "short black hair with serious face"
        )
        assertEquals(mapOf("__hair__" to "short black hair"), generated.replacements)
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
