package com.example.gemgemgen.wildcard.domain

data class WildcardEditorSession(
    val selectedFile: WildcardTextFile? = null,
    val savedText: String = "",
    val editingText: String = "",
    val undoStack: List<String> = emptyList()
) {
    val hasUnsavedChanges: Boolean
        get() = selectedFile != null && savedText != editingText

    fun open(file: WildcardTextFile, text: String): WildcardEditorSession {
        return copy(
            selectedFile = file,
            savedText = text,
            editingText = text,
            undoStack = emptyList()
        )
    }

    fun edit(text: String): WildcardEditorSession {
        return copy(editingText = text)
    }

    fun apply(result: WildcardTextEditResult): WildcardEditorSession {
        return copy(
            editingText = result.text,
            undoStack = result.undoStack
        )
    }

    fun markSaved(): WildcardEditorSession {
        return copy(savedText = editingText)
    }

    fun rename(file: WildcardTextFile): WildcardEditorSession {
        return copy(selectedFile = file)
    }

    fun clear(): WildcardEditorSession = WildcardEditorSession()

    /**
     * Drops heavy buffers when the wildcard tab is not visible.
     * Clean: clear body + undo (reload on re-enter). Dirty: clear undo only.
     */
    fun trimForInactiveTab(): WildcardEditorSession {
        if (selectedFile == null) return this
        return if (hasUnsavedChanges) {
            if (undoStack.isEmpty()) this else copy(undoStack = emptyList())
        } else {
            if (savedText.isEmpty() && editingText.isEmpty() && undoStack.isEmpty()) {
                this
            } else {
                copy(savedText = "", editingText = "", undoStack = emptyList())
            }
        }
    }
}
