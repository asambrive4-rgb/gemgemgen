package com.example.gemgemgen.analysis.domain

data class CandidateAutomationSession(
    val originalSource: String,
    val targetSegment: AnalysisTargetSegment,
    val appliedCandidate: String,
    val automationSegmentStartIndex: Int
) {
    fun matches(source: String, segment: AnalysisTargetSegment): Boolean {
        return originalSource == source &&
            targetSegment.text == segment.text &&
            targetSegment.startIndex == segment.startIndex &&
            targetSegment.endIndex == segment.endIndex
    }
}
