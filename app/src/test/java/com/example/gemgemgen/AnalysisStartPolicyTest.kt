package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisStartBlockReason
import com.example.gemgemgen.analysis.domain.AnalysisStartGate
import com.example.gemgemgen.analysis.domain.AnalysisStartPolicy
import com.example.gemgemgen.analysis.domain.AnalysisStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisStartPolicyTest {
    @Test
    fun evaluateInputs_blocksInCategorySourceKeyOrder() {
        assertEquals(
            AnalysisStartBlockReason.MissingCategory,
            blockedReason(source = "", category = null, hasActiveKey = false)
        )
        assertEquals(
            AnalysisStartBlockReason.BlankSource,
            blockedReason(
                source = "  ",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                hasActiveKey = false
            )
        )
        assertEquals(
            AnalysisStartBlockReason.NoActiveKey,
            blockedReason(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                hasActiveKey = false
            )
        )
        assertEquals(
            AnalysisStartGate.Allowed,
            AnalysisStartPolicy.evaluateInputs(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                hasActiveKey = true
            )
        )
    }

    @Test
    fun canAnalyze_requiresReadyInputsAndNotGenerating() {
        assertTrue(
            AnalysisStartPolicy.canAnalyze(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                hasActiveKey = true,
                status = AnalysisStatus.IDLE
            )
        )
        assertFalse(
            AnalysisStartPolicy.canAnalyze(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                hasActiveKey = true,
                status = AnalysisStatus.GENERATING
            )
        )
        assertFalse(
            AnalysisStartPolicy.canAnalyze(
                source = "",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                hasActiveKey = true,
                status = AnalysisStatus.IDLE
            )
        )
    }

    @Test
    fun canGenerate_requiresReadyInputsAndNotAnalyzing() {
        assertTrue(
            AnalysisStartPolicy.canGenerate(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                hasActiveKey = true,
                status = AnalysisStatus.IDLE
            )
        )
        assertFalse(
            AnalysisStartPolicy.canGenerate(
                source = "prompt",
                category = AnalysisCategory.WOMEN_HAIRSTYLE,
                hasActiveKey = true,
                status = AnalysisStatus.ANALYZING
            )
        )
    }

    private fun blockedReason(
        source: String,
        category: AnalysisCategory?,
        hasActiveKey: Boolean
    ): AnalysisStartBlockReason {
        val gate = AnalysisStartPolicy.evaluateInputs(source, category, hasActiveKey)
        return (gate as AnalysisStartGate.Blocked).reason
    }
}
