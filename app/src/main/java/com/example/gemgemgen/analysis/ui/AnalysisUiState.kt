package com.example.gemgemgen.analysis.ui

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisDirection
import com.example.gemgemgen.analysis.domain.AnalysisDummyDirections
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisResultPresentation
import com.example.gemgemgen.analysis.domain.AnalysisStartBlockReason
import com.example.gemgemgen.analysis.domain.AnalysisStartPolicy
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_5_FLASH_LITE
import com.example.gemgemgen.analysis.domain.MODEL_GROK_4_5
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary

const val DEFAULT_ANALYSIS_RESULT_FILE_NAME = "analysis-wildcard-results.txt"
val DEFAULT_ANALYSIS_CATEGORY: AnalysisCategory = AnalysisCategory.FREE_EDIT

data class AnalysisUiState(
    val sourcePrompt: String = "",
    val selectedCategory: AnalysisCategory? = DEFAULT_ANALYSIS_CATEGORY,
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
    /** 마지막 생성 결과의 표시 방식. 후보가 비면 [AnalysisResultPresentation.NONE]. */
    val resultPresentation: AnalysisResultPresentation = AnalysisResultPresentation.NONE,
    /** 「생성」카드 모드에서 자동화 프롬프트에 적용한 후보 인덱스. 없으면 null. */
    val selectedCandidateIndex: Int? = null,
    /** 자동화 프롬프트에 적용한 후보가 있어 원본 복원이 가능한지 여부. */
    val hasAppliedCandidateToAutomation: Boolean = false,
    val resultFileName: String = DEFAULT_ANALYSIS_RESULT_FILE_NAME,
    val pendingOverwriteFileName: String? = null,
    val showResetConfirmation: Boolean = false,
    val showKeyDialog: Boolean = false,
    val apiKeys: List<GeminiApiKeySummary> = emptyList(),
    val keyLabelInput: String = "",
    val keyValueInput: String = "",
    val editingApiKey: GeminiApiKeySummary? = null,
    val editingKeyLabelInput: String = "",
    val maskingProvider: AnalysisProvider = AnalysisProvider.GEMINI,
    val maskingModel: String = MODEL_GEMINI_3_5_FLASH_LITE,
    val generationProvider: AnalysisProvider = AnalysisProvider.GROK,
    val generationModel: String = MODEL_GROK_4_5,
    val isGrokLoggedIn: Boolean = false,
    val grokAccountPreview: String = "",
    val showGrokLoginDialog: Boolean = false,
    val grokLoginUserCode: String = "",
    val grokLoginVerificationUri: String = "",
    val isGrokLoginPolling: Boolean = false,
    /** Grok 남은 크레딧 %. 로그인 전이거나 조회 실패 시 null. */
    val grokRemainingPercent: Int? = null,
    /** 자동 마스킹 모델을 통한 분석이 필요한지 여부 (캐시 미스 등) */
    val needsMaskingAnalysis: Boolean = true
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

    val isBusy: Boolean
        get() = status == AnalysisStatus.ANALYZING || status == AnalysisStatus.GENERATING

    val startBlockedReason: AnalysisStartBlockReason?
        get() = if (isBusy) {
            null
        } else {
            AnalysisStartPolicy.evaluatePreconditions(
                source = sourcePrompt,
                category = selectedCategory,
                needsMaskingAnalysis = needsMaskingAnalysis,
                maskingProvider = maskingProvider,
                hasMaskingCredential = hasMaskingCredential,
                generationProvider = generationProvider,
                hasGenerationCredential = hasGenerationCredential
            )
        }

    val preconditionHintMessage: String?
        get() = startBlockedReason?.let { AnalysisUiText.startBlockedMessage(it) }

    val canGenerate: Boolean
        get() = !isBusy && startBlockedReason == null

    val canCopyOrSave: Boolean
        get() = resultPresentation == AnalysisResultPresentation.TXT &&
            generatedCandidates.isNotEmpty() &&
            status != AnalysisStatus.ANALYZING &&
            status != AnalysisStatus.GENERATING

    val geminiKeyPreview: String
        get() = apiKeys.firstOrNull { it.isActive }?.preview.orEmpty()

    val canResetSession: Boolean
        get() = sourcePrompt.isNotEmpty() ||
            selectedCategory != DEFAULT_ANALYSIS_CATEGORY ||
            targetSegment != null ||
            generatedCandidates.isNotEmpty() ||
            selectedDirectionIds.isNotEmpty() ||
            customHint.isNotEmpty() ||
            txtCount != AnalysisTxtCountPolicy.DEFAULT_COUNT ||
            resultFileName != DEFAULT_ANALYSIS_RESULT_FILE_NAME ||
            selectedCandidateIndex != null ||
            hasAppliedCandidateToAutomation ||
            pendingOverwriteFileName != null ||
            error.isNotEmpty() ||
            message.isNotEmpty() ||
            warning.isNotEmpty() ||
            isBusy

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
