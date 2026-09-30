// 역할: 프롬프트 전체 또는 특정 문단 단위 분석 대상 선정 규칙을 판정합니다.
package com.example.gemgemgen.analysis.domain

import com.example.gemgemgen.wildcard.domain.WildcardFileParser

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

    fun fromAutoReport(
        report: AnalysisReport,
        category: AnalysisCategory
    ): AnalysisTargetSegment? {
        val detected = report.targetSegment?.takeIf { it.isValid } ?: return null
        return AnalysisTargetSegment(
            text = detected.exactText,
            startIndex = detected.startIndex,
            endIndex = detected.endIndex,
            category = category,
            confidence = detected.confidence
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
