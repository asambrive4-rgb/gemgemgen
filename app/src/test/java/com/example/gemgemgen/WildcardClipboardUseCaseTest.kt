package com.example.gemgemgen

import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.wildcard.usecase.WildcardClipboardPasteResult
import com.example.gemgemgen.wildcard.usecase.WildcardClipboardUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardClipboardUseCaseTest {
    @Test
    fun paste_withEmptyClipboard_returnsEmptyClipboard() = runBlocking {
        val useCase = useCase(readableText = "")

        val result = useCase.paste(currentText = "current", undoStack = emptyList())

        assertEquals(WildcardClipboardPasteResult.EmptyClipboard, result)
    }

    @Test
    fun pasteBelow_appendsClipboardTextAndUndoSnapshot() = runBlocking {
        val useCase = useCase(readableText = "silver hair")

        val result = useCase.pasteBelow(
            currentText = "black hair",
            undoStack = emptyList()
        )

        assertTrue(result is WildcardClipboardPasteResult.Success)
        val edit = (result as WildcardClipboardPasteResult.Success).edit
        assertEquals("black hair\nsilver hair", edit.text)
        assertEquals(listOf("black hair"), edit.undoStack)
    }

    @Test
    fun copy_writesNonEmptyTextOnly() = runBlocking {
        val clipboardGateway = FakeClipboardGateway()
        val useCase = useCase(clipboardGateway = clipboardGateway)

        assertFalse(useCase.copy(""))
        assertEquals("", clipboardGateway.writtenText)

        assertTrue(useCase.copy("prompt"))
        assertEquals("prompt", clipboardGateway.writtenText)
    }

    private fun useCase(
        readableText: String = "",
        clipboardGateway: FakeClipboardGateway = FakeClipboardGateway(readableText)
    ): WildcardClipboardUseCase {
        return WildcardClipboardUseCase(
            clipboardGateway = clipboardGateway,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )
    }

    private class FakeClipboardGateway(
        private val readableText: String = ""
    ) : ClipboardGateway {
        var writtenText: String = ""

        override fun readText(): String = readableText

        override fun writeText(text: String) {
            writtenText = text
        }
    }
}
