package com.example.gemgemgen

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
        val wildcardsByToken = wildcardSets.associateBy { it.token }

        return (1..repeatCount.coerceAtLeast(0)).map { index ->
            val replacements = chooseReplacements(basePrompt, wildcardsByToken)
            GeneratedPrompt(
                index = index,
                basePrompt = basePrompt,
                finalPrompt = tokenRegex.replace(basePrompt) { match ->
                    replacements[match.value] ?: match.value
                },
                replacements = replacements
            )
        }
    }

    private fun chooseReplacements(
        basePrompt: String,
        wildcardsByToken: Map<String, WildcardSet>
    ): Map<String, String> {
        return tokenRegex
            .findAll(basePrompt)
            .map { it.value }
            .distinct()
            .mapNotNull { token ->
                val items = wildcardsByToken[token]?.items.orEmpty()
                if (items.isEmpty()) {
                    null
                } else {
                    token to items[random.nextInt(items.size)]
                }
            }
            .toMap()
    }

    companion object {
        private val tokenRegex = Regex("__[^\\s]+?__")
    }
}
