// 역할: AI 프롬프트 분석 전제조건 검사, 타겟 구간 확정, 텍스트 생성 및 모델 최근 사용 기록을 조율합니다.
package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisMaskingPolicy
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisStartBlockReason
import com.example.gemgemgen.analysis.domain.AnalysisStartPolicy
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class AnalysisGenerationStep {
    MASKING,
    GENERATING
}

data class ExecuteAnalysisGenerationRequest(
    val sourcePrompt: String,
    val category: AnalysisCategory?,
    val targetSegment: AnalysisTargetSegment?,
    val cache: AnalysisReportCache?,
    val count: Int,
    val selectedHints: List<String> = emptyList(),
    val customHint: String = "",
    val maskingProvider: AnalysisProvider,
    val hasMaskingCredential: Boolean,
    val maskingModel: String,
    val generationProvider: AnalysisProvider,
    val hasGenerationCredential: Boolean,
    val generationModel: String,
    val failureFallback: String = "생성에 실패했습니다."
)

sealed interface ExecuteAnalysisGenerationResult {
    data class Blocked(val reason: AnalysisStartBlockReason) : ExecuteAnalysisGenerationResult
    data class Success(
        val candidates: List<String>,
        val targetSegment: AnalysisTargetSegment,
        val cache: AnalysisReportCache,
        val targetChanged: Boolean,
        val warning: String,
        val didAnalyze: Boolean
    ) : ExecuteAnalysisGenerationResult
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : ExecuteAnalysisGenerationResult
}

class ExecuteAnalysisGenerationUseCase(
    private val resolveTarget: ResolveAnalysisTargetUseCase,
    private val generateTxtUseCase: GenerateAnalysisTxtUseCase,
    private val keyManager: ManageGeminiApiKeysUseCase,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun execute(
        request: ExecuteAnalysisGenerationRequest,
        onStep: ((AnalysisGenerationStep) -> Unit)? = null,
        onTargetChanged: ((AnalysisTargetSegment, String) -> Unit)? = null
    ): ExecuteAnalysisGenerationResult = withContext(dispatchers.io) {
        val needsMaskingAnalysis = AnalysisMaskingPolicy.shouldAnalyzeMasking(
            source = request.sourcePrompt,
            category = request.category,
            targetSegment = request.targetSegment,
            cache = request.cache,
            selectedHints = request.selectedHints,
            customHint = request.customHint
        )
        val blockedReason = AnalysisStartPolicy.evaluatePreconditions(
            source = request.sourcePrompt,
            category = request.category,
            needsMaskingAnalysis = needsMaskingAnalysis,
            maskingProvider = request.maskingProvider,
            hasMaskingCredential = request.hasMaskingCredential,
            generationProvider = request.generationProvider,
            hasGenerationCredential = request.hasGenerationCredential
        )
        if (blockedReason != null) {
            return@withContext ExecuteAnalysisGenerationResult.Blocked(blockedReason)
        }
        val category = checkNotNull(request.category)

        if (needsMaskingAnalysis) {
            onStep?.invoke(AnalysisGenerationStep.MASKING)
        } else {
            onStep?.invoke(AnalysisGenerationStep.GENERATING)
        }

        try {
            val ensured = resolveTarget.ensureForGeneration(
                source = request.sourcePrompt,
                category = category,
                existingTarget = request.targetSegment,
                cache = request.cache,
                selectedHints = request.selectedHints,
                customHint = request.customHint
            )
            coroutineContext.ensureActive()

            if (ensured.targetChanged) {
                onTargetChanged?.invoke(ensured.target, ensured.warning)
            }

            if (needsMaskingAnalysis) {
                onStep?.invoke(AnalysisGenerationStep.GENERATING)
            }

            val result = generateTxtUseCase.generate(
                sourcePrompt = request.sourcePrompt,
                category = category,
                targetSegment = ensured.target,
                analysisReport = ensured.report,
                count = request.count,
                selectedHints = request.selectedHints,
                customHint = request.customHint
            )
            coroutineContext.ensureActive()

            if (ensured.didAnalyze) {
                keyManager.rememberLastUsed(
                    role = AnalysisModelRole.MASKING,
                    provider = request.maskingProvider,
                    modelId = request.maskingModel
                )
            }
            keyManager.rememberLastUsed(
                role = AnalysisModelRole.GENERATION,
                provider = request.generationProvider,
                modelId = request.generationModel
            )

            ExecuteAnalysisGenerationResult.Success(
                candidates = result.candidates,
                targetSegment = ensured.target,
                cache = ensured.cache,
                targetChanged = ensured.targetChanged,
                warning = result.warning,
                didAnalyze = ensured.didAnalyze
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExecuteAnalysisGenerationResult.Failure(
                message = e.message ?: request.failureFallback,
                cause = e
            )
        }
    }
}
