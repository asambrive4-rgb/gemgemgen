// 역할: 프롬프트 원문, 카테고리, 힌트 변경에 따른 마스킹 분석 필요 여부 및 캐시 유효성을 판정합니다.
package com.example.gemgemgen.analysis.domain

interface MaskingAnalysisCacheSnapshot {
    val sourcePrompt: String
    val category: AnalysisCategory
    val targetSegment: AnalysisTargetSegment?
    val selectedHints: List<String>
    val customHint: String
}

object AnalysisMaskingPolicy {
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
}
