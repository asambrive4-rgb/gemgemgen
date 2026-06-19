package com.example.gemgemgen.wildcard.usecase

import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.wildcard.domain.WildcardTextEditPolicy
import com.example.gemgemgen.wildcard.domain.WildcardTextEditResult

class WildcardClipboardUseCase(
    private val clipboardGateway: ClipboardGateway
) {
    fun paste(
        currentText: String,
        undoStack: List<String>
    ): WildcardClipboardPasteResult {
        val text = clipboardGateway.readText()
        if (text.isEmpty()) return WildcardClipboardPasteResult.EmptyClipboard

        return WildcardClipboardPasteResult.Success(
            WildcardTextEditPolicy.paste(
                currentText = currentText,
                undoStack = undoStack,
                pastedText = text
            )
        )
    }

    fun pasteBelow(
        currentText: String,
        undoStack: List<String>
    ): WildcardClipboardPasteResult {
        val text = clipboardGateway.readText()
        if (text.isEmpty()) return WildcardClipboardPasteResult.EmptyClipboard

        return WildcardClipboardPasteResult.Success(
            WildcardTextEditPolicy.pasteBelow(
                currentText = currentText,
                undoStack = undoStack,
                pastedText = text
            )
        )
    }

    fun copy(text: String): Boolean {
        if (text.isEmpty()) return false

        clipboardGateway.writeText(text)
        return true
    }

    fun undo(undoStack: List<String>): WildcardTextEditResult? {
        return WildcardTextEditPolicy.undo(undoStack)
    }
}

sealed interface WildcardClipboardPasteResult {
    data class Success(val edit: WildcardTextEditResult) : WildcardClipboardPasteResult
    data object EmptyClipboard : WildcardClipboardPasteResult
}
