package com.example.gemgemgen.analysis.domain

import com.example.gemgemgen.wildcard.domain.WildcardFileParser

sealed class ManualTargetSegmentResult {
    data class Success(val segment: AnalysisTargetSegment) : ManualTargetSegmentResult()
    data object EmptySelection : ManualTargetSegmentResult()
}

/**
 * Rules for creating, validating, and substituting analysis target segments.
 * No Android/Compose dependencies.
 */
object AnalysisTargetSegmentPolicy {
    fun isStillValid(source: String, segment: AnalysisTargetSegment): Boolean {
        if (segment.startIndex < 0 ||
            segment.endIndex > source.length ||
            segment.startIndex > segment.endIndex
        ) {
            return false
        }
        return source.substring(segment.startIndex, segment.endIndex) == segment.text
    }

    fun fromManual(
        source: String,
        start: Int,
        end: Int,
        category: AnalysisCategory
    ): ManualTargetSegmentResult {
        val safeStart = start.coerceIn(0, source.length)
        val safeEnd = end.coerceIn(safeStart, source.length)
        if (safeStart == safeEnd) {
            return ManualTargetSegmentResult.EmptySelection
        }
        val selectedText = source.substring(safeStart, safeEnd)
        return ManualTargetSegmentResult.Success(
            AnalysisTargetSegment(
                text = selectedText,
                startIndex = safeStart,
                endIndex = safeEnd,
                source = AnalysisTargetSource.MANUAL,
                category = category,
                confidence = 1.0,
                reason = "사용자가 직접 선택한 구간입니다."
            )
        )
    }

    fun fromAutoReport(
        report: AnalysisReport,
        category: AnalysisCategory
    ): AnalysisTargetSegment? {
        val detected = report.targetSegment?.takeIf { it.isValid } ?: return null
        return AnalysisTargetSegment(
            text = detected.exactText,
            startIndex = detected.startIndex,
            endIndex = detected.endIndex,
            source = AnalysisTargetSource.AUTO,
            category = category,
            confidence = detected.confidence,
            reason = detected.reason
        )
    }

    /**
     * Replaces [segment] in [source] with a wildcard token derived from [savedFileName]
     * (e.g. `hair.txt` → `__hair__`). If [segment] is null or token cannot be built,
     * returns [source] unchanged.
     */
    fun replaceSegmentWithWildcardToken(
        source: String,
        segment: AnalysisTargetSegment?,
        savedFileName: String
    ): String {
        if (segment == null) return source
        val token = WildcardFileParser.tokenFromFileName(savedFileName) ?: return source
        val start = segment.startIndex.coerceIn(0, source.length)
        val end = segment.endIndex.coerceIn(start, source.length)
        return source.replaceRange(start, end, token)
    }
}
