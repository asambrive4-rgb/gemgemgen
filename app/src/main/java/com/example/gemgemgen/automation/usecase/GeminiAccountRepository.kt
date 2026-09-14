// 역할: 사용자가 등록한 Gemini 계정 목록을 영구 저장소에서 불러오고 저장하는 인터페이스를 정의합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.GeminiAccountProfile

interface GeminiAccountRepository {
    fun loadAccounts(): List<GeminiAccountProfile>
    fun saveAccounts(accounts: List<GeminiAccountProfile>)
}
