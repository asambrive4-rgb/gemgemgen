// 역할: 원격 통신 네트워크 채널 연결, 자동화 요청, 메모리 정리 및 Gemini 계정 전환 송수신 인터페이스를 정의합니다.
package com.example.gemgemgen.remote.usecase

import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import kotlinx.coroutines.flow.StateFlow

interface RemoteAutomationGateway {
    val status: StateFlow<RemoteAutomationStatus>

    fun selectMode(mode: AutomationMode)
    suspend fun pair(pairingCode: String): RemoteActionResult
    suspend fun disconnect(): RemoteActionResult
    suspend fun send(request: RemoteAutomationRequest)
    fun forceStop(requestId: String?)
    fun updateRepeatCount(requestId: String, repeatCount: Int)
    suspend fun cleanMemory(): RemoteActionResult
    suspend fun switchGeminiAccount(email: String, alias: String): RemoteActionResult = RemoteActionResult.Success
}
