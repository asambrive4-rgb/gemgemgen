// 역할: 인증 조회와 프롬프트 조립을 병렬 준비해 AI 분석 결과를 바탕으로 후보 결과물을 관대하게 생성하고 중복 후보를 제거합니다.
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class AnalysisTxtGenerationResult(
    val candidates: List<String>,
    val warning: String = ""
)

class GenerateAnalysisTxtUseCase(
    private val aiGateway: AnalysisAiGateway,
    private val credentialResolver: ResolveAnalysisCredentialUseCase,
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
        if (!targetSegment.isValid) {
            throw AnalysisException("변주 대상 구간을 먼저 지정해주세요.")
        }
        val normalizedCount = count.coerceAtLeast(1)
        val (credential, payload) = coroutineScope {
            val credentialDeferred = async { credentialResolver.resolveForRole(AnalysisModelRole.GENERATION) }
            val builtPayload = AnalysisPromptBuilder.buildTxtPrompt(
                sourcePrompt = sourcePrompt,
                category = category,
                targetSegment = targetSegment,
                analysisReport = analysisReport,
                count = normalizedCount,
                selectedHints = selectedHints,
                customHint = customHint
            )
            credentialDeferred.await() to builtPayload
        }
        val responseText = aiGateway.analyze(
            apiKey = credential.accessTokenOrApiKey,
            modelId = credential.modelId,
            payload = payload,
            provider = credential.provider
        )
        val candidates = if (category == AnalysisCategory.FREE_EDIT) {
            val rawList = AnalysisResponseParser.parseEditCandidates(responseText).take(normalizedCount)
            var lastError: Throwable? = null
            val assembled = rawList.mapNotNull { edits ->
                runCatching { AnalysisEditPolicy.assemble(sourcePrompt, targetSegment, edits, analysisReport) }
                    .onFailure { lastError = it }
                    .getOrNull()
            }
            if (assembled.isEmpty() && lastError != null) {
                throw lastError!!
            }
            assembled
        } else {
            AnalysisResponseParser.parseTxtCandidates(responseText).take(normalizedCount).map { candidate ->
                AnalysisEditPolicy.assemble(sourcePrompt, targetSegment, listOf(AnalysisTextEdit(
                    AnalysisSourceRange(targetSegment.startIndex, targetSegment.endIndex, targetSegment.text), candidate
                )), analysisReport)
            }
        }.distinct()
        val warning = if (candidates.size < normalizedCount) {
            "요청한 ${normalizedCount}개보다 적은 ${candidates.size}개만 생성되었습니다."
        } else {
            ""
        }
        AnalysisTxtGenerationResult(candidates = candidates, warning = warning)
    }
}
