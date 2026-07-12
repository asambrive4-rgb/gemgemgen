package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.PromptEditorSession
import com.example.gemgemgen.automation.domain.PromptParagraphActionResult
import com.example.gemgemgen.automation.domain.PromptParagraphMessageKey
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.PromptTypingChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptEditorSessionTest {
    @Test
    fun toggleSelectionMode_enablesGuideAndClears() {
        val enabled = PromptEditorSession(text = "인물\n장소").toggleSelectionMode()

        assertTrue(enabled.isParagraphSelectionMode)
        assertEquals(PromptParagraphMessageKey.Guide, enabled.messageKey)
        assertEquals(null, enabled.selectedParagraphRange)

        val cleared = enabled.toggleSelectionMode()
        assertFalse(cleared.isParagraphSelectionMode)
        assertEquals(PromptParagraphMessageKey.None, cleared.messageKey)
    }

    @Test
    fun selectAt_selectsNonBlankLineOrReportsEmpty() {
        val base = PromptEditorSession(text = "첫째\n둘째\n셋째").toggleSelectionMode()

        val selected = base.selectAt(4)
        assertEquals(PromptParagraphRange(3, 5), selected.selectedParagraphRange)
        assertEquals(PromptParagraphMessageKey.Selected, selected.messageKey)

        val blankLine = PromptEditorSession(text = "첫째\n\n셋째")
            .toggleSelectionMode()
            .selectAt(3)
        assertEquals(null, blankLine.selectedParagraphRange)
        assertEquals(PromptParagraphMessageKey.EmptyParagraph, blankLine.messageKey)
    }

    @Test
    fun prepareReplaceSelected_mutatesOnlySelectedParagraph() {
        val session = PromptEditorSession(text = "인물\n장소\n조명")
            .toggleSelectionMode()
            .selectAt(4)

        val result = session.prepareReplaceSelected("새 장소\n보조 설명")
        val mutation = (result as PromptParagraphActionResult.Mutated).mutation

        assertEquals("인물\n새 장소\n보조 설명\n조명", mutation.session.text)
        assertFalse(mutation.session.isParagraphSelectionMode)
        assertEquals("인물\n장소\n조명", mutation.previousTextForUndo)
        assertEquals("인물\n새 장소\n보조 설명".length, mutation.selectionStart)
    }

    @Test
    fun prepareReplaceSelected_blankKeepsSelection() {
        val session = PromptEditorSession(text = "인물\n장소")
            .toggleSelectionMode()
            .selectAt(4)

        val result = session.prepareReplaceSelected("   ") as PromptParagraphActionResult.SessionOnly

        assertEquals("인물\n장소", result.session.text)
        assertTrue(result.session.isParagraphSelectionMode)
        assertEquals(PromptParagraphRange(3, 5), result.session.selectedParagraphRange)
        assertEquals(PromptParagraphMessageKey.EmptyClipboard, result.session.messageKey)
    }

    @Test
    fun prepareReplaceSelected_withoutRange_asksToSelectFirst() {
        val session = PromptEditorSession(text = "인물\n장소").toggleSelectionMode()

        val result = session.prepareReplaceSelected("새 장소") as PromptParagraphActionResult.SessionOnly

        assertEquals(PromptParagraphMessageKey.SelectFirst, result.session.messageKey)
        assertTrue(result.session.isParagraphSelectionMode)
    }

    @Test
    fun prepareDeleteSelected_removesParagraphText() {
        val session = PromptEditorSession(text = "인물\n장소\n조명")
            .toggleSelectionMode()
            .selectAt(4)

        val mutation =
            (session.prepareDeleteSelected() as PromptParagraphActionResult.Mutated).mutation

        assertEquals("인물\n\n조명", mutation.session.text)
        assertEquals("인물\n".length, mutation.selectionStart)
        assertFalse(mutation.session.isParagraphSelectionMode)
    }

    @Test
    fun cancelSelection_isIdempotentWhenAlreadyClear() {
        val session = PromptEditorSession()
        assertEquals(session, session.cancelSelection())
    }

    @Test
    fun classifyTypingChange_coversEchoUnchangedAndUserEdit() {
        assertEquals(
            PromptTypingChange.IgnoredEcho,
            PromptEditorSession.classifyTypingChange(
                previousText = "a",
                newText = "b",
                programmaticEchoText = "b"
            )
        )
        assertEquals(
            PromptTypingChange.Unchanged,
            PromptEditorSession.classifyTypingChange(
                previousText = "same",
                newText = "same",
                programmaticEchoText = null
            )
        )
        assertEquals(
            PromptTypingChange.UserEdit(previousText = "a", newText = "ab"),
            PromptEditorSession.classifyTypingChange(
                previousText = "a",
                newText = "ab",
                programmaticEchoText = null
            )
        )
    }
}
