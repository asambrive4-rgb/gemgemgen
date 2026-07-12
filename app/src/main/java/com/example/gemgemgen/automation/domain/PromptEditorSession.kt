package com.example.gemgemgen.automation.domain

/**
 * 자동화 탭 프롬프트 편집 세션(문단 선택 모드·선택 구간·본문).
 * Compose TextField / 코루틴 debounce 는 포함하지 않는다.
 */
enum class PromptParagraphMessageKey {
    None,
    Guide,
    Selected,
    EmptyParagraph,
    SelectFirst,
    EmptyClipboard
}

data class PromptTextMutation(
    val session: PromptEditorSession,
    val previousTextForUndo: String,
    val selectionStart: Int,
    val selectionEnd: Int
)

sealed class PromptParagraphActionResult {
    data object NoOp : PromptParagraphActionResult()
    data class SessionOnly(val session: PromptEditorSession) : PromptParagraphActionResult()
    data class Mutated(val mutation: PromptTextMutation) : PromptParagraphActionResult()
}

sealed class PromptTypingChange {
    data object IgnoredEcho : PromptTypingChange()
    data object Unchanged : PromptTypingChange()
    data class UserEdit(
        val previousText: String,
        val newText: String
    ) : PromptTypingChange()
}

data class PromptEditorSession(
    val text: String = "",
    val isParagraphSelectionMode: Boolean = false,
    val selectedParagraphRange: PromptParagraphRange? = null,
    val messageKey: PromptParagraphMessageKey = PromptParagraphMessageKey.None
) {
    fun withText(text: String): PromptEditorSession = copy(text = text)

    fun toggleSelectionMode(): PromptEditorSession {
        return if (isParagraphSelectionMode) {
            cancelSelection()
        } else {
            copy(
                isParagraphSelectionMode = true,
                selectedParagraphRange = null,
                messageKey = PromptParagraphMessageKey.Guide
            )
        }
    }

    fun cancelSelection(): PromptEditorSession {
        if (!isParagraphSelectionMode &&
            selectedParagraphRange == null &&
            messageKey == PromptParagraphMessageKey.None
        ) {
            return this
        }
        return copy(
            isParagraphSelectionMode = false,
            selectedParagraphRange = null,
            messageKey = PromptParagraphMessageKey.None
        )
    }

    fun selectAt(offset: Int): PromptEditorSession {
        if (!isParagraphSelectionMode) return this

        val range = PromptParagraphEditPolicy.findParagraph(text = text, offset = offset)
        return if (range == null) {
            copy(
                selectedParagraphRange = null,
                messageKey = PromptParagraphMessageKey.EmptyParagraph
            )
        } else {
            copy(
                selectedParagraphRange = range,
                messageKey = PromptParagraphMessageKey.Selected
            )
        }
    }

    fun prepareReplaceSelected(replacement: String): PromptParagraphActionResult {
        if (!isParagraphSelectionMode) return PromptParagraphActionResult.NoOp

        val range = selectedParagraphRange
        return when {
            range == null -> PromptParagraphActionResult.SessionOnly(
                copy(messageKey = PromptParagraphMessageKey.SelectFirst)
            )
            replacement.isBlank() -> PromptParagraphActionResult.SessionOnly(
                copy(messageKey = PromptParagraphMessageKey.EmptyClipboard)
            )
            !isRangeInBounds(range) -> PromptParagraphActionResult.SessionOnly(cancelSelection())
            else -> {
                val newText = PromptParagraphEditPolicy.replace(text, range, replacement)
                PromptParagraphActionResult.Mutated(
                    PromptTextMutation(
                        session = clearedSelection(newText),
                        previousTextForUndo = text,
                        selectionStart = range.start + replacement.length,
                        selectionEnd = range.start + replacement.length
                    )
                )
            }
        }
    }

    fun prepareDeleteSelected(): PromptParagraphActionResult {
        val range = selectedParagraphRange ?: return PromptParagraphActionResult.NoOp
        if (!isRangeInBounds(range)) {
            return PromptParagraphActionResult.SessionOnly(cancelSelection())
        }

        val newText = PromptParagraphEditPolicy.replace(text, range, "")
        return PromptParagraphActionResult.Mutated(
            PromptTextMutation(
                session = clearedSelection(newText),
                previousTextForUndo = text,
                selectionStart = range.start,
                selectionEnd = range.start
            )
        )
    }

    fun afterWholeReplace(newText: String): PromptEditorSession {
        return clearedSelection(newText)
    }

    fun afterUndo(restoredText: String): PromptEditorSession {
        return clearedSelection(restoredText)
    }

    fun afterPaste(newText: String): PromptEditorSession {
        return copy(text = newText)
    }

    private fun clearedSelection(newText: String): PromptEditorSession {
        return copy(
            text = newText,
            isParagraphSelectionMode = false,
            selectedParagraphRange = null,
            messageKey = PromptParagraphMessageKey.None
        )
    }

    private fun isRangeInBounds(range: PromptParagraphRange): Boolean {
        return range.start in 0..text.length && range.endExclusive in range.start..text.length
    }

    companion object {
        fun classifyTypingChange(
            previousText: String,
            newText: String,
            programmaticEchoText: String?
        ): PromptTypingChange {
            if (programmaticEchoText != null && programmaticEchoText == newText) {
                return PromptTypingChange.IgnoredEcho
            }
            if (previousText == newText) {
                return PromptTypingChange.Unchanged
            }
            return PromptTypingChange.UserEdit(
                previousText = previousText,
                newText = newText
            )
        }
    }
}
