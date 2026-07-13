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
}
