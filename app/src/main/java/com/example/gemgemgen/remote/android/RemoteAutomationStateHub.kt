// 역할: 원격 제어 연결 상태와 수신된 최신 메시지를 전역으로 전파하고 중계합니다.
package com.example.gemgemgen.remote.android

import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal object RemoteAutomationStateHub {
    private val mutableStatus = MutableStateFlow(RemoteAutomationStatus())
    val status: StateFlow<RemoteAutomationStatus> = mutableStatus.asStateFlow()

    fun update(transform: (RemoteAutomationStatus) -> RemoteAutomationStatus) {
        mutableStatus.update(transform)
    }
}
