// 역할: AI 분석 시작 조건 및 차단 정책 판정 로직을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisSessionPolicy
import com.example.gemgemgen.analysis.domain.AnalysisStartBlockReason
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisStartPolicyTest {
    @Test
    fun evaluatePreconditions_blocksInSourceCategoryMaskingGenerationOrder() {
        // 1. 원문 없음 -> BlankSource (카테고리/키 여부와 무관하게 1순위)
        assertEquals(
            AnalysisStartBlockReason.BlankSource,
            AnalysisSessionPolicy.evaluateStartBlockReason(
                source = "",
                category = null,
                needsMaskingAnalysis = true,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = false,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = false
            )
        )
        assertEquals(
            AnalysisStartBlockReason.BlankSource,
            AnalysisSessionPolicy.evaluateStartBlockReason(
                source = "   ",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                needsMaskingAnalysis = false,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = true,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = true
            )
        )

        // 2. 원문 있고 카테고리 없음 -> MissingCategory (2순위)
        assertEquals(
            AnalysisStartBlockReason.MissingCategory,
            AnalysisSessionPolicy.evaluateStartBlockReason(
                source = "prompt",
                category = null,
                needsMaskingAnalysis = true,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = false,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = false
            )
        )

        // 3. 원문+카테고리 있고, 자동 마스킹 필요한데 마스킹 키 없음 -> MissingMaskingCredential (3순위)
        assertEquals(
            AnalysisStartBlockReason.MissingMaskingCredential(AnalysisProvider.GEMINI),
            AnalysisSessionPolicy.evaluateStartBlockReason(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                needsMaskingAnalysis = true,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = false,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = true
            )
        )
        assertEquals(
            AnalysisStartBlockReason.MissingMaskingCredential(AnalysisProvider.GROK),
            AnalysisSessionPolicy.evaluateStartBlockReason(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                needsMaskingAnalysis = true,
                maskingProvider = AnalysisProvider.GROK,
                hasMaskingCredential = false,
                generationProvider = AnalysisProvider.GEMINI,
                hasGenerationCredential = true
            )
        )

        // 4. 마스킹 인증 충족(또는 불필요) + 생성 인증 없음 -> MissingGenerationCredential (4순위)
        assertEquals(
            AnalysisStartBlockReason.MissingGenerationCredential(AnalysisProvider.GROK),
            AnalysisSessionPolicy.evaluateStartBlockReason(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                needsMaskingAnalysis = true,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = true,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = false
            )
        )
        assertEquals(
            AnalysisStartBlockReason.MissingGenerationCredential(AnalysisProvider.GEMINI),
            AnalysisSessionPolicy.evaluateStartBlockReason(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                needsMaskingAnalysis = false, // 마스킹 불필요
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = false, // 마스킹 키 없어도 영향 없음
                generationProvider = AnalysisProvider.GEMINI,
                hasGenerationCredential = false
            )
        )

        // 5. 모든 조건 충족 -> null
        assertNull(
            AnalysisSessionPolicy.evaluateStartBlockReason(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                needsMaskingAnalysis = true,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = true,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = true
            )
        )
    }

    @Test
    fun canGenerate_respectsPreconditionsAndBusyStatus() {
        // 준비 완료 및 대기 상태
        assertTrue(
            AnalysisSessionPolicy.canGenerate(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                needsMaskingAnalysis = false,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = false,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = true,
                status = AnalysisStatus.IDLE
            )
        )
        // 작업 중일 때는 불가
        assertFalse(
            AnalysisSessionPolicy.canGenerate(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                needsMaskingAnalysis = false,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = false,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = true,
                status = AnalysisStatus.GENERATING
            )
        )
        // 사전조건 부족 시 불가
        assertFalse(
            AnalysisSessionPolicy.canGenerate(
                source = "",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                needsMaskingAnalysis = false,
                maskingProvider = AnalysisProvider.GEMINI,
                hasMaskingCredential = false,
                generationProvider = AnalysisProvider.GROK,
                hasGenerationCredential = true,
                status = AnalysisStatus.IDLE
            )
        )
    }
}

