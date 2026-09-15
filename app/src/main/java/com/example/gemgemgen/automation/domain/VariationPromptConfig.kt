// 역할: 변주 생성용 프롬프트 설정 데이터 및 추후 선택 문구 결합 규칙을 제공합니다.
package com.example.gemgemgen.automation.domain

data class VariationPromptConfig(
    val prompt: String = DEFAULT_VARIATION_PROMPT
) {
    /**
     * 템플릿의 드래그 선택 영역 텍스트를 변주 프롬프트 끝에 결합합니다.
     * 선택된 텍스트가 없으면 기본 프롬프트만 반환합니다.
     */
    fun buildPrompt(selectedText: String? = null): String {
        val trimmedSelected = selectedText?.trim()
        if (trimmedSelected.isNullOrEmpty()) {
            return prompt
        }
        val normalizedPrompt = prompt.trimEnd()
        return if (normalizedPrompt.isBlank()) {
            trimmedSelected
        } else {
            normalizedPrompt + "\n\n" + trimmedSelected
        }
    }

    companion object {
        const val DEFAULT_VARIATION_PROMPT: String =
            """당신은 자연어 기반 이미지 생성 모델에 최적화된 프롬프트 엔지니어입니다.

사용자가 전체 이미지 프롬프트 중 일부 발췌한 특정 문장을 입력하면, 와일드카드 파일용 텍스트와 인라인 다이나믹 프롬프트 두 가지 형태로 변주 목록을 제작하세요.

## [수행 규칙]

[변주 생성] 원본 문구의 문맥과 시각적 역할을 분석하여, 자연어 이미지 생성 모델이 디테일을 잘 표현할 수 있는 고품질 변주 프롬프트를 정확히 '20'개 작성하세요.

[도구 사용 절대 금지] 이미지를 직접 생성하는 도구나 툴을 절대 호출하거나 실행하지 마세요. 목표는 오직 '프롬프트 텍스트 자산'을 추출하는 것입니다.

[언어 규칙] 코드 블록 내부의 모든 프롬프트와 안내 텍스트는 100% 한국어로 작성하세요.

[출력 양식] 반드시 아래의 마크다운 섹션 구조와 구분선을 엄격히 준수하여 출력하세요. ### 1. 와일드카드 (언어 식별자 없는 코드 블록 안에 20개 항목을 1줄당 1개씩 총 20줄로 출력. 번호, 기호, 설명 일체 제외) --- ### 2. 다이나믹 프롬프트 (언어 식별자 없는 코드 블록 안에 20개 항목을 파이프로 연결하여 단 1줄로 출력: 양식 -> `<항목1|항목2|...|항목20>` (반드시 꺾쇠괄호 `< >`만 사용. 중괄호 `{ }`나 대괄호 `[ ]`로 임의 변경 절대 금지) --- ### 3. 추가 제안
* 프롬프트를 추가로 변주해볼 만한 구체적인 후속 질문 2~3개 번호로 제시
---

[후속 질문] 두 코드 블록 출력이 끝난 뒤, 블록 바깥 하단에 사용자가 추가로 시도해볼 만한 변주 방향에 대한 예시 프롬프트 문장을 포함한 구체적인 후속 질문 2~3개를 제시하세요.

[입력 양식]

(선택) 특별히 선호하는 톤/방향:

변주할 문구:"""
        val DEFAULT = VariationPromptConfig(DEFAULT_VARIATION_PROMPT)
    }
}
