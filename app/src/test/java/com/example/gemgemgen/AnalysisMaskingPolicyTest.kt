// 역할: 마스킹 분석 필요 여부 및 캐시 일치 판정 로직을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisMaskingPolicy
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.MaskingAnalysisCacheSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisMaskingPolicyTest {

    private data class FakeCacheSnapshot(
        override val sourcePrompt: String,
        override val category: AnalysisCategory,
        override val targetSegment: AnalysisTargetSegment? = null,
        override val selectedHints: List<String> = emptyList(),
        override val customHint: String = ""
    ) : MaskingAnalysisCacheSnapshot

    @Test
    fun shouldAnalyzeMasking_returnsFalse_whenCategoryIsNull() {
        val result = AnalysisMaskingPolicy.shouldAnalyzeMasking(
            source = "test prompt",
            category = null,
            targetSegment = null,
            cache = null,
            selectedHints = emptyList(),
            customHint = ""
        )
        assertFalse(result)
    }

    @Test
    fun shouldAnalyzeMasking_returnsTrue_whenCacheIsNull() {
        val result = AnalysisMaskingPolicy.shouldAnalyzeMasking(
            source = "test prompt",
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            targetSegment = null,
            cache = null,
            selectedHints = emptyList(),
            customHint = ""
        )
        assertTrue(result)
    }

    @Test
    fun shouldAnalyzeMasking_returnsFalse_whenAllFieldsMatch() {
        val cache = FakeCacheSnapshot(
            sourcePrompt = "test prompt",
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            targetSegment = null,
            selectedHints = listOf("hint1"),
            customHint = "custom"
        )
        val result = AnalysisMaskingPolicy.shouldAnalyzeMasking(
            source = "test prompt",
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            targetSegment = null,
            cache = cache,
            selectedHints = listOf("hint1"),
            customHint = "custom"
        )
        assertFalse(result)
    }

    @Test
    fun shouldAnalyzeMasking_returnsTrue_whenSourceChanges() {
        val cache = FakeCacheSnapshot(
            sourcePrompt = "old prompt",
            category = AnalysisCategory.WOMEN_HAIRSTYLE
        )
        val result = AnalysisMaskingPolicy.shouldAnalyzeMasking(
            source = "new prompt",
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            targetSegment = null,
            cache = cache,
            selectedHints = emptyList(),
            customHint = ""
        )
        assertTrue(result)
    }

    @Test
    fun shouldAnalyzeMasking_returnsTrue_whenHintsOrCustomHintChange() {
        val cache = FakeCacheSnapshot(
            sourcePrompt = "prompt",
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            selectedHints = listOf("a"),
            customHint = "old"
        )
        assertTrue(
            AnalysisMaskingPolicy.shouldAnalyzeMasking(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                targetSegment = null,
                cache = cache,
                selectedHints = listOf("a", "b"),
                customHint = "old"
            )
        )
        assertTrue(
            AnalysisMaskingPolicy.shouldAnalyzeMasking(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                targetSegment = null,
                cache = cache,
                selectedHints = listOf("a"),
                customHint = "new"
            )
        )
    }
}
