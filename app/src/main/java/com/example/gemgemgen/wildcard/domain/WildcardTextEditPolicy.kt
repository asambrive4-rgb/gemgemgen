package com.example.gemgemgen.wildcard.domain

data class WildcardTextEditResult(
    val text: String,
    val undoStack: List<String>
)

internal object WildcardTextEditPolicy {
    fun paste(
        currentText: String,
        undoStack: List<String>,
        pastedText: String
    ): WildcardTextEditResult {
        return replaceText(currentText, undoStack, pastedText)
    }

    fun pasteBelow(
        currentText: String,
        undoStack: List<String>,
        pastedText: String
    ): WildcardTextEditResult {
        val baseText = when {
            currentText.isEmpty() -> ""
            currentText.endsWith("\n") -> currentText
            else -> "$currentText\n"
        }
        return replaceText(currentText, undoStack, baseText + pastedText)
    }

    fun undo(undoStack: List<String>): WildcardTextEditResult? {
        val previous = undoStack.firstOrNull() ?: return null
        return WildcardTextEditResult(
            text = previous,
            undoStack = undoStack.drop(1)
        )
    }

    private fun replaceText(
        currentText: String,
        undoStack: List<String>,
        newText: String
    ): WildcardTextEditResult {
        return WildcardTextEditResult(
            text = newText,
            undoStack = (listOf(currentText) + undoStack).take(MAX_UNDO_COUNT)
        )
    }

    private const val MAX_UNDO_COUNT = 5
}

