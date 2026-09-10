package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisEditPolicy
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisPromptBuilder
import com.example.gemgemgen.analysis.domain.AnalysisReport
import com.example.gemgemgen.analysis.domain.AnalysisResponseParser
import com.example.gemgemgen.analysis.domain.AnalysisSourceRange
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTextEdit
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.withContext

data class AnalysisTxtGenerationResult(
    val candidates: List<String>,
    val warning: String = ""
)

class GenerateAnalysisTxtUseCase(
    private val aiGateway: AnalysisAiGateway,
    private val credentialResolver: AnalysisCredentialResolver,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    /**
     * @param count 호출측에서 모드별 정책으로 정규화한 개수.
     *  (TXT: [com.example.gemgemgen.analysis.domain.AnalysisTxtCountPolicy],
     *   생성: [com.example.gemgemgen.analysis.domain.AnalysisGenerationCountPolicy])
     */
    suspend fun generate(
        sourcePrompt: String,
        category: AnalysisCategory,
        targetSegment: AnalysisTargetSegment,
        analysisReport: AnalysisReport,
        count: Int,
        selectedHints: List<String>,
        customHint: String? = null
    ): AnalysisTxtGenerationResult = withContext(dispatchers.io) {
        if (sourcePrompt.isBlank()) {
            throw AnalysisException("원본 프롬프트를 입력해주세요.")
        }
        if (!targetSegment.isValid) {
            throw AnalysisException("변주 대상 구간을 먼저 지정해주세요.")
        }
        if (analysisReport.clarificationQuestion.isNotBlank()) {
            throw AnalysisException("추가 요구사항에 답을 적어 주세요: ${analysisReport.clarificationQuestion}")
        }
        val editTarget = AnalysisEditPolicy.envelope(sourcePrompt, targetSegment, analysisReport)
        require(editTarget.startIndex == targetSegment.startIndex && editTarget.endIndex == targetSegment.endIndex) {
            "함께 수정할 구간이 아직 반영되지 않았습니다. 다시 분석해 주세요."
        }
        val credential = credentialResolver.resolveForRole(AnalysisModelRole.GENERATION)
        val normalizedCount = count.coerceAtLeast(1)
        val payload = AnalysisPromptBuilder.buildTxtPrompt(
            sourcePrompt = sourcePrompt,
            category = category,
            targetSegment = editTarget,
            analysisReport = analysisReport,
            count = normalizedCount,
            selectedHints = selectedHints,
            customHint = customHint
        )
        val responseText = aiGateway.generateTxt(
            apiKey = credential.accessTokenOrApiKey,
            modelId = credential.modelId,
            payload = payload
        )
        val candidates = if (category == AnalysisCategory.FREE_EDIT) {
            AnalysisResponseParser.parseEditCandidates(responseText).take(normalizedCount).map { edits ->
                AnalysisEditPolicy.assemble(sourcePrompt, editTarget, edits, analysisReport)
            }
        } else {
            AnalysisResponseParser.parseTxtCandidates(responseText).take(normalizedCount).map { candidate ->
                AnalysisEditPolicy.assemble(sourcePrompt, editTarget, listOf(AnalysisTextEdit(
                    AnalysisSourceRange(editTarget.startIndex, editTarget.endIndex, editTarget.text), candidate
                )), analysisReport)
            }
        }
        val warning = if (candidates.size < normalizedCount) {
            "요청한 ${normalizedCount}개보다 적은 ${candidates.size}개만 생성되었습니다."
        } else {
            ""
        }
        AnalysisTxtGenerationResult(candidates = candidates, warning = warning)
    }
}
