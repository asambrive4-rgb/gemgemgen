// 역할: 프롬프트 상용구 본문 검색(대소문자 무시, 스니펫 생성) 및 일괄 치환 유스케이스의 단위 테스트를 제공합니다.
package com.example.gemgemgen.automation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PromptSnippetTextUseCaseTest {

    private lateinit var useCase: PromptSnippetTextUseCase

    @Before
    fun setUp() {
        useCase = PromptSnippetTextUseCase()
    }

    @Test
    fun `findMatches finds snippets case-insensitively and counts occurrences`() {
        val snippets = listOf(
            PromptSnippet(id = "1", shortcut = "퀄리티", content = "Masterpiece, ultra high quality, MASTERPIECE lighting"),
            PromptSnippet(id = "2", shortcut = "스타일", content = "anime style, vibrant colors"),
            PromptSnippet(id = "3", shortcut = "디테일", content = "super masterpiece finish")
        )

        val results = useCase.findMatches(snippets, "masterpiece")

        assertEquals(2, results.size)
        assertEquals("1", results[0].snippet.id)
        assertEquals(2, results[0].occurrenceCount)
        assertEquals("3", results[1].snippet.id)
        assertEquals(1, results[1].occurrenceCount)
    }

    @Test
    fun `findMatches returns empty list when search text is blank or not found`() {
        val snippets = listOf(
            PromptSnippet(id = "1", shortcut = "퀄리티", content = "high quality")
        )

        assertTrue(useCase.findMatches(snippets, "").isEmpty())
        assertTrue(useCase.findMatches(snippets, "   ").isEmpty())
        assertTrue(useCase.findMatches(snippets, "unknown_word").isEmpty())
    }

    @Test
    fun `replaceContent replaces text case-insensitively across all matching snippets`() {
        val snippets = listOf(
            PromptSnippet(id = "1", shortcut = "퀄리티", content = "Masterpiece, ultra high quality, MASTERPIECE lighting"),
            PromptSnippet(id = "2", shortcut = "스타일", content = "anime style, vibrant colors"),
            PromptSnippet(id = "3", shortcut = "디테일", content = "super masterpiece finish")
        )

        val (updated, changedCount) = useCase.replaceContent(
            snippets = snippets,
            searchText = "masterpiece",
            replacementText = "best quality"
        )

        assertEquals(2, changedCount)
        assertEquals("best quality, ultra high quality, best quality lighting", updated[0].content)
        assertEquals("anime style, vibrant colors", updated[1].content)
        assertEquals("super best quality finish", updated[2].content)
    }

    @Test
    fun `replaceContent deletes matching text when replacement text is empty`() {
        val snippets = listOf(
            PromptSnippet(id = "1", shortcut = "태그", content = "masterpiece, 8k, best quality")
        )

        val (updated, changedCount) = useCase.replaceContent(
            snippets = snippets,
            searchText = ", 8k",
            replacementText = ""
        )

        assertEquals(1, changedCount)
        assertEquals("masterpiece, best quality", updated[0].content)
    }

    @Test
    fun `replaceContent returns original snippets and zero count when search text is same as replacement`() {
        val snippets = listOf(
            PromptSnippet(id = "1", shortcut = "태그", content = "masterpiece")
        )

        val (updated, changedCount) = useCase.replaceContent(
            snippets = snippets,
            searchText = "masterpiece",
            replacementText = "masterpiece"
        )

        assertEquals(0, changedCount)
        assertEquals("masterpiece", updated[0].content)
    }
}
