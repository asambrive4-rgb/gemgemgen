// 역할: 현재 실행 중인 자동화 작업 프로세스의 생명주기와 취소 토큰을 보관합니다.
package com.example.gemgemgen.automation.android

import com.example.gemgemgen.automation.usecase.ExecuteAutomationLoopUseCase

internal object ProcessAutomationHolder {
    @Volatile
    private var instance: ExecuteAutomationLoopUseCase? = null
    private val lock = Any()

    fun getOrCreate(create: () -> ExecuteAutomationLoopUseCase): ExecuteAutomationLoopUseCase {
        instance?.let { return it }
        return synchronized(lock) {
            instance ?: create().also { instance = it }
        }
    }

    fun current(): ExecuteAutomationLoopUseCase? = instance

    fun onAccessibilityLost() {
        current()?.onAccessibilityLost()
    }
}
