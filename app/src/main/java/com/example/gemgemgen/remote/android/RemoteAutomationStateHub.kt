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
