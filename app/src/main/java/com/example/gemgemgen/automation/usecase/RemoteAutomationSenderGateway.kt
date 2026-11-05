// 역할: 원격 디바이스로 자동화 실행, 강제 중지, 메모리 정리 및 계정 전환 명령을 전달하는 송신 게이트웨이 인터페이스입니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.RemoteActionResult

interface RemoteAutomationSenderGateway {
    suspend fun start(request: AutomationRunRequest): RemoteActionResult
    fun forceStop()
    suspend fun cleanMemory(): RemoteActionResult
    suspend fun switchGeminiAccount(email: String, alias: String): RemoteActionResult
}
