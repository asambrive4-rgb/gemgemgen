// 역할: 분석 결과 텍스트의 세그먼트 벾위와 수정 편집 규칙을 관리합니다.
package com.example.gemgemgen.analysis.domain

data class AnalysisSourceRange(
    val startIndex: Int,
    val endIndex: Int,
    val exactText: String,
    val occurrence: Int? = null
)
data class AnalysisTextEdit(val range: AnalysisSourceRange, val replacement: String)

/** 떨어진 편집을 검증하고 사이의 원문을 그대로 보존해 기존 치환 후보로 조립한다. */
object AnalysisEditPolicy {
    fun validateRange(source: String, range: AnalysisSourceRange) {
        require(range.startIndex >= 0 && range.endIndex >= range.startIndex &&
            range.endIndex <= source.length &&
            source.substring(range.startIndex, range.endIndex) == range.exactText
        ) { "편집 위치가 원문과 일치하지 않습니다. 다시 분석해 주세요." }
    }

    fun envelope(source: String, target: AnalysisTargetSegment, report: AnalysisReport): AnalysisTargetSegment {
        val ranges = target.editableRanges.ifEmpty {
            listOf(AnalysisSourceRange(target.startIndex, target.endIndex, target.text))
        } +
            report.cascadingTrace.conflictingSegments
        ranges.forEach { validateRange(source, it) }
        report.preservedSegments.forEach { validateRange(source, it) }
        val start = ranges.minOf { it.startIndex }
        val end = ranges.maxOf { it.endIndex }
        if (target.category != AnalysisCategory.FREE_EDIT) {
            require(start == target.startIndex && end == target.endIndex) {
                "선택 구간 밖에도 함께 수정할 문장이 있습니다. '분석 수정' 카테고리로 생성해 주세요."
            }
            return target
        }
        val merged = mutableListOf<AnalysisSourceRange>()
        ranges.sortedBy { it.startIndex }.forEach { range ->
            val previous = merged.lastOrNull()
            if (previous != null && range.startIndex <= previous.endIndex) {
                val mergedEnd = maxOf(previous.endIndex, range.endIndex)
                merged[merged.lastIndex] = AnalysisSourceRange(previous.startIndex, mergedEnd,
                    source.substring(previous.startIndex, mergedEnd))
            } else {
                merged += range
            }
        }
        return target.copy(
            text = source.substring(start, end), startIndex = start, endIndex = end,
            editableRanges = merged
        )
    }

    fun assemble(
        source: String,
        target: AnalysisTargetSegment,
        edits: List<AnalysisTextEdit>,
        report: AnalysisReport
    ): String {
        require(edits.isNotEmpty()) { "편집 목록이 비어 있습니다." }
        validateRange(source, AnalysisSourceRange(target.startIndex, target.endIndex, target.text))
        val ordered = edits.map { it.copy(range = AnalysisSourceLocator.resolve(source, it.range)) }
            .distinct().sortedBy { it.range.startIndex }
        var cursor = target.startIndex
        var previousStart = -1
        val result = StringBuilder()
        ordered.forEach { edit ->
            val range = edit.range
            validateRange(source, range)
            require(range.startIndex >= cursor && range.startIndex != previousStart && range.endIndex <= target.endIndex) {
                "편집 구간이 겹치거나 분석 범위를 벗어났습니다. 다시 생성해 주세요."
            }
            require(report.preservedSegments.none { protected ->
                validateRange(source, protected)
                overlaps(range, protected)
            }) { "보존해야 할 원문 구간을 수정하는 후보입니다. 다시 생성해 주세요." }
            result.append(source, cursor, range.startIndex).append(edit.replacement)
            cursor = range.endIndex
            previousStart = range.startIndex
        }
        return result.append(source, cursor, target.endIndex).toString().also {
            require(it.isNotBlank()) { "편집 결과가 비어 있습니다." }
        }
    }

    private fun overlaps(edit: AnalysisSourceRange, protected: AnalysisSourceRange): Boolean =
        if (edit.startIndex == edit.endIndex) {
            edit.startIndex > protected.startIndex && edit.startIndex < protected.endIndex
        } else {
            edit.startIndex < protected.endIndex && edit.endIndex > protected.startIndex
        }
}
