// 역할: 생성된 AI 후보를 자동화 프롬프트에 반영(치환)하고 최초 원본으로 복원하는 세션을 관리합니다.
package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegmentPolicy
import com.example.gemgemgen.analysis.domain.CandidateAutomationSession
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.PromptWorkspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

sealed interface ApplyCandidateResult {
    data class Success(val session: CandidateAutomationSession) : ApplyCandidateResult
    data class SegmentMissing(
        val message: String = "마스킹 구간이 없어 자동화 프롬프트에 반영할 수 없습니다."
    ) : ApplyCandidateResult
    data class SegmentInvalid(
        val message: String = "마스킹 구간이 원문과 맞지 않아 교체할 수 없습니다."
    ) : ApplyCandidateResult
    data class SourceMismatch(
        val message: String = "분석 원문이 변경되었습니다. 자동화 프롬프트를 원본으로 되돌린 뒤 다시 적용해 주세요."
    ) : ApplyCandidateResult
    data class ReplacementFailed(
        val message: String = "후보는 복사했지만 자동화 프롬프트에서 교체할 구간을 찾지 못했습니다. 자동화에서 원문을 다시 가져와 주세요."
    ) : ApplyCandidateResult
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : ApplyCandidateResult
}

sealed interface RestorePromptResult {
    data object NoSession : RestorePromptResult
    data object Success : RestorePromptResult
    data class ReplacementFailed(
        val message: String = "자동화 프롬프트에서 복원할 구간을 찾지 못했습니다. 자동화에서 원문을 다시 가져와 주세요."
    ) : RestorePromptResult
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : RestorePromptResult
}

class ManageCandidateHandoffUseCase(
    private val copyResults: CopyAnalysisResultsUseCase,
    private val promptWorkspace: PromptWorkspace? = null,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    var currentSession: CandidateAutomationSession? = null
        private set

    fun clearSession() {
        currentSession = null
    }

    fun setSession(session: CandidateAutomationSession?) {
        currentSession = session
    }

    suspend fun applyCandidate(
        candidate: String,
        sourcePrompt: String,
        targetSegment: AnalysisTargetSegment?,
        applyToAutomation: ((
            expectedSegment: String,
            replacement: String,
            preferredStartIndex: Int
        ) -> Int?)? = null
    ): ApplyCandidateResult = withContext(dispatchers.io) {
        val segment = targetSegment?.takeIf { it.isValid }
            ?: return@withContext ApplyCandidateResult.SegmentMissing()

        if (!AnalysisTargetSegmentPolicy.isStillValid(sourcePrompt, segment)) {
            return@withContext ApplyCandidateResult.SegmentInvalid()
        }

        val existing = currentSession
        if (existing != null && !existing.matches(sourcePrompt, segment)) {
            return@withContext ApplyCandidateResult.SourceMismatch()
        }

        val expectedSegment = existing?.appliedCandidate ?: segment.text
        val preferredStartIndex = existing?.automationSegmentStartIndex ?: segment.startIndex

        try {
            copyResults.copyText(candidate)
            val replacer = applyToAutomation ?: { exp, rep, start ->
                promptWorkspace?.replaceSegment(exp, rep, start)
            }
            val appliedStartIndex = replacer(
                expectedSegment,
                candidate,
                preferredStartIndex
            ) ?: return@withContext ApplyCandidateResult.ReplacementFailed()

            val newSession = CandidateAutomationSession(
                originalSource = existing?.originalSource ?: sourcePrompt,
                targetSegment = existing?.targetSegment ?: segment,
                appliedCandidate = candidate,
                automationSegmentStartIndex = appliedStartIndex
            )
            currentSession = newSession
            ApplyCandidateResult.Success(newSession)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApplyCandidateResult.Failure(e.message ?: "후보 적용에 실패했습니다.", e)
        }
    }

    fun restoreOriginalPrompt(
        restoreInAutomation: ((
            expectedSegment: String,
            originalSegment: String,
            preferredStartIndex: Int
        ) -> Int?)? = null
    ): RestorePromptResult {
        val session = currentSession ?: return RestorePromptResult.NoSession
        val replacer = restoreInAutomation ?: { exp, rep, start ->
            promptWorkspace?.replaceSegment(exp, rep, start)
        }
        return try {
            val restoredStartIndex = replacer(
                session.appliedCandidate,
                session.targetSegment.text,
                session.automationSegmentStartIndex
            ) ?: return RestorePromptResult.ReplacementFailed()

            currentSession = null
            RestorePromptResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RestorePromptResult.Failure(e.message ?: "원본 복원에 실패했습니다.", e)
        }
    }
}
