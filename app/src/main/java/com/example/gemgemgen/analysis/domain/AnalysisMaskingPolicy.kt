// 역할: 프롬프트 원문, 카테고리, 방향 힌트 추출 및 변경에 따른 마스킹 분석 필요 여부와 캐시 유효성을 판정합니다.
package com.example.gemgemgen.analysis.domain

interface MaskingAnalysisCacheSnapshot {
    val sourcePrompt: String
    val category: AnalysisCategory
    val targetSegment: AnalysisTargetSegment?
    val selectedHints: List<String>
    val customHint: String
}

data class AnalysisDirectionInput(
    val selectedHints: List<String>,
    val customHint: String
)

object AnalysisMaskingPolicy {
    fun extractHints(
        directions: List<AnalysisDirection>,
        selectedDirectionIds: Set<String>
    ): List<String> {
        return directions
            .filter { it.id in selectedDirectionIds }
            .map { it.hint }
    }

    fun extractDirectionInput(
        directions: List<AnalysisDirection>,
        selectedDirectionIds: Set<String>,
        customHint: String
    ): AnalysisDirectionInput {
        return AnalysisDirectionInput(
            selectedHints = extractHints(directions, selectedDirectionIds),
            customHint = customHint.trim()
        )
    }

    fun shouldAnalyzeMasking(
        source: String,
        category: AnalysisCategory?,
        targetSegment: AnalysisTargetSegment?,
        cache: MaskingAnalysisCacheSnapshot?,
        selectedHints: List<String>,
        customHint: String
    ): Boolean {
        if (category == null) return false
        if (cache == null) return true
        return cache.sourcePrompt != source ||
            cache.category != category ||
            cache.targetSegment != targetSegment ||
            cache.selectedHints != selectedHints ||
            cache.customHint != customHint
    }

    fun shouldAnalyzeMaskingFromHints(
        source: String,
        category: AnalysisCategory?,
        targetSegment: AnalysisTargetSegment?,
        cache: MaskingAnalysisCacheSnapshot?,
        directions: List<AnalysisDirection>,
        selectedDirectionIds: Set<String>,
        customHint: String
    ): Boolean {
        val directionInput = extractDirectionInput(directions, selectedDirectionIds, customHint)
        return shouldAnalyzeMasking(
            source = source,
            category = category,
            targetSegment = targetSegment,
            cache = cache,
            selectedHints = directionInput.selectedHints,
            customHint = directionInput.customHint
        )
    }
}
