// 역할: 변주 생성용 프롬프트 설정 데이터 및 추후 선택 문구 결합 규칙을 제공합니다.
package com.example.gemgemgen.automation.domain

data class VariationPromptConfig(
    val prompt: String = DEFAULT_VARIATION_PROMPT
) {
    /**
     * 추후 템플릿의 드래그 선택 영역 텍스트를 변주 프롬프트 끝에 결합합니다.
     * 선택된 텍스트가 없으면 기본 프롬프트만 반환합니다.
     */
    fun buildPrompt(selectedText: String? = null): String {
        val trimmedSelected = selectedText?.trim()
        if (trimmedSelected.isNullOrEmpty()) {
            return prompt
        }
        return if (prompt.isBlank()) {
            trimmedSelected
        } else {
            prompt + "\n\n" + trimmedSelected
        }
    }

    companion object {
        const val DEFAULT_VARIATION_PROMPT: String =
            "다음 프롬프트의 스타일과 핵심 의도를 유지하며, 새롭고 창의적인 변주(Variation)를 작성해주세요."
        val DEFAULT = VariationPromptConfig(DEFAULT_VARIATION_PROMPT)
    }
}
