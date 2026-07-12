package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisReport
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegmentPolicy
import com.example.gemgemgen.analysis.domain.AnalysisTargetSource

data class AnalysisReportCache(
    val sourcePrompt: String,
    val category: AnalysisCategory,
    val targetSegment: AnalysisTargetSegment?,
    val report: AnalysisReport
)

data class AnalyzeAndMaskResult(
    val cache: AnalysisReportCache,
    val targetSegment: AnalysisTargetSegment,
    val warning: String
)

data class EnsureTargetResult(
    val target: AnalysisTargetSegment,
    val report: AnalysisReport,
    val cache: AnalysisReportCache,
    val targetChanged: Boolean,
    val warning: String
)

/**
 * Resolves auto/manual analysis targets and manages report cache hits.
 * Stateless: callers hold [AnalysisReportCache].
 */
class ResolveAnalysisTargetUseCase(
    private val analyzePrompt: AnalyzePromptForCategoryUseCase
) {
    suspend fun analyzeAndMask(
        source: String,
        category: AnalysisCategory
    ): AnalyzeAndMaskResult {
        // 자동 마스킹 버튼 → 마스킹 모델
        val report = analyzePrompt.analyze(
            sourcePrompt = source,
            category = category,
            role = AnalysisModelRole.MASKING
        )
        val autoTarget = AnalysisTargetSegmentPolicy.fromAutoReport(report, category)
            ?: throw AnalysisException(
                "자동으로 변주 대상을 찾지 못했습니다. 원문에서 직접 구간을 선택해주세요."
            )
        val cache = AnalysisReportCache(
            sourcePrompt = source,
            category = category,
            targetSegment = autoTarget,
            report = report
        )
        return AnalyzeAndMaskResult(
            cache = cache,
            targetSegment = autoTarget,
            warning = report.warnings.firstOrNull().orEmpty()
        )
    }

    suspend fun ensureForGeneration(
        source: String,
        category: AnalysisCategory,
        existingTarget: AnalysisTargetSegment?,
        cache: AnalysisReportCache?
    ): EnsureTargetResult {
        // 생성 전 재분석 → 생성 모델(기본 Grok)
        if (existingTarget?.source == AnalysisTargetSource.MANUAL && existingTarget.isValid) {
            val report = getOrAnalyzeReport(
                source = source,
                category = category,
                targetSegment = existingTarget,
                cache = cache,
                role = AnalysisModelRole.GENERATION
            )
            return EnsureTargetResult(
                target = existingTarget,
                report = report.report,
                cache = report.cache,
                targetChanged = false,
                warning = ""
            )
        }

        val resolved = getOrAnalyzeReport(
            source = source,
            category = category,
            targetSegment = existingTarget,
            cache = cache,
            role = AnalysisModelRole.GENERATION
        )
        val autoTarget = AnalysisTargetSegmentPolicy.fromAutoReport(resolved.report, category)
            ?: throw AnalysisException(
                "자동으로 변주 대상을 찾지 못했습니다. 원문에서 직접 구간을 선택해주세요."
            )
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
            }
        )
    }

    private suspend fun getOrAnalyzeReport(
        source: String,
        category: AnalysisCategory,
        targetSegment: AnalysisTargetSegment?,
        cache: AnalysisReportCache?,
        role: AnalysisModelRole
    ): CachedReport {
        if (cache != null &&
            cache.sourcePrompt == source &&
            cache.category == category &&
            cache.targetSegment == targetSegment
        ) {
            return CachedReport(report = cache.report, cache = cache)
        }
        val report = analyzePrompt.analyze(
            sourcePrompt = source,
            category = category,
            role = role
        )
        val nextCache = AnalysisReportCache(
            sourcePrompt = source,
            category = category,
            targetSegment = targetSegment,
            report = report
        )
        return CachedReport(report = report, cache = nextCache)
    }

    private data class CachedReport(
        val report: AnalysisReport,
        val cache: AnalysisReportCache
    )
}
