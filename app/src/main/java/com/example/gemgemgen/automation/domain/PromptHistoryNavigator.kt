// 역할: 프롬프트 실행 기록 목록을 바탕으로 브라우저 방식의 앞/뒤 탐색, 초안(Draft) 보존, 점 인디케이터 위치 및 활성화 상태를 관리합니다.
package com.example.gemgemgen.automation.domain

class PromptHistoryNavigator(
    initialHistory: List<String> = emptyList(),
    initialDraft: String = ""
) {
    private var historyItems: List<String> = initialHistory.filter { it.isNotBlank() }.take(MAX_HISTORY_COUNT)
    private var draftPrompt: String = initialDraft
    private var currentIndex: Int = historyItems.size
    private var _isNavigating: Boolean = false

    val isNavigating: Boolean
        get() = _isNavigating

    val canNavigateBack: Boolean
        get() = historyItems.isNotEmpty() && currentIndex > 0

    val canNavigateForward: Boolean
        get() = _isNavigating && currentIndex < historyItems.size

    val dotCount: Int
        get() = if (historyItems.isEmpty()) 0 else minOf(MAX_DOT_COUNT, historyItems.size + 1)

    val activeDotIndex: Int
        get() = currentIndex.coerceIn(0, (dotCount - 1).coerceAtLeast(0))

    val isIndicatorVisible: Boolean
        get() = _isNavigating && historyItems.isNotEmpty()

    fun updateHistory(newHistory: List<String>, currentText: String) {
        val filtered = newHistory.filter { it.isNotBlank() }.take(MAX_HISTORY_COUNT)
        historyItems = filtered
        if (!_isNavigating) {
            draftPrompt = currentText
            currentIndex = filtered.size
        } else {
            currentIndex = currentIndex.coerceIn(0, filtered.size)
        }
    }

    fun onAutomationStarted(executedPrompt: String, updatedHistory: List<String>) {
        val filtered = updatedHistory.filter { it.isNotBlank() }.take(MAX_HISTORY_COUNT)
        historyItems = filtered
        draftPrompt = executedPrompt
        currentIndex = historyItems.size
        _isNavigating = false
    }

    fun onUserTyping(newText: String) {
        _isNavigating = false
        draftPrompt = newText
        currentIndex = historyItems.size
    }

    fun navigateBack(currentText: String): String? {
        if (historyItems.isEmpty() || currentIndex <= 0) return null

        if (!_isNavigating) {
            draftPrompt = currentText
            _isNavigating = true
            currentIndex = historyItems.size
            val immediatePreviousIndex = historyItems.size - 1
            val immediatePreviousPrompt = getPromptAt(immediatePreviousIndex)
            if (immediatePreviousPrompt == currentText && immediatePreviousIndex > 0) {
                currentIndex = immediatePreviousIndex - 1
            } else {
                currentIndex = immediatePreviousIndex
            }
        } else {
            currentIndex--
        }

        return getPromptAt(currentIndex)
    }

    fun navigateForward(): String? {
        if (!_isNavigating || currentIndex >= historyItems.size) return null

        currentIndex++
        if (currentIndex == historyItems.size) {
            _isNavigating = false
            return draftPrompt
        }

        return getPromptAt(currentIndex)
    }

    private fun getPromptAt(index: Int): String {
        if (index == historyItems.size) {
            return draftPrompt
        }
        val historyIndex = historyItems.size - 1 - index
        return historyItems.getOrNull(historyIndex) ?: draftPrompt
    }

    companion object {
        const val MAX_HISTORY_COUNT = 4
        const val MAX_DOT_COUNT = 5
    }
}
