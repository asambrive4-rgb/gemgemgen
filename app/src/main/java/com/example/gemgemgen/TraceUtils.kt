package com.example.gemgemgen

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
