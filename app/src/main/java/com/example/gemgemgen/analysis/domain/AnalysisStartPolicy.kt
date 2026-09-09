package com.example.gemgemgen.analysis.domain

/**
 * 분석/TXT 생성 시작 전 입력·키·상태 사전조건.
 * UI 메시지 문자열은 포함하지 않는다.
 */
sealed interface AnalysisStartBlockReason {
    data object BlankSource : AnalysisStartBlockReason
    data object MissingCategory : AnalysisStartBlockReason
    data class MissingMaskingCredential(val provider: AnalysisProvider) : AnalysisStartBlockReason
    data class MissingGenerationCredential(val provider: AnalysisProvider) : AnalysisStartBlockReason
}

sealed interface AnalysisStartGate {
    data object Allowed : AnalysisStartGate
    data class Blocked(val reason: AnalysisStartBlockReason) : AnalysisStartGate
}

object AnalysisStartPolicy {
    fun evaluatePreconditions(
        source: String,
        category: AnalysisCategory?,
        needsMaskingAnalysis: Boolean,
        maskingProvider: AnalysisProvider,
        hasMaskingCredential: Boolean,
        generationProvider: AnalysisProvider,
        hasGenerationCredential: Boolean
    ): AnalysisStartBlockReason? {
        if (source.isBlank()) {
            return AnalysisStartBlockReason.BlankSource
        }
        if (category == null) {
            return AnalysisStartBlockReason.MissingCategory
        }
        if (needsMaskingAnalysis && !hasMaskingCredential) {
            return AnalysisStartBlockReason.MissingMaskingCredential(maskingProvider)
        }
        if (!hasGenerationCredential) {
            return AnalysisStartBlockReason.MissingGenerationCredential(generationProvider)
        }
        return null
    }

    fun evaluateGeneration(
        source: String,
        category: AnalysisCategory?,
        needsMaskingAnalysis: Boolean,
        maskingProvider: AnalysisProvider,
        hasMaskingCredential: Boolean,
        generationProvider: AnalysisProvider,
        hasGenerationCredential: Boolean
    ): AnalysisStartGate {
        val reason = evaluatePreconditions(
            source = source,
            category = category,
            needsMaskingAnalysis = needsMaskingAnalysis,
            maskingProvider = maskingProvider,
            hasMaskingCredential = hasMaskingCredential,
            generationProvider = generationProvider,
            hasGenerationCredential = hasGenerationCredential
        )
        return if (reason == null) AnalysisStartGate.Allowed else AnalysisStartGate.Blocked(reason)
    }

    fun canGenerate(
        source: String,
        category: AnalysisCategory?,
        needsMaskingAnalysis: Boolean,
        maskingProvider: AnalysisProvider,
        hasMaskingCredential: Boolean,
        generationProvider: AnalysisProvider,
        hasGenerationCredential: Boolean,
        status: AnalysisStatus
    ): Boolean {
        if (status == AnalysisStatus.ANALYZING || status == AnalysisStatus.GENERATING) {
            return false
        }
        return evaluatePreconditions(
            source = source,
            category = category,
            needsMaskingAnalysis = needsMaskingAnalysis,
            maskingProvider = maskingProvider,
            hasMaskingCredential = hasMaskingCredential,
            generationProvider = generationProvider,
            hasGenerationCredential = hasGenerationCredential
        ) == null
    }
}
