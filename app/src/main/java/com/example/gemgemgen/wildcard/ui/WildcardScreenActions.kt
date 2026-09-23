// 역할: 와일드카드 관리 화면에서 발생하는 모든 사용자 입력 인터랙션을 캡슐화한 인터페이스입니다.
package com.example.gemgemgen.wildcard.ui

import com.example.gemgemgen.wildcard.domain.WildcardTextFile

interface WildcardScreenActions : WildcardClassifyActions {
    fun onRefresh() {}
    fun onSelectFolder() {}
    fun onFileClick(file: WildcardTextFile) {}
    fun onRequestNewFile() {}
    fun onNewFileNameChange(name: String) {}
    fun onCreateNewFile() {}
    fun onDismissNewFile() {}
    fun onRequestRename() {}
    fun onRenameFileNameChange(name: String) {}
    fun onConfirmRename() {}
    fun onDismissRename() {}
    fun onRequestDelete() {}
    fun onConfirmDelete() {}
    fun onDismissDelete() {}

    fun onTextChanged(text: String) {}
    fun onSaveFile() {}
    fun onPaste() {}
    fun onPasteBelow() {}
    fun onCopy() {}
    fun onUndo() {}

    fun onEnterLineSelectionMode() {}
    fun onExitLineSelectionMode() {}
    fun onToggleLineSelection(index: Int) {}
    fun onSelectAllLines() {}
    fun onDeselectAllLines() {}
    fun onComposeDynamicPrompt() {}

    fun onConfirmPendingSave() {}
    fun onConfirmPendingDiscard() {}
    fun onCancelPending() {}

    companion object {
        val Empty = object : WildcardScreenActions {}
    }
}
