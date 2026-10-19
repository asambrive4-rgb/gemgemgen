// 역할: 프롬프트 에디터 컴포넌트에서 발생하는 텍스트 입력, 문단 선택, 검색, 히스토리 탐색 액션을 정의하는 인터페이스입니다.
package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface PromptEditorUiActions {
    val activeSuggestions: StateFlow<List<WildcardTokenAutocomplete.Candidate>>
        get() = MutableStateFlow(emptyList())

    fun onPromptTemplateChange(value: String) {}
    fun onPromptTemplateFromEditor(value: String) {}
    fun importPromptFromClipboard() {}
    fun copyPromptToClipboard() {}
    fun applySuggestion(candidate: WildcardTokenAutocomplete.Candidate) {}

    fun navigatePromptHistoryBack() {}
    fun navigatePromptHistoryForward() {}

    fun toggleParagraphSelectionMode() {}
    fun selectPromptParagraphAt(offset: Int) {}
    fun deleteSelectedPromptParagraph() {}
    fun replaceSelectedPromptParagraph(replacement: String) {}
    fun cancelParagraphSelection() {}

    fun toggleSearch(active: Boolean? = null) {}
    fun setSearchQuery(query: String) {}
    fun navigateSearchNext() {}
    fun navigateSearchPrevious() {}
    fun closeSearch() {}
}
