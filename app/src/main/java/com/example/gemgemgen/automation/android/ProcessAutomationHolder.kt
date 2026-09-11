// 역할: 현재 실행 중인 자동화 작업 프로세스의 생명주기와 취소 토큰을 보관합니다.
package com.example.gemgemgen.automation.android

import com.example.gemgemgen.automation.usecase.RunAutomationUseCase

internal object ProcessAutomationHolder {
    @Volatile
    private var instance: RunAutomationUseCase? = null
    private val lock = Any()

    fun getOrCreate(create: () -> RunAutomationUseCase): RunAutomationUseCase {
        instance?.let { return it }
        return synchronized(lock) {
            instance ?: create().also { instance = it }
        }
    }

    fun current(): RunAutomationUseCase? = instance

    fun onAccessibilityLost() {
        current()?.onAccessibilityLost()
    }
}
