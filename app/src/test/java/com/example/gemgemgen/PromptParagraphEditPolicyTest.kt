package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.PromptParagraphEditPolicy
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptParagraphEditPolicyTest {
    @Test
    fun findParagraph_singleLine_returnsWholeText() {
        val text = "새벽 안개가 낮게 깔린 호숫가 오두막."

        assertEquals(
            PromptParagraphRange(0, text.length),
            PromptParagraphEditPolicy.findParagraph(text, 8)
        )
    }

    @Test
    fun findParagraph_multipleLines_returnsTouchedLineOnly() {
        val text = "첫 번째 장소\n두 번째 장소\n세 번째 장소"
        val secondStart = text.indexOf("두")

        assertEquals(
            PromptParagraphRange(secondStart, secondStart + "두 번째 장소".length),
            PromptParagraphEditPolicy.findParagraph(text, secondStart + 3)
        )
    }

    @Test
    fun findParagraph_blankAndWhitespaceLines_returnsNull() {
        assertNull(PromptParagraphEditPolicy.findParagraph("첫째\n\n셋째", 3))
        assertNull(PromptParagraphEditPolicy.findParagraph("첫째\n   \n셋째", 5))
    }

    @Test
    fun findParagraph_supportsLfCrLfAndCr() {
        val cases = listOf(
            "첫째\n둘째" to 3,
            "첫째\r\n둘째" to 4,
            "첫째\r둘째" to 3
        )

        cases.forEach { (text, secondStart) ->
            assertEquals(
                PromptParagraphRange(secondStart, text.length),
                PromptParagraphEditPolicy.findParagraph(text, secondStart)
            )
        }
    }

    @Test
    fun replace_preservesSurroundingBlankLinesAndSeparators() {
        val text = "첫째\r\n\r\n둘째\n\n셋째"
        val secondStart = text.indexOf("둘째")
        val range = PromptParagraphRange(secondStart, secondStart + 2)

        val result = PromptParagraphEditPolicy.replace(text, range, "새 장소\n보조 설명")

        assertEquals("첫째\r\n\r\n새 장소\n보조 설명\n\n셋째", result)
    }

    @Test
    fun replace_withEmptyText_keepsLineSeparators() {
        val text = "첫째\n둘째\n셋째"
        val secondStart = text.indexOf("둘째")

        val result = PromptParagraphEditPolicy.replace(
            text,
            PromptParagraphRange(secondStart, secondStart + 2),
            ""
        )

        assertEquals("첫째\n\n셋째", result)
    }
}
