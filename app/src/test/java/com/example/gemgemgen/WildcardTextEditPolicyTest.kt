package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WildcardTextEditPolicyTest {
    @Test
    fun paste_replacesTextAndStoresPreviousText() {
        val result = WildcardTextEditPolicy.paste(
            currentText = "black hair",
            undoStack = emptyList(),
            pastedText = "silver hair"
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
    fun paste_keepsMostRecentFiveUndoItems() {
        val result = WildcardTextEditPolicy.paste(
            currentText = "current",
            undoStack = listOf("one", "two", "three", "four", "five"),
            pastedText = "next"
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
