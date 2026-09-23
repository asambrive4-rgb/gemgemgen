// 역할: 와일드카드 토큰 자동완성 제안 목록 추출을 검증합니다.
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

        // 짧은 이름 우선 → 장소(2) 다음 top(3)
        assertEquals(
            listOf(
                Candidate(name = "장소", token = "__장소__"),
                Candidate(name = "top", token = "__top__")
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
    fun suggestions_prefixMatchKoreanAndEnglish() {
        // 짧은 이름 우선 → 장면·장소(2) 후 장소명(3). 동길이면 이름 순(장면 < 장소).
        assertEquals(
            listOf("__장면__", "__장소__", "__장소명__"),
            WildcardTokenAutocomplete.suggestions(
                text = "장",
                cursor = 1,
                candidates = candidates
            )
        )
        assertEquals(
            listOf("__top__"),
            WildcardTokenAutocomplete.suggestions(
                text = "t",
                cursor = 1,
                candidates = candidates
            )
        )
    }

    @Test
    fun suggestions_isCaseInsensitiveForEnglish() {
        assertEquals(
            listOf("__top__"),
            WildcardTokenAutocomplete.suggestions(
                text = "T",
                cursor = 1,
                candidates = candidates
            )
        )
    }

    @Test
    fun suggestions_limitsToMaxThree() {
        val many = (1..10).map { i ->
            Candidate(name = "a$i", token = "__a${i}__")
        }
        val result = WildcardTokenAutocomplete.suggestions(
            text = "a",
            cursor = 1,
            candidates = many,
            maxCount = 3
        )
        assertEquals(3, result.size)
    }

    @Test
    fun suggestions_hidesWhenWordIsCompleteToken() {
        val text = "__장소__"
        assertTrue(
            WildcardTokenAutocomplete.suggestions(
                text = text,
                cursor = text.length,
                candidates = candidates
            ).isEmpty()
        )
    }

    @Test
    fun suggestions_usesWordAtCursorOnly() {
        // "배경은 장|" — 커서 앞 단어 "장"만 매칭
        val text = "배경은 장"
        assertEquals(
            listOf("__장면__", "__장소__", "__장소명__"),
            WildcardTokenAutocomplete.suggestions(
                text = text,
                cursor = text.length,
                candidates = candidates
            )
        )
    }

    @Test
    fun suggestions_emptyWhenCursorOnWhitespace() {
        assertTrue(
            WildcardTokenAutocomplete.suggestions(
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

        // 짧은 이름 우선: 화풍(2) -> 고화질(3)
        assertEquals(2, result.size)
        assertEquals("화풍", result[0].name)
        assertEquals("oil painting style", result[0].token)
        assertEquals("화풍", result[0].displayText)
        assertEquals(Candidate.Type.SNIPPET, result[0].type)

        assertEquals("고화질", result[1].name)
        assertEquals("8k masterpiece, extremely detailed", result[1].token)
        assertEquals("고화질", result[1].displayText)
        assertEquals(Candidate.Type.SNIPPET, result[1].type)
    }

    @Test
    fun suggestCandidates_combinesWildcardsAndSnippetsCorrectly() {
        val combinedCandidates = listOf(
            Candidate(name = "장소", token = "__장소__", displayText = "__장소__", type = Candidate.Type.WILDCARD),
            Candidate(name = "장면", token = "__장면__", displayText = "__장면__", type = Candidate.Type.WILDCARD),
            Candidate(name = "장편스토리", token = "A long story about...", displayText = "장편스토리", type = Candidate.Type.SNIPPET)
        )

        val suggestions = WildcardTokenAutocomplete.suggestCandidates(
            text = "장",
            cursor = 1,
            candidates = combinedCandidates
        )

        assertEquals(3, suggestions.size)
        // 짧은 순: 장면, 장소 (길이 2) -> 장편스토리 (길이 5)
        assertEquals("장면", suggestions[0].name)
        assertEquals(Candidate.Type.WILDCARD, suggestions[0].type)

        assertEquals("장소", suggestions[1].name)
        assertEquals(Candidate.Type.WILDCARD, suggestions[1].type)

        assertEquals("장편스토리", suggestions[2].name)
        assertEquals("장편스토리", suggestions[2].displayText)
        assertEquals("A long story about...", suggestions[2].token)
        assertEquals(Candidate.Type.SNIPPET, suggestions[2].type)
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
}
