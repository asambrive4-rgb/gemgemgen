// 역할: AI 프롬프트 분석 화면과 다이얼로그에서 발생하는 모든 사용자 인터랙션을 캡슐화한 인터페이스입니다.
package com.example.gemgemgen.analysis.ui

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary

interface AnalysisScreenActions {
    fun onSourcePromptChange(value: String) {}
    fun importSourcePromptFromAutomation() {}
    fun onCategorySelected(category: AnalysisCategory) {}
    fun clearTargetSegment() {}
    fun generate() {}
    fun generateTxt() {}
    fun cancelActiveWork() {}
    fun requestResetSession() {}
    fun confirmResetSession() {}
    fun dismissResetSession() {}
    fun onTxtCountChange(value: Int) {}
    fun toggleDirection(id: String) {}
    fun onCustomHintChange(value: String) {}
    fun onResultFileNameChange(value: String) {}
    fun applyCandidate(index: Int) {}
    fun copyCandidate(index: Int) {}
    fun restoreOriginalPrompt() {}
    fun copyGeneratedResults() {}
    fun onSaveResults() {}
    fun onConfirmOverwrite() {}
    fun dismissOverwrite() {}
    fun onRoleProviderSelected(role: AnalysisModelRole, provider: AnalysisProvider) {}
    fun onRoleModelSelected(role: AnalysisModelRole, modelId: String) {}
    fun startGrokLogin() {}
    fun cancelGrokLogin() {}
    fun logoutGrok() {}
    fun onOpenGrokLoginUrl(url: String) {}
    fun showKeyDialog() {}
    fun dismissKeyDialog() {}
    fun onKeyLabelChange(value: String) {}
    fun onKeyValueChange(value: String) {}
    fun addApiKey() {}
    fun deleteApiKey(id: String) {}
    fun activateApiKey(id: String) {}
    fun startEditingApiKey(key: GeminiApiKeySummary) {}
    fun onEditingKeyLabelChange(value: String) {}
    fun cancelEditingApiKey() {}
    fun updateApiKeyLabel() {}
    fun onClearFocus() {}
}
