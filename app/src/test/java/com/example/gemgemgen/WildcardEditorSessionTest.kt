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
}
