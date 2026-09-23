// 역할: AI 분석 세션 정책(사전조건 평가, 생성 권한, 복사/저장 권한, 세션 초기화 가능 여부)의 도메인 규칙을 검증합니다.
package com.example.gemgemgen.analysis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.gemgemgen.analysis.domain.AnalysisTargetSource

class AnalysisSessionPolicyTest {

    @Test
    fun evaluateStartBlockReason_whenBusy_alwaysReturnsNull() {
        val reason = AnalysisSessionPolicy.evaluateStartBlockReason(
            source = "",
            category = null,
            needsMaskingAnalysis = true,
            maskingProvider = AnalysisProvider.GEMINI,
            hasMaskingCredential = false,
            generationProvider = AnalysisProvider.GROK,
            hasGenerationCredential = false,
            isBusy = true
        )
        assertNull(reason)
    }

    @Test
    fun evaluateStartBlockReason_whenNotBusy_evaluatesPreconditionsInOrder() {
        // 1. 공백 원문
        val blankReason = AnalysisSessionPolicy.evaluateStartBlockReason(
            source = "   ",
            category = AnalysisCategory.FREE_EDIT,
            needsMaskingAnalysis = false,
            maskingProvider = AnalysisProvider.GEMINI,
            hasMaskingCredential = true,
            generationProvider = AnalysisProvider.GROK,
            hasGenerationCredential = true,
            isBusy = false
        )
        assertEquals(AnalysisStartBlockReason.BlankSource, blankReason)

        // 2. 카테고리 누락
        val missingCategoryReason = AnalysisSessionPolicy.evaluateStartBlockReason(
            source = "hello prompt",
            category = null,
            needsMaskingAnalysis = false,
            maskingProvider = AnalysisProvider.GEMINI,
            hasMaskingCredential = true,
            generationProvider = AnalysisProvider.GROK,
            hasGenerationCredential = true,
            isBusy = false
        )
        assertEquals(AnalysisStartBlockReason.MissingCategory, missingCategoryReason)

        // 3. 마스킹 키 누락
        val missingMaskingReason = AnalysisSessionPolicy.evaluateStartBlockReason(
            source = "hello prompt",
            category = AnalysisCategory.FREE_EDIT,
            needsMaskingAnalysis = true,
            maskingProvider = AnalysisProvider.GEMINI,
            hasMaskingCredential = false,
            generationProvider = AnalysisProvider.GROK,
            hasGenerationCredential = true,
            isBusy = false
        )
        assertEquals(AnalysisStartBlockReason.MissingMaskingCredential(AnalysisProvider.GEMINI), missingMaskingReason)

        // 4. 생성 키 누락
        val missingGenerationReason = AnalysisSessionPolicy.evaluateStartBlockReason(
            source = "hello prompt",
            category = AnalysisCategory.FREE_EDIT,
            needsMaskingAnalysis = false,
            maskingProvider = AnalysisProvider.GEMINI,
            hasMaskingCredential = true,
            generationProvider = AnalysisProvider.GROK,
            hasGenerationCredential = false,
            isBusy = false
        )
        assertEquals(AnalysisStartBlockReason.MissingGenerationCredential(AnalysisProvider.GROK), missingGenerationReason)

        // 5. 모든 조건 충족 -> null
        val allowedReason = AnalysisSessionPolicy.evaluateStartBlockReason(
            source = "hello prompt",
            category = AnalysisCategory.FREE_EDIT,
            needsMaskingAnalysis = true,
            maskingProvider = AnalysisProvider.GEMINI,
            hasMaskingCredential = true,
            generationProvider = AnalysisProvider.GROK,
            hasGenerationCredential = true,
            isBusy = false
        )
        assertNull(allowedReason)
    }

    @Test
    fun canGenerate_respectsStatusAndPreconditions() {
        assertFalse(
            AnalysisSessionPolicy.canGenerate(
                source = "prompt",
                category = AnalysisCategory.FREE_EDIT,
                needsMaskingAnalysis = false,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = true,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = true,
                status = AnalysisStatus.ANALYZING
            )
        )
        assertFalse(
            AnalysisSessionPolicy.canGenerate(
                source = "prompt",
                category = AnalysisCategory.FREE_EDIT,
                needsMaskingAnalysis = false,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = true,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = true,
                status = AnalysisStatus.GENERATING
            )
        )
        assertTrue(
            AnalysisSessionPolicy.canGenerate(
                source = "prompt",
                category = AnalysisCategory.FREE_EDIT,
                needsMaskingAnalysis = false,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = true,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = true,
                status = AnalysisStatus.IDLE
            )
        )
    }

