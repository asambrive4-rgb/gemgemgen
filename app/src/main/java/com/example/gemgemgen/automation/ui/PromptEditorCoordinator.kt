// 역할: 프롬프트 입력창의 텍스트 편집, 세그먼트 치환, 와일드카드/상용구 자동완성 추천 계산 및 실행 기록(History) 네비게이션을 조율합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextRange
import com.example.gemgemgen.automation.domain.PromptEditorSession
import com.example.gemgemgen.automation.domain.PromptHistoryNavigator
import com.example.gemgemgen.automation.domain.PromptParagraphActionResult
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.PromptSegmentEditPolicy
import com.example.gemgemgen.automation.domain.PromptTextMutation
import com.example.gemgemgen.automation.domain.PromptTypingChange
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.automation.usecase.ApplyWildcardTokenUseCase
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import kotlinx.coroutines.CoroutineScope
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
    val canNavigateHistoryBack: Boolean = false,
    val canNavigateHistoryForward: Boolean = false,
    val isHistoryIndicatorVisible: Boolean = false,
    val historyDotCount: Int = 0,
    val activeHistoryDotIndex: Int = 0,
    val activeSuggestionCandidates: List<WildcardTokenAutocomplete.Candidate> = emptyList()
)

/**
 * 프롬프트 텍스트 편집기(TextFieldState)의 입력 동기화, 커서 위치 동기화,
 * 문단 단위 편집 세션, 실행 기록(History) 앞/뒤 네비게이션, 클립보드 입출력 및 와일드카드 치환을 전담하는 코디네이터.
 */
