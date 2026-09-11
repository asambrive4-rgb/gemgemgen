// 역할: 사용자가 입력한 반복 횟수 텍스트를 안전한 정수 숫자로 변환하고 검증합니다.
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

