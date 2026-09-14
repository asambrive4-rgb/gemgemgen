// 역할: 사용자가 등록한 Gemini 계정(ID) 정보(별칭, 식별자, 순서, 활성 상태)를 정의합니다.
package com.example.gemgemgen.automation.domain

data class GeminiAccountProfile(
    val id: String,
    val alias: String,
    val identifier: String,
    val order: Int,
    val isActive: Boolean = false
)
