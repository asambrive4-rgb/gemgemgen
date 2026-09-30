// 역할: AI 분석 세션의 실행 전제조건·차단 사유 판정, 결과 복사/저장 권한, 세션 초기화 가능 여부를 판정합니다.
package com.example.gemgemgen.analysis.domain

import com.example.gemgemgen.analysis.ui.AnalysisUiState

internal const val DEFAULT_ANALYSIS_RESULT_FILE_NAME = "analysis-wildcard-results.txt"
internal val DEFAULT_ANALYSIS_CATEGORY: AnalysisCategory = AnalysisCategory.FREE_EDIT

sealed interface AnalysisStartBlockReason {
    data object BlankSource : AnalysisStartBlockReason
    data object MissingCategory : AnalysisStartBlockReason
    data class MissingMaskingCredential(val provider: AnalysisProvider) : AnalysisStartBlockReason
    data class MissingGenerationCredential(val provider: AnalysisProvider) : AnalysisStartBlockReason
}

object AnalysisSessionPolicy {
    fun evaluateStartBlockReason(
        source: String,
        category: AnalysisCategory?,
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
        if (status == AnalysisStatus.GENERATING) {
            return false
        }
        return evaluateStartBlockReason(
            source = source,
            category = category,
            needsMaskingAnalysis = needsMaskingAnalysis,
            maskingProvider = maskingProvider,
            hasMaskingCredential = hasMaskingCredential,
            generationProvider = generationProvider,
            hasGenerationCredential = hasGenerationCredential
        ) == null
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

    fun canResetSession(
        state: AnalysisUiState,
        sourcePrompt: String = state.sourcePrompt
    ): Boolean {
        val hasErrorOrMessage = state.error.isNotEmpty() || state.message.isNotEmpty() || state.warning.isNotEmpty()
        return sourcePrompt.isNotEmpty() ||
            state.selectedCategory != DEFAULT_ANALYSIS_CATEGORY ||
            state.targetSegment != null ||
            state.generatedCandidates.isNotEmpty() ||
            state.selectedDirectionIds.isNotEmpty() ||
            state.customHint.isNotEmpty() ||
            state.txtCount != AnalysisTxtCountPolicy.DEFAULT_COUNT ||
            state.resultFileName != DEFAULT_ANALYSIS_RESULT_FILE_NAME ||
            state.selectedCandidateIndex != null ||
            state.hasAppliedCandidateToAutomation ||
            state.pendingOverwriteFileName != null ||
            hasErrorOrMessage ||
            state.isBusy
    }
}

