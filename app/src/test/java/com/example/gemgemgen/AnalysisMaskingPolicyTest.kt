// 역할: 방향 힌트 추출, 마스킹 분석 필요 여부 및 캐시 일치 판정 로직을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisDirection
import com.example.gemgemgen.analysis.domain.AnalysisDirectionInput
import com.example.gemgemgen.analysis.domain.AnalysisMaskingPolicy
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.MaskingAnalysisCacheSnapshot
import org.junit.Assert.assertEquals
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

    private val dummyDirections = listOf(
        AnalysisDirection(
            id = "dir1",
            title = "방향 1",
            description = "설명 1",
            hint = "hint 1"
        ),
        AnalysisDirection(
            id = "dir2",
            title = "방향 2",
            description = "설명 2",
            hint = "hint 2"
        ),
        AnalysisDirection(
            id = "dir3",
            title = "방향 3",
            description = "설명 3",
            hint = "hint 3"
        )
    )

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

    @Test
    fun extractHints_returnsFilteredHintsInOrder() {
        val hints = AnalysisMaskingPolicy.extractHints(
            directions = dummyDirections,
            selectedDirectionIds = setOf("dir3", "dir1")
        )
        assertEquals(listOf("hint 1", "hint 3"), hints)
    }

    @Test
    fun extractHints_returnsEmptyList_whenNoMatch() {
        val hints = AnalysisMaskingPolicy.extractHints(
            directions = dummyDirections,
            selectedDirectionIds = setOf("unknown")
        )
        assertTrue(hints.isEmpty())
    }

    @Test
    fun extractDirectionInput_trimsCustomHintAndExtractsHints() {
        val input = AnalysisMaskingPolicy.extractDirectionInput(
            directions = dummyDirections,
            selectedDirectionIds = setOf("dir2"),
            customHint = "   hello custom hint   "
        )
        assertEquals(
            AnalysisDirectionInput(
                selectedHints = listOf("hint 2"),
                customHint = "hello custom hint"
            ),
            input
        )
    }

    @Test
    fun shouldAnalyzeMaskingFromHints_returnsFalse_whenMatchingCache() {
        val cache = FakeCacheSnapshot(
            sourcePrompt = "test prompt",
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            targetSegment = null,
            selectedHints = listOf("hint 1", "hint 2"),
            customHint = "custom"
        )
        val result = AnalysisMaskingPolicy.shouldAnalyzeMaskingFromHints(
            source = "test prompt",
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            targetSegment = null,
            cache = cache,
            directions = dummyDirections,
            selectedDirectionIds = setOf("dir1", "dir2"),
            customHint = "  custom  "
        )
        assertFalse(result)
    }

    @Test
    fun shouldAnalyzeMaskingFromHints_returnsTrue_whenDirectionSelectionChanges() {
        val cache = FakeCacheSnapshot(
            sourcePrompt = "test prompt",
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            targetSegment = null,
            selectedHints = listOf("hint 1"),
            customHint = "custom"
        )
        val result = AnalysisMaskingPolicy.shouldAnalyzeMaskingFromHints(
            source = "test prompt",
            category = AnalysisCategory.WOMEN_HAIRSTYLE,
            targetSegment = null,
            cache = cache,
            directions = dummyDirections,
            selectedDirectionIds = setOf("dir1", "dir2"),
            customHint = "custom"
        )
        assertTrue(result)
    }

    @Test
    fun shouldAnalyzeMaskingFromHints_returnsFalse_whenCategoryIsNull() {
        val result = AnalysisMaskingPolicy.shouldAnalyzeMaskingFromHints(
            source = "test prompt",
            category = null,
            targetSegment = null,
            cache = null,
            directions = dummyDirections,
            selectedDirectionIds = setOf("dir1"),
            customHint = "custom"
        )
        assertFalse(result)
    }
}
