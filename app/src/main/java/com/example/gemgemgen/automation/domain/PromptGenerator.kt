package com.example.gemgemgen.automation.domain

import com.example.gemgemgen.wildcard.domain.WildcardSet
import java.security.SecureRandom
import java.util.Random

class PromptGenerator(
    private val random: Random = SecureRandom()
) {
    fun generate(
        basePrompt: String,
        wildcardSets: List<WildcardSet>,
        repeatCount: Int
    ): List<GeneratedPrompt> {
        val compiledPrompt = compile(
            basePrompt = basePrompt,
            wildcardSets = wildcardSets
        )

        return (1..repeatCount.coerceAtLeast(0)).map { index ->
            compiledPrompt.generate(index)
        }
    }

    fun compile(
        basePrompt: String,
        wildcardSets: List<WildcardSet>
    ): CompiledPrompt {
        return CompiledPrompt(
            basePrompt = basePrompt,
            tokens = extractTokens(basePrompt),
            wildcardsByToken = wildcardSets.associateBy { it.token },
            random = random
        )
    }

    fun extractTokens(basePrompt: String): List<String> {
        return tokenRegex.findAll(basePrompt)
            .map { it.value }
            .distinct()
            .toList()
    }

    class CompiledPrompt internal constructor(
        private val basePrompt: String,
        private val tokens: List<String>,
        private val wildcardsByToken: Map<String, WildcardSet>,
        private val random: Random
    ) {
        fun generate(index: Int): GeneratedPrompt {
            val replacements = chooseReplacements()
            return GeneratedPrompt(
                index = index,
                basePrompt = basePrompt,
                finalPrompt = tokenRegex.replace(basePrompt) { match ->
                    replacements[match.value] ?: match.value
                },
                replacements = replacements
            )
        }

        private fun chooseReplacements(): Map<String, String> {
            return tokens.mapNotNull { token ->
                val items = wildcardsByToken[token]?.items.orEmpty()
                if (items.isEmpty()) {
                    null
                } else {
                    token to items[random.nextInt(items.size)]
                }
            }.toMap()
        }
    }

    companion object {
        private val tokenRegex = Regex("__[^\\s]+?__")
    }
}

