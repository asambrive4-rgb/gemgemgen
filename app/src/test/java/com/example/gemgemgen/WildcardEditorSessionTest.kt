package com.example.gemgemgen

import com.example.gemgemgen.wildcard.domain.WildcardEditorSession
import com.example.gemgemgen.wildcard.domain.WildcardTextEditResult
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardEditorSessionTest {
    @Test
    fun editAndSave_manageUnsavedStateWithoutUiState() {
        val file = WildcardTextFile("hair", "hair.txt")
        val opened = WildcardEditorSession().open(file, "black hair")

        val edited = opened.edit("silver hair")
        assertTrue(edited.hasUnsavedChanges)

        val saved = edited.markSaved()
        assertFalse(saved.hasUnsavedChanges)
        assertEquals("silver hair", saved.savedText)
    }

    @Test
    fun apply_updatesTextAndUndoStackTogether() {
        val session = WildcardEditorSession().apply(
            WildcardTextEditResult(
                text = "new text",
                undoStack = listOf("old text")
            )
        )

        assertEquals("new text", session.editingText)
        assertEquals(listOf("old text"), session.undoStack)
    }

    @Test
    fun trimForInactiveTab_clearsBodyAndUndoWhenClean() {
        val file = WildcardTextFile("hair", "hair.txt")
        val session = WildcardEditorSession()
            .open(file, "black hair")
            .apply(WildcardTextEditResult("black hair", listOf("older")))
            .markSaved()

        val trimmed = session.trimForInactiveTab()

        assertEquals(file, trimmed.selectedFile)
        assertEquals("", trimmed.savedText)
        assertEquals("", trimmed.editingText)
        assertTrue(trimmed.undoStack.isEmpty())
        assertFalse(trimmed.hasUnsavedChanges)
    }

    @Test
    fun trimForInactiveTab_clearsOnlyUndoWhenDirty() {
        val file = WildcardTextFile("hair", "hair.txt")
        val session = WildcardEditorSession()
            .open(file, "black hair")
            .edit("silver hair")
            .apply(WildcardTextEditResult("silver hair", listOf("black hair")))

        val trimmed = session.trimForInactiveTab()

        assertEquals(file, trimmed.selectedFile)
        assertEquals("black hair", trimmed.savedText)
        assertEquals("silver hair", trimmed.editingText)
        assertTrue(trimmed.undoStack.isEmpty())
        assertTrue(trimmed.hasUnsavedChanges)
    }
}
