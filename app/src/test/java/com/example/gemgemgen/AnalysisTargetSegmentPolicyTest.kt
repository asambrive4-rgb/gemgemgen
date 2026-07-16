package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisDetectedSegment
import com.example.gemgemgen.analysis.domain.AnalysisReport
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegmentPolicy
import com.example.gemgemgen.analysis.domain.AnalysisTargetSource
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
        source = AnalysisTargetSource.AUTO,
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

    @Test
    fun replaceSegmentWithText_replacesSpanWithFragment() {
        val replaced = AnalysisTargetSegmentPolicy.replaceSegmentWithText(
            source = source,
            segment = hairSegment,
            replacement = "black wavy hair"
        )
        assertEquals("black wavy hair and blue dress", replaced)
    }

    @Test
    fun segmentAfterReplacement_keepsStartAndUpdatesEnd() {
        val next = AnalysisTargetSegmentPolicy.segmentAfterReplacement(
            previous = hairSegment,
            replacement = "black wavy hair"
        )
        assertEquals("black wavy hair", next.text)
        assertEquals(hairSegment.startIndex, next.startIndex)
        assertEquals(hairSegment.startIndex + "black wavy hair".length, next.endIndex)
    }
}
