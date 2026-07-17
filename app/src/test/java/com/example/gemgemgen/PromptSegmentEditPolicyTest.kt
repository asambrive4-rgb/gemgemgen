package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.PromptSegmentEditPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptSegmentEditPolicyTest {
    @Test
    fun replace_preservesTextOutsideTargetWhenTargetPositionShifted() {
        val result = PromptSegmentEditPolicy.replace(
            currentText = "quality, red hair and blue dress, masterpiece",
            expectedSegment = "blue dress",
            replacement = "검은 원피스",
            preferredStartIndex = 13
        )

        assertEquals("quality, red hair and 검은 원피스, masterpiece", result?.updatedText)
        assertEquals(22, result?.startIndex)
    }

    @Test
    fun replace_prefersTrackedPositionWhenSameTextAppearsMoreThanOnce() {
        val result = PromptSegmentEditPolicy.replace(
            currentText = "blue dress and blue dress",
            expectedSegment = "blue dress",
            replacement = "검은 원피스",
            preferredStartIndex = 15
        )

        assertEquals("blue dress and 검은 원피스", result?.updatedText)
        assertEquals(15, result?.startIndex)
    }

    @Test
    fun replace_returnsNullWhenTargetIsMissing() {
        val result = PromptSegmentEditPolicy.replace(
            currentText = "red hair only",
            expectedSegment = "blue dress",
            replacement = "검은 원피스",
            preferredStartIndex = 13
        )

        assertEquals(null, result)
    }
}
