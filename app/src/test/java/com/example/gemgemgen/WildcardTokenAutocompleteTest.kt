// 역할: 와일드카드 토큰·상용구·연동 와일드카드 개별 항목 자동완성 제안 목록 추출, 3단계 우선순위 정렬 및 텍스트 치환을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardTokenAutocompleteTest {

    private val candidates = listOf(
        Candidate(name = "top", token = "__top__"),
        Candidate(name = "장소", token = "__장소__"),
        Candidate(name = "장소명", token = "__장소명__"),
        Candidate(name = "장면", token = "__장면__")
    )

    @Test
    fun candidatesFromFileNames_mapsTxtFilesToTokens() {
        val result = WildcardTokenAutocomplete.candidatesFromFileNames(
            listOf("장소.txt", "top.TXT", "readme.md", "  .txt")
        )

        // 가나다(알파벳) 사전순: top 다음 장소
        assertEquals(
            listOf(
                Candidate(name = "top", token = "__top__"),
                Candidate(name = "장소", token = "__장소__")
            ),
            result
        )
    }

    @Test
    fun candidatesFromFileNames_removesWhitespaceInToken() {
        val result = WildcardTokenAutocomplete.candidatesFromFileNames(
            listOf("여성 의상.txt")
        )
        assertEquals(
            listOf(Candidate(name = "여성의상", token = "__여성의상__")),
            result
        )
    }

    @Test
    fun suggestCandidates_prefixMatchKoreanAndEnglish() {
        // 가나다 사전순: 장면 < 장소 < 장소명
        assertEquals(
            listOf("__장면__", "__장소__", "__장소명__"),
            WildcardTokenAutocomplete.suggestCandidates(
                text = "장",
                cursor = 1,
                candidates = candidates
            ).map { it.token }
        )
        assertEquals(
            listOf("__top__"),
            WildcardTokenAutocomplete.suggestCandidates(
                text = "t",
                cursor = 1,
                candidates = candidates
            ).map { it.token }
        )
    }

    @Test
    fun suggestCandidates_isCaseInsensitiveForEnglish() {
        assertEquals(
            listOf("__top__"),
            WildcardTokenAutocomplete.suggestCandidates(
                text = "T",
                cursor = 1,
                candidates = candidates
            ).map { it.token }
        )
    }

    @Test
    fun suggestCandidates_returnsAllMatchingCandidatesWithoutLimitByDefault() {
        val many = (1..10).map { i ->
            Candidate(name = "a$i", token = "__a${i}__")
        }
        val result = WildcardTokenAutocomplete.suggestCandidates(
            text = "a",
            cursor = 1,
            candidates = many
        )
        assertEquals(10, result.size)
    }

    @Test
    fun suggestCandidates_respectsExplicitMaxCount() {
        val many = (1..10).map { i ->
            Candidate(name = "a$i", token = "__a${i}__")
        }
        val result = WildcardTokenAutocomplete.suggestCandidates(
            text = "a",
            cursor = 1,
            candidates = many,
            maxCount = 3
        )
        assertEquals(3, result.size)
    }

    @Test
    fun suggestCandidates_hidesWhenWordIsCompleteToken() {
        val text = "__장소__"
        assertTrue(
            WildcardTokenAutocomplete.suggestCandidates(
                text = text,
                cursor = text.length,
                candidates = candidates
            ).isEmpty()
        )
    }

    @Test
    fun suggestCandidates_usesWordAtCursorOnly() {
        // "배경은 장|" — 커서 앞 단어 "장"만 매칭
        val text = "배경은 장"
        assertEquals(
            listOf("__장면__", "__장소__", "__장소명__"),
            WildcardTokenAutocomplete.suggestCandidates(
                text = text,
                cursor = text.length,
                candidates = candidates
            ).map { it.token }
        )
    }

    @Test
    fun suggestCandidates_emptyWhenCursorOnWhitespace() {
        assertTrue(
            WildcardTokenAutocomplete.suggestCandidates(
                text = "한적한 ",
                cursor = 4,
                candidates = candidates
            ).isEmpty()
        )
    }

    @Test
    fun replaceWordAtCursor_replacesOnlyCurrentWord() {
        val text = "한적한 장소"
        val result = WildcardTokenAutocomplete.replaceWordAtCursor(
            text = text,
            cursor = text.length,
            token = "__장소__"
        )

        assertEquals("한적한 __장소__", result?.newText)
        assertEquals("한적한 __장소__".length, result?.cursorAfter)
    }

    @Test
    fun replaceWordAtCursor_middleOfSentence() {
        val text = "배경은 장 이고"
        // 커서 at end of "장" (index 5: "배경은 " is 0-3 chars... "배"=0 "경"=1 "은"=2 " "=3 "장"=4)
        val cursor = text.indexOf('장') + 1
        val result = WildcardTokenAutocomplete.replaceWordAtCursor(
            text = text,
            cursor = cursor,
            token = "__장소__"
        )

        assertEquals("배경은 __장소__ 이고", result?.newText)
        assertEquals("배경은 __장소__".length, result?.cursorAfter)
    }

    @Test
    fun replaceWordAtCursor_returnsNullWithoutWord() {
        assertNull(
            WildcardTokenAutocomplete.replaceWordAtCursor(
                text = "   ",
                cursor = 1,
                token = "__장소__"
            )
        )
    }

    @Test
    fun wordRangeAt_splitsOnWhitespace() {
        val text = "한적한 장소"
        val range = WildcardTokenAutocomplete.wordRangeAt(text, text.length)
        assertEquals(4, range?.first)
        assertEquals(text.length - 1, range?.last)
        assertEquals("장소", text.substring(range!!.first, range.last + 1))
    }

    @Test
    fun candidatesFromSnippets_mapsShortcutsToSnippetCandidates() {
        val snippets = listOf(
            com.example.gemgemgen.automation.domain.PromptSnippet(
                shortcut = "고화질",
                content = "8k masterpiece, extremely detailed"
            ),
            com.example.gemgemgen.automation.domain.PromptSnippet(
                shortcut = "화풍",
                content = "oil painting style"
            ),
            com.example.gemgemgen.automation.domain.PromptSnippet(
                shortcut = "  ",
                content = "empty shortcut"
            )
        )

        val result = WildcardTokenAutocomplete.candidatesFromSnippets(snippets)

        // 가나다 순: 고화질 -> 화풍
        assertEquals(2, result.size)
        assertEquals("고화질", result[0].name)
        assertEquals("8k masterpiece, extremely detailed", result[0].token)
        assertEquals("고화질", result[0].displayText)
        assertEquals(Candidate.Type.SNIPPET, result[0].type)

        assertEquals("화풍", result[1].name)
        assertEquals("oil painting style", result[1].token)
        assertEquals("화풍", result[1].displayText)
        assertEquals(Candidate.Type.SNIPPET, result[1].type)
    }

    @Test
    fun suggestCandidates_combinesWildcardsAndSnippetsCorrectly() {
        val combinedCandidates = listOf(
            Candidate(name = "장소", token = "__장소__", displayText = "__장소__", type = Candidate.Type.WILDCARD),
            Candidate(name = "장면", token = "__장면__", displayText = "__장면__", type = Candidate.Type.WILDCARD),
            Candidate(name = "장편스토리", token = "A long story about...", displayText = "장편스토리", type = Candidate.Type.SNIPPET),
            Candidate(name = "장미", token = "red rose flower", displayText = "장미", type = Candidate.Type.SNIPPET)
        )

        val suggestions = WildcardTokenAutocomplete.suggestCandidates(
            text = "장",
            cursor = 1,
            candidates = combinedCandidates
        )

        assertEquals(4, suggestions.size)
        // 1순위 상용구 가나다순: 장미 -> 장편스토리
        // 2순위 와일드카드 가나다순: 장면 -> 장소
        assertEquals("장미", suggestions[0].name)
        assertEquals(Candidate.Type.SNIPPET, suggestions[0].type)

        assertEquals("장편스토리", suggestions[1].name)
        assertEquals(Candidate.Type.SNIPPET, suggestions[1].type)

        assertEquals("장면", suggestions[2].name)
        assertEquals(Candidate.Type.WILDCARD, suggestions[2].type)

        assertEquals("장소", suggestions[3].name)
        assertEquals(Candidate.Type.WILDCARD, suggestions[3].type)
    }

    @Test
    fun replaceWordAtCursor_replacesSnippetWithLongContent() {
        val text = "프롬프트 시작 고화질"
        val snippetCandidate = Candidate(
            name = "고화질",
            token = "8k masterpiece, photorealistic, best quality",
            displayText = "고화질",
            type = Candidate.Type.SNIPPET
        )

        val replacement = WildcardTokenAutocomplete.replaceWordAtCursor(
            text = text,
            cursor = text.length,
            token = snippetCandidate.token
        )

        assertEquals("프롬프트 시작 8k masterpiece, photorealistic, best quality", replacement?.newText)
        assertEquals("프롬프트 시작 8k masterpiece, photorealistic, best quality".length, replacement?.cursorAfter)
    }

    @Test
    fun suggestCandidates_matchesCategoryPrefixAndPrioritizesDirectShortcutMatchFirst() {
        val snippets = listOf(
            com.example.gemgemgen.automation.domain.PromptSnippet(
                shortcut = "고화질",
                content = "8k masterpiece",
                category = "스타일"
            ),
            com.example.gemgemgen.automation.domain.PromptSnippet(
                shortcut = "시네마틱",
                content = "cinematic lighting",
                category = "스타일"
            ),
            com.example.gemgemgen.automation.domain.PromptSnippet(
                shortcut = "스타일링",
                content = "fashion styling",
                category = "의상"
            )
        )
        val wildcardCandidates = listOf(
            Candidate(name = "스타디움", token = "__스타디움__", displayText = "__스타디움__", type = Candidate.Type.WILDCARD)
        )
        val combined = wildcardCandidates + WildcardTokenAutocomplete.candidatesFromSnippets(snippets)

        // "스타" 입력 시:
        // 1순위: 단축어 직접 접두사 일치 ("스타일링")
        // 2순위: 카테고리 접두사 일치 ("[스타일] 고화질", "[스타일] 시네마틱")
        // 3순위: 와일드카드 접두사 일치 ("__스타디움__")
        val suggestions = WildcardTokenAutocomplete.suggestCandidates(
            text = "스타",
            cursor = 2,
            candidates = combined
        )

        assertEquals(
            listOf("스타일링", "고화질", "시네마틱", "스타디움"),
            suggestions.map { it.name }
        )
        assertEquals("의상", suggestions[0].category)
        assertEquals("스타일", suggestions[1].category)
        assertEquals("스타일", suggestions[2].category)
    }

    @Test
    fun candidatesFromWildcardSets_mapsItemsToWildcardItemCandidates() {
        val wildcardSets = listOf(
            com.example.gemgemgen.wildcard.domain.WildcardSet(
                token = "__헤어스타일__",
                fileName = "헤어스타일.txt",
                items = listOf("포니테일", "단발", "트윈테일", "  ")
            )
        )
        val result = WildcardTokenAutocomplete.candidatesFromWildcardSets(wildcardSets)

        assertEquals(3, result.size)
        assertEquals("단발", result[0].name)
        assertEquals("단발", result[0].token)
        assertEquals("헤어스타일", result[0].category)
        assertEquals(Candidate.Type.WILDCARD_ITEM, result[0].type)

        assertEquals("트윈테일", result[1].name)
        assertEquals("포니테일", result[2].name)
    }

    @Test
    fun suggestCandidates_prioritizesSnippetThenWildcardItemThenWildcard() {
        val snippet = Candidate(
            name = "포커스",
            token = "depth of field focus",
            displayText = "포커스",
            type = Candidate.Type.SNIPPET
        )
        val wildcardItem = Candidate(
            name = "포니테일",
            token = "포니테일",
            displayText = "포니테일",
            category = "헤어스타일",
            type = Candidate.Type.WILDCARD_ITEM
        )
        val wildcardToken = Candidate(
            name = "포즈",
            token = "__포즈__",
            displayText = "__포즈__",
            type = Candidate.Type.WILDCARD
        )

        val combined = listOf(wildcardToken, wildcardItem, snippet)
        val suggestions = WildcardTokenAutocomplete.suggestCandidates(
            text = "포",
            cursor = 1,
            candidates = combined
        )

        // 우선순위: 1순위 상용구(포커스) > 2순위 와일드카드 항목(포니테일) > 3순위 와일드카드 토큰(포즈)
        assertEquals(
            listOf("포커스", "포니테일", "포즈"),
            suggestions.map { it.name }
        )
        assertEquals(Candidate.Type.SNIPPET, suggestions[0].type)
        assertEquals(Candidate.Type.WILDCARD_ITEM, suggestions[1].type)
        assertEquals(Candidate.Type.WILDCARD, suggestions[2].type)
    }

    @Test
    fun suggestCandidates_wildcardItemOnlyMatchesWordPrefixNotCategory() {
        val hairItem = Candidate(
            name = "포니테일",
            token = "포니테일",
            displayText = "포니테일",
            category = "헤어스타일",
            type = Candidate.Type.WILDCARD_ITEM
        )
        val hairBand = Candidate(
            name = "헤어밴드",
            token = "헤어밴드",
            displayText = "헤어밴드",
            category = "헤어스타일",
            type = Candidate.Type.WILDCARD_ITEM
        )

        // "헤어" 입력 시: category 가 "헤어스타일"이어도 name 이 "헤어"로 시작하는 항목만 매칭됨 (방식 1)
        val suggestions = WildcardTokenAutocomplete.suggestCandidates(
            text = "헤어",
            cursor = 2,
            candidates = listOf(hairItem, hairBand)
        )

        assertEquals(listOf("헤어밴드"), suggestions.map { it.name })
    }

    @Test
    fun applyToken_replacesWithItemDirectlyForWildcardItem() {
        val hairItem = Candidate(
            name = "포니테일",
            token = "포니테일",
            displayText = "포니테일",
            category = "헤어스타일",
            type = Candidate.Type.WILDCARD_ITEM
        )

        val result = WildcardTokenAutocomplete.applyToken(
            text = "1girl, 포",
            selectionStart = 9,
            selectionEnd = 9,
            candidate = hairItem,
            candidates = listOf(hairItem)
        )

        org.junit.Assert.assertNotNull(result)
        assertEquals("1girl, 포니테일", result?.newText)
        assertEquals(11, result?.cursorAfter)
    }
}
