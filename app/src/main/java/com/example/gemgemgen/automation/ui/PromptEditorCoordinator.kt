// 역할: 프롬프트 입력창의 텍스트 편집, 세그먼트 치환, 문구 찾기(검색), 실시간 텍스트 동기화 및 실행 기록 네비게이션을 조율합니다.
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
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.ui.TextHighlightRange
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<TextHighlightRange> = emptyList(),
    val activeSearchMatchIndex: Int = -1,
    val canNavigateHistoryBack: Boolean = false,
    val canNavigateHistoryForward: Boolean = false,
    val isHistoryIndicatorVisible: Boolean = false,
    val historyDotCount: Int = 0,
    val activeHistoryDotIndex: Int = 0,
    val activeSuggestionCandidates: List<WildcardTokenAutocomplete.Candidate> = emptyList()
)

/**
 * 프롬프트 텍스트 편집기(TextFieldState)의 입력 동기화, 커서 위치 동기화,
 * 문단 단위 편집 세션, 문구 찾기(검색), 실행 기록(History) 앞/뒤 네비게이션, 클립보드 입출력 및 와일드카드 치환을 전담하는 코디네이터.
 */
class PromptEditorCoordinator(
    private val clipboardGateway: ClipboardGateway,
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    initialPrompt: String = "",
    private val onPromptTextChanged: ((String) -> Unit)? = null
) {
    val textFieldState = TextFieldState()
    private val promptHistoryNavigator = PromptHistoryNavigator(initialDraft = initialPrompt)
    private var ignoredPromptChangeText: String? = null
    private var promptEditorSession = PromptEditorSession(text = initialPrompt)

    private val _editorUiState = MutableStateFlow(
        PromptEditorUiState(promptTemplate = initialPrompt)
    )
    val editorUiState: StateFlow<PromptEditorUiState> = _editorUiState.asStateFlow()

    init {
        if (initialPrompt.isNotEmpty()) {
            applyPromptTemplateText(initialPrompt)
        }
        onPromptTextChanged?.invoke(initialPrompt)
        scope.launch {
            try {
                snapshotFlow { textFieldState.selection }
                    .distinctUntilChanged()
                    .collect {
                        if (textFieldState.text.contentEquals(promptEditorSession.text)) {
                            refreshActiveSuggestions()
                        }
                    }
            } catch (_: Throwable) {
                // JUnit 테스트 등 snapshot 시스템 미구동 환경 예외 방어
            }
        }
    }

    fun onPromptTemplateFromEditor(value: String) =
        onPromptTemplateChange(value, updateTextFieldState = false)

    fun onPromptTemplateChange(value: String, updateTextFieldState: Boolean = true) {
        if (updateTextFieldState && !textFieldState.text.contentEquals(value)) {
            textFieldState.setTextAndPlaceCursorAtEnd(value)
        }
        when (
            val change = PromptEditorSession.classifyTypingChange(
                previousText = promptEditorSession.text,
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
        val selection = textFieldState.selection
        val nextCandidates = resolveActiveSuggestions(
            text = value,
            cursor = selection.max,
            selectionMin = selection.min,
            selectionMax = selection.max,
            candidates = currentAutocompleteCandidates,
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
        if (_editorUiState.value.isSearchActive && _editorUiState.value.searchQuery.isNotEmpty()) {
            recalculateSearchMatches(
                query = _editorUiState.value.searchQuery,
                text = value,
                moveCursor = false
            )
        }
    }

    fun toggleSearch(active: Boolean? = null) {
        val nextActive = active ?: !_editorUiState.value.isSearchActive
        if (nextActive) {
            if (_editorUiState.value.isParagraphSelectionMode) {
                publishEditorSession(promptEditorSession.cancelSelection())
            }
            val currentSelection = textFieldState.selection
            val initialQuery = if (currentSelection.collapsed) {
                _editorUiState.value.searchQuery
            } else {
                val text = textFieldState.text.toString()
                val start = currentSelection.min.coerceIn(0, text.length)
                val end = currentSelection.max.coerceIn(start, text.length)
                text.substring(start, end)
            }
            _editorUiState.update { it.copy(isSearchActive = true, searchQuery = initialQuery) }
            if (initialQuery.isNotEmpty()) {
                recalculateSearchMatches(query = initialQuery, text = textFieldState.text.toString())
            }
        } else {
            closeSearch()
        }
    }

    fun closeSearch() {
        _editorUiState.update {
            it.copy(
                isSearchActive = false,
                searchQuery = "",
                searchMatches = emptyList(),
                activeSearchMatchIndex = -1
            )
        }
    }

    fun setSearchQuery(query: String) {
        val text = textFieldState.text.toString()
        _editorUiState.update { it.copy(searchQuery = query) }
        recalculateSearchMatches(query = query, text = text, resetToFirst = true)
    }

    fun navigateSearchNext() {
        val state = _editorUiState.value
        if (state.searchMatches.isEmpty()) return
        val nextIndex = (state.activeSearchMatchIndex + 1) % state.searchMatches.size
        _editorUiState.update { it.copy(activeSearchMatchIndex = nextIndex) }
        selectSearchMatch(state.searchMatches[nextIndex])
    }

    fun navigateSearchPrevious() {
        val state = _editorUiState.value
        if (state.searchMatches.isEmpty()) return
        val prevIndex = (state.activeSearchMatchIndex - 1 + state.searchMatches.size) % state.searchMatches.size
        _editorUiState.update { it.copy(activeSearchMatchIndex = prevIndex) }
        selectSearchMatch(state.searchMatches[prevIndex])
    }

    private fun recalculateSearchMatches(
        query: String,
        text: String,
        resetToFirst: Boolean = false,
        moveCursor: Boolean = true
    ) {
        if (query.isEmpty() || text.isEmpty()) {
            _editorUiState.update {
                it.copy(searchMatches = emptyList(), activeSearchMatchIndex = -1)
            }
            return
        }

        val matches = mutableListOf<TextHighlightRange>()
        var startIndex = 0
        while (startIndex < text.length) {
            val foundIndex = text.indexOf(query, startIndex, ignoreCase = true)
            if (foundIndex == -1) break
            matches.add(
                TextHighlightRange(
                    start = foundIndex,
                    endExclusive = foundIndex + query.length
                )
            )
            startIndex = foundIndex + 1
        }

        val currentIndex = if (matches.isEmpty()) {
            -1
        } else if (resetToFirst) {
            0
        } else {
            val previousIndex = _editorUiState.value.activeSearchMatchIndex
            if (previousIndex in matches.indices) previousIndex else 0
        }

        _editorUiState.update {
            it.copy(
                searchMatches = matches,
                activeSearchMatchIndex = currentIndex
            )
        }

        if (moveCursor && currentIndex in matches.indices) {
            selectSearchMatch(matches[currentIndex])
        }
    }

    private fun selectSearchMatch(match: TextHighlightRange) {
        try {
            textFieldState.edit {
                // 블록 선택 대신 커서만 이동하여 안드로이드 시스템의 강제 하단 스크롤(BringIntoView)과
                // '복사/공유' 툴바 팝업이 화면을 가리는 현상을 방지합니다.
                selection = TextRange(match.start)
            }
        } catch (_: Throwable) {
            // JUnit 테스트 등 snapshot 시스템 미구동 환경 예외 방어
        }
    }

    fun syncHistoryItems(items: List<String>) {
        syncPromptTemplateFromTextField()
        promptHistoryNavigator.updateHistory(items, promptEditorSession.text)
        updateNavigationAvailability()
    }

    fun onAutomationStarted(prompt: String) {
        syncPromptTemplateFromTextField()
        promptHistoryNavigator.onAutomationStarted(prompt)
        updateNavigationAvailability()
    }

    fun navigatePromptHistoryBack() {
        syncPromptTemplateFromTextField()
        val target = promptHistoryNavigator.navigateBack(promptEditorSession.text) ?: return
        applyPromptTemplateText(target)
        publishEditorSession(promptEditorSession.afterWholeReplace(target))
    }

    fun navigatePromptHistoryForward() {
        syncPromptTemplateFromTextField()
        val target = promptHistoryNavigator.navigateForward() ?: return
        applyPromptTemplateText(target)
        publishEditorSession(promptEditorSession.afterWholeReplace(target))
    }

    fun toggleParagraphSelectionMode() {
        if (_editorUiState.value.isSearchActive) {
            closeSearch()
        }
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
        return promptEditorSession.text
    }

    fun replacePromptTemplateSegment(
        expectedSegment: String,
        replacement: String,
        preferredStartIndex: Int
    ): Int? {
        syncPromptTemplateFromTextField()
        val edit = PromptSegmentEditPolicy.replace(
            currentText = promptEditorSession.text,
            expectedSegment = expectedSegment,
            replacement = replacement,
            preferredStartIndex = preferredStartIndex
        ) ?: return null

        commitProgrammaticEdit(
            session = promptEditorSession.afterWholeReplace(edit.updatedText),
            selection = TextRange(edit.replacementEndIndex),
            replaceStart = edit.startIndex,
            replaceEnd = edit.previousEndIndex,
            replacementText = replacement
        )
        return edit.startIndex
    }

    fun copyPromptToClipboard() {
        syncPromptTemplateFromTextField()
        val text = promptEditorSession.text
        if (text.isBlank()) return

        scope.launch {
            withContext(dispatchers.io) {
                clipboardGateway.writeText(text)
            }
        }
    }

    fun insertTopInstruction(topInstruction: String) =
        insertInstruction(topInstruction, isTop = true)

    fun insertBottomInstruction(bottomInstruction: String) =
        insertInstruction(bottomInstruction, isTop = false)

    private fun insertInstruction(instruction: String, isTop: Boolean) {
        if (instruction.isBlank()) return
        syncPromptTemplateFromTextField()
        val currentText = promptEditorSession.text
        val newText = when {
            currentText.isEmpty() -> instruction
            isTop -> "$instruction\n\n$currentText"
            else -> "$currentText\n\n$instruction"
        }
        if (currentText == newText) return

        val cursorAfter = when {
            !isTop -> newText.length
            currentText.isEmpty() -> instruction.length
            else -> instruction.length + 2
        }
        commitProgrammaticEdit(
            session = promptEditorSession.afterWholeReplace(newText),
            selection = TextRange(cursorAfter.coerceIn(0, newText.length))
        )
    }

    fun applySuggestion(
        candidate: WildcardTokenAutocomplete.Candidate,
        candidates: List<WildcardTokenAutocomplete.Candidate> = currentAutocompleteCandidates
    ) {
        val state = _editorUiState.value
        val selection = textFieldState.selection
        val currentText = textFieldState.text.toString()

        val result = WildcardTokenAutocomplete.applyToken(
            text = currentText,
            selectionStart = selection.min,
            selectionEnd = selection.max,
            candidate = candidate,
            candidates = candidates,
            isParagraphSelectionMode = state.isParagraphSelectionMode
        ) ?: return

        val cursorAfter = result.cursorAfter.coerceIn(0, result.newText.length)
        commitProgrammaticEdit(
            session = promptEditorSession.withText(result.newText),
            selection = TextRange(cursorAfter),
            candidates = candidates
        )
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
        promptHistoryNavigator.onUserTyping(prompt)
        publishEditorSession(promptEditorSession.afterWholeReplace(prompt))
    }

    fun replaceWholePromptTemplate(replacement: String) {
        syncEditorTextFromCurrent()
        if (promptEditorSession.text == replacement) return
        restorePrompt(replacement)
    }

    private fun applyTextMutation(mutation: PromptTextMutation) {
        val newText = mutation.session.text
        val start = mutation.selectionStart.coerceIn(0, newText.length)
        val end = mutation.selectionEnd.coerceIn(start, newText.length)
        commitProgrammaticEdit(
            session = mutation.session,
            selection = TextRange(start, end)
        )
    }

    private fun commitProgrammaticEdit(
        session: PromptEditorSession,
        selection: TextRange,
        replaceStart: Int = 0,
        replaceEnd: Int = textFieldState.text.length,
        replacementText: String = session.text,
        candidates: List<WildcardTokenAutocomplete.Candidate> = currentAutocompleteCandidates
    ) {
        ignoredPromptChangeText = session.text
        textFieldState.edit {
            replace(replaceStart, replaceEnd, replacementText)
            this.selection = selection
        }
        promptHistoryNavigator.onUserTyping(session.text)
        publishEditorSession(session, candidates)
    }

    private fun applyPromptTemplateText(text: String) {
        if (!textFieldState.text.contentEquals(text)) {
            ignoredPromptChangeText = text
            textFieldState.setTextAndPlaceCursorAtEnd(text)
        }
    }

    fun syncPromptTemplateFromTextField() {
        val currentText = textFieldState.text.toString()
        if (_editorUiState.value.promptTemplate == currentText &&
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
        if (promptEditorSession.text != currentText) {
            setPromptTextOnly(currentText)
        }
    }

    private fun setPromptTextOnly(text: String) {
        promptEditorSession = promptEditorSession.withText(text)
        onPromptTextChanged?.invoke(text)
    }

    private fun publishEditorSession(
        session: PromptEditorSession,
        candidates: List<WildcardTokenAutocomplete.Candidate> = currentAutocompleteCandidates
    ) {
        promptEditorSession = session
        onPromptTextChanged?.invoke(session.text)
        val message = AutomationUiText.paragraphMessage(session.messageKey)
        val suggestions = resolveActiveSuggestions(
            text = session.text,
            cursor = session.text.length,
            selectionMin = session.text.length,
            selectionMax = session.text.length,
            candidates = candidates,
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
    private var lastSuggestionText: String? = null
    private var lastSuggestionCursor: Int = -1
    private var lastSuggestionCandidates: List<WildcardTokenAutocomplete.Candidate> = emptyList()
    private var lastSuggestionParagraphMode: Boolean = false
    private var lastComputedSuggestions: List<WildcardTokenAutocomplete.Candidate> = emptyList()

    private fun resolveActiveSuggestions(
        text: String,
        cursor: Int,
        selectionMin: Int,
        selectionMax: Int,
        candidates: List<WildcardTokenAutocomplete.Candidate>,
        isParagraphSelectionMode: Boolean = false
    ): List<WildcardTokenAutocomplete.Candidate> {
        if (isParagraphSelectionMode || selectionMin != selectionMax) {
            lastSuggestionText = text
            lastSuggestionCursor = cursor
            lastSuggestionCandidates = candidates
            lastSuggestionParagraphMode = isParagraphSelectionMode
            lastComputedSuggestions = emptyList()
            return emptyList()
        }
        if (text == lastSuggestionText &&
            cursor == lastSuggestionCursor &&
            candidates === lastSuggestionCandidates &&
            isParagraphSelectionMode == lastSuggestionParagraphMode
        ) {
            return lastComputedSuggestions
        }
        val computed = computeActiveSuggestions(
            text = text,
            cursor = cursor,
            selectionMin = selectionMin,
            selectionMax = selectionMax,
            candidates = candidates,
            isParagraphSelectionMode = isParagraphSelectionMode
        )
        lastSuggestionText = text
        lastSuggestionCursor = cursor
        lastSuggestionCandidates = candidates
        lastSuggestionParagraphMode = isParagraphSelectionMode
        lastComputedSuggestions = computed
        return computed
    }

    fun updateAutocompleteCandidates(
        candidates: List<WildcardTokenAutocomplete.Candidate>
    ) {
        currentAutocompleteCandidates = candidates
        refreshActiveSuggestions()
    }

    fun refreshActiveSuggestions() {
        val selection = textFieldState.selection
        val suggestions = resolveActiveSuggestions(
            text = textFieldState.text.toString(),
            cursor = selection.max,
            selectionMin = selection.min,
            selectionMax = selection.max,
            candidates = currentAutocompleteCandidates,
            isParagraphSelectionMode = _editorUiState.value.isParagraphSelectionMode
        )
        _editorUiState.update {
            if (it.activeSuggestionCandidates == suggestions) it else it.copy(activeSuggestionCandidates = suggestions)
        }
    }

    companion object {
        fun computeActiveSuggestions(
            text: String,
            cursor: Int,
            selectionMin: Int,
            selectionMax: Int,
            candidates: List<WildcardTokenAutocomplete.Candidate>,
            isParagraphSelectionMode: Boolean = false
        ): List<WildcardTokenAutocomplete.Candidate> {
            if (isParagraphSelectionMode || selectionMin != selectionMax) {
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
