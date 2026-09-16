// 역할: 접근성 서비스를 통한 Gemini 계정 전환 및 앱 실행 기능을 추상화하는 포트 인터페이스입니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.android.GeminiAccountSwitchResult

interface GeminiAccountSwitcherGateway {
    val isServiceAvailable: Boolean
    suspend fun switchAccount(
        identifier: String,
        alias: String,
        onProgress: (phase: String, message: String) -> Unit
    ): GeminiAccountSwitchResult
    fun launchGeminiForManualSwitch(): Boolean
}
