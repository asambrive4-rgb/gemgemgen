package com.example.gemgemgen.remote.usecase

import java.util.concurrent.atomic.AtomicReference

/** 수신 기기에서 가장 최근에 승인한 요청만 현재 작업으로 인정한다. */
class ManageReceivedAutomationUseCase {
    private val activeRequestId = AtomicReference<String?>(null)

    fun acceptLatest(requestId: String): String? {
        require(requestId.isNotBlank())
        return activeRequestId.getAndSet(requestId)
    }

    fun canPublish(requestId: String): Boolean {
        return activeRequestId.get() == requestId
    }

    fun stopIfCurrent(requestId: String): Boolean {
        return activeRequestId.compareAndSet(requestId, null)
    }

    fun finishIfCurrent(requestId: String): Boolean {
        return activeRequestId.compareAndSet(requestId, null)
    }
}
