package com.example.gemgemgen.automation.domain

import com.example.gemgemgen.core.AppDefaults

object RepeatCountParser {
    fun normalizeInput(value: String): String {
        return value.filter { it.isDigit() }
    }

    fun parse(value: String): Int {
        return normalizeInput(value)
            .toIntOrNull()
            ?.coerceIn(1, 999)
            ?: AppDefaults.DEFAULT_REPEAT_COUNT
    }
}

