package com.example.gemgemgen.analysis.ui

import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.AnalysisStartBlockReason

object AnalysisUiText {
    fun startBlockedMessage(
        reason: AnalysisStartBlockReason,
        provider: AnalysisProvider = AnalysisProvider.GEMINI,
        role: AnalysisModelRole = AnalysisModelRole.MASKING
    ): String {
        return when (reason) {
            AnalysisStartBlockReason.MissingCategory -> "카테고리를 선택해주세요."
            AnalysisStartBlockReason.BlankSource -> "원본 프롬프트를 입력해주세요."
            AnalysisStartBlockReason.NoActiveKey -> {
                val step = when (role) {
                    AnalysisModelRole.MASKING -> "자동 마스킹"
                    AnalysisModelRole.GENERATION -> "TXT 생성"
                }
                when (provider) {
                    AnalysisProvider.GEMINI ->
                        "$step 에 활성 Gemini API 키가 필요합니다."
                    AnalysisProvider.GROK ->
                        "$step 에 Grok 로그인이 필요합니다."
                }
            }
        }
    }
}
