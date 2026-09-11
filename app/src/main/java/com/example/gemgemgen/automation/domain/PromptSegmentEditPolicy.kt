// 역할: 프롬프트 내 특정 구문의 치환 및 삽입 편집 규칙을 처리합니다.
package com.example.gemgemgen.automation.domain

import kotlin.math.abs

data class PromptSegmentEdit(
    val updatedText: String,
    val startIndex: Int,
    val previousEndIndex: Int,
    val replacementEndIndex: Int
)

/** 자동화 프롬프트의 나머지 문장을 보존하면서 지정된 구간만 교체하는 규칙. */
object PromptSegmentEditPolicy {
    fun replace(
        currentText: String,
        expectedSegment: String,
        replacement: String,
        preferredStartIndex: Int
    ): PromptSegmentEdit? {
        if (expectedSegment.isEmpty()) return null

        val start = findSegmentStart(
            currentText = currentText,
            expectedSegment = expectedSegment,
            preferredStartIndex = preferredStartIndex
        ) ?: return null
        val previousEnd = start + expectedSegment.length

        return PromptSegmentEdit(
            updatedText = currentText.replaceRange(start, previousEnd, replacement),
            startIndex = start,
            previousEndIndex = previousEnd,
            replacementEndIndex = start + replacement.length
        )
    }

    private fun findSegmentStart(
        currentText: String,
        expectedSegment: String,
        preferredStartIndex: Int
    ): Int? {
        val preferredEnd = preferredStartIndex + expectedSegment.length
        if (preferredStartIndex >= 0 &&
            preferredEnd <= currentText.length &&
            currentText.regionMatches(
                thisOffset = preferredStartIndex,
                other = expectedSegment,
                otherOffset = 0,
                length = expectedSegment.length
            )
        ) {
            return preferredStartIndex
        }

        val matches = buildList {
            var start = currentText.indexOf(expectedSegment)
            while (start >= 0) {
                add(start)
                start = currentText.indexOf(expectedSegment, startIndex = start + 1)
            }
        }
        if (matches.isEmpty()) return null

        val nearestDistance = matches.minOf { abs(it - preferredStartIndex) }
        return matches.singleOrNull { abs(it - preferredStartIndex) == nearestDistance }
    }
}
