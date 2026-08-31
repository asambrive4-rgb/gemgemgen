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
