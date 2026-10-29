// 역할: 프롬프트 입력창에서 단어 입력 시 상용구·연동 와일드카드 개별 항목·와일드카드 토큰 후보를 우선순위별로 자동완성 추천하고 치환합니다.
package com.example.gemgemgen.automation.domain

import com.example.gemgemgen.wildcard.domain.WildcardFileParser
import com.example.gemgemgen.wildcard.domain.WildcardSet

/**
 * 자동화 프롬프트 입력 중 와일드카드 토큰 및 상용구 자동 추천.
 * Compose / ViewModel 에 의존하지 않는 순수 규칙.
 */
object WildcardTokenAutocomplete {
    const val MAX_SUGGESTIONS = Int.MAX_VALUE

    data class Candidate(
        /** 매칭 기준명 (확장자 제외 파일명 또는 단축어 별칭, 예: 장소, 고화질, 포니테일) */
        val name: String,
        /** 실제 치환될 토큰/문구 (예: __장소__, 8k masterpiece..., 포니테일) */
        val token: String,
        /** 추천 칩에 표시될 라벨 (예: __장소__, 고화질, 포니테일) */
        val displayText: String = token,
        /** 상용구 카테고리 또는 출처 와일드카드 파일명 (선택 사항, 미분류 시 빈 문자열) */
        val category: String = "",
        /** 후보 타입 (와일드카드 변주 토큰 vs 상용구 vs 와일드카드 개별 항목) */
        val type: Type = Type.WILDCARD
    ) {
        enum class Type {
            WILDCARD,
            SNIPPET,
            WILDCARD_ITEM
        }
    }

    data class Replacement(
        val newText: String,
        val cursorAfter: Int
    )

    /**
     * 자동완성 후보 기본 정렬 비교자.
     * 1순위: 상용구(SNIPPET) 우선
     * 2순위: 와일드카드 개별 항목(WILDCARD_ITEM)
     * 3순위: 와일드카드 토큰(WILDCARD) 후순위
     * 각 그룹 내에서는 단축어/항목명/토큰명 가나다(대소문자 무시 사전) 순
     */
    val CANDIDATE_COMPARATOR: Comparator<Candidate> = compareBy<Candidate> {
        when (it.type) {
            Candidate.Type.SNIPPET -> 0
            Candidate.Type.WILDCARD_ITEM -> 1
            Candidate.Type.WILDCARD -> 2
        }
    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

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
                val trimmedCategory = snippet.category.trim()
                if (trimmedShortcut.isEmpty() || trimmedContent.isEmpty()) return@mapNotNull null
                Candidate(
                    name = trimmedShortcut,
                    token = snippet.content,
                    displayText = trimmedShortcut,
                    category = trimmedCategory,
                    type = Candidate.Type.SNIPPET
                )
            }
            .distinctBy { it.name.lowercase() }
            .sortedWith(CANDIDATE_COMPARATOR)
    }

    /** 연동된 와일드카드 파일 세트 목록 → 개별 항목(단어) 추천 후보 (단어 오름차순, 중복 제거). */
    fun candidatesFromWildcardSets(wildcardSets: Iterable<WildcardSet>): List<Candidate> {
        return wildcardSets
            .flatMap { set ->
                val category = set.fileName.removeSuffix(".txt")
                set.items.mapNotNull { item ->
                    val trimmed = item.trim()
                    if (trimmed.isEmpty()) return@mapNotNull null
                    Candidate(
                        name = trimmed,
                        token = trimmed,
                        displayText = trimmed,
                        category = category,
                        type = Candidate.Type.WILDCARD_ITEM
                    )
                }
            }
            .distinctBy { "${it.category}:${it.name.lowercase()}" }
            .sortedWith(CANDIDATE_COMPARATOR)
    }

    /**
     * 커서 기준 현재 단어에 대해 접두 매칭 후보를 반환.
     * 우선순위:
     * 1순위: 단축어 이름이 접두사 일치하는 상용구 (단축어 가나다순)
     * 2순위: 카테고리명이 접두사 일치하는 상용구 (카테고리 가나다순 -> 단축어 가나다순)
     * 3순위: 내용 단어가 접두사 일치하는 와일드카드 개별 항목 (단어 가나다순)
     * 4순위: 토큰명이 접두사 일치하는 와일드카드 (이름 가나다순)
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

        val matchPriorityComparator = compareBy<Candidate> { candidate ->
            when (candidate.type) {
                Candidate.Type.SNIPPET -> {
                    if (candidate.name.startsWith(word, ignoreCase = true)) 0 else 1
                }
                Candidate.Type.WILDCARD_ITEM -> 2
                Candidate.Type.WILDCARD -> 3
            }
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { candidate ->
            if (candidate.type == Candidate.Type.SNIPPET &&
                !candidate.name.startsWith(word, ignoreCase = true)
            ) {
                candidate.category
            } else {
                ""
            }
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

        return candidates
            .asSequence()
            .filter { candidate ->
                when (candidate.type) {
                    Candidate.Type.SNIPPET -> {
                        candidate.name.startsWith(word, ignoreCase = true) ||
                            (candidate.category.isNotBlank() &&
                                candidate.category.startsWith(word, ignoreCase = true))
                    }
                    Candidate.Type.WILDCARD_ITEM -> {
                        candidate.name.startsWith(word, ignoreCase = true)
                    }
                    Candidate.Type.WILDCARD -> {
                        candidate.name.startsWith(word, ignoreCase = true)
                    }
                }
            }
            .sortedWith(matchPriorityComparator)
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

        val isCategoryPrefix = candidate.type == Candidate.Type.SNIPPET &&
            candidate.category.isNotBlank() &&
            (candidate.category.startsWith(word, ignoreCase = true) ||
                candidate.category.equals(word, ignoreCase = true))

        val isMatchingPrefix = candidate.name.startsWith(word, ignoreCase = true) ||
            candidate.token.startsWith(word, ignoreCase = true) ||
            candidate.token.equals(word, ignoreCase = true) ||
            candidate.name.equals(word, ignoreCase = true) ||
            isCategoryPrefix
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

