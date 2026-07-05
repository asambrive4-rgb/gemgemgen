package com.example.gemgemgen.wildcard.usecase

import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.wildcard.domain.WildcardTextEditPolicy
import com.example.gemgemgen.wildcard.domain.WildcardTextEditResult
import kotlinx.coroutines.withContext

class WildcardClipboardUseCase(
    private val clipboardGateway: ClipboardGateway,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun paste(
        currentText: String,
        undoStack: List<String>
    ): WildcardClipboardPasteResult = withContext(dispatchers.io) {
        val text = clipboardGateway.readText()
        if (text.isEmpty()) return@withContext WildcardClipboardPasteResult.EmptyClipboard

        WildcardClipboardPasteResult.Success(
            WildcardTextEditPolicy.paste(
                currentText = currentText,
                undoStack = undoStack,
                pastedText = text
            )
        )
    }

    suspend fun pasteBelow(
        currentText: String,
        undoStack: List<String>
    ): WildcardClipboardPasteResult = withContext(dispatchers.io) {
        val text = clipboardGateway.readText()
        if (text.isEmpty()) return@withContext WildcardClipboardPasteResult.EmptyClipboard

        WildcardClipboardPasteResult.Success(
            WildcardTextEditPolicy.pasteBelow(
                currentText = currentText,
                undoStack = undoStack,
                pastedText = text
            )
        )
    }

    suspend fun copy(text: String): Boolean = withContext(dispatchers.io) {
        if (text.isEmpty()) return@withContext false

        clipboardGateway.writeText(text)
        true
    }

    fun undo(undoStack: List<String>): WildcardTextEditResult? {
        return WildcardTextEditPolicy.undo(undoStack)
    }
}

sealed interface WildcardClipboardPasteResult {
    data class Success(val edit: WildcardTextEditResult) : WildcardClipboardPasteResult
    data object EmptyClipboard : WildcardClipboardPasteResult
}
