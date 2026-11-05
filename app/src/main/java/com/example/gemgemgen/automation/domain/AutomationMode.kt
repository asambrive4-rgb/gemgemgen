// 역할: 단독 실행 모드(NORMAL)와 원격 송신(SENDER) 또는 원격 수신(RECEIVER) 모드 구분을 정의합니다.
package com.example.gemgemgen.automation.domain

enum class AutomationMode(val storageValue: String) {
    NORMAL("normal"),
    SENDER("sender"),
    RECEIVER("receiver");

    companion object {
        fun fromStorageValue(value: String?): AutomationMode {
            return entries.firstOrNull { it.storageValue == value } ?: NORMAL
        }
    }
}
