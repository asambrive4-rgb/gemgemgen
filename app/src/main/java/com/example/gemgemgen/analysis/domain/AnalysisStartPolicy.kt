package com.example.gemgemgen.analysis.domain

/**
 * 분석/TXT 생성 시작 전 입력·키·상태 사전조건.
 * UI 메시지 문자열은 포함하지 않는다.
 */
enum class AnalysisStartBlockReason {
    MissingCategory,
    BlankSource,
    NoActiveKey
}

sealed interface AnalysisStartGate {
    data object Allowed : AnalysisStartGate
    data class Blocked(val reason: AnalysisStartBlockReason) : AnalysisStartGate
}

object AnalysisStartPolicy {
    fun evaluateInputs(
        source: String,
        category: AnalysisCategory?,
        hasActiveKey: Boolean
    ): AnalysisStartGate {
        if (category == null) {
            return AnalysisStartGate.Blocked(AnalysisStartBlockReason.MissingCategory)
        }
        if (source.isBlank()) {
            return AnalysisStartGate.Blocked(AnalysisStartBlockReason.BlankSource)
        }
        if (!hasActiveKey) {
            return AnalysisStartGate.Blocked(AnalysisStartBlockReason.NoActiveKey)
        }
        return AnalysisStartGate.Allowed
    }

    fun canAnalyze(
        source: String,
        category: AnalysisCategory?,
        hasActiveKey: Boolean,
        status: AnalysisStatus
    ): Boolean {
        return evaluateInputs(source, category, hasActiveKey) is AnalysisStartGate.Allowed &&
            status != AnalysisStatus.GENERATING
    }

    fun canGenerate(
        source: String,
        category: AnalysisCategory?,
        hasActiveKey: Boolean,
        status: AnalysisStatus
    ): Boolean {
        return evaluateInputs(source, category, hasActiveKey) is AnalysisStartGate.Allowed &&
            status != AnalysisStatus.ANALYZING
    }
}
