// 역할: 메인 단축어 및 다중 추가 단축어와 선택적 카테고리로 긴 프롬프트 문구를 분류·관리하고 즉시 치환할 수 있도록 저장하는 상용구(스니펫) 데이터 모델입니다.
package com.example.gemgemgen.automation.domain

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 스마트폰의 텍스트 대치(자주 쓰는 문구) 기능과 같이
 * 대표 메인 단축어, 다중 추가 단축어(동의어/별칭), 선택적 카테고리, 실제 긴 프롬프트 본문을 매핑하여 보관하는 도메인 모델.
 */
@Serializable
data class PromptSnippet(
    val id: String = UUID.randomUUID().toString(),
    val shortcut: String,
    @SerialName("additional_shortcuts")
    val additionalShortcuts: List<String> = emptyList(),
    val content: String,
    val category: String = "",
    @SerialName("created_at")
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    /** 메인 단축어와 추가 단축어를 모두 포함한 고유 단축어 목록 (공백 제외) */
    val allShortcuts: List<String>
        get() = buildList {
            val trimmed = shortcut.trim()
            if (trimmed.isNotEmpty()) add(trimmed)
            additionalShortcuts.forEach { alias ->
                val aliasTrimmed = alias.trim()
                if (aliasTrimmed.isNotEmpty() && !any { it.equals(aliasTrimmed, ignoreCase = true) }) {
                    add(aliasTrimmed)
                }
            }
        }
}


