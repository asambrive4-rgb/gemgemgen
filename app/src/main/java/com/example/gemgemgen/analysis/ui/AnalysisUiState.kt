package com.example.gemgemgen.analysis.ui

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisDirection
import com.example.gemgemgen.analysis.domain.AnalysisDummyDirections
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary

data class AnalysisUiState(
    val sourcePrompt: String = "",
    val selectedCategory: AnalysisCategory? = null,
    val targetSegment: AnalysisTargetSegment? = null,
    val status: AnalysisStatus = AnalysisStatus.IDLE,
    val error: String = "",
    val message: String = "",
    val warning: String = "",
    val txtCount: Int = AnalysisTxtCountPolicy.DEFAULT_COUNT,
    val directions: List<AnalysisDirection> = AnalysisDummyDirections.values,
    val selectedDirectionIds: Set<String> = emptySet(),
    val generatedCandidates: List<String> = emptyList(),
    val resultFileName: String = "analysis-wildcard-results.txt",
    val pendingOverwriteFileName: String? = null,
    val showKeyDialog: Boolean = false,
    val apiKeys: List<GeminiApiKeySummary> = emptyList(),
    val keyLabelInput: String = "",
    val keyValueInput: String = ""
) {
    val canAnalyze: Boolean
        get() = sourcePrompt.isNotBlank() &&
            selectedCategory != null &&
            apiKeys.any { it.isActive } &&
            status != AnalysisStatus.GENERATING

    val canGenerate: Boolean
        get() = sourcePrompt.isNotBlank() &&
            selectedCategory != null &&
            apiKeys.any { it.isActive } &&
            status != AnalysisStatus.ANALYZING

    val canCopyOrSave: Boolean
        get() = generatedCandidates.isNotEmpty() &&
            status != AnalysisStatus.ANALYZING &&
            status != AnalysisStatus.GENERATING

    val activeKeyPreview: String
        get() = apiKeys.firstOrNull { it.isActive }?.preview.orEmpty()

    val isBusy: Boolean
        get() = status == AnalysisStatus.ANALYZING || status == AnalysisStatus.GENERATING
}
