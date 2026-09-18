// 역할: 와일드카드 자동완성 추천 토큰 추출 유스케이스 동작을 검증합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveWildcardAutocompleteUseCaseTest {

    private val useCase = ResolveWildcardAutocompleteUseCase()

    private val candidates = listOf(
        Candidate(name = "top", token = "__top__"),
        Candidate(name = "장소", token = "__장소__"),
        Candidate(name = "장소명", token = "__장소명__"),
        Candidate(name = "장면", token = "__장면__")
    )

    @Test
    fun invoke_returnsMatchingSuggestionsAtCursor() {
        val text = "배경은 장"
        val result = useCase(
            text = text,
            selectionStart = text.length,
            selectionEnd = text.length,
            candidates = candidates
        )
        // 짧은 이름 우선 정렬: 장면, 장소 (길이 2) -> 장소명 (길이 3)
        assertEquals(listOf("__장면__", "__장소__", "__장소명__"), result)
    }

    @Test
    fun invoke_hidesSuggestionsWhenSelectionIsRange() {
        // 드래그 선택 중 (selectionStart != selectionEnd)
        val result = useCase(
            text = "배경은 장소",
            selectionStart = 4,
            selectionEnd = 6,
            candidates = candidates
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun invoke_hidesSuggestionsWhenParagraphSelectionModeActive() {
        val result = useCase(
            text = "장",
            selectionStart = 1,
            selectionEnd = 1,
            candidates = candidates,
            isParagraphSelectionMode = true
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun invoke_hidesSuggestionsWhenDisabled() {
        val result = useCase(
            text = "장",
            selectionStart = 1,
            selectionEnd = 1,
            candidates = candidates,
            isEnabled = false
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun invoke_returnsEmptyWhenCandidatesEmptyOrTextEmpty() {
        assertTrue(
            useCase(
                text = "",
                selectionStart = 0,
                selectionEnd = 0,
                candidates = candidates
            ).isEmpty()
        )
        assertTrue(
            useCase(
                text = "장",
                selectionStart = 1,
                selectionEnd = 1,
                candidates = emptyList()
            ).isEmpty()
        )
    }

    @Test
    fun invoke_limitsSuggestionsToMaxCount() {
        val manyCandidates = (1..10).map { i ->
            Candidate(name = "a$i", token = "__a${i}__")
        }
        val result = useCase(
            text = "a",
            selectionStart = 1,
            selectionEnd = 1,
            candidates = manyCandidates,
            maxCount = 2
        )
        assertEquals(2, result.size)
    }
}
