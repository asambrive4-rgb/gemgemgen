// 역할: AI 분석 세션의 실행 전제조건·차단 사유 판정, 결과 및 다이나믹 프롬프트 복사/저장 권한, 세션 초기화 가능 여부를 판정합니다.
package com.example.gemgemgen.analysis.domain

internal const val DEFAULT_ANALYSIS_RESULT_FILE_NAME = "analysis-wildcard-results.txt"
internal val DEFAULT_ANALYSIS_CATEGORY: AnalysisCategory = AnalysisCategory.FREE_EDIT

sealed interface AnalysisStartBlockReason {
    data object BlankSource : AnalysisStartBlockReason
    data class MissingMaskingCredential(val provider: AnalysisProvider) : AnalysisStartBlockReason
    data class MissingGenerationCredential(val provider: AnalysisProvider) : AnalysisStartBlockReason
}

object AnalysisSessionPolicy {
    fun evaluateStartBlockReason(
        source: String,
        category: AnalysisCategory,
        needsMaskingAnalysis: Boolean,
        maskingProvider: AnalysisProvider,
        hasMaskingCredential: Boolean,
        generationProvider: AnalysisProvider,
        hasGenerationCredential: Boolean,
        isBusy: Boolean = false
    ): AnalysisStartBlockReason? {
        if (isBusy) return null
        if (source.isBlank()) {
            return AnalysisStartBlockReason.BlankSource
        }
        if (needsMaskingAnalysis && !hasMaskingCredential) {
            return AnalysisStartBlockReason.MissingMaskingCredential(maskingProvider)
        }
        if (!hasGenerationCredential) {
            return AnalysisStartBlockReason.MissingGenerationCredential(generationProvider)
        }
        return null
    }

    fun canCopyOrSave(
        resultPresentation: AnalysisResultPresentation,
        candidateCount: Int,
        status: AnalysisStatus
    ): Boolean {
        return resultPresentation == AnalysisResultPresentation.TXT &&
            candidateCount > 0 &&
            status != AnalysisStatus.GENERATING
    }

    fun canCopyDynamicPrompt(
        candidateCount: Int,
        status: AnalysisStatus
    ): Boolean {
        return candidateCount > 0 && status != AnalysisStatus.GENERATING
    }

    fun canResetSession(
        sourcePrompt: String,
        selectedCategory: AnalysisCategory,
        hasTargetSegment: Boolean,
        hasGeneratedCandidates: Boolean,
        hasSelectedDirections: Boolean,
        customHint: String,
        txtCount: Int,
        resultFileName: String,
        hasSelectedCandidate: Boolean,
        hasAppliedCandidateToAutomation: Boolean,
        hasPendingOverwriteFile: Boolean,
        hasErrorOrMessage: Boolean,
        isBusy: Boolean
    ): Boolean {
        return sourcePrompt.isNotEmpty() ||
            selectedCategory != DEFAULT_ANALYSIS_CATEGORY ||
            hasTargetSegment ||
            hasGeneratedCandidates ||
            hasSelectedDirections ||
            customHint.isNotEmpty() ||
            txtCount != AnalysisTxtCountPolicy.DEFAULT_COUNT ||
            resultFileName != DEFAULT_ANALYSIS_RESULT_FILE_NAME ||
            hasSelectedCandidate ||
            hasAppliedCandidateToAutomation ||
            hasPendingOverwriteFile ||
            hasErrorOrMessage ||
            isBusy
    }
}

