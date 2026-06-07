package com.example.gemgemgen

object RepeatCountParser {
    fun parse(value: String): Int {
        return value.toIntOrNull()?.coerceIn(1, 999) ?: AppDefaults.DEFAULT_REPEAT_COUNT
    }
}
