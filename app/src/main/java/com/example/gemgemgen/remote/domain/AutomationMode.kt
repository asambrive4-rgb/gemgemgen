// 역할: 단독 실행 모드와 원격 수신 또는 제어 모드 구분을 정의합니다.
package com.example.gemgemgen.remote.domain

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
