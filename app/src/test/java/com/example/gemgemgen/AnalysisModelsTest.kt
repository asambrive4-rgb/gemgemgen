// 역할: 분석 모델의 기본 상수 설정을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.DEFAULT_ANALYSIS_MODEL
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_7_FLASH
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisModelsTest {
    @Test
    fun `Gemini 기본 모델은 3점7 Flash다`() {
        assertEquals(MODEL_GEMINI_3_7_FLASH, DEFAULT_ANALYSIS_MODEL)
    }
}
