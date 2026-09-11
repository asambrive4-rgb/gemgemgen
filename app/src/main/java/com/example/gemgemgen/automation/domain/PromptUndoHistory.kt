// 역할: 프롬프트 텍스트 편집 중 되돌리기와 다시 실행 히스토리를 관리합니다.
package com.example.gemgemgen.automation.domain

class PromptUndoHistory(
    private val maxSnapshots: Int = DEFAULT_MAX_SNAPSHOT_COUNT
) {
    private var undoStack: List<String> = emptyList()
    private var pendingTypingSnapshot: String? = null

    val canUndo: Boolean
        get() = pendingTypingSnapshot != null || undoStack.isNotEmpty()

    fun recordTypingSnapshot(previousText: String) {
        if (pendingTypingSnapshot == null) {
            pendingTypingSnapshot = previousText
        }
    }

    fun recordImmediateSnapshot(snapshot: String) {
        push(snapshot)
    }

    fun commitPendingTyping(currentText: String) {
        val snapshot = pendingTypingSnapshot ?: return
        pendingTypingSnapshot = null
        if (snapshot != currentText) {
            push(snapshot)
        }
    }

    fun popUndo(): String? {
        val snapshot = undoStack.firstOrNull() ?: return null
        undoStack = undoStack.drop(1)
        return snapshot
    }

    private fun push(snapshot: String) {
        if (undoStack.firstOrNull() == snapshot) return

        undoStack = (listOf(snapshot) + undoStack).take(maxSnapshots)
    }

    private companion object {
        const val DEFAULT_MAX_SNAPSHOT_COUNT = 5
    }
}
