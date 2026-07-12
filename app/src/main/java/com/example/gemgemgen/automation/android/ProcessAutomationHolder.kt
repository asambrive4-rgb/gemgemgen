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
