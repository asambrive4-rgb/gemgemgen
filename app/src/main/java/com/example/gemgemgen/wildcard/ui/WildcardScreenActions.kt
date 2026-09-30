// 역할: 와일드카드 관리 화면에서 발생하는 사용자 입력 인터랙션을 뷰모델 액션과 직접 연결하는 인터페이스입니다.
package com.example.gemgemgen.wildcard.ui

import com.example.gemgemgen.wildcard.domain.WildcardTextFile

interface WildcardScreenActions : WildcardClassifyActions {
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

    fun onConfirmPendingSave() {}
    fun onConfirmPendingDiscard() {}
    fun cancelPendingAction() {}
}
