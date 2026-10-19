// 역할: 와일드카드 관리 화면에서 발생하는 사용자 입력 인터랙션 및 분류(Classify) 액션 그룹을 제공하는 인터페이스입니다.
package com.example.gemgemgen.wildcard.ui

import com.example.gemgemgen.wildcard.domain.WildcardTextFile

interface WildcardScreenActions {
    val classify: WildcardClassifyActions
        get() = object : WildcardClassifyActions {}

    fun onRefresh() {}
    fun onSelectFolder() {}
    fun selectFile(file: WildcardTextFile) {}
    fun requestNewFile() {}
    fun onNewFileNameChange(name: String) {}
    fun createNewFile() {}
    fun dismissNewFileDialog() {}
    fun requestRenameSelectedFile() {}
    fun onRenameFileNameChange(name: String) {}
    fun renameSelectedFile() {}
    fun dismissRenameDialog() {}
    fun requestDeleteSelectedFile() {}
    fun confirmDeleteSelectedFile() {}
    fun dismissDeleteConfirm() {}

    fun onTextChange(value: String) {}
    fun onSaveFile() {}
    fun pasteFromClipboard() {}
    fun pasteBelowFromClipboard() {}
    fun copyToClipboard() {}
    fun undoClipboardEdit() {}

    fun enterLineSelectionMode() {}
    fun exitLineSelectionMode() {}
    fun toggleLineSelection(index: Int) {}
    fun selectAllLines() {}
    fun deselectAllLines() {}
    fun composeDynamicPromptToClipboard() {}
    fun requestDeriveFile() {}
    fun onDeriveFileNameChange(name: String) {}
    fun confirmDeriveFile() {}
    fun dismissDeriveFileDialog() {}

    fun onConfirmPendingSave() {}
    fun onConfirmPendingDiscard() {}
    fun cancelPendingAction() {}
}
