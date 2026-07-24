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
                finalPrompt = applyReplacements(replacements),
                replacements = replacements
            )
        }

        @Suppress("UNUSED_PARAMETER")
        fun generateFinalPrompt(index: Int): String {
            return applyReplacements(chooseReplacements())
        }

        private fun applyReplacements(replacements: Map<String, String>): String {
            // 1) 와일드카드 토큰 치환 → 2) 다이나믹 <A|B|…> 선택
            val afterWildcards = tokenRegex.replace(basePrompt) { match ->
                replacements[match.value] ?: match.value
            }
            return expandDynamicPrompts(afterWildcards, random)
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

        /**
         * 중첩 없는 `<…>` 구간. 안에 `|` 가 있을 때만 후보 중 하나를 고른다.
         * `|` 가 없으면 원문 유지. 옵션은 trim 하며 빈 문자열 후보도 허용한다.
         */
        private val dynamicSegmentRegex = Regex("<([^<>]+)>")

        internal fun expandDynamicPrompts(text: String, random: Random): String {
            if (text.isEmpty() || !text.contains('<')) return text

            return dynamicSegmentRegex.replace(text) { match ->
                val inner = match.groupValues[1]
                if (!inner.contains('|')) {
                    match.value
                } else {
                    val options = inner.split('|').map { it.trim() }
                    options[random.nextInt(options.size)]
                }
            }
        }
    }
}