class PromptEditorCoordinator(
    private val clipboardGateway: ClipboardGateway,
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    initialPrompt: String = "",
    private val applyWildcardTokenUseCase: ApplyWildcardTokenUseCase = ApplyWildcardTokenUseCase()
) {
    val textFieldState = TextFieldState()
    private val promptHistoryNavigator = PromptHistoryNavigator(initialDraft = initialPrompt)
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
 promptHistoryNavigator.onUserTyping(change.newText)
 publishPromptTemplateToUiState(change.newText, force = updateTextFieldState)
 updateNavigationAvailability()
 }
 }
 }

    private fun publishPromptTemplateToUiState(value: String, force: Boolean) {
        val nextCandidates = computeActiveSuggestions(
            text = value,
            isParagraphSelectionMode = _editorUiState.value.isParagraphSelectionMode
        )
        _editorUiState.update { state ->
            if (state.promptTemplate == value && state.activeSuggestionCandidates == nextCandidates) {
                state
            } else if (!force && state.promptTemplate.isBlank() == value.isBlank() && state.activeSuggestionCandidates == nextCandidates) {
                state
            } else {
                state.copy(promptTemplate = value, activeSuggestionCandidates = nextCandidates)
            }
        }
    }

 fun syncHistoryItems(items: List<String>) {
 syncPromptTemplateFromTextField()
 promptHistoryNavigator.updateHistory(items, promptTemplateValue)
 updateNavigationAvailability()
 }

 fun onAutomationStarted(prompt: String, updatedHistory: List<String>) {
 syncPromptTemplateFromTextField()
 promptHistoryNavigator.onAutomationStarted(prompt, updatedHistory)
 updateNavigationAvailability()
 }

 fun navigatePromptHistoryBack(isBlocked: Boolean = false) {
 if (isBlocked) return
 syncPromptTemplateFromTextField()
 val target = promptHistoryNavigator.navigateBack(promptTemplateValue) ?: return
 applyPromptTemplateText(target)
 promptTemplateValue = target
 publishEditorSession(promptEditorSession.afterWholeReplace(target))
 }

 fun navigatePromptHistoryForward(isBlocked: Boolean = false) {
 if (isBlocked) return
 syncPromptTemplateFromTextField()
 val target = promptHistoryNavigator.navigateForward() ?: return
 applyPromptTemplateText(target)
 promptTemplateValue = target
 publishEditorSession(promptEditorSession.afterWholeReplace(target))
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

 ignoredPromptChangeText = edit.updatedText
 textFieldState.edit {
 replace(edit.startIndex, edit.previousEndIndex, replacement)
 selection = TextRange(edit.replacementEndIndex)
 }
 promptTemplateValue = edit.updatedText
 promptHistoryNavigator.onUserTyping(edit.updatedText)
 publishEditorSession(promptEditorSession.afterWholeReplace(edit.updatedText))
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

 val selection = textFieldState.selection
 val start = selection.min
 val end = selection.max

 textFieldState.edit {
 replace(start, end, text)
 this.selection = TextRange(start + text.length)
 }

 val newText = textFieldState.text.toString()
 promptTemplateValue = newText
 promptHistoryNavigator.onUserTyping(newText)
 promptEditorSession = promptEditorSession.afterPaste(newText)
 _editorUiState.update {
 it.copy(
 promptTemplate = newText,
 canNavigateHistoryBack = promptHistoryNavigator.canNavigateBack,
 canNavigateHistoryForward = promptHistoryNavigator.canNavigateForward,
 isHistoryIndicatorVisible = promptHistoryNavigator.isIndicatorVisible,
 historyDotCount = promptHistoryNavigator.dotCount,
 activeHistoryDotIndex = promptHistoryNavigator.activeDotIndex
 )
 }
 }
 }

 fun insertTopInstruction(topInstruction: String, isBlocked: Boolean = false) {
 if (isBlocked || topInstruction.isBlank()) return
 syncPromptTemplateFromTextField()
 val currentText = promptTemplateValue
 val newText = if (currentText.isEmpty()) topInstruction else "$topInstruction\n\n$currentText"
 if (currentText == newText) return

 ignoredPromptChangeText = newText
 val cursorAfter = if (currentText.isEmpty()) {
 topInstruction.length
 } else {
 topInstruction.length + 2
 }
 textFieldState.edit {
 replace(0, length, newText)
 selection = TextRange(cursorAfter.coerceIn(0, newText.length))
 }
 promptTemplateValue = newText
 promptHistoryNavigator.onUserTyping(newText)
 publishEditorSession(promptEditorSession.afterWholeReplace(newText))
 }

 fun insertBottomInstruction(bottomInstruction: String, isBlocked: Boolean = false) {
 if (isBlocked || bottomInstruction.isBlank()) return
 syncPromptTemplateFromTextField()
 val currentText = promptTemplateValue
 val newText = if (currentText.isEmpty()) bottomInstruction else "$currentText\n\n$bottomInstruction"
 if (currentText == newText) return

 ignoredPromptChangeText = newText
 val cursorAfter = newText.length
 textFieldState.edit {
 replace(0, length, newText)
 selection = TextRange(cursorAfter.coerceIn(0, newText.length))
 }
 promptTemplateValue = newText
 promptHistoryNavigator.onUserTyping(newText)
 publishEditorSession(promptEditorSession.afterWholeReplace(newText))
 }

 fun applySuggestion(
 candidate: WildcardTokenAutocomplete.Candidate,
 isBlocked: Boolean,
 candidates: List<WildcardTokenAutocomplete.Candidate>
 ) {
 val state = _editorUiState.value
 val selection = textFieldState.selection
 val currentText = textFieldState.text.toString()

 val result = applyWildcardTokenUseCase(
 text = currentText,
 selectionStart = selection.min,
 selectionEnd = selection.max,
 candidate = candidate,
 candidates = candidates,
 isParagraphSelectionMode = state.isParagraphSelectionMode,
 isBlocked = isBlocked
 ) ?: return

 ignoredPromptChangeText = result.newText
 val cursorAfter = result.cursorAfter.coerceIn(0, result.newText.length)
 textFieldState.edit {
 replace(0, length, result.newText)
 this.selection = TextRange(cursorAfter)
 }
 promptTemplateValue = result.newText
 promptHistoryNavigator.onUserTyping(result.newText)
 promptEditorSession = promptEditorSession.withText(result.newText)
 _editorUiState.update {
 it.copy(
 promptTemplate = result.newText,
 canNavigateHistoryBack = promptHistoryNavigator.canNavigateBack,
 canNavigateHistoryForward = promptHistoryNavigator.canNavigateForward,
 isHistoryIndicatorVisible = promptHistoryNavigator.isIndicatorVisible,
 historyDotCount = promptHistoryNavigator.dotCount,
 activeHistoryDotIndex = promptHistoryNavigator.activeDotIndex
 )
 }
 }

 fun applyWildcardTokenSuggestion(
 token: String,
 isBlocked: Boolean,
 candidates: List<WildcardTokenAutocomplete.Candidate>
 ) {
 val targetCandidate = candidates.firstOrNull { it.token == token }
 if (targetCandidate != null) {
 applySuggestion(targetCandidate, isBlocked, candidates)
 return
 }

 val state = _editorUiState.value
 val selection = textFieldState.selection
 val currentText = textFieldState.text.toString()

 val result = applyWildcardTokenUseCase(
 text = currentText,
 selectionStart = selection.min,
 selectionEnd = selection.max,
 token = token,
 candidates = candidates,
 isParagraphSelectionMode = state.isParagraphSelectionMode,
 isBlocked = isBlocked
 ) ?: return

 ignoredPromptChangeText = result.newText
 val cursorAfter = result.cursorAfter.coerceIn(0, result.newText.length)
 textFieldState.edit {
 replace(0, length, result.newText)
 this.selection = TextRange(cursorAfter)
 }
 promptTemplateValue = result.newText
 promptHistoryNavigator.onUserTyping(result.newText)
 promptEditorSession = promptEditorSession.withText(result.newText)
 _editorUiState.update {
 it.copy(
 promptTemplate = result.newText,
 canNavigateHistoryBack = promptHistoryNavigator.canNavigateBack,
 canNavigateHistoryForward = promptHistoryNavigator.canNavigateForward,
 isHistoryIndicatorVisible = promptHistoryNavigator.isIndicatorVisible,
 historyDotCount = promptHistoryNavigator.dotCount,
 activeHistoryDotIndex = promptHistoryNavigator.activeDotIndex
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

 fun restorePrompt(prompt: String) {
 applyPromptTemplateText(prompt)
 promptTemplateValue = prompt
 promptHistoryNavigator.onUserTyping(prompt)
 publishEditorSession(promptEditorSession.afterWholeReplace(prompt))
 }

 private fun replaceWholePromptTemplate(replacement: String) {
 syncEditorTextFromCurrent()
 val currentText = promptTemplateValue
 if (currentText == replacement) return

 applyPromptTemplateText(replacement)
 promptTemplateValue = replacement
 promptHistoryNavigator.onUserTyping(replacement)
 publishEditorSession(promptEditorSession.afterWholeReplace(replacement))
 }

 private fun applyTextMutation(mutation: PromptTextMutation) {
 val newText = mutation.session.text
 ignoredPromptChangeText = newText
 val start = mutation.selectionStart.coerceIn(0, newText.length)
 val end = mutation.selectionEnd.coerceIn(start, newText.length)
 textFieldState.edit {
 replace(0, length, newText)
 selection = TextRange(start, end)
 }
 promptTemplateValue = newText
 promptHistoryNavigator.onUserTyping(newText)
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

    private fun publishEditorSession(session: PromptEditorSession) {
        promptEditorSession = session
        promptTemplateValue = session.text
        val message = AutomationUiText.paragraphMessage(session.messageKey)
        val suggestions = computeActiveSuggestions(
            text = session.text,
            isParagraphSelectionMode = session.isParagraphSelectionMode
        )
        _editorUiState.update { state ->
            state.copy(
                promptTemplate = session.text,
                isParagraphSelectionMode = session.isParagraphSelectionMode,
                selectedParagraphRange = session.selectedParagraphRange,
                paragraphSelectionMessage = message,
                canNavigateHistoryBack = promptHistoryNavigator.canNavigateBack,
                canNavigateHistoryForward = promptHistoryNavigator.canNavigateForward,
                isHistoryIndicatorVisible = promptHistoryNavigator.isIndicatorVisible,
                historyDotCount = promptHistoryNavigator.dotCount,
                activeHistoryDotIndex = promptHistoryNavigator.activeDotIndex,
                activeSuggestionCandidates = suggestions
            )
        }
    }

    private fun updateNavigationAvailability() {
        _editorUiState.update { state ->
            state.copy(
                canNavigateHistoryBack = promptHistoryNavigator.canNavigateBack,
                canNavigateHistoryForward = promptHistoryNavigator.canNavigateForward,
                isHistoryIndicatorVisible = promptHistoryNavigator.isIndicatorVisible,
                historyDotCount = promptHistoryNavigator.dotCount,
                activeHistoryDotIndex = promptHistoryNavigator.activeDotIndex
            )
        }
    }

    private var currentAutocompleteCandidates: List<WildcardTokenAutocomplete.Candidate> = emptyList()

    fun updateAutocompleteCandidates(
        candidates: List<WildcardTokenAutocomplete.Candidate>,
        isRunning: Boolean = false
    ) {
        currentAutocompleteCandidates = candidates
        refreshActiveSuggestions(isRunning = isRunning)
    }

    fun computeActiveSuggestions(
        text: String = textFieldState.text.toString(),
        cursor: Int = textFieldState.selection.max,
        selectionStart: Int = textFieldState.selection.min,
        selectionEnd: Int = textFieldState.selection.max,
        candidates: List<WildcardTokenAutocomplete.Candidate> = currentAutocompleteCandidates,
        isParagraphSelectionMode: Boolean = _editorUiState.value.isParagraphSelectionMode,
        isRunning: Boolean = false
    ): List<WildcardTokenAutocomplete.Candidate> {
        return Companion.computeActiveSuggestions(
            text = text,
            cursor = cursor,
            selectionMin = selectionStart,
            selectionMax = selectionEnd,
            candidates = candidates,
            isParagraphSelectionMode = isParagraphSelectionMode,
            isRunning = isRunning
        )
    }

    fun refreshActiveSuggestions(isRunning: Boolean = false) {
        val selection = textFieldState.selection
        val suggestions = computeActiveSuggestions(
            text = textFieldState.text.toString(),
            cursor = selection.max,
            selectionStart = selection.min,
            selectionEnd = selection.max,
            candidates = currentAutocompleteCandidates,
            isParagraphSelectionMode = _editorUiState.value.isParagraphSelectionMode,
            isRunning = isRunning
        )
        _editorUiState.update { it.copy(activeSuggestionCandidates = suggestions) }
    }

    companion object {
        fun computeActiveSuggestions(
            text: String,
            cursor: Int,
            selectionMin: Int,
            selectionMax: Int,
            candidates: List<WildcardTokenAutocomplete.Candidate>,
            isParagraphSelectionMode: Boolean = false,
            isRunning: Boolean = false
        ): List<WildcardTokenAutocomplete.Candidate> {
            if (isParagraphSelectionMode || isRunning || selectionMin != selectionMax) {
                return emptyList()
            }
            return WildcardTokenAutocomplete.suggestCandidates(
                text = text,
                cursor = cursor,
                candidates = candidates
            )
        }
    }
}
