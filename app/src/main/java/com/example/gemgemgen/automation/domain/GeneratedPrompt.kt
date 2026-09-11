// 역할: 동적 치환 또는 와일드카드 확장이 완료된 최종 생성 프롬프트 데이터를 표현합니다.
package com.example.gemgemgen.automation.domain

data class GeneratedPrompt(
    val index: Int,
    val basePrompt: String,
    val finalPrompt: String,
    val replacements: Map<String, String>
)
