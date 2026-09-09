package com.example.gemgemgen.analysis.ui

import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisStartBlockReason

object AnalysisUiText {
    fun startBlockedMessage(reason: AnalysisStartBlockReason): String {
        return when (reason) {
            AnalysisStartBlockReason.BlankSource -> "원문을 입력하거나 가져오세요."
            AnalysisStartBlockReason.MissingCategory -> "변경할 카테고리를 선택하세요."
            is AnalysisStartBlockReason.MissingMaskingCredential -> when (reason.provider) {
                AnalysisProvider.GEMINI -> "자동 마스킹용 Gemini API 키를 등록하거나 활성화하세요."
                AnalysisProvider.GROK -> "자동 마스킹용 Grok에 로그인하세요."
            }
            is AnalysisStartBlockReason.MissingGenerationCredential -> when (reason.provider) {
                AnalysisProvider.GEMINI -> "생성용 Gemini API 키를 등록하거나 활성화하세요."
                AnalysisProvider.GROK -> "생성용 Grok에 로그인하세요."
            }
        }
    }
}
