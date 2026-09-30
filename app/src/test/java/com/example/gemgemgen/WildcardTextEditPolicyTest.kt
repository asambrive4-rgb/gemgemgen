// 역할: 와일드카드 파일 내 텍스트 편집 및 중복 제거 정책을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.wildcard.domain.WildcardTextEditPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WildcardTextEditPolicyTest {
    @Test
    fun replaceText_replacesTextAndStoresPreviousText() {
        val result = WildcardTextEditPolicy.replaceText(
            currentText = "black hair",
            undoStack = emptyList(),
            newText = "silver hair"
        )

        assertEquals("silver hair", result.text)
        assertEquals(listOf("black hair"), result.undoStack)
    }

    @Test
    fun pasteBelow_appendsTextOnNewLine() {
        val result = WildcardTextEditPolicy.pasteBelow(
            currentText = "black hair",
            undoStack = emptyList(),
            pastedText = "silver hair"
        )

        assertEquals("black hair\nsilver hair", result.text)
        assertEquals(listOf("black hair"), result.undoStack)
    }

    @Test
    fun pasteBelow_keepsExistingTrailingNewLine() {
        val result = WildcardTextEditPolicy.pasteBelow(
            currentText = "black hair\n",
            undoStack = emptyList(),
            pastedText = "silver hair"
        )

        assertEquals("black hair\nsilver hair", result.text)
    }

    @Test
    fun replaceText_keepsMostRecentFiveUndoItems() {
        val result = WildcardTextEditPolicy.replaceText(
            currentText = "current",
            undoStack = listOf("one", "two", "three", "four", "five"),
            newText = "next"
        )

        assertEquals(listOf("current", "one", "two", "three", "four"), result.undoStack)
    }

    @Test
    fun undo_restoresPreviousTextAndDropsUndoItem() {
        val result = WildcardTextEditPolicy.undo(listOf("previous", "older"))

        assertEquals("previous", result?.text)
        assertEquals(listOf("older"), result?.undoStack)
    }

    @Test
    fun undo_returnsNullWhenUndoStackIsEmpty() {
        assertNull(WildcardTextEditPolicy.undo(emptyList()))
    }
}
