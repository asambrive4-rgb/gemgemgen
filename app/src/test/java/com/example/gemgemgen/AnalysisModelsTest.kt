// 역할: 분석 모델의 기본 상수 설정, 4가지 변주 방향 지시문 및 완화된 카테고리 규칙을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisCategoryRules
import com.example.gemgemgen.analysis.domain.AnalysisDefaultDirections
import com.example.gemgemgen.analysis.domain.DEFAULT_ANALYSIS_MODEL
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_7_FLASH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisModelsTest {
    @Test
    fun `Gemini 기본 모델은 3점7 Flash다`() {
        assertEquals(MODEL_GEMINI_3_7_FLASH, DEFAULT_ANALYSIS_MODEL)
    }

    @Test
    fun `4가지 기본 변주 방향 지시문이 의상 예시와 의도에 맞게 정의되어 있다`() {
        val byId = AnalysisDefaultDirections.values.associateBy { it.id }

        val lengthOnly = byId.getValue("length-only")
        assertEquals("분량 추가", lengthOnly.title)
        assertTrue(lengthOnly.hint.contains("Do NOT invent new items"))
        assertTrue(lengthOnly.hint.contains("흰 크롭티, 청바지"))

        val detailAdd = byId.getValue("detail-add")
        assertEquals("디테일 추가", detailAdd.title)
        assertTrue(detailAdd.hint.contains("커다란 해바라기가 전면에 프린팅된 흰 크롭티"))
        assertFalse(detailAdd.hint.contains("keeping the fragment length close to the original"))

        val sameLineage = byId.getValue("same-lineage-other-kind")
        assertEquals("같은계열 다른 종류", sameLineage.title)
        assertTrue(sameLineage.hint.contains("베이지 크롭 나시, 데님 미니스커트"))

        val otherLineage = byId.getValue("other-lineage")
        assertEquals("다른계열", otherLineage.title)
        assertTrue(otherLineage.hint.contains("흰 셔츠, 슬랙스"))
        assertFalse(otherLineage.hint.contains("dark fantasy"))
    }

    @Test
    fun `카테고리 규칙에서 강제 단문 및 디테일 금지 제한이 제거되었다`() {
        val womenClothing = AnalysisCategoryRules.ruleFor(AnalysisCategory.WOMEN_CLOTHING)
        assertFalse(womenClothing.avoid.contains("긴 서술형 문장"))
        assertFalse(womenClothing.avoid.contains("과도한 디테일 열거"))
        assertFalse(womenClothing.output.contains("명사구 위주의 짧은"))
    }
}
