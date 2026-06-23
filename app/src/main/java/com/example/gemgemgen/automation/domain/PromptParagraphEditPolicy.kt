package com.example.gemgemgen.automation.domain

data class PromptParagraphRange(
    val start: Int,
    val endExclusive: Int
) {
    init {
        require(start >= 0)
        require(endExclusive >= start)
    }
}

object PromptParagraphEditPolicy {
    fun findParagraph(
        text: String,
        offset: Int
    ): PromptParagraphRange? {
        if (offset !in 0..text.length) return null

        var lineStart = 0
        var index = 0
        while (index < text.length) {
            val separatorLength = separatorLengthAt(text, index)
            if (separatorLength == 0) {
                index += 1
                continue
            }

            val lineEnd = index
            if (offset in lineStart..lineEnd) {
                return nonBlankRange(text, lineStart, lineEnd)
            }
            if (offset in (lineEnd + 1) until (lineEnd + separatorLength)) {
                return null
            }

            lineStart = lineEnd + separatorLength
            index = lineStart
        }

        return if (offset in lineStart..text.length) {
            nonBlankRange(text, lineStart, text.length)
        } else {
            null
        }
    }

    fun replace(
        text: String,
        range: PromptParagraphRange,
        replacement: String
    ): String {
        require(range.endExclusive <= text.length)
        return text.replaceRange(range.start, range.endExclusive, replacement)
    }

    private fun nonBlankRange(
        text: String,
        start: Int,
        endExclusive: Int
    ): PromptParagraphRange? {
        return if (text.substring(start, endExclusive).isBlank()) {
            null
        } else {
            PromptParagraphRange(start, endExclusive)
        }
    }

    private fun separatorLengthAt(text: String, index: Int): Int {
        return when (text[index]) {
            '\n' -> 1
            '\r' -> if (text.getOrNull(index + 1) == '\n') 2 else 1
            else -> 0
        }
    }
}
