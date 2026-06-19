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
import org.junit.Test
import java.util.Random

class PromptGeneratorTest {
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
}
