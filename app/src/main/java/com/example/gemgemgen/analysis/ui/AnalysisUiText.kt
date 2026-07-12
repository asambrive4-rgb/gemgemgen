package com.example.gemgemgen.analysis.ui

import com.example.gemgemgen.analysis.domain.AnalysisStartBlockReason

object AnalysisUiText {
    fun startBlockedMessage(reason: AnalysisStartBlockReason): String {
        return when (reason) {
            AnalysisStartBlockReason.MissingCategory -> "카테고리를 선택해주세요."
            AnalysisStartBlockReason.BlankSource -> "원본 프롬프트를 입력해주세요."
            AnalysisStartBlockReason.NoActiveKey -> "활성 Gemini API 키를 먼저 선택해주세요."
        }
    }
}
