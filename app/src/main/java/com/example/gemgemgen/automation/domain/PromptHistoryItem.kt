// 역할: 과거에 실행했던 개별 프롬프트 기록의 내용과 생성 시각 데이터를 정의합니다.
package com.example.gemgemgen.automation.domain

data class PromptHistoryItem(
    val id: String,
    val prompt: String,
    val targetApp: AutomationTargetApp,
    val createdAtMillis: Long
)
