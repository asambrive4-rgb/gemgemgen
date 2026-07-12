package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisDetectedSegment
import com.example.gemgemgen.analysis.domain.AnalysisReport
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegmentPolicy
import com.example.gemgemgen.analysis.domain.AnalysisTargetSource
import com.example.gemgemgen.analysis.domain.ManualTargetSegmentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTargetSegmentPolicyTest {
    private val source = "red hair and blue dress"
    private val hairSegment = AnalysisTargetSegment(
        text = "red hair",
        startIndex = 0,
        endIndex = 8,
        source = AnalysisTargetSource.MANUAL,
        category = AnalysisCategory.WOMEN_HAIRSTYLE
    )

    @Test
    fun isStillValid_sameSpan_returnsTrue() {
        assertTrue(AnalysisTargetSegmentPolicy.isStillValid(source, hairSegment))
    }

    @Test
    fun isStillValid_textChanged_returnsFalse() {
        assertFalse(
            AnalysisTargetSegmentPolicy.isStillValid(
                "black hair and blue dress",
                hairSegment
            )
        )
    }

    @Test
    fun isStillValid_indexOutOfRange_returnsFalse() {
        val bad = hairSegment.copy(endIndex = 999)
        assertFalse(AnalysisTargetSegmentPolicy.isStillValid(source, bad))
    }

    @Test
    fun fromManual_validSelection_returnsSuccess() {
        val result = AnalysisTargetSegmentPolicy.fromManual(
            source = source,
            start = 0,
            end = 8,
            category = AnalysisCategory.WOMEN_HAIRSTYLE
        )
        val success = result as ManualTargetSegmentResult.Success
        assertEquals("red hair", success.segment.text)
        assertEquals(0, success.segment.startIndex)
        assertEquals(8, success.segment.endIndex)
        assertEquals(AnalysisTargetSource.MANUAL, success.segment.source)
        assertEquals(AnalysisCategory.WOMEN_HAIRSTYLE, success.segment.category)
    }

    @Test
    fun fromManual_emptySelection_returnsEmptySelection() {
        val result = AnalysisTargetSegmentPolicy.fromManual(
            source = source,
            start = 3,
            end = 3,
            category = AnalysisCategory.WOMEN_HAIRSTYLE
        )
        assertEquals(ManualTargetSegmentResult.EmptySelection, result)
    }

    @Test
    fun fromAutoReport_validDetected_returnsAutoSegment() {
        val report = AnalysisReport(
            targetSegment = AnalysisDetectedSegment(
                exactText = "blue dress",
                startIndex = 13,
                endIndex = 23,
                confidence = 0.9,
                reason = "auto"
            )
        )
        val segment = AnalysisTargetSegmentPolicy.fromAutoReport(
            report,
            AnalysisCategory.WOMEN_CLOTHING
        )
        checkNotNull(segment)
        assertEquals("blue dress", segment.text)
        assertEquals(AnalysisTargetSource.AUTO, segment.source)
        assertEquals(0.9, segment.confidence, 0.0)
        assertEquals("auto", segment.reason)
    }

    @Test
    fun fromAutoReport_invalidDetected_returnsNull() {
        val report = AnalysisReport(
            targetSegment = AnalysisDetectedSegment(
                exactText = "",
                startIndex = 0,
                endIndex = 0,
                confidence = 0.1,
                reason = "missing"
            )
        )
        assertNull(
            AnalysisTargetSegmentPolicy.fromAutoReport(
                report,
                AnalysisCategory.WOMEN_CLOTHING
            )
        )
    }

    @Test
    fun replaceSegmentWithWildcardToken_replacesSpan() {
        val replaced = AnalysisTargetSegmentPolicy.replaceSegmentWithWildcardToken(
            source = source,
            segment = hairSegment,
            savedFileName = "hair.txt"
        )
        assertEquals("__hair__ and blue dress", replaced)
    }

    @Test
    fun replaceSegmentWithWildcardToken_nullSegment_keepsSource() {
        val replaced = AnalysisTargetSegmentPolicy.replaceSegmentWithWildcardToken(
            source = source,
            segment = null,
            savedFileName = "hair.txt"
        )
        assertEquals(source, replaced)
    }
}
