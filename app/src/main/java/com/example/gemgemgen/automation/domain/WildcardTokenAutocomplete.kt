package com.example.gemgemgen.automation.domain

import com.example.gemgemgen.wildcard.domain.WildcardFileParser

/**
 * 자동화 프롬프트 입력 중 와일드카드 토큰 자동 추천.
 * Compose / ViewModel 에 의존하지 않는 순수 규칙.
 */
object WildcardTokenAutocomplete {
    const val MAX_SUGGESTIONS = 3

    private val completeTokenRegex = Regex("^__[^\\s]+__$")

    data class Candidate(
        /** 확장자 제외 파일명 (예: 장소) */
        val name: String,
        /** 삽입 토큰 (예: __장소__) */
        val token: String
    )

    data class Replacement(
        val newText: String,
        val cursorAfter: Int
    )

    /** 파일명 목록 → 추천 후보 (이름 오름차순, 토큰 중복 제거). */
    fun candidatesFromFileNames(fileNames: Iterable<String>): List<Candidate> {
        return fileNames
            .mapNotNull { fileName ->
                val token = WildcardFileParser.tokenFromFileName(fileName) ?: return@mapNotNull null
                val name = token.removePrefix("__").removeSuffix("__")
                if (name.isEmpty()) return@mapNotNull null
                Candidate(name = name, token = token)
            }
            .distinctBy { it.token.lowercase() }
            .sortedWith(
                compareBy<Candidate> { it.name.length }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
    }

    /**
     * 커서 기준 현재 단어에 대해 접두 매칭 토큰을 최대 [maxCount]개 반환.
     * 완성 토큰(`__…__`)이거나 단어가 비어 있으면 빈 목록.
     */
    fun suggestions(
        text: String,
        cursor: Int,
        candidates: List<Candidate>,
        maxCount: Int = MAX_SUGGESTIONS
    ): List<String> {
        if (candidates.isEmpty() || maxCount <= 0 || text.isEmpty()) return emptyList()

        val range = wordRangeAt(text, cursor) ?: return emptyList()
        val word = text.substring(range.first, range.last + 1)
        if (word.isEmpty()) return emptyList()
        if (completeTokenRegex.matches(word)) return emptyList()

        return candidates
            .asSequence()
            .filter { it.name.startsWith(word, ignoreCase = true) }
            .sortedWith(
                compareBy<Candidate> { it.name.length }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            .map { it.token }
            .take(maxCount)
            .toList()
    }

    /**
     * 커서 위치의 단어를 [token]으로 교체.
     * 단어가 없으면 null.
     */
    fun replaceWordAtCursor(
        text: String,
        cursor: Int,
        token: String
    ): Replacement? {
        if (token.isEmpty()) return null
        val range = wordRangeAt(text, cursor) ?: return null
        val newText = text.replaceRange(range.first, range.last + 1, token)
        return Replacement(
            newText = newText,
            cursorAfter = range.first + token.length
        )
    }

    /**
     * 커서 기준 단어 범위 (끝 인덱스 포함).
     * 경계: 공백·줄바꿈 등 [Char.isWhitespace].
     */
    fun wordRangeAt(text: String, cursor: Int): IntRange? {
        if (text.isEmpty()) return null
        val pos = cursor.coerceIn(0, text.length)
        var start = pos
        while (start > 0 && !text[start - 1].isWhitespace()) {
            start--
        }
        var endExclusive = pos
        while (endExclusive < text.length && !text[endExclusive].isWhitespace()) {
            endExclusive++
        }
        if (start >= endExclusive) return null
        return start..endExclusive - 1
    }
}
