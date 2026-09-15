// 역할: 프롬프트 입력창에서 드래그 선택된 텍스트를 안전하게 추출하는 규칙을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.ui.selectedPromptText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptSelectionTest {

    @Test
    fun selectedPromptText_returnsDraggedRangeAndPreservesInternalLineBreaks() {
        assertEquals(
            "선택된\n문장",
            selectedPromptText(
                text = "앞 선택된\n문장 뒤",
                selectionStart = 2,
                selectionEnd = 8
            )
        )
    }

    @Test
    fun selectedPromptText_returnsNullWhenSelectionIsCollapsed() {
        assertNull(
            selectedPromptText(
                text = "프롬프트",
                selectionStart = 3,
                selectionEnd = 3
            )
        )
    }

    @Test
    fun selectedPromptText_normalizesReversedAndOutOfBoundsSelection() {
        assertEquals(
            "프롬프트",
            selectedPromptText(
                text = "프롬프트",
                selectionStart = 99,
                selectionEnd = -10
            )
        )
    }
}
