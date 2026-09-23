// 역할: AI 분석 세션의 실행 가능 여부, 결과 복사/저장 권한, 세션 초기화 가능 여부 등 핵심 도메인 규칙을 판정합니다.
package com.example.gemgemgen.analysis.domain

const val DEFAULT_ANALYSIS_RESULT_FILE_NAME = "analysis-wildcard-results.txt"
val DEFAULT_ANALYSIS_CATEGORY: AnalysisCategory = AnalysisCategory.FREE_EDIT

object AnalysisSessionPolicy {
    fun evaluateStartBlockReason(
        source: String,
        category: AnalysisCategory?,
        needsMaskingAnalysis: Boolean,
        maskingProvider: AnalysisProvider,
        hasMaskingCredential: Boolean,
        generationProvider: AnalysisProvider,
        hasGenerationCredential: Boolean,
        isBusy: Boolean
    ): AnalysisStartBlockReason? {
        if (isBusy) return null
        return AnalysisStartPolicy.evaluatePreconditions(
            source = source,
            category = category,
            needsMaskingAnalysis = needsMaskingAnalysis,
            maskingProvider = maskingProvider,
            hasMaskingCredential = hasMaskingCredential,
            generationProvider = generationProvider,
            hasGenerationCredential = hasGenerationCredential
        )
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
        return AnalysisStartPolicy.canGenerate(
            source = source,
            category = category,
            needsMaskingAnalysis = needsMaskingAnalysis,
            maskingProvider = maskingProvider,
            hasMaskingCredential = hasMaskingCredential,
            generationProvider = generationProvider,
            hasGenerationCredential = hasGenerationCredential,
            status = status
        )
    }

    fun canCopyOrSave(
        resultPresentation: AnalysisResultPresentation,
        candidateCount: Int,
        status: AnalysisStatus
    ): Boolean {
        val isBusy = status == AnalysisStatus.ANALYZING || status == AnalysisStatus.GENERATING
        return resultPresentation == AnalysisResultPresentation.TXT &&
            candidateCount > 0 &&
            !isBusy
    }

    fun canResetSession(
        sourcePrompt: String,
        selectedCategory: AnalysisCategory?,
        targetSegment: AnalysisTargetSegment? = null,
        generatedCandidatesCount: Int = 0,
        selectedDirectionIdsCount: Int = 0,
        customHint: String = "",
        txtCount: Int = AnalysisTxtCountPolicy.DEFAULT_COUNT,
        resultFileName: String = DEFAULT_ANALYSIS_RESULT_FILE_NAME,
        selectedCandidateIndex: Int? = null,
        hasAppliedCandidateToAutomation: Boolean = false,
        hasPendingOverwrite: Boolean = false,
        error: String = "",
        message: String = "",
        warning: String = "",
        isBusy: Boolean = false,
        defaultCategory: AnalysisCategory = DEFAULT_ANALYSIS_CATEGORY,
        defaultTxtCount: Int = AnalysisTxtCountPolicy.DEFAULT_COUNT,
        defaultResultFileName: String = DEFAULT_ANALYSIS_RESULT_FILE_NAME
    ): Boolean {
        val hasErrorOrMessage = error.isNotEmpty() || message.isNotEmpty() || warning.isNotEmpty()
        return sourcePrompt.isNotEmpty() ||
            selectedCategory != defaultCategory ||
            targetSegment != null ||
            generatedCandidatesCount > 0 ||
            selectedDirectionIdsCount > 0 ||
            customHint.isNotEmpty() ||
            txtCount != defaultTxtCount ||
            resultFileName != defaultResultFileName ||
            selectedCandidateIndex != null ||
            hasAppliedCandidateToAutomation ||
            hasPendingOverwrite ||
            hasErrorOrMessage ||
            isBusy
    }
}
