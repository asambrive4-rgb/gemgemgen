// 역할: 프롬프트 상용구 본문에서 특정 텍스트를 대소문자 구분 없이 검색하고 미리보기 스니펫 생성 및 일괄 치환하는 유스케이스입니다.
package com.example.gemgemgen.automation.domain

/**
 * 상용구 본문 검색 매칭 결과 모델.
 *
 * @property snippet 일치한 원본 상용구
 * @property occurrenceCount 본문 내 검색어 등장 횟수
 * @property preview 검색어 주변 문맥을 포함한 미리보기 문자열
 */
data class PromptSnippetMatch(
    val snippet: PromptSnippet,
    val occurrenceCount: Int,
    val preview: String
)

class PromptSnippetTextUseCase {

    /**
     * 상용구 목록 중 본문에 [searchText]가 포함된 항목을 대소문자 구분 없이 검색합니다.
     */
    fun findMatches(snippets: List<PromptSnippet>, searchText: String): List<PromptSnippetMatch> {
        if (searchText.isEmpty()) return emptyList()

        return snippets.mapNotNull { snippet ->
            val count = countOccurrences(snippet.content, searchText)
            if (count > 0) {
                PromptSnippetMatch(
                    snippet = snippet,
                    occurrenceCount = count,
                    preview = generateSnippetPreview(snippet.content, searchText)
                )
            } else {
                null
            }
        }
    }

    /**
     * 상용구 목록의 본문에서 [searchText]를 찾아 [replacementText]로 대소문자 구분 없이 일괄 치환합니다.
     *
     * @return 치환이 반영된 전체 상용구 리스트와 실제로 내용이 변경된 상용구 개수
     */
    fun replaceContent(
        snippets: List<PromptSnippet>,
        searchText: String,
        replacementText: String
    ): Pair<List<PromptSnippet>, Int> {
        if (searchText.isEmpty() || searchText == replacementText) {
            return Pair(snippets, 0)
        }

        var changedCount = 0
        val updatedList = snippets.map { snippet ->
            val originalContent = snippet.content
            if (originalContent.contains(searchText, ignoreCase = true)) {
                val newContent = originalContent.replace(searchText, replacementText, ignoreCase = true)
                if (newContent != originalContent) {
                    changedCount++
                    snippet.copy(content = newContent)
                } else {
                    snippet
                }
            } else {
                snippet
            }
        }

        return Pair(updatedList, changedCount)
    }

    private fun countOccurrences(body: String, searchText: String): Int {
        if (searchText.isEmpty()) return 0
        var count = 0
        var index = 0
        while (index < body.length) {
            val nextIndex = body.indexOf(searchText, index, ignoreCase = true)
            if (nextIndex < 0) break
            count++
            index = nextIndex + searchText.length
        }
        return count
    }

    private fun generateSnippetPreview(body: String, searchText: String): String {
        if (searchText.isEmpty() || body.length <= MAX_SNIPPET_LENGTH) return body
        val matchStart = body.indexOf(searchText, ignoreCase = true)
        if (matchStart < 0) return body.take(MAX_SNIPPET_LENGTH)

        val halfLength = (MAX_SNIPPET_LENGTH - searchText.length).coerceAtLeast(0) / 2
        var start = (matchStart - halfLength).coerceAtLeast(0)
        var end = (start + MAX_SNIPPET_LENGTH).coerceAtMost(body.length)

        if (end == body.length) {
            start = (body.length - MAX_SNIPPET_LENGTH).coerceAtLeast(0)
        }

        val hasLeading = start > 0
        val hasTrailing = end < body.length

        return buildString {
            if (hasLeading) append('…')
            append(body.substring(start, end))
            if (hasTrailing) append('…')
        }
    }

    companion object {
        private const val MAX_SNIPPET_LENGTH = 160
    }
}
