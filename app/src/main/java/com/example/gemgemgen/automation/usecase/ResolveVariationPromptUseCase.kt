// 역할: 프롬프트 본문과 선택 영역(문단 하이라이트 또는 드래그)을 분석하여 변주 대상 텍스트를 누락 없이 안전하게 결정하고 결합합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.PromptParagraphRange
import com.example.gemgemgen.automation.domain.VariationPromptConfig

class ResolveVariationPromptUseCase {

    /**
     * 현재 프롬프트 본문과 선택 상태(문단 선택 또는 드래그)를 바탕으로 변주 대상 텍스트를 결정합니다.
     * 1순위: 문단 선택 모드 활성화 & 문단 선택 범위 존재 시 해당 문단 텍스트
     * 2순위: 외부에서 명시적으로 전달된 드래그 선택 텍스트
     * 3순위: 둘 다 없거나 공백이면 null
     */
    fun resolveTarget(
        fullText: String,
        explicitSelectedText: String? = null,
        isParagraphSelectionMode: Boolean = false,
        selectedParagraphRange: PromptParagraphRange? = null
    ): String? {
        if (isParagraphSelectionMode && selectedParagraphRange != null) {
            val start = selectedParagraphRange.start.coerceIn(0, fullText.length)
            val end = selectedParagraphRange.endExclusive.coerceIn(start, fullText.length)
            val paragraphText = fullText.substring(start, end).trim()
            if (paragraphText.isNotEmpty()) {
                return paragraphText
            }
        }

        val trimmedExplicit = explicitSelectedText?.trim()
        if (!trimmedExplicit.isNullOrEmpty()) {
            return trimmedExplicit
        }

        return null
    }

    /**
     * 결정된 변주 대상 텍스트를 [config]의 기본 변주 프롬프트 끝에 결합하여 최종 프롬프트를 반환합니다.
     */
    fun buildPrompt(
        config: VariationPromptConfig,
        fullText: String,
        explicitSelectedText: String? = null,
        isParagraphSelectionMode: Boolean = false,
        selectedParagraphRange: PromptParagraphRange? = null
    ): String {
        val target = resolveTarget(
            fullText = fullText,
            explicitSelectedText = explicitSelectedText,
            isParagraphSelectionMode = isParagraphSelectionMode,
            selectedParagraphRange = selectedParagraphRange
        )
        return config.buildPrompt(target)
    }
}
