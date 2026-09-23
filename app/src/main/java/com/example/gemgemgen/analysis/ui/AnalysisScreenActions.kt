// 역할: AI 프롬프트 분석 화면에서 발생하는 모든 사용자 인터랙션을 캡슐화한 인터페이스입니다.
package com.example.gemgemgen.analysis.ui

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary

interface AnalysisScreenActions {
    fun onSourcePromptChange(value: String) {}
    fun onImportFromAutomation() {}
    fun onCategorySelected(category: AnalysisCategory) {}
    fun onClearTargetSegment() {}
    fun onGenerate() {}
    fun onGenerateTxt() {}
    fun onCancelWork() {}
    fun onRequestResetSession() {}
    fun onConfirmResetSession() {}
    fun onDismissResetSession() {}
    fun onTxtCountChange(value: Int) {}
    fun onToggleDirection(id: String) {}
    fun onCustomHintChange(value: String) {}
    fun onResultFileNameChange(value: String) {}
    fun onApplyCandidate(index: Int) {}
    fun onCopyCandidate(index: Int) {}
    fun onRestoreOriginalPrompt() {}
    fun onCopyResults() {}
    fun onSaveResults() {}
    fun onConfirmOverwrite() {}
    fun onDismissOverwrite() {}
    fun onRoleProviderSelected(role: AnalysisModelRole, provider: AnalysisProvider) {}
    fun onRoleModelSelected(role: AnalysisModelRole, modelId: String) {}
    fun onStartGrokLogin() {}
    fun onCancelGrokLogin() {}
    fun onLogoutGrok() {}
    fun onOpenGrokLoginUrl(url: String) {}
    fun onShowKeyDialog() {}
    fun onDismissKeyDialog() {}
    fun onKeyLabelChange(value: String) {}
    fun onKeyValueChange(value: String) {}
    fun onAddApiKey() {}
    fun onDeleteApiKey(id: String) {}
    fun onActivateApiKey(id: String) {}
    fun onStartEditApiKey(key: GeminiApiKeySummary) {}
    fun onEditKeyLabelChange(value: String) {}
    fun onCancelEditApiKey() {}
    fun onUpdateKeyLabel() {}
    fun onClearFocus() {}

    companion object {
        val Empty = object : AnalysisScreenActions {}
    }
}
