// 역할: 단축어와 선택적 카테고리로 긴 프롬프트 문구를 분류·관리하고 즉시 치환할 수 있도록 저장하는 상용구(스니펫) 데이터 모델입니다.
package com.example.gemgemgen.automation.domain

import java.util.UUID

/**
 * 스마트폰의 텍스트 대치(자주 쓰는 문구) 기능과 같이
 * 짧은 단축어(별칭), 선택적 카테고리, 실제 긴 프롬프트 본문을 매핑하여 보관하는 도메인 모델.
 */
data class PromptSnippet(
    val id: String = UUID.randomUUID().toString(),
    val shortcut: String,
    val content: String,
    val category: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

