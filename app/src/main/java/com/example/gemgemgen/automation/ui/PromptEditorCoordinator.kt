package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextRange
import com.example.gemgemgen.automation.domain.PromptEditorSession
import com.example.gemgemgen.automation.domain.PromptParagraphActionResult
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.PromptSegmentEditPolicy
import com.example.gemgemgen.automation.domain.PromptTextMutation
import com.example.gemgemgen.automation.domain.PromptTypingChange
import com.example.gemgemgen.automation.domain.PromptUndoHistory
import com.example.gemgemgen.automation.domain.SystemInstructionPrompt
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PromptEditorUiState(
    val promptTemplate: String = "",
    val isParagraphSelectionMode: Boolean = false,
    val selectedParagraphRange: PromptParagraphRange? = null,
    val paragraphSelectionMessage: String = "",
    val canUndoPromptEdit: Boolean = false
)

/**
 * 프롬프트 텍스트 편집기(TextFieldState)의 입력 디바운스, 커서 위치 동기화,
 * 문단 단위 편집 세션, 실행 취소(Undo) 스냅샷 관리, 클립보드 입출력 및 와일드카드 치환을 전담하는 코디네이터.
 */
class PromptEditorCoordinator(
    private val clipboardGateway: ClipboardGateway,
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    initialPrompt: String = ""
) {
    val textFieldState = TextFieldState()
    private val promptUndoHistory = PromptUndoHistory()
    private var promptUndoDebounceJob: Job? = null
    private var ignoredPromptChangeText: String? = null
    private var promptTemplateValue: String = initialPrompt
    private var promptEditorSession = PromptEditorSession(text = initialPrompt)

    private val _editorUiState = MutableStateFlow(
        PromptEditorUiState(promptTemplate = initialPrompt)
    )
    val editorUiState: StateFlow<PromptEditorUiState> = _editorUiState.asStateFlow()

    init {
        if (initialPrompt.isNotEmpty()) {
            applyPromptTemplateText(initialPrompt)
        }
    }

    fun onPromptTemplateChange(value: String) {
        onPromptTemplateChange(value, updateTextFieldState = true)
    }

    fun onPromptTemplateFromEditor(value: String) {
        onPromptTemplateChange(value, updateTextFieldState = false)
    }

    fun onPromptTemplateChange(value: String, updateTextFieldState: Boolean) {
        if (updateTextFieldState && !textFieldState.text.contentEquals(value)) {
            textFieldState.setTextAndPlaceCursorAtEnd(value)
        }
        when (
            val change = PromptEditorSession.classifyTypingChange(
                previousText = promptTemplateValue,
                newText = value,
                programmaticEchoText = ignoredPromptChangeText
            )
        ) {
            PromptTypingChange.IgnoredEcho -> {
                ignoredPromptChangeText = null
                setPromptTextOnly(value)
                publishPromptTemplateToUiState(value, force = true)
            }
            PromptTypingChange.Unchanged -> Unit
            is PromptTypingChange.UserEdit -> {
                setPromptTextOnly(change.newText)
                schedulePromptTypingUndo(change.previousText)
                publishPromptTemplateToUiState(change.newText, force = updateTextFieldState)
            }
        }
    }

    private fun publishPromptTemplateToUiState(value: String, force: Boolean) {
        _editorUiState.update { state ->
            if (state.promptTemplate == value) {
                state
            } else if (!force && state.promptTemplate.isBlank() == value.isBlank()) {
                state
            } else {
                state.copy(promptTemplate = value)
            }
        }
    }

    fun toggleParagraphSelectionMode() {
        syncEditorTextFromCurrent()
        publishEditorSession(promptEditorSession.toggleSelectionMode())
    }

    fun selectPromptParagraphAt(offset: Int) {
        syncEditorTextFromCurrent()
        publishEditorSession(promptEditorSession.selectAt(offset))
    }

    fun deleteSelectedPromptParagraph() {
        syncEditorTextFromCurrent()
        when (val result = promptEditorSession.prepareDeleteSelected()) {
            PromptParagraphActionResult.NoOp -> Unit
            is PromptParagraphActionResult.SessionOnly -> publishEditorSession(result.session)
            is PromptParagraphActionResult.Mutated -> applyTextMutation(result.mutation)
        }
    }

    fun cancelParagraphSelection() {
        publishEditorSession(promptEditorSession.cancelSelection())
    }

    fun importPromptFromClipboard() {
        syncPromptTemplateFromTextField()
        scope.launch {
            val text = withContext(dispatchers.io) {
                clipboardGateway.readText()
            }
            val state = _editorUiState.value
            if (!state.isParagraphSelectionMode) {
                replaceWholePromptTemplate(text)
                return@launch
            }
            replaceSelectedPromptParagraph(text)
        }
    }

    fun currentPromptTemplateText(): String {
        syncPromptTemplateFromTextField()
        return promptTemplateValue
    }

    fun replacePromptTemplateEntirely(replacement: String) {
        replaceWholePromptTemplate(replacement)
    }

    fun replacePromptTemplateSegment(
        expectedSegment: String,
        replacement: String,
        preferredStartIndex: Int
    ): Int? {
        syncPromptTemplateFromTextField()
        val currentText = promptTemplateValue
        val edit = PromptSegmentEditPolicy.replace(
            currentText = currentText,
            expectedSegment = expectedSegment,
            replacement = replacement,
            preferredStartIndex = preferredStartIndex
        ) ?: return null

        recordImmediatePromptUndo(currentText)
        ignoredPromptChangeText = edit.updatedText
        textFieldState.edit {
            replace(edit.startIndex, edit.previousEndIndex, replacement)
            selection = TextRange(edit.replacementEndIndex)
        }
        promptTemplateValue = edit.updatedText
        publishEditorSession(
            session = promptEditorSession.afterWholeReplace(edit.updatedText),
            canUndoPromptEdit = hasPromptUndo()
        )
        return edit.startIndex
    }

    fun copyPromptToClipboard(isBlocked: Boolean = false) {
        syncPromptTemplateFromTextField()
        val text = _editorUiState.value.promptTemplate
        if (isBlocked || text.isBlank()) return

        scope.launch {
            withContext(dispatchers.io) {
                clipboardGateway.writeText(text)
            }
        }
    }

    fun pastePromptFromClipboard() {
        syncPromptTemplateFromTextField()
        scope.launch {
            val text = withContext(dispatchers.io) {
                clipboardGateway.readText()
            }
            if (text.isEmpty()) return@launch

            val currentText = textFieldState.text.toString()
            recordImmediatePromptUndo(currentText)

            val selection = textFieldState.selection
            val start = selection.min
            val end = selection.max

            textFieldState.edit {
                replace(start, end, text)
                this.selection = TextRange(start + text.length)
            }

            val newText = textFieldState.text.toString()
            promptTemplateValue = newText
            promptEditorSession = promptEditorSession.afterPaste(newText)
            _editorUiState.update {
                it.copy(
                    promptTemplate = newText,
                    canUndoPromptEdit = hasPromptUndo()
                )
            }
        }
    }

    fun insertSystemInstruction(isBlocked: Boolean = false) {
        if (isBlocked) return
        syncPromptTemplateFromTextField()
        val currentText = promptTemplateValue
        val newText = SystemInstructionPrompt.prependTo(currentText)
        if (currentText == newText) return

        recordImmediatePromptUndo(currentText)
        ignoredPromptChangeText = newText
        val cursorAfter = if (currentText.isEmpty()) {
            SystemInstructionPrompt.text.length
        } else {
            SystemInstructionPrompt.text.length + 2
        }
        textFieldState.edit {
            replace(0, length, newText)
            selection = TextRange(cursorAfter.coerceIn(0, newText.length))
        }
        promptTemplateValue = newText
        publishEditorSession(
            session = promptEditorSession.afterWholeReplace(newText),
            canUndoPromptEdit = hasPromptUndo()
        )
    }

    fun applyWildcardTokenSuggestion(
        token: String,
        isBlocked: Boolean = false,
        candidates: List<WildcardTokenAutocomplete.Candidate> = emptyList()
    ) {
        val state = _editorUiState.value
        if (isBlocked || state.isParagraphSelectionMode) return
        if (token.isBlank()) return
        if (candidates.none { it.token == token }) return

        val currentText = textFieldState.text.toString()
        val selection = textFieldState.selection
        if (selection.min != selection.max) return

        val replacement = WildcardTokenAutocomplete.replaceWordAtCursor(
            text = currentText,
            cursor = selection.max,
            token = token
        ) ?: return
        if (replacement.newText == currentText) return

        recordImmediatePromptUndo(currentText)
        ignoredPromptChangeText = replacement.newText
        val cursorAfter = replacement.cursorAfter.coerceIn(0, replacement.newText.length)
        textFieldState.edit {
            replace(0, length, replacement.newText)
            this.selection = TextRange(cursorAfter)
        }
        promptTemplateValue = replacement.newText
        promptEditorSession = promptEditorSession.withText(replacement.newText)
        _editorUiState.update {
            it.copy(
                promptTemplate = replacement.newText,
                canUndoPromptEdit = hasPromptUndo()
            )
        }
    }

    fun replaceSelectedPromptParagraph(replacement: String) {
        syncEditorTextFromCurrent()
        when (val result = promptEditorSession.prepareReplaceSelected(replacement)) {
            PromptParagraphActionResult.NoOp -> Unit
            is PromptParagraphActionResult.SessionOnly -> publishEditorSession(result.session)
            is PromptParagraphActionResult.Mutated -> applyTextMutation(result.mutation)
        }
    }

    fun undoPromptEdit(isBlocked: Boolean = false) {
        if (isBlocked) return

        commitPendingPromptUndo()
        val previous = promptUndoHistory.popUndo() ?: return
        applyPromptTemplateText(previous)
        promptTemplateValue = previous
        publishEditorSession(
            session = promptEditorSession.afterUndo(previous),
            canUndoPromptEdit = hasPromptUndo()
        )
    }

    fun restorePrompt(prompt: String) {
        commitPendingPromptUndo()
        if (promptTemplateValue != prompt) {
            promptUndoHistory.recordImmediateSnapshot(promptTemplateValue)
        }
        applyPromptTemplateText(prompt)
        promptTemplateValue = prompt
        publishEditorSession(
            session = promptEditorSession.afterWholeReplace(prompt),
            canUndoPromptEdit = hasPromptUndo()
        )
    }

    private fun replaceWholePromptTemplate(replacement: String) {
        syncEditorTextFromCurrent()
        val currentText = promptTemplateValue
        if (currentText == replacement) return

        recordImmediatePromptUndo(currentText)
        applyPromptTemplateText(replacement)
        promptTemplateValue = replacement
        publishEditorSession(
            session = promptEditorSession.afterWholeReplace(replacement),
            canUndoPromptEdit = hasPromptUndo()
        )
    }

    private fun applyTextMutation(mutation: PromptTextMutation) {
        recordImmediatePromptUndo(mutation.previousTextForUndo)
        val newText = mutation.session.text
        ignoredPromptChangeText = newText
        val start = mutation.selectionStart.coerceIn(0, newText.length)
        val end = mutation.selectionEnd.coerceIn(start, newText.length)
        textFieldState.edit {
            replace(0, length, newText)
            selection = TextRange(start, end)
        }
        promptTemplateValue = newText
        publishEditorSession(mutation.session)
    }

    private fun applyPromptTemplateText(text: String) {
        if (!textFieldState.text.contentEquals(text)) {
            ignoredPromptChangeText = text
            textFieldState.setTextAndPlaceCursorAtEnd(text)
        }
    }

    fun syncPromptTemplateFromTextField() {
        val currentText = textFieldState.text.toString()
        if (promptTemplateValue == currentText &&
            _editorUiState.value.promptTemplate == currentText &&
            promptEditorSession.text == currentText
        ) {
            return
        }
        setPromptTextOnly(currentText)
        _editorUiState.update {
            if (it.promptTemplate == currentText) it else it.copy(promptTemplate = currentText)
        }
    }

    private fun syncEditorTextFromCurrent() {
        val currentText = textFieldState.text.toString()
        if (promptTemplateValue != currentText || promptEditorSession.text != currentText) {
            setPromptTextOnly(currentText)
        }
    }

    private fun setPromptTextOnly(text: String) {
        promptTemplateValue = text
        promptEditorSession = promptEditorSession.withText(text)
    }

    private fun publishEditorSession(
        session: PromptEditorSession,
        canUndoPromptEdit: Boolean? = null
    ) {
        promptEditorSession = session
        promptTemplateValue = session.text
        val message = AutomationUiText.paragraphMessage(session.messageKey)
        _editorUiState.update { state ->
            val nextCanUndo = canUndoPromptEdit ?: state.canUndoPromptEdit
            if (state.promptTemplate == session.text &&
                state.isParagraphSelectionMode == session.isParagraphSelectionMode &&
                state.selectedParagraphRange == session.selectedParagraphRange &&
                state.paragraphSelectionMessage == message &&
                state.canUndoPromptEdit == nextCanUndo
            ) {
                state
            } else {
                state.copy(
                    promptTemplate = session.text,
                    isParagraphSelectionMode = session.isParagraphSelectionMode,
                    selectedParagraphRange = session.selectedParagraphRange,
                    paragraphSelectionMessage = message,
                    canUndoPromptEdit = nextCanUndo
                )
            }
        }
    }

    private fun schedulePromptTypingUndo(previous: String) {
        promptUndoHistory.recordTypingSnapshot(previous)
        promptUndoDebounceJob?.cancel()
        promptUndoDebounceJob = scope.launch {
            delay(PROMPT_UNDO_DEBOUNCE_MILLIS)
            commitPendingPromptUndo()
        }
        updatePromptUndoAvailability()
    }

    private fun recordImmediatePromptUndo(snapshot: String) {
        commitPendingPromptUndo()
        promptUndoHistory.recordImmediateSnapshot(snapshot)
        updatePromptUndoAvailability()
    }

    private fun commitPendingPromptUndo() {
        promptUndoHistory.commitPendingTyping(promptTemplateValue)
        promptUndoDebounceJob?.cancel()
        promptUndoDebounceJob = null
        updatePromptUndoAvailability()
    }

    private fun updatePromptUndoAvailability() {
        val canUndo = hasPromptUndo()
        _editorUiState.update {
            if (it.canUndoPromptEdit == canUndo) it else it.copy(canUndoPromptEdit = canUndo)
        }
    }

    private fun hasPromptUndo(): Boolean {
        return promptUndoHistory.canUndo
    }

    companion object {
        const val PROMPT_UNDO_DEBOUNCE_MILLIS = 700L
    }
}
