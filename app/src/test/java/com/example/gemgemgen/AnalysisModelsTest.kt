package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.DEFAULT_ANALYSIS_MODEL
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_5_FLASH_LITE
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_6_FLASH
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_7_FLASH
import com.example.gemgemgen.analysis.domain.migrateLegacyAnalysisModelId
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisModelsTest {
    @Test
    fun `Gemini 기본 모델은 3점7 Flash다`() {
        assertEquals(MODEL_GEMINI_3_7_FLASH, DEFAULT_ANALYSIS_MODEL)
    }

    @Test
    fun `삭제된 일반형 Gemini 모델은 3점7 Flash로 이전한다`() {
        assertEquals(
            MODEL_GEMINI_3_7_FLASH,
            migrateLegacyAnalysisModelId("gemini-3.5-flash")
        )
    }

    @Test
    fun `지원되는 Gemini 모델은 이전하지 않는다`() {
        assertEquals(
            MODEL_GEMINI_3_6_FLASH,
            migrateLegacyAnalysisModelId(MODEL_GEMINI_3_6_FLASH)
        )
        assertEquals(
            MODEL_GEMINI_3_5_FLASH_LITE,
            migrateLegacyAnalysisModelId(MODEL_GEMINI_3_5_FLASH_LITE)
        )
    }
}
