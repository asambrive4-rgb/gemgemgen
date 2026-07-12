package com.example.gemgemgen.analysis.ui

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisDirection
import com.example.gemgemgen.analysis.domain.AnalysisDummyDirections
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisStartPolicy
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_1_FLASH_LITE
import com.example.gemgemgen.analysis.domain.MODEL_GROK_4_5
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
    val customHint: String = "",
    val generatedCandidates: List<String> = emptyList(),
    val resultFileName: String = "analysis-wildcard-results.txt",
    val pendingOverwriteFileName: String? = null,
    val showKeyDialog: Boolean = false,
    val apiKeys: List<GeminiApiKeySummary> = emptyList(),
    val keyLabelInput: String = "",
    val keyValueInput: String = "",
    val editingApiKey: GeminiApiKeySummary? = null,
    val editingKeyLabelInput: String = "",
    val maskingProvider: AnalysisProvider = AnalysisProvider.GEMINI,
    val maskingModel: String = MODEL_GEMINI_3_1_FLASH_LITE,
    val generationProvider: AnalysisProvider = AnalysisProvider.GROK,
    val generationModel: String = MODEL_GROK_4_5,
    val isGrokLoggedIn: Boolean = false,
    val grokAccountPreview: String = "",
    val showGrokLoginDialog: Boolean = false,
    val grokLoginUserCode: String = "",
    val grokLoginVerificationUri: String = "",
    val isGrokLoginPolling: Boolean = false,
    /** Grok 남은 크레딧 %. 로그인 전이거나 조회 실패 시 null. */
    val grokRemainingPercent: Int? = null
) {
    val hasGeminiCredential: Boolean
        get() = apiKeys.any { it.isActive }

    val hasGrokCredential: Boolean
        get() = isGrokLoggedIn

    val hasMaskingCredential: Boolean
        get() = hasCredentialFor(maskingProvider)

    val hasGenerationCredential: Boolean
        get() = hasCredentialFor(generationProvider)

    val usesGemini: Boolean
        get() = maskingProvider == AnalysisProvider.GEMINI ||
            generationProvider == AnalysisProvider.GEMINI

    val usesGrok: Boolean
        get() = maskingProvider == AnalysisProvider.GROK ||
            generationProvider == AnalysisProvider.GROK

    val canAnalyze: Boolean
        get() = AnalysisStartPolicy.canAnalyze(
            source = sourcePrompt,
            category = selectedCategory,
            hasActiveKey = hasMaskingCredential,
            status = status
        )

    val canGenerate: Boolean
        get() = AnalysisStartPolicy.canGenerate(
            source = sourcePrompt,
            category = selectedCategory,
            hasActiveKey = hasGenerationCredential,
            status = status
        )

    val canCopyOrSave: Boolean
        get() = generatedCandidates.isNotEmpty() &&
            status != AnalysisStatus.ANALYZING &&
            status != AnalysisStatus.GENERATING

    val geminiKeyPreview: String
        get() = apiKeys.firstOrNull { it.isActive }?.preview.orEmpty()

    val isBusy: Boolean
        get() = status == AnalysisStatus.ANALYZING || status == AnalysisStatus.GENERATING

    fun providerFor(role: AnalysisModelRole): AnalysisProvider {
        return when (role) {
            AnalysisModelRole.MASKING -> maskingProvider
            AnalysisModelRole.GENERATION -> generationProvider
        }
    }

    fun modelFor(role: AnalysisModelRole): String {
        return when (role) {
            AnalysisModelRole.MASKING -> maskingModel
            AnalysisModelRole.GENERATION -> generationModel
        }
    }

    private fun hasCredentialFor(provider: AnalysisProvider): Boolean {
        return when (provider) {
            AnalysisProvider.GEMINI -> hasGeminiCredential
            AnalysisProvider.GROK -> hasGrokCredential
        }
    }
}
