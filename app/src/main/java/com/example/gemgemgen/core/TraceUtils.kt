// 역할: 앱 실행 중 성능 병목과 주요 처리 구간을 측정하는 추적 로그 유틸리티를 제공합니다.
package com.example.gemgemgen.core

import android.os.Trace

inline fun <T> traceSection(name: String, block: () -> T): T {
    val tracingStarted = try {
        Trace.beginSection(name)
        true
    } catch (_: RuntimeException) {
        false
    }

    return try {
        block()
    } finally {
        if (tracingStarted) {
            try {
                Trace.endSection()
            } catch (_: RuntimeException) {
                // Android framework methods are mocked in local unit tests.
            }
        }
    }
}
