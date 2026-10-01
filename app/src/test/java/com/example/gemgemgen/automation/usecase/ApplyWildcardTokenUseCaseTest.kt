// 역할: 와일드카드 토큰 및 상용구 치환 규칙 동작과 안전성 조건을 검증합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ApplyWildcardTokenUseCaseTest {

    private fun useCase(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        candidate: Candidate,
        candidates: List<Candidate>,
        isParagraphSelectionMode: Boolean = false
    ) = WildcardTokenAutocomplete.applyToken(
        text = text,
        selectionStart = selectionStart,
        selectionEnd = selectionEnd,
        candidate = candidate,
        candidates = candidates,
        isParagraphSelectionMode = isParagraphSelectionMode
    )

    private val placeCandidate = Candidate(name = "장소", token = "__장소__")

    private val candidates = listOf(
        Candidate(name = "top", token = "__top__"),
        placeCandidate
    )

    @Test
    fun invoke_replacesWordAtCursorSuccessfully() {
        val text = "한적한 장"
        val result = useCase(
            text = text,
            selectionStart = text.length,
            selectionEnd = text.length,
            candidate = placeCandidate,
            candidates = candidates
        )

        assertNotNull(result)
        assertEquals("한적한 __장소__", result?.newText)
        assertEquals("한적한 __장소__".length, result?.cursorAfter)
    }

    @Test
    fun invoke_replacesWordInMiddleOfSentence() {
        val text = "배경은 장 이고"
        val cursor = text.indexOf('장') + 1
        val result = useCase(
            text = text,
            selectionStart = cursor,
            selectionEnd = cursor,
            candidate = placeCandidate,
            candidates = candidates
        )

        assertNotNull(result)
        assertEquals("배경은 __장소__ 이고", result?.newText)
        assertEquals("배경은 __장소__".length, result?.cursorAfter)
    }

    @Test
    fun invoke_returnsNullInParagraphSelectionMode() {
        val result = useCase(
            text = "장",
            selectionStart = 1,
            selectionEnd = 1,
            candidate = placeCandidate,
            candidates = candidates,
            isParagraphSelectionMode = true
        )
        assertNull(result)
    }

    @Test
    fun invoke_returnsNullWhenSelectionIsRange() {
        val result = useCase(
            text = "한적한 장소",
            selectionStart = 4,
            selectionEnd = 6,
            candidate = placeCandidate,
            candidates = candidates
        )
        assertNull(result)
    }

    @Test
    fun invoke_returnsNullWhenTokenNotInCandidates() {
        val result = useCase(
            text = "장",
            selectionStart = 1,
            selectionEnd = 1,
            candidate = Candidate(name = "미등록토큰", token = "__미등록토큰__"),
            candidates = candidates
        )
        assertNull(result)
    }

    @Test
    fun invoke_returnsNullWhenTokenIsBlank() {
        val result = useCase(
            text = "장",
            selectionStart = 1,
            selectionEnd = 1,
            candidate = Candidate(name = "장소", token = "   "),
            candidates = candidates
        )
        assertNull(result)
    }

    @Test
    fun invoke_returnsNullWhenCursorOnWhitespace() {
        val result = useCase(
            text = "한적한    ",
            selectionStart = 5,
            selectionEnd = 5,
            candidate = placeCandidate,
            candidates = candidates
        )
        assertNull(result)
    }

    @Test
    fun invoke_replacesSnippetCandidateSuccessfully() {
        val snippetCandidate = Candidate(
            name = "고화질",
            token = "8k masterpiece, extremely detailed",
            displayText = "📋 고화질",
            type = Candidate.Type.SNIPPET
        )
        val text = "프롬프트 시작 고화"
        val result = useCase(
            text = text,
            selectionStart = text.length,
            selectionEnd = text.length,
            candidate = snippetCandidate,
            candidates = candidates + snippetCandidate
        )

        assertNotNull(result)
        assertEquals("프롬프트 시작 8k masterpiece, extremely detailed", result?.newText)
        assertEquals("프롬프트 시작 8k masterpiece, extremely detailed".length, result?.cursorAfter)
    }

    @Test
    fun invoke_returnsNullWhenWordAtCursorDoesNotMatchCandidatePrefix() {
        val snippetCandidate = Candidate(
            name = "하체",
            token = "두꺼운 허벅지.",
            displayText = "📋 하체",
            type = Candidate.Type.SNIPPET
        )
        val text = "여성은 두툼하고 존재감이 뚜렷한 허벅지."
        val result = useCase(
            text = text,
            selectionStart = text.length,
            selectionEnd = text.length,
            candidate = snippetCandidate,
            candidates = listOf(snippetCandidate)
        )

        assertNull(result)
    }

    @Test
    fun invoke_replacesWhenWordIsExactShortcutMatch() {
        val snippetCandidate = Candidate(
            name = "하체",
            token = "두꺼운 허벅지.",
            displayText = "📋 하체",
            type = Candidate.Type.SNIPPET
        )
        val text = "여성은 두툼하고 존재감이 뚜렷한 하체"
        val result = useCase(
            text = text,
            selectionStart = text.length,
            selectionEnd = text.length,
            candidate = snippetCandidate,
            candidates = listOf(snippetCandidate)
        )

        assertNotNull(result)
        assertEquals("여성은 두툼하고 존재감이 뚜렷한 두꺼운 허벅지.", result?.newText)
    }
}
