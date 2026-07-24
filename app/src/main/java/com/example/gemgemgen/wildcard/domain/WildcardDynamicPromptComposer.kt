package com.example.gemgemgen.wildcard.domain

/**
 * 와일드카드 편집 본문의 일부 줄을 다이나믹 프롬프트 `<A|B|…>` 로 조립한다.
 * UI/파일 I/O 에 의존하지 않는 순수 규칙.
 */
object WildcardDynamicPromptComposer {
    /** 편집 중 텍스트에서 선택 가능한 줄 (trim, 빈 줄 제외, 위→아래 순서). */
    fun selectableLines(editingText: String): List<String> {
        return WildcardFileParser.parseItems(editingText)
    }

    /**
     * [selectedIndices] 는 [allLines] 인덱스. 파일 순서를 유지하도록 정렬한 뒤 조립한다.
     */
    fun composeFromIndices(
        allLines: List<String>,
        selectedIndices: Set<Int>
    ): ComposeResult {
        val ordered = selectedIndices
            .asSequence()
            .filter { it in allLines.indices }
            .sorted()
            .map { allLines[it] }
            .toList()
        return compose(ordered)
    }

    fun compose(selectedLinesInOrder: List<String>): ComposeResult {
        if (selectedLinesInOrder.isEmpty()) {
            return ComposeResult.NoSelection
        }

        val invalid = selectedLinesInOrder.filter { lineContainsDynamicSyntaxChars(it) }
        if (invalid.isNotEmpty()) {
            return ComposeResult.InvalidCharacters(invalid)
        }

        return ComposeResult.Success(
            dynamicPrompt = "<${selectedLinesInOrder.joinToString("|")}>"
        )
    }

    fun lineContainsDynamicSyntaxChars(line: String): Boolean {
        return line.any { it == '|' || it == '<' || it == '>' }
    }

    sealed interface ComposeResult {
        data class Success(val dynamicPrompt: String) : ComposeResult
        data object NoSelection : ComposeResult
        data class InvalidCharacters(val lines: List<String>) : ComposeResult
    }
}