    @Test
    fun canCopyOrSave_allowsOnlyWhenTxtModeAndCandidatesExistAndNotBusy() {
        // TXT 모드 + 후보 존재 + IDLE -> 허용
        assertTrue(
            AnalysisSessionPolicy.canCopyOrSave(
                resultPresentation = AnalysisResultPresentation.TXT,
                candidateCount = 3,
                status = AnalysisStatus.SUCCESS
            )
        )

        // 후보 없음 -> 차단
        assertFalse(
            AnalysisSessionPolicy.canCopyOrSave(
                resultPresentation = AnalysisResultPresentation.TXT,
                candidateCount = 0,
                status = AnalysisStatus.SUCCESS
            )
        )

        // TXT 모드가 아님 (NONE) -> 차단
        assertFalse(
            AnalysisSessionPolicy.canCopyOrSave(
                resultPresentation = AnalysisResultPresentation.NONE,
                candidateCount = 3,
                status = AnalysisStatus.SUCCESS
            )
        )

        // Busy 상태 -> 차단
        assertFalse(
            AnalysisSessionPolicy.canCopyOrSave(
                resultPresentation = AnalysisResultPresentation.TXT,
                candidateCount = 3,
                status = AnalysisStatus.GENERATING
            )
        )
        assertFalse(
            AnalysisSessionPolicy.canCopyOrSave(
                resultPresentation = AnalysisResultPresentation.TXT,
                candidateCount = 3,
                status = AnalysisStatus.ANALYZING
            )
        )
    }

    @Test
    fun canResetSession_evaluatesAllSessionStateProperties() {
        // 1. 완전히 깨끗한 초기 상태는 리셋 불가
        assertFalse(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                targetSegment = null,
                generatedCandidatesCount = 0,
                selectedDirectionIdsCount = 0,
                customHint = "",
                txtCount = AnalysisTxtCountPolicy.DEFAULT_COUNT,
                resultFileName = DEFAULT_ANALYSIS_RESULT_FILE_NAME,
                selectedCandidateIndex = null,
                hasAppliedCandidateToAutomation = false,
                hasPendingOverwrite = false,
                error = "",
                message = "",
                warning = "",
                isBusy = false
            )
        )

        // 2. 원문이 있는 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "something",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY
            )
        )

        // 3. 기본 카테고리가 아닌 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = AnalysisCategory.WOMEN_POSE
            )
        )

        // 4. 타겟 세그먼트가 있는 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                targetSegment = AnalysisTargetSegment(
                    text = "test",
                    startIndex = 0,
                    endIndex = 4,
                    source = AnalysisTargetSource.AUTO,
                    category = AnalysisCategory.FREE_EDIT
                )
            )
        )

        // 5. 생성된 후보가 있는 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                generatedCandidatesCount = 1
            )
        )

        // 6. 방향 선택이 있는 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                selectedDirectionIdsCount = 1
            )
        )

        // 7. 커스텀 힌트가 있는 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                customHint = "hint"
            )
        )

        // 8. txtCount가 기본값이 아닌 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                txtCount = 5
            )
        )

        // 9. 결과 파일명이 기본값이 아닌 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                resultFileName = "custom.txt"
            )
        )

        // 10. 후보 인덱스가 선택된 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                selectedCandidateIndex = 0
            )
        )

        // 11. 자동화에 후보가 적용된 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                hasAppliedCandidateToAutomation = true
            )
        )

        // 12. 덮어쓰기 대기 상태인 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                hasPendingOverwrite = true
            )
        )

        // 13. 에러 또는 메시지가 있는 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                error = "some error"
            )
        )
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                message = "some message"
            )
        )
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                warning = "some warning"
            )
        )

        // 14. Busy 상태인 경우
        assertTrue(
            AnalysisSessionPolicy.canResetSession(
                sourcePrompt = "",
                selectedCategory = DEFAULT_ANALYSIS_CATEGORY,
                isBusy = true
            )
        )
    }
}
