// 역할: 선택된 카테고리에 맞춰 AI 마스킹 분석을 수행하고 타겟 구간 확정 및 분석 캐시를 관리합니다.
package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisCategoryRules
import com.example.gemgemgen.analysis.domain.AnalysisEditPolicy
import com.example.gemgemgen.analysis.domain.AnalysisMaskingPolicy
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisPromptBuilder
import com.example.gemgemgen.analysis.domain.AnalysisReport
import com.example.gemgemgen.analysis.domain.AnalysisReportCache
import com.example.gemgemgen.analysis.domain.AnalysisResponseParser
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegmentPolicy
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.withContext

class AnalysisException(message: String) : RuntimeException(message)

data class EnsureTargetResult(
    val target: AnalysisTargetSegment,
    val report: AnalysisReport,
    val cache: AnalysisReportCache,
    val targetChanged: Boolean,
    val warning: String,
    /** true면 캐시 미스 등으로 마스킹 모델 분석 API를 호출함 */
    val didAnalyze: Boolean
)

/**
 * Resolves auto analysis targets and manages report cache hits.
 * Stateless: callers hold [AnalysisReportCache].
 */
class ResolveAnalysisTargetUseCase(
    private val aiGateway: AnalysisAiGateway,
    private val credentialResolver: ResolveAnalysisCredentialUseCase,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun ensureForGeneration(
        source: String,
        category: AnalysisCategory,
        existingTarget: AnalysisTargetSegment?,
        cache: AnalysisReportCache?,
        selectedHints: List<String> = emptyList(),
        customHint: String? = null,
        needsAnalyze: Boolean? = null
    ): EnsureTargetResult {
        val normalizedCustomHint = customHint?.trim().orEmpty()
        val resolved = getOrAnalyzeReport(
            source = source,
            category = category,
            targetSegment = existingTarget,
            cache = cache,
            role = AnalysisModelRole.MASKING,
            selectedHints = selectedHints,
            customHint = normalizedCustomHint,
            needsAnalyze = needsAnalyze
        )
        requireNoClarification(resolved.report)
        val detectedTarget = AnalysisTargetSegmentPolicy.fromAutoReport(resolved.report, category)
            ?: throw AnalysisException(
                "자동으로 변주 대상을 찾지 못했습니다. 원문에서 직접 구간을 선택해주세요."
            )
        val autoTarget = AnalysisEditPolicy.envelope(source, detectedTarget, resolved.report)
        val targetChanged = existingTarget != autoTarget
        val nextCache = if (targetChanged) {
            resolved.cache.copy(targetSegment = autoTarget)
        } else {
            resolved.cache
        }
        return EnsureTargetResult(
            target = autoTarget,
            report = resolved.report,
            cache = nextCache,
            targetChanged = targetChanged,
            warning = if (targetChanged) {
                resolved.report.warnings.firstOrNull().orEmpty()
            } else {
                ""
            },
            didAnalyze = resolved.didAnalyze
        )
    }

    private suspend fun getOrAnalyzeReport(
        source: String,
        category: AnalysisCategory,
        targetSegment: AnalysisTargetSegment?,
        cache: AnalysisReportCache?,
        role: AnalysisModelRole,
        selectedHints: List<String>,
        customHint: String,
        needsAnalyze: Boolean? = null
    ): CachedReport {
        val shouldAnalyze = needsAnalyze ?: AnalysisMaskingPolicy.shouldAnalyzeMasking(
            source = source,
            category = category,
            targetSegment = targetSegment,
            cache = cache,
            selectedHints = selectedHints,
            customHint = customHint
        )
        if (!shouldAnalyze && cache != null) {
            return CachedReport(report = cache.report, cache = cache, didAnalyze = false)
        }
        val report = analyze(
            sourcePrompt = source,
            category = category,
            role = role,
            selectedHints = selectedHints,
            customHint = customHint.ifBlank { null }
        )
        val nextCache = AnalysisReportCache(
            sourcePrompt = source,
            category = category,
            targetSegment = targetSegment,
            report = report,
            selectedHints = selectedHints,
            customHint = customHint
        )
        return CachedReport(report = report, cache = nextCache, didAnalyze = true)
    }

    private suspend fun analyze(
        sourcePrompt: String,
        category: AnalysisCategory,
        role: AnalysisModelRole = AnalysisModelRole.MASKING,
        selectedHints: List<String> = emptyList(),
        customHint: String? = null
    ): AnalysisReport = withContext(dispatchers.io) {
        if (sourcePrompt.isBlank()) {
            throw AnalysisException("원본 프롬프트를 입력해주세요.")
        }
        val credential = credentialResolver.resolveForRole(role)
        val normalizedCustomHint = customHint?.trim().orEmpty()

        val payload = AnalysisPromptBuilder.buildAnalysisPrompt(
            sourcePrompt = sourcePrompt,
            category = category,
            selectedHints = selectedHints,
            customHint = normalizedCustomHint.ifBlank { null }
        )
        val responseText = aiGateway.analyze(
            apiKey = credential.accessTokenOrApiKey,
            modelId = credential.modelId,
            payload = payload,
            provider = credential.provider
        )
        val report = AnalysisResponseParser.parseReport(responseText, sourcePrompt)
        if (report.variationGoal.isNotBlank()) {
            report
        } else {
            report.copy(variationGoal = AnalysisCategoryRules.ruleFor(category).goal)
        }
    }

    private data class CachedReport(
        val report: AnalysisReport,
        val cache: AnalysisReportCache,
        val didAnalyze: Boolean
    )

    private fun requireNoClarification(report: AnalysisReport) {
        if (report.clarificationQuestion.isNotBlank()) {
            throw AnalysisException("추가 요구사항에 답을 적어 주세요: ${report.clarificationQuestion}")
        }
    }
}

