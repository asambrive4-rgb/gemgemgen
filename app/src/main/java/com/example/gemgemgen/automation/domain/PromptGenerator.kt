// 역할: 와일드카드 규칙과 다이나믹 구문을 치환하여 실제 전송할 최종 프롬프트를 생성합니다.
package com.example.gemgemgen.automation.domain

import com.example.gemgemgen.wildcard.domain.WildcardFileParser
import com.example.gemgemgen.wildcard.domain.WildcardSet
import java.util.Random

class PromptGenerator(
    private val random: Random = Random()
) {
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
        return WildcardFileParser.TOKEN_REGEX.findAll(basePrompt)
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
        fun generateFinalPrompt(): String {
            return applyReplacements(chooseReplacements())
        }

        private fun applyReplacements(replacements: Map<String, String>): String {
            // 1) 와일드카드 토큰 치환 → 2) 다이나믹 <A|B|…> 선택
            val afterWildcards = WildcardFileParser.TOKEN_REGEX.replace(basePrompt) { match ->
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
                    pickDynamicOption(inner, random)
                }
            }
        }

        private fun pickDynamicOption(inner: String, random: Random): String {
            var pipeCount = 0
            for (i in 0 until inner.length) {
                if (inner[i] == '|') pipeCount++
            }
            if (pipeCount == 0) return inner

            val chosenIndex = random.nextInt(pipeCount + 1)
            var currentOption = 0
            var startIndex = 0
            for (i in 0 until inner.length) {
                if (inner[i] == '|') {
                    if (currentOption == chosenIndex) {
                        return inner.substring(startIndex, i).trim()
                    }
                    currentOption++
                    startIndex = i + 1
                }
            }
            return inner.substring(startIndex).trim()
        }
    }
}
