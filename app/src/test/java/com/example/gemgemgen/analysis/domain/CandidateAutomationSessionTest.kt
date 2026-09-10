package com.example.gemgemgen.analysis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateAutomationSessionTest {

    private fun createSegment(
        text: String = "사과",
        startIndex: Int = 3,
        endIndex: Int = 5,
        source: AnalysisTargetSource = AnalysisTargetSource.AUTO,
        category: AnalysisCategory = AnalysisCategory.LOCATION,
        confidence: Double = 0.95,
        reason: String = "단어 감지"
    ): AnalysisTargetSegment {
        return AnalysisTargetSegment(
            text = text,
            startIndex = startIndex,
            endIndex = endIndex,
            source = source,
            category = category,
            confidence = confidence,
            reason = reason
        )
    }

    @Test
    fun `원본 소스와 마스킹 구간의 텍스트 및 인덱스가 일치하면 matches는 true를 반환한다`() {
        val segment = createSegment(text = "사과", startIndex = 3, endIndex = 5)
        val session = CandidateAutomationSession(
            originalSource = "맛있는 사과를 먹는다",
            targetSegment = segment,
            appliedCandidate = "바나나",
            automationSegmentStartIndex = 3
        )

        val matchingSegment = createSegment(text = "사과", startIndex = 3, endIndex = 5)
        assertTrue(session.matches("맛있는 사과를 먹는다", matchingSegment))
    }

    @Test
    fun `원본 소스가 다르면 matches는 false를 반환한다`() {
        val segment = createSegment(text = "사과", startIndex = 3, endIndex = 5)
        val session = CandidateAutomationSession(
            originalSource = "맛있는 사과를 먹는다",
            targetSegment = segment,
            appliedCandidate = "바나나",
            automationSegmentStartIndex = 3
        )

        val matchingSegment = createSegment(text = "사과", startIndex = 3, endIndex = 5)
        assertFalse(session.matches("빨간 사과를 먹는다", matchingSegment))
    }

    @Test
    fun `구간 텍스트가 다르면 matches는 false를 반환한다`() {
        val segment = createSegment(text = "사과", startIndex = 3, endIndex = 5)
        val session = CandidateAutomationSession(
            originalSource = "맛있는 사과를 먹는다",
            targetSegment = segment,
            appliedCandidate = "바나나",
            automationSegmentStartIndex = 3
        )

        val differentTextSegment = createSegment(text = "포도", startIndex = 3, endIndex = 5)
        assertFalse(session.matches("맛있는 사과를 먹는다", differentTextSegment))
    }

    @Test
    fun `구간 시작 인덱스가 다르면 matches는 false를 반환한다`() {
        val segment = createSegment(text = "사과", startIndex = 3, endIndex = 5)
        val session = CandidateAutomationSession(
            originalSource = "맛있는 사과를 먹는다",
            targetSegment = segment,
            appliedCandidate = "바나나",
            automationSegmentStartIndex = 3
        )

        val differentStartIndexSegment = createSegment(text = "사과", startIndex = 4, endIndex = 5)
        assertFalse(session.matches("맛있는 사과를 먹는다", differentStartIndexSegment))
    }

    @Test
    fun `구간 종료 인덱스가 다르면 matches는 false를 반환한다`() {
        val segment = createSegment(text = "사과", startIndex = 3, endIndex = 5)
        val session = CandidateAutomationSession(
            originalSource = "맛있는 사과를 먹는다",
            targetSegment = segment,
            appliedCandidate = "바나나",
            automationSegmentStartIndex = 3
        )

        val differentEndIndexSegment = createSegment(text = "사과", startIndex = 3, endIndex = 6)
        assertFalse(session.matches("맛있는 사과를 먹는다", differentEndIndexSegment))
    }

    @Test
    fun `구간 메타데이터가 달라도 소스 텍스트와 인덱스가 같으면 matches는 true다`() {
        val segment = createSegment(
            text = "사과",
            startIndex = 3,
            endIndex = 5,
            source = AnalysisTargetSource.AUTO,
            category = AnalysisCategory.LOCATION,
            confidence = 0.95,
            reason = "기본 감지"
        )
        val session = CandidateAutomationSession(
            originalSource = "맛있는 사과를 먹는다",
            targetSegment = segment,
            appliedCandidate = "바나나",
            automationSegmentStartIndex = 3
        )

        val differentMetadataSegment = createSegment(
            text = "사과",
            startIndex = 3,
            endIndex = 5,
            source = AnalysisTargetSource.MANUAL,
            category = AnalysisCategory.WOMEN_CLOTHING,
            confidence = 0.5,
            reason = "사용자 직접 지정"
        )
        assertTrue(session.matches("맛있는 사과를 먹는다", differentMetadataSegment))
    }

    @Test
    fun `세션 객체는 적용 후보와 자동화 구간 시작 위치를 올바르게 보관한다`() {
        val segment = createSegment()
        val session = CandidateAutomationSession(
            originalSource = "원본 프롬프트",
            targetSegment = segment,
            appliedCandidate = "치환된 후보 텍스트",
            automationSegmentStartIndex = 10
        )

        assertEquals("원본 프롬프트", session.originalSource)
        assertEquals(segment, session.targetSegment)
        assertEquals("치환된 후보 텍스트", session.appliedCandidate)
        assertEquals(10, session.automationSegmentStartIndex)
    }
}
