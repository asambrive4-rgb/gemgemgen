// 역할: 프롬프트 입력창에서 단어 입력 시 와일드카드 토큰 및 상용구 후보를 자동완성 추천하고 치환합니다.
package com.example.gemgemgen.automation.domain

import com.example.gemgemgen.wildcard.domain.WildcardFileParser

/**
 * 자동화 프롬프트 입력 중 와일드카드 토큰 및 상용구 자동 추천.
 * Compose / ViewModel 에 의존하지 않는 순수 규칙.
 */
object WildcardTokenAutocomplete {
    const val MAX_SUGGESTIONS = 3

    data class Candidate(
        /** 매칭 기준명 (확장자 제외 파일명 또는 단축어 별칭, 예: 장소, 고화질) */
        val name: String,
        /** 실제 치환될 토큰/문구 (예: __장소__, 8k masterpiece...) */
        val token: String,
        /** 추천 칩에 표시될 라벨 (예: __장소__, 📋 고화질) */
        val displayText: String = token,
        /** 후보 타입 (와일드카드 변주 토큰 vs 텍스트 대치 상용구) */
        val type: Type = Type.WILDCARD
    ) {
        enum class Type {
            WILDCARD,
            SNIPPET
        }
    }

    data class Replacement(
        val newText: String,
        val cursorAfter: Int
    )

    val CANDIDATE_COMPARATOR: Comparator<Candidate> = compareBy<Candidate> { it.name.length }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

    /** 파일명 목록 → 와일드카드 추천 후보 (이름 오름차순, 토큰 중복 제거). */
    fun candidatesFromFileNames(fileNames: Iterable<String>): List<Candidate> {
        return fileNames
            .mapNotNull { fileName ->
                val token = WildcardFileParser.tokenFromFileName(fileName) ?: return@mapNotNull null
                val name = token.removePrefix("__").removeSuffix("__")
                if (name.isEmpty()) return@mapNotNull null
                Candidate(name = name, token = token, displayText = token, type = Candidate.Type.WILDCARD)
            }
            .distinctBy { it.token.lowercase() }
            .sortedWith(CANDIDATE_COMPARATOR)
    }

    /** 상용구(스니펫) 목록 → 추천 후보 (단축어 오름차순, 단축어 중복 제거). */
    fun candidatesFromSnippets(snippets: Iterable<PromptSnippet>): List<Candidate> {
        return snippets
            .mapNotNull { snippet ->
                val trimmedShortcut = snippet.shortcut.trim()
                val trimmedContent = snippet.content.trim()
                if (trimmedShortcut.isEmpty() || trimmedContent.isEmpty()) return@mapNotNull null
                Candidate(
                    name = trimmedShortcut,
                    token = snippet.content,
                    displayText = trimmedShortcut,
                    type = Candidate.Type.SNIPPET
                )
            }
            .distinctBy { it.name.lowercase() }
            .sortedWith(CANDIDATE_COMPARATOR)
    }

    /**
     * 커서 기준 현재 단어에 대해 접두 매칭 후보를 최대 [maxCount]개 반환.
     * 완성 토큰(`__…__`)이거나 단어가 비어 있으면 빈 목록.
     */
    fun suggestCandidates(
        text: String,
        cursor: Int,
        candidates: List<Candidate>,
        maxCount: Int = MAX_SUGGESTIONS
    ): List<Candidate> {
        if (candidates.isEmpty() || maxCount <= 0 || text.isEmpty()) return emptyList()

        val range = wordRangeAt(text, cursor) ?: return emptyList()
        val word = text.substring(range.first, range.last + 1)
        if (word.isEmpty()) return emptyList()
        if (WildcardFileParser.COMPLETE_TOKEN_REGEX.matches(word)) return emptyList()

        return candidates
            .asSequence()
            .filter { it.name.startsWith(word, ignoreCase = true) }
            .sortedWith(CANDIDATE_COMPARATOR)
            .take(maxCount)
            .toList()
    }

    /**
     * 추천 칩 클릭 시 커서 위치의 단어를 해당 와일드카드 토큰 또는 상용구 문구로 치환합니다.
     */
    fun applyToken(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        candidate: Candidate,
        candidates: List<Candidate>,
        isParagraphSelectionMode: Boolean = false
    ): Replacement? {
        if (isParagraphSelectionMode) return null
        if (candidate.token.isBlank()) return null
        if (selectionStart != selectionEnd) return null // 드래그 선택 중에는 치환 불가
        if (candidates.none { it.token == candidate.token }) return null

        val range = wordRangeAt(text, selectionEnd) ?: return null
        val word = text.substring(range.first, range.last + 1)
        if (word.isEmpty()) return null

        val isMatchingPrefix = candidate.name.startsWith(word, ignoreCase = true) ||
                candidate.token.startsWith(word, ignoreCase = true) ||
                candidate.token.equals(word, ignoreCase = true) ||
                candidate.name.equals(word, ignoreCase = true)
        if (!isMatchingPrefix) return null

        val replacement = replaceWordAtCursor(
            text = text,
            cursor = selectionEnd,
            token = candidate.token
        ) ?: return null

        if (replacement.newText == text) return null

        return replacement
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
