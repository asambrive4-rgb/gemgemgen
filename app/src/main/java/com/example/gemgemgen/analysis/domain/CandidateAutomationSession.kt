// 역할: 분석 결과에서 선택된 후보 프롬프트의 자동화 실행 세션 데이터를 관리합니다.
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
